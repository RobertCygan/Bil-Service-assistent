package app.servicepassport.android.data

import androidx.room.withTransaction
import java.time.Clock
import java.time.LocalDate
import java.time.Year
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProfileInput(
    val name: String, val modelYear: Int?, val firstRegistrationDay: Long? = null,
    val make: String? = null, val model: String? = null, val generation: String? = null,
    val vin: String? = null, val registrationNumber: String? = null,
    val engineCode: String? = null, val gearboxType: String? = null, val gearboxCode: String? = null,
    val powertrain: String? = null, val market: String? = null, val serviceRegime: String? = null,
    val usageProfile: String? = null,
)
enum class ProfileProblem { NAME_REQUIRED, NAME_TOO_LONG, INVALID_YEAR }
object ProfileValidation {
    fun problem(name: String, year: String, currentYear: Int): ProfileProblem? = when {
        name.isBlank() -> ProfileProblem.NAME_REQUIRED
        name.trim().length > 80 -> ProfileProblem.NAME_TOO_LONG
        year.isNotBlank() && (year.toIntOrNull() == null || year.toInt() !in 1900..currentYear + 1) -> ProfileProblem.INVALID_YEAR
        else -> null
    }
}

class ProfileRepository(private val database: GarageDatabase, private val clock: Clock = Clock.systemDefaultZone()) {
    private val dao get() = database.vehicles()
    val today: LocalDate get() = LocalDate.now(clock)
    fun observeProfile() = dao.observeProfile()
    fun observeGarage() = dao.observeGarage()
    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    private fun String?.code(): String? = clean()?.uppercase(Locale.ROOT)
    private suspend fun <T> transaction(block: suspend () -> T): T = withContext(Dispatchers.IO) { database.withTransaction { block() } }
    private fun reject(problem: InputProblem): Nothing = throw InputRejected(problem)
    private suspend fun garage(vehicleId: String): GarageSnapshot = dao.garage(vehicleId) ?: reject(InputProblem.NOT_FOUND)

    suspend fun save(input: ProfileInput) = transaction {
        require(ProfileValidation.problem(input.name, input.modelYear?.toString().orEmpty(), Year.now(clock).value) == null)
        input.firstRegistrationDay?.let { HistoryValidation.day(it, today) }
        val values = listOf(input.make, input.model, input.generation, input.vin, input.registrationNumber, input.engineCode, input.gearboxType, input.gearboxCode, input.powertrain, input.market, input.serviceRegime, input.usageProfile)
        if (values.any { (it?.trim()?.length ?: 0) > 80 }) reject(InputProblem.INVALID_PROFILE)
        val old = dao.getProfile(); val now = clock.millis()
        var updated = VehicleEntity(
            id = old?.id ?: UUID.randomUUID().toString(), displayName = input.name.trim(), modelYear = input.modelYear,
            createdAtUtc = old?.createdAtUtc ?: now, updatedAtUtc = now,
            firstRegistrationDay = input.firstRegistrationDay, make = input.make.clean(), model = input.model.clean(),
            generation = input.generation.clean(), vin = input.vin.code(), registrationNumber = input.registrationNumber.code(),
            engineCode = input.engineCode.code(), gearboxType = input.gearboxType.clean(), gearboxCode = input.gearboxCode.code(),
            powertrain = input.powertrain.clean(), market = input.market.code(), serviceRegime = input.serviceRegime.clean(), usageProfile = input.usageProfile.clean(),
            configurationRevision = old?.configurationRevision ?: 1,
        )
        if (old != null) {
            val before = old.copy(displayName = updated.displayName, updatedAtUtc = updated.updatedAtUtc)
            if (before != updated) updated = updated.copy(configurationRevision = old.configurationRevision + 1)
        }
        dao.save(updated)
    }

    private fun verifyReading(snapshot: GarageSnapshot, excludedId: String?, day: Long, km: Long, current: Boolean, reason: String, acknowledged: Boolean, token: String?) {
        HistoryValidation.day(day, today); HistoryValidation.km(km)
        if (reason.length > 1000) reject(InputProblem.NOTE_TOO_LONG)
        val warnings = ReadingConsistency.warnings(day, km, snapshot.readings.filter { it.id != excludedId }, snapshot.currentReading, current)
        if (warnings.isNotEmpty() && (!acknowledged || reason.isBlank() || token != ReadingConsistency.token(snapshot, excludedId, day, km, current))) reject(InputProblem.WARNINGS_NEED_REVIEW)
    }

    suspend fun saveReading(input: ReadingInput) = transaction {
        val snapshot = garage(input.vehicleId)
        val old = dao.reading(input.id)
        if (old != null && (old.vehicleId != input.vehicleId || old.eventId != null)) reject(InputProblem.NOT_FOUND)
        if (old == null && input.expectedRevision != null) reject(InputProblem.NOT_FOUND)
        if (old != null && old.revision != input.expectedRevision) {
            if (input.expectedRevision == null && old.observedDay == input.observedDay && old.km == input.km && old.exceptionReason == input.exceptionReason.trim()) return@transaction
            reject(InputProblem.CONFLICT)
        }
        verifyReading(snapshot, old?.id, input.observedDay, input.km, input.useAsCurrent, input.exceptionReason, input.warningsAcknowledged, input.reviewToken)
        val changed = old != null && (old.observedDay != input.observedDay || old.km != input.km)
        if (changed && snapshot.currentReading?.id == old?.id && !input.useAsCurrent) dao.clearSelection(input.vehicleId)
        val now = clock.millis()
        dao.saveReading(OdometerReadingEntity(input.id, input.vehicleId, null, input.observedDay, input.km, input.exceptionReason.trim(), (old?.revision ?: 0) + 1, old?.createdAtUtc ?: now, now))
        if (input.useAsCurrent) dao.selectReading(CurrentOdometerEntity(input.vehicleId, input.id))
    }

    suspend fun selectCurrent(input: CurrentReadingInput) = transaction {
        val reading = dao.reading(input.readingId) ?: reject(InputProblem.NOT_FOUND)
        if (reading.vehicleId != input.vehicleId) reject(InputProblem.NOT_FOUND)
        if (reading.revision != input.expectedRevision) reject(InputProblem.CONFLICT)
        val snapshot = garage(input.vehicleId)
        val reason = input.exceptionReason?.trim() ?: reading.exceptionReason
        verifyReading(snapshot, reading.id, reading.observedDay, reading.km, true, reason, input.warningsAcknowledged, input.reviewToken)
        if (reason != reading.exceptionReason) {
            val now = clock.millis()
            dao.saveReading(reading.copy(exceptionReason = reason, revision = reading.revision + 1, updatedAtUtc = now))
            // A service owns its reading. Protect an open service editor from overwriting this review.
            reading.eventId?.let { eventId ->
                val event = dao.event(eventId)?.event ?: reject(InputProblem.NOT_FOUND)
                dao.updateEvent(event.copy(revision = event.revision + 1, updatedAtUtc = now))
            }
        }
        dao.selectReading(CurrentOdometerEntity(input.vehicleId, reading.id))
    }

    suspend fun deleteReading(vehicleId: String, id: String, expectedRevision: Long) = transaction {
        val reading = dao.reading(id) ?: reject(InputProblem.NOT_FOUND)
        if (reading.vehicleId != vehicleId || reading.eventId != null) reject(InputProblem.NOT_FOUND)
        if (reading.revision != expectedRevision) reject(InputProblem.CONFLICT)
        dao.deleteReading(id) // FK cascade clears an explicit current selection, never substitutes another reading.
    }

    suspend fun saveService(input: ServiceInput): String = transaction {
        HistoryValidation.service(input, today)
        val snapshot = garage(input.vehicleId)
        val old = dao.event(input.id)
        if (old != null && old.event.vehicleId != input.vehicleId) reject(InputProblem.NOT_FOUND)
        if (old == null && input.expectedRevision != null) reject(InputProblem.NOT_FOUND)
        if (old != null && old.event.revision != input.expectedRevision) {
            if (input.expectedRevision == null && sameService(old, input)) return@transaction old.event.id
            reject(InputProblem.CONFLICT)
        }
        if (input.km != null) verifyReading(snapshot, old?.reading?.id, input.performedDay, input.km, input.useAsCurrent, input.exceptionReason, input.warningsAcknowledged, input.reviewToken)
        val now = clock.millis()
        val event = ServiceEventEntity(input.id, input.vehicleId, input.performedDay, input.costMinor, if (input.costMinor == null) null else "NOK", input.note.trim(), (old?.event?.revision ?: 0) + 1, old?.event?.createdAtUtc ?: now, now)
        if (old == null) dao.insertEvent(event) else dao.updateEvent(event)
        dao.clearActions(input.id)
        dao.insertActions(input.actions.mapIndexed { position, work -> ServiceActionEntity("${input.id}:$position", input.id, position, work.actionCode, work.operationCode, work.customLabel?.trim()) })
        val previous = old?.reading
        if (input.km == null) {
            previous?.let { dao.deleteReading(it.id) }
        } else {
            val id = previous?.id ?: UUID.randomUUID().toString()
            val changed = previous != null && (previous.observedDay != input.performedDay || previous.km != input.km)
            if (changed && snapshot.currentReading?.id == id && !input.useAsCurrent) dao.clearSelection(input.vehicleId)
            dao.saveReading(OdometerReadingEntity(id, input.vehicleId, input.id, input.performedDay, input.km, input.exceptionReason.trim(), (previous?.revision ?: 0) + 1, previous?.createdAtUtc ?: now, now))
            if (input.useAsCurrent) dao.selectReading(CurrentOdometerEntity(input.vehicleId, id))
        }
        input.id
    }

    private fun sameService(old: ServiceRecord, input: ServiceInput): Boolean =
        old.event.performedDay == input.performedDay && old.reading?.km == input.km && old.reading?.exceptionReason.orEmpty() == input.exceptionReason.trim() && old.event.costMinor == input.costMinor && old.event.note == input.note.trim() &&
            old.orderedActions.map { WorkInput(it.actionCode, it.operationCode, it.customLabel) } == input.actions.map { it.copy(customLabel = it.customLabel?.trim()) }

    suspend fun deleteService(vehicleId: String, id: String, expectedRevision: Long) = transaction {
        val record = dao.event(id) ?: reject(InputProblem.NOT_FOUND)
        if (record.event.vehicleId != vehicleId) reject(InputProblem.NOT_FOUND)
        if (record.event.revision != expectedRevision) reject(InputProblem.CONFLICT)
        dao.deleteEvent(id)
    }
}
