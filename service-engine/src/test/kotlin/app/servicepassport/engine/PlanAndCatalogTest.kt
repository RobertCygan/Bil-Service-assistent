package app.servicepassport.engine

import app.servicepassport.engine.demo.DemoCatalog
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanAndCatalogTest {
    private val today = LocalDate.parse("2026-11-10")
    private val catalog = listOf(DemoCatalog.plan)
    private val full = mapOf(
        ConfigAttribute.MAKE to "demo motors",
        ConfigAttribute.MODEL to " Example ",
        ConfigAttribute.MODEL_YEAR to "2022",
        ConfigAttribute.MARKET to "no",
        ConfigAttribute.ENGINE_CODE to "ignored-extra",
    )

    @Test
    fun `full match assigns the plan regardless of case and whitespace`() {
        assertEquals(PlanResolution.Assigned(DemoCatalog.plan), PlanResolver.resolve(full, catalog))
    }

    @Test
    fun `missing attribute blocks assignment and is reported`() {
        val res = PlanResolver.resolve(full - ConfigAttribute.MARKET + (ConfigAttribute.MODEL_YEAR to " "), catalog)
        assertEquals(PlanResolution.NoVerifiedSchedule(setOf(ConfigAttribute.MODEL_YEAR, ConfigAttribute.MARKET)), res)
    }

    @Test
    fun `mismatch gives no schedule and no misleading hints`() {
        val res = PlanResolver.resolve(full + (ConfigAttribute.MODEL_YEAR to "2019") - ConfigAttribute.MARKET, catalog)
        assertEquals(PlanResolution.NoVerifiedSchedule(emptySet()), res)
        assertEquals(PlanResolution.NoVerifiedSchedule(emptySet()), PlanResolver.resolve(full, emptyList()))
    }

    @Test
    fun `two matching plans are ambiguous, never auto-picked`() {
        val twin = DemoCatalog.plan.copy(id = "twin", scope = mapOf(ConfigAttribute.MAKE to setOf("Demo Motors")))
        assertEquals(PlanResolution.Ambiguous(listOf("demo-plan", "twin")), PlanResolver.resolve(full, catalog + twin))
    }

    @Test
    fun `plans split by usage profile resolve only when the profile is known`() {
        fun plan(id: String, profile: String) = DemoCatalog.plan.copy(
            id = id, scope = DemoCatalog.plan.scope + (ConfigAttribute.USAGE_PROFILE to setOf(profile)),
        )
        val both = listOf(plan("normal", "NORMAL"), plan("severe", "SEVERE"))
        assertEquals(PlanResolution.NoVerifiedSchedule(setOf(ConfigAttribute.USAGE_PROFILE)), PlanResolver.resolve(full, both))
        val res = PlanResolver.resolve(full + (ConfigAttribute.USAGE_PROFILE to "severe"), both)
        assertEquals("severe", (res as PlanResolution.Assigned).plan.id)
    }

    // --- bramka katalogu produkcyjnego ---

    private val verified = DemoCatalog.source.copy(id = "TEST-SRC", origin = DataOrigin.VERIFIED_SOURCE)
    private val good = DemoCatalog.plan.copy(source = verified, rules = DemoCatalog.plan.rules.map { it.copy(source = verified) })

    private fun codes(vararg plans: ServicePlan) = CatalogValidator.productionIssues(plans.toList(), today).map { it.code }.toSet()

    @Test
    fun `demo catalog is rejected for production`() {
        val issues = CatalogValidator.productionIssues(catalog, today)
        assertEquals(setOf(CatalogIssueCode.NOT_VERIFIED_ORIGIN), issues.map { it.code }.toSet())
        assertEquals(1 + DemoCatalog.plan.rules.size, issues.size, "plan and every rule are reported")
    }

    @Test
    fun `complete verified catalog passes`() {
        assertTrue(codes(good).isEmpty())
    }

    @Test
    fun `user defined rules cannot hide in the production catalog`() {
        val user = verified.copy(origin = DataOrigin.USER_DEFINED)
        val plan = good.copy(rules = good.rules.take(1).map { it.copy(source = user) })
        assertEquals(setOf(CatalogIssueCode.NOT_VERIFIED_ORIGIN), codes(plan))
    }

    @Test
    fun `provenance and structure problems are reported`() {
        assertEquals(setOf(CatalogIssueCode.SOURCE_FIELDS_BLANK), codes(good.copy(source = verified.copy(usageRights = " "))))
        assertEquals(setOf(CatalogIssueCode.VERIFIED_IN_FUTURE), codes(good.copy(source = verified.copy(verifiedOn = today.plusDays(1)))))
        assertEquals(
            setOf(CatalogIssueCode.VALIDITY_BEFORE_VERIFICATION),
            codes(good.copy(source = verified.copy(validUntil = verified.verifiedOn.minusDays(1)))),
        )
        assertEquals(setOf(CatalogIssueCode.EMPTY_SCOPE), codes(good.copy(scope = emptyMap())))
        assertEquals(setOf(CatalogIssueCode.EMPTY_SCOPE), codes(good.copy(scope = mapOf(ConfigAttribute.MAKE to emptySet()))))
        assertEquals(setOf(CatalogIssueCode.EMPTY_RULES), codes(good.copy(rules = emptyList())))
        assertEquals(setOf(CatalogIssueCode.DUPLICATE_PLAN_ID), codes(good, good))
        assertEquals(
            setOf(CatalogIssueCode.DUPLICATE_RULE_ID, CatalogIssueCode.DUPLICATE_TASK_CODE),
            codes(good.copy(rules = good.rules + good.rules.first())),
        )
    }
}
