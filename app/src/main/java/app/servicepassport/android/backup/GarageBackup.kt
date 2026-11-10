package app.servicepassport.android.backup

import androidx.room.withTransaction
import app.servicepassport.android.data.*
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
    /** Documents or indicator readings: this app version has no table for them, so restoring would lose data. */
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
)

class BackupRefused(val problem: RestoreProblem) : IllegalArgumentException(problem.name)

/** Lossless mapping between Room v3 rows and the export format. No value is recomputed or guessed. */
object BackupMapper {
    private fun date(day: Long): String = LocalDate.ofEpochDay(day).toString()
    private fun day(date: String): Long = LocalDate.parse(date).toEpochDay()

    fun export(snapshots: List<GarageSnapshot>): ExportData = ExportData(
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
                )
            }
        },
        userRules = snapshots.flatMap { s ->
            s.intervals.sortedBy { it.id }.map { ExportUserRule(it.id, it.vehicleId, it.actionCode, it.operationCode, it.distanceKm, it.months, it.revision, it.createdAtUtc, it.updatedAtUtc) }
        },
    )

    /** @throws BackupRefused when the content cannot be stored without loss or reinterpretation */
    fun rows(data: ExportData): GarageRows {
        fun refuse(problem: RestoreProblem): Nothing = throw BackupRefused(problem)
        if (data.vehicles.isEmpty()) refuse(RestoreProblem.EMPTY_BACKUP)
        if (data.vehicles.size > 1) refuse(RestoreProblem.SEVERAL_VEHICLES)
        if (data.documents.isNotEmpty() || data.indicatorReadings.isNotEmpty()) refuse(RestoreProblem.UNSUPPORTED_CONTENT)
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
            if (e.documentIds.isNotEmpty()) refuse(RestoreProblem.UNSUPPORTED_CONTENT)
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
        return GarageRows(vehicles, events, actions, readings, selections, intervals)
    }
}

/**
 * Writes and restores a complete copy of the local database.
 * Restore replaces all local data in one transaction; any refusal leaves the database untouched.
 */
class GarageBackup(private val database: GarageDatabase, private val clock: Clock = Clock.systemDefaultZone(), private val settings: ReminderStore? = null) {
    private val dao get() = database.vehicles()

    private object Discard : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    suspend fun write(out: OutputStream, appVersion: String, password: CharArray? = null): ExportManifest = withContext(Dispatchers.IO) {
        val data = database.withTransaction { BackupMapper.export(dao.vehicleIds().mapNotNull { dao.garage(it) }) }
            .copy(settings = settings?.exportSettings().orEmpty())
        ExportArchive.write(out, data, emptyList(), Instant.now(clock).toString(), appVersion, LocalDate.now(clock), password)
    }

    suspend fun restore(input: InputStream, password: CharArray? = null): RestoreOutcome = withContext(Dispatchers.IO) {
        // The whole archive is verified before the first row changes. Documents are not supported yet and are discarded.
        val data = when (val result = ExportArchive.read(input, LocalDate.now(clock), password = password) { Discard }) {
            is ImportResult.Failure -> return@withContext RestoreOutcome.ArchiveRefused(result.reason)
            is ImportResult.Success -> result.data
        }
        val rows = try { BackupMapper.rows(data) } catch (refused: BackupRefused) { return@withContext RestoreOutcome.ContentRefused(refused.problem) }
        database.withTransaction {
            dao.deleteAllVehicles()
            rows.vehicles.forEach { dao.save(it) }
            rows.events.forEach { dao.insertEvent(it) }
            dao.insertActions(rows.actions)
            rows.readings.forEach { dao.saveReading(it) }
            rows.selections.forEach { dao.selectReading(it) }
            rows.intervals.forEach { dao.insertInterval(it) }
        }
        settings?.importSettings(data.settings)
        RestoreOutcome.Restored
    }
}
