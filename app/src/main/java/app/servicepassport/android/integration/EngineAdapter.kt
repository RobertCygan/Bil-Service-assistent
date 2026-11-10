package app.servicepassport.android.integration

import app.servicepassport.engine.*
import java.time.LocalDate

/** Explicit application binding. Historical mileage never substitutes a missing selected reading. */
object EngineAdapter {
    fun input(facts: PreparedEngineFacts, today: LocalDate, rules: List<ServiceRule>) = EvaluationInput(
        today = today,
        inServiceDate = facts.inServiceDate,
        rules = rules,
        history = facts.performedActions.map { PerformedTask(it.task.taskCode, it.performedOn, it.odometerKm) },
        odometer = facts.historicalOdometers.map { OdometerReading(it.date, it.km) },
        // The display reading belongs to the inspection service; the engine ignores readings older than the last confirmed one.
        indicators = facts.indicators.map { IndicatorReading(INDICATOR_TASK, it.date, it.odometerKm, it.remainingKm, it.remainingDays) },
        currentOdometer = facts.currentOdometer?.let { CurrentOdometer.Selected(OdometerReading(it.date, it.km)) }
            ?: CurrentOdometer.NotSelected,
        settings = EngineSettings(
            dueSoonKm = facts.policy.dueSoonKm,
            dueSoonDays = facts.policy.dueSoonDays,
            odometerStaleAfterDays = facts.policy.odometerStaleAfterDays,
        ),
    )

    /** The task the car's service display stands for. */
    const val INDICATOR_TASK = "inspection_service:inspect"

    fun configuration(facts: PreparedEngineFacts): Map<ConfigAttribute, String> =
        facts.configuration.mapKeys { (name, _) -> ConfigAttribute.valueOf(name) }
}
