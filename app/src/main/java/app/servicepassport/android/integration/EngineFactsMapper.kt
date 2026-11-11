package app.servicepassport.android.integration

import app.servicepassport.android.data.GarageSnapshot
import app.servicepassport.contract.AppPolicy
import app.servicepassport.contract.ServiceTask
import java.time.LocalDate

/** Android-owned preparation boundary. This is factual input, not an engine result or a schedule. */
data class PreparedEngineFacts(
    val vehicleId: String,
    val configurationRevision: Long,
    /** Keys correspond to the reviewed engine's ConfigAttribute names. No fuzzy matching. */
    val configuration: Map<String, String>,
    val inServiceDate: LocalDate?,
    /** Null means no explicit selection. A historical reading must never replace it. */
    val currentOdometer: PreparedOdometer?,
    val historicalOdometers: List<PreparedOdometer>,
    val performedActions: List<PreparedPerformedAction>,
    val unmappedActions: List<PreparedCustomAction>,
    val odometerCautions: Set<OdometerCaution>,
    /** Readings of the car's own service display, newest last. */
    val indicators: List<PreparedIndicator> = emptyList(),
    val policy: PreparedEnginePolicy = PreparedEnginePolicy(),
)

data class PreparedOdometer(
    val id: String,
    val eventId: String?,
    val date: LocalDate,
    val km: Int,
    val exceptionReason: String,
)

data class PreparedPerformedAction(
    val eventId: String,
    val actionId: String,
    val task: ServiceTask,
    val performedOn: LocalDate,
    val odometerKm: Int?,
)

data class PreparedIndicator(val id: String, val date: LocalDate, val odometerKm: Int, val remainingKm: Int?, val remainingDays: Int?)

data class PreparedCustomAction(
    val eventId: String,
    val actionId: String,
    val description: String,
)

enum class OdometerCaution { DECREASING_VALUES, DIFFERENT_VALUES_ON_SAME_DAY, RECORDED_EXCEPTION }

data class PreparedEnginePolicy(
    val dueSoonKm: Int = AppPolicy.DUE_SOON_KM,
    val dueSoonDays: Int = AppPolicy.DUE_SOON_DAYS,
    val odometerStaleAfterDays: Int = AppPolicy.ODOMETER_STALE_AFTER_DAYS,
)

/**
 * Maps a transactional Room snapshot to the factual input used by EngineAdapter.
 * service_events contains confirmed history only: a document that was read becomes an entry when the driver saves it.
 * No engine dependency or hidden reconstruction of current mileage is allowed at this boundary.
 */
object EngineFactsMapper {
    fun prepare(snapshot: GarageSnapshot): PreparedEngineFacts {
        val vehicle = snapshot.vehicle
        val configuration = buildMap {
            fun attribute(key: String, value: String?) {
                value?.trim()?.takeIf { it.isNotEmpty() }?.let { put(key, it) }
            }
            fun knownChoice(key: String, value: String?, supported: Set<String>) {
                value?.trim()?.uppercase(java.util.Locale.ROOT)?.takeIf { it in supported }?.let { put(key, it) }
            }
            attribute("MAKE", vehicle.make)
            attribute("MODEL", vehicle.model)
            attribute("GENERATION", vehicle.generation)
            attribute("MODEL_YEAR", vehicle.modelYear?.toString())
            attribute("ENGINE_CODE", vehicle.engineCode)
            attribute("GEARBOX_CODE", vehicle.gearboxCode)
            attribute("MARKET", vehicle.market)
            knownChoice("POWERTRAIN", vehicle.powertrain, setOf("PETROL", "DIESEL", "HYBRID", "PHEV", "EV"))
            knownChoice("GEARBOX_TYPE", vehicle.gearboxType, setOf("MANUAL", "AUTOMATIC", "CVT", "DCT"))
            knownChoice("SERVICE_REGIME", vehicle.serviceRegime, setOf("FIXED", "VARIABLE"))
            knownChoice("USAGE_PROFILE", vehicle.usageProfile, setOf("NORMAL", "SEVERE"))
        }
        val readings = snapshot.readings.map { reading ->
            require(reading.vehicleId == vehicle.id) { "Reading belongs to another vehicle" }
            PreparedOdometer(reading.id, reading.eventId, LocalDate.ofEpochDay(reading.observedDay), kilometers(reading.km), reading.exceptionReason)
        }.sortedWith(compareBy<PreparedOdometer> { it.date }.thenBy { it.km }.thenBy { it.id })
        val current = snapshot.currentReading?.let { selected ->
            readings.single { it.id == selected.id }
        }
        val performed = mutableListOf<PreparedPerformedAction>()
        val custom = mutableListOf<PreparedCustomAction>()
        for (record in snapshot.history) {
            require(record.event.vehicleId == vehicle.id) { "Service belongs to another vehicle" }
            val date = LocalDate.ofEpochDay(record.event.performedDay)
            val reading = record.reading
            require(reading == null || reading.vehicleId == vehicle.id && reading.eventId == record.event.id) { "Invalid service reading ownership" }
            val km = reading?.km?.let(::kilometers)
            for (action in record.orderedActions) {
                require(action.eventId == record.event.id) { "Invalid action ownership" }
                if (action.customLabel != null) {
                    require(action.actionCode == null && action.operationCode == null && action.customLabel.isNotBlank()) { "Invalid custom action" }
                    custom += PreparedCustomAction(record.event.id, action.id, action.customLabel)
                } else {
                    val task = action.actionCode?.let { code ->
                        action.operationCode?.let { operation -> ServiceTask.fromTaskCode("$code:$operation") }
                    }
                    requireNotNull(task) { "Unknown action or operation in confirmed history" }
                    performed += PreparedPerformedAction(record.event.id, action.id, task, date, km)
                }
            }
        }
        val cautions = buildSet {
            if (readings.zipWithNext().any { (a, b) -> a.date < b.date && b.km < a.km }) add(OdometerCaution.DECREASING_VALUES)
            if (readings.groupBy { it.date }.any { (_, group) -> group.map { it.km }.distinct().size > 1 }) add(OdometerCaution.DIFFERENT_VALUES_ON_SAME_DAY)
            if (readings.any { it.exceptionReason.isNotBlank() }) add(OdometerCaution.RECORDED_EXCEPTION)
        }
        return PreparedEngineFacts(
            vehicle.id, vehicle.configurationRevision, configuration,
            vehicle.firstRegistrationDay?.let(LocalDate::ofEpochDay), current, readings,
            performed.toList(), custom.toList(), cautions,
            snapshot.indicators.map {
                require(it.vehicleId == vehicle.id) { "Indicator reading belongs to another vehicle" }
                PreparedIndicator(it.id, LocalDate.ofEpochDay(it.observedDay), kilometers(it.odometerKm), it.remainingKm, it.remainingDays)
            }.sortedWith(compareBy<PreparedIndicator> { it.date }.thenBy { it.id }),
        )
    }

    private fun kilometers(value: Long): Int {
        require(value in 0..Int.MAX_VALUE.toLong()) { "Odometer is outside the engine range" }
        return value.toInt()
    }
}
