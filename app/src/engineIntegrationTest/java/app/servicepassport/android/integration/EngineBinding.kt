package app.servicepassport.android.integration

import app.servicepassport.engine.*
import java.time.LocalDate

/** Test-only binding to pinned PR #2. Move into the application only in the separately accepted engine integration stage. */
internal object EngineBinding {
    fun input(facts: PreparedEngineFacts, today: LocalDate, rules: List<ServiceRule>) = EvaluationInput(
        today = today,
        inServiceDate = facts.inServiceDate,
        rules = rules,
        history = facts.performedActions.map { PerformedTask(it.task.taskCode, it.performedOn, it.odometerKm) },
        odometer = facts.historicalOdometers.map { OdometerReading(it.date, it.km) },
        currentOdometer = facts.currentOdometer?.let { CurrentOdometer.Selected(OdometerReading(it.date, it.km)) }
            ?: CurrentOdometer.NotSelected,
        settings = EngineSettings(
            dueSoonKm = facts.policy.dueSoonKm,
            dueSoonDays = facts.policy.dueSoonDays,
            odometerStaleAfterDays = facts.policy.odometerStaleAfterDays,
        ),
    )

    fun configuration(facts: PreparedEngineFacts): Map<ConfigAttribute, String> =
        facts.configuration.mapKeys { (name, _) -> ConfigAttribute.valueOf(name) }
}
