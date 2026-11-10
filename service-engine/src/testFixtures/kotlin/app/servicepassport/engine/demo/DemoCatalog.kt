package app.servicepassport.engine.demo

import app.servicepassport.engine.ConfigAttribute
import app.servicepassport.engine.DataOrigin
import app.servicepassport.engine.Interval
import app.servicepassport.engine.Schedule
import app.servicepassport.engine.ServicePlan
import app.servicepassport.engine.ServiceRule
import app.servicepassport.engine.SourceRef
import java.time.LocalDate

/**
 * DANE DEMONSTRACYJNE. Fikcyjny pojazd i ZMYŚLONE interwały — to nie są zalecenia żadnego producenta.
 * Plik leży w `testFixtures`, więc nie wchodzi do artefaktu produkcyjnego modułu, a pochodzenie
 * DEMO jest dodatkowo odrzucane przez CatalogValidator.
 */
public object DemoCatalog {
    public val source: SourceRef = SourceRef(
        id = "DEMO",
        title = "Demo data - not manufacturer recommendations",
        version = "demo-1",
        verifiedOn = LocalDate.of(2026, 11, 10),
        validUntil = null,
        origin = DataOrigin.DEMO,
        usageRights = "Fictional data created for this project",
    )

    public const val OIL: String = "DEMO_ENGINE_OIL"
    public const val BRAKE_FLUID: String = "DEMO_BRAKE_FLUID"
    public const val INSPECTION: String = "DEMO_INSPECTION"

    public val plan: ServicePlan = ServicePlan(
        id = "demo-plan",
        source = source,
        scope = mapOf(
            ConfigAttribute.MAKE to setOf("Demo Motors"),
            ConfigAttribute.MODEL to setOf("Example"),
            ConfigAttribute.MODEL_YEAR to setOf("2022", "2023"),
            ConfigAttribute.MARKET to setOf("NO"),
        ),
        rules = listOf(
            ServiceRule("demo-oil", OIL, Schedule.Fixed(Interval(km = 15_000, months = 12)), source),
            ServiceRule(
                "demo-brake-fluid", BRAKE_FLUID,
                Schedule.Fixed(repeat = Interval(months = 24), first = Interval(months = 36)), source,
            ),
            ServiceRule("demo-inspection", INSPECTION, Schedule.Variable(Interval(km = 30_000, months = 24)), source),
        ),
    )
}
