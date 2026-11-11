package app.servicepassport.android.backup

import androidx.room.withTransaction
import app.servicepassport.android.data.*
import app.servicepassport.android.documents.DocumentStore
import app.servicepassport.android.documents.DocumentTypes
import app.servicepassport.android.integration.EngineAdapter
import app.servicepassport.android.reminders.ReminderStore
import app.servicepassport.contract.ServiceTask
import app.servicepassport.export.*
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Why a technically valid archive cannot become this app's database. */
enum class RestoreProblem {
    /** An action or interval uses a code outside the shared service contract. */
    UNKNOWN_TASK,
    /** History stores NOK only; another currency must not be relabelled. */
    UNSUPPORTED_CURRENCY,
    /** A document that is not tied to exactly one entry or has an unsupported type, or a display reading for another task. */
    UNSUPPORTED_CONTENT,
    /** The app handles one vehicle. */
    SEVERAL_VEHICLES,
    EMPTY_BACKUP,
}

sealed interface RestoreOutcome {
    data object Restored : RestoreOutcome
    /** The archive itself was refused (wrong password, damaged, newer format, inconsistent). */
    data class ArchiveRefused(val reason: ImportFailure) : RestoreOutcome
    data class ContentRefused(val problem: RestoreProblem) : RestoreOutcome
}

/** All rows of one database, in insertion order that satisfies the foreign keys. */
data class GarageRows(
    val vehicles: List<VehicleEntity>,
    val events: List<ServiceEventEntity>,
    val actions: List<ServiceActionEntity>,
    val readings: List<OdometerReadingEntity>,
    val selections: List<CurrentOdometerEntity>,
    val intervals: List<UserIntervalEntity>,
    val indicators: List<IndicatorReadingEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
)

class BackupRefused(val problem: RestoreProblem) : IllegalArgumentException(problem.name)

/** Lossless mapping between Room rows and the export format. No value is recomputed or guessed. */
object BackupMapper {
    private fun date(day: Long): String = LocalDate.ofEpochDay(day).toString()
    private fun day(date: String): Long = LocalDate.parse(date).toEpochDay()

    private fun path(document: DocumentEntity) = ExportFormat.DOCUMENTS_DIR + document.fileName

    /** @param hasFile a document whose file is gone cannot be copied; it is left out instead of producing a broken archive */
    fun export(snapshots: List<GarageSnapshot>, hasFile: (DocumentEntity) -> Boolean = { true }): ExportData = ExportData(
        vehicles = snapshots.map { s ->
            val v = s.vehicle
            ExportVehicle(
                id = v.id, displayName = v.displayName, modelYear = v.modelYear,
                firstRegistrationDate = v.firstRegistrationDay?.let(::date), make = v.make, model = v.model, generation = v.generation,
                vin = v.vin, registrationNumber = v.registrationNumber, engineCode = v.engineCode, gearboxType = v.gearboxType,
                gearboxCode = v.gearboxCode, powertrain = v.powertrain, market = v.market, serviceRegime = v.serviceRegime,
                usageProfile = v.usageProfile, configurationRevision = v.configurationRevision,
                createdAtUtc = v.createdAtUtc, updatedAtUtc = v.updatedAtUtc, currentReadingId = s.currentReading?.id,
            )
        },
        odometerReadings = snapshots.flatMap { s ->
            s.readings.sortedBy { it.id }.map { ExportOdometerReading(it.id, it.vehicleId, it.eventId, date(it.observedDay), it.km, it.exceptionReason, it.revision, it.createdAtUtc, it.updatedAtUtc) }
        },
        serviceEvents = snapshots.flatMap { s ->
            s.events.sortedBy { it.event.id }.map { record ->
                val e = record.event
                ExportServiceEvent(
                    e.id, e.vehicleId, date(e.performedDay), e.costMinor, e.currency, e.note, e.revision, e.createdAtUtc, e.updatedAtUtc,
                    record.orderedActions.map { ExportAction(it.id, it.actionCode, it.operationCode, it.customLabel) },
                    record.documents.filter(hasFile).map { it.id }.sorted(),
                )
            }
        },
        documents = snapshots.flatMap { s ->
            s.events.flatMap { it.documents }.filter(hasFile).sortedBy { it.id }.map { ExportDocument(it.id, it.vehicleId, path(it), it.mimeType, date(it.addedDay), it.createdAtUtc) }
        },
        userRules = snapshots.flatMap { s ->
            s.intervals.sortedBy { it.id }.map { ExportUserRule(it.id, it.vehicleId, it.actionCode, it.operationCode, it.distanceKm, it.months, it.revision, it.createdAtUtc, it.updatedAtUtc) }
        },
        indicatorReadings = snapshots.flatMap { s ->
            s.indicators.sortedBy { it.id }.map { ExportIndicatorReading(it.id, it.vehicleId, EngineAdapter.INDICATOR_TASK, date(it.observedDay), it.odometerKm.toInt(), it.remainingKm, it.remainingDays, it.revision, it.createdAtUtc, it.updatedAtUtc) }
        },
    )

    /** @throws BackupRefused when the content cannot be stored without loss or reinterpretation */
    fun rows(data: ExportData, files: List<ExportedFile> = emptyList()): GarageRows {
        fun refuse(problem: RestoreProblem): Nothing = throw BackupRefused(problem)
        if (data.vehicles.isEmpty()) refuse(RestoreProblem.EMPTY_BACKUP)
        if (data.vehicles.size > 1) refuse(RestoreProblem.SEVERAL_VEHICLES)
        if (data.indicatorReadings.any { it.taskCode != EngineAdapter.INDICATOR_TASK }) refuse(RestoreProblem.UNSUPPORTED_CONTENT)
        fun known(action: String, operation: String) { if (ServiceTask.fromTaskCode("$action:$operation") == null) refuse(RestoreProblem.UNKNOWN_TASK) }

        val vehicles = data.vehicles.map { v ->
            VehicleEntity(
                id = v.id, displayName = v.displayName, modelYear = v.modelYear, createdAtUtc = v.createdAtUtc, updatedAtUtc = v.updatedAtUtc,
                firstRegistrationDay = v.firstRegistrationDate?.let(::day), make = v.make, model = v.model, generation = v.generation,
                vin = v.vin, registrationNumber = v.registrationNumber, engineCode = v.engineCode, gearboxType = v.gearboxType,
                gearboxCode = v.gearboxCode, powertrain = v.powertrain, market = v.market, serviceRegime = v.serviceRegime,
                usageProfile = v.usageProfile, configurationRevision = v.configurationRevision,
            )
        }
        val events = data.serviceEvents.map { e ->
            if (e.costMinor == null && e.currency != null || e.costMinor != null && e.currency != "NOK") refuse(RestoreProblem.UNSUPPORTED_CURRENCY)
            ServiceEventEntity(e.id, e.vehicleId, day(e.date), e.costMinor, e.currency, e.note, e.revision, e.createdAtUtc, e.updatedAtUtc)
        }
        val actions = data.serviceEvents.flatMap { e ->
            e.actions.mapIndexed { position, a ->
                if (a.customLabel == null) known(requireNotNull(a.actionCode), requireNotNull(a.operationCode))
                ServiceActionEntity(a.id, e.id, position, a.actionCode, a.operationCode, a.customLabel)
            }
        }
        val readings = data.odometerReadings.map { OdometerReadingEntity(it.id, it.vehicleId, it.eventId, day(it.date), it.km, it.exceptionReason, it.revision, it.createdAtUtc, it.updatedAtUtc) }
        val selections = data.vehicles.mapNotNull { v -> v.currentReadingId?.let { CurrentOdometerEntity(v.id, it) } }
        val intervals = data.userRules.map {
            known(it.actionCode, it.operationCode)
            UserIntervalEntity(it.id, it.vehicleId, it.actionCode, it.operationCode, it.km, it.months, it.revision, it.createdAtUtc, it.updatedAtUtc)
        }
        val indicators = data.indicatorReadings.map { IndicatorReadingEntity(it.id, it.vehicleId, day(it.date), it.odometerKm.toLong(), it.remainingKm, it.remainingDays, it.revision, it.createdAtUtc, it.updatedAtUtc) }
        // A document belongs to exactly one entry of the same vehicle; its size and checksum come from the verified manifest.
        val owners = data.serviceEvents.flatMap { e -> e.documentIds.map { it to e } }
        if (owners.map { it.first }.distinct().size != owners.size) refuse(RestoreProblem.UNSUPPORTED_CONTENT)
        val ownerOf = owners.toMap()
        val manifest = files.associateBy { it.path }
        val documents = data.documents.map { d ->
            val event = ownerOf[d.id] ?: refuse(RestoreProblem.UNSUPPORTED_CONTENT)
            val file = manifest[d.path] ?: refuse(RestoreProblem.UNSUPPORTED_CONTENT)
            val name = d.path.removePrefix(ExportFormat.DOCUMENTS_DIR)
            if (event.vehicleId != d.vehicleId || !DocumentTypes.safeName(name) || !DocumentTypes.supported(d.mimeType)) refuse(RestoreProblem.UNSUPPORTED_CONTENT)
            DocumentEntity(d.id, d.vehicleId, event.id, name, d.mimeType, file.sha256, file.bytes, day(d.addedOn), d.createdAtUtc)
        }
        return GarageRows(vehicles, events, actions, readings, selections, intervals, indicators, documents)
    }
}

/**
 * Writes and restores a complete copy of the local database.
 * Restore replaces all local data in one transaction; any refusal leaves the database untouched.
 */
class GarageBackup(
    private val database: GarageDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val settings: ReminderStore? = null,
    private val documents: DocumentStore? = null,
) {
    private val dao get() = database.vehicles()

    private object Discard : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    suspend fun write(out: OutputStream, appVersion: String, password: CharArray? = null): ExportManifest = withContext(Dispatchers.IO) {
        val snapshots = database.withTransaction { dao.vehicleIds().mapNotNull { dao.garage(it) } }
        val stored = snapshots.flatMap { s -> s.events.flatMap { it.documents } }.filter { documents?.exists(it) == true }
        val ids = stored.map { it.id }.toSet()
        val data = BackupMapper.export(snapshots) { it.id in ids }.copy(settings = settings?.exportSettings().orEmpty())
        val sources = stored.map { document -> DocumentSource(ExportFormat.DOCUMENTS_DIR + document.fileName) { requireNotNull(documents).open(document) } }
        ExportArchive.write(out, data, sources, Instant.now(clock).toString(), appVersion, LocalDate.now(clock), password)
    }

    /** Removes every vehicle with its history, readings, intervals and document files, and resets reminder preferences. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        database.withTransaction { dao.deleteAllVehicles() }
        documents?.deleteAll()
        settings?.reset()
    }

    suspend fun restore(input: InputStream, password: CharArray? = null): RestoreOutcome = withContext(Dispatchers.IO) {
        // Nothing else may move document files while a restore reads, verifies and replaces them.
        documents?.exclusive { restoreLocked(input, password) } ?: restoreLocked(input, password)
    }

    private suspend fun restoreLocked(input: InputStream, password: CharArray?): RestoreOutcome {
        // The whole archive is verified before the first row changes. Document files wait in a separate directory until then.
        documents?.abortRestore()
        val result = try {
            ExportArchive.read(input, LocalDate.now(clock), password = password) { path ->
                documents?.restoreSink(path.removePrefix(ExportFormat.DOCUMENTS_DIR)) ?: Discard
            }
        } catch (failure: Exception) {
            documents?.abortRestore()
            throw failure
        }
        val success = when (result) {
            is ImportResult.Failure -> { documents?.abortRestore(); return RestoreOutcome.ArchiveRefused(result.reason) }
            is ImportResult.Success -> result
        }
        val data = success.data
        val rows = try {
            BackupMapper.rows(data, success.manifest.files).also { if (it.documents.isNotEmpty() && documents == null) throw BackupRefused(RestoreProblem.UNSUPPORTED_CONTENT) }
        } catch (refused: BackupRefused) {
            documents?.abortRestore()
            return RestoreOutcome.ContentRefused(refused.problem)
        }
        database.withTransaction {
            dao.deleteAllVehicles()
            rows.vehicles.forEach { dao.save(it) }
            rows.events.forEach { dao.insertEvent(it) }
            dao.insertActions(rows.actions)
            rows.readings.forEach { dao.saveReading(it) }
            rows.selections.forEach { dao.selectReading(it) }
            rows.intervals.forEach { dao.insertInterval(it) }
            rows.indicators.forEach { dao.insertIndicator(it) }
            rows.documents.forEach { dao.insertDocument(it) }
        }
        documents?.commitRestore(rows.documents.map { it.fileName }.toSet())
        settings?.importSettings(data.settings)
        return RestoreOutcome.Restored
    }
}
