package app.servicepassport.engine

import app.servicepassport.engine.demo.DemoCatalog
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Wszystkie interwały w testach są fikcyjne (DEMO) — sprawdzamy arytmetykę, nie zalecenia producenta. */
class ServiceEngineTest {
    private val today = d("2026-11-10")
    private val src = DemoCatalog.source
    private val oil = rule("oil", Schedule.Fixed(Interval(km = 15_000, months = 12)))

    private fun d(s: String) = LocalDate.parse(s)
    private fun rule(code: String, schedule: Schedule, source: SourceRef = src) =
        ServiceRule("r-$code", code, schedule, source)

    private fun eval(
        vararg rules: ServiceRule,
        history: List<PerformedTask> = emptyList(),
        odometer: List<OdometerReading> = emptyList(),
        indicators: List<IndicatorReading> = emptyList(),
        inService: LocalDate? = d("2022-03-01"),
    ) = ServiceEngine.evaluate(EvaluationInput(today, inService, rules.toList(), history, odometer, indicators))

    private fun one(
        rule: ServiceRule,
        history: List<PerformedTask> = emptyList(),
        odometer: List<OdometerReading> = emptyList(),
        indicators: List<IndicatorReading> = emptyList(),
        inService: LocalDate? = d("2022-03-01"),
    ) = eval(rule, history = history, odometer = odometer, indicators = indicators, inService = inService).single()

    // --- interwały km / czas / co nastąpi wcześniej ---

    @Test
    fun `km interval - boundaries between no urgency, due soon and overdue`() {
        val r = rule("x", Schedule.Fixed(Interval(km = 10_000)))
        val done = listOf(PerformedTask("x", d("2026-06-01"), 50_000))
        fun at(km: Int) = one(r, done, listOf(OdometerReading(d("2026-11-01"), km)))

        assertEquals(Category.NO_URGENCY, at(58_499).category)
        assertEquals(Category.DUE_SOON, at(58_500).category)
        at(60_000).let {
            assertEquals(Category.DUE_SOON, it.category, "exactly at the limit is due, not overdue")
            assertEquals(0, it.remainingKm)
            assertEquals(Dimension.DISTANCE, it.basis.decidedBy)
        }
        at(60_001).let {
            assertEquals(Category.OVERDUE, it.category)
            assertEquals(-1, it.remainingKm)
            assertEquals(60_000, it.dueKm)
            assertNull(it.dueDate, "km-only rule has no due date")
        }
    }

    @Test
    fun `time interval - boundaries and no odometer needed`() {
        val r = rule("x", Schedule.Fixed(Interval(months = 12)))
        fun doneOn(date: String) = one(r, listOf(PerformedTask("x", d(date), null)))

        assertEquals(Category.NO_URGENCY, doneOn("2025-12-11").category) // 31 dni
        assertEquals(Category.DUE_SOON, doneOn("2025-12-10").category) // 30 dni
        assertEquals(Category.DUE_SOON, doneOn("2025-11-10").category) // termin dziś
        doneOn("2025-11-09").let {
            assertEquals(Category.OVERDUE, it.category)
            assertEquals(-1, it.remainingDays)
            assertEquals(Dimension.TIME, it.basis.decidedBy)
            assertNull(it.basis.odometerUsed)
            assertTrue(it.reasons.isEmpty())
        }
    }

    @Test
    fun `month arithmetic clamps to end of month`() {
        val r = rule("x", Schedule.Fixed(Interval(months = 12)))
        assertEquals(d("2025-02-28"), one(r, listOf(PerformedTask("x", d("2024-02-29"), null))).dueDate)
    }

    @Test
    fun `whichever first - time wins although km remain`() {
        val s = one(oil, listOf(PerformedTask("oil", d("2025-10-01"), 40_000)), listOf(OdometerReading(d("2026-11-01"), 45_000)))
        assertEquals(Category.OVERDUE, s.category)
        assertEquals(Dimension.TIME, s.basis.decidedBy)
        assertEquals(10_000, s.remainingKm)
    }

    @Test
    fun `whichever first - km wins although time remains`() {
        val s = one(oil, listOf(PerformedTask("oil", d("2026-06-01"), 40_000)), listOf(OdometerReading(d("2026-11-01"), 55_500)))
        assertEquals(Category.OVERDUE, s.category)
        assertEquals(Dimension.DISTANCE, s.basis.decidedBy)
        assertTrue(s.remainingDays!! > 0)
    }

    // --- pierwszy i kolejne serwisy, ostatnia potwierdzona czynność ---

    @Test
    fun `first interval applies from vehicle start, repeat after a confirmed task`() {
        val r = rule("bf", Schedule.Fixed(repeat = Interval(months = 24), first = Interval(months = 60)))
        one(r).let {
            assertEquals(d("2027-03-01"), it.dueDate)
            assertEquals(IntervalKind.FIRST, it.basis.intervalKind)
            assertEquals(AnchorKind.VEHICLE_START, it.basis.anchorKind)
            assertEquals(Category.NO_URGENCY, it.category)
        }
        one(r, listOf(PerformedTask("bf", d("2026-01-15"), null))).let {
            assertEquals(d("2028-01-15"), it.dueDate)
            assertEquals(IntervalKind.REPEAT, it.basis.intervalKind)
            assertEquals(AnchorKind.LAST_CONFIRMED_TASK, it.basis.anchorKind)
        }
    }

    @Test
    fun `latest confirmed task is the anchor - adding an older historical entry changes nothing`() {
        val recent = PerformedTask("oil", d("2026-05-01"), 60_000)
        val odo = listOf(OdometerReading(d("2026-11-01"), 65_000))
        val before = one(oil, listOf(recent), odo)
        val after = one(oil, listOf(PerformedTask("oil", d("2024-05-01"), 30_000), recent, PerformedTask("other", d("2026-10-01"), 64_000)), odo)
        assertEquals(before.dueKm, after.dueKm)
        assertEquals(before.dueDate, after.dueDate)
        assertEquals(75_000, after.dueKm)
        assertEquals(d("2026-05-01"), after.basis.anchorDate)
    }

    @Test
    fun `historical entry odometer does not replace the current reading`() {
        val s = one(
            oil,
            listOf(PerformedTask("oil", d("2026-05-01"), 60_000)),
            listOf(OdometerReading(d("2026-11-01"), 65_000)),
        )
        assertEquals(OdometerReading(d("2026-11-01"), 65_000), s.basis.odometerUsed)
    }

    @Test
    fun `service entry odometer counts as current reading when it is the newest`() {
        val s = one(oil, listOf(PerformedTask("oil", d("2026-10-20"), 60_000)))
        assertEquals(Category.NO_URGENCY, s.category)
        assertEquals(15_000, s.remainingKm)
    }

    // --- brak historii i niepełne dane ---

    @Test
    fun `no history and first threshold passed is missing confirmation, never overdue`() {
        val s = one(oil, odometer = listOf(OdometerReading(d("2026-11-01"), 80_000)))
        assertEquals(Category.NEEDS_DATA, s.category)
        assertTrue(Reason.NO_CONFIRMATION_IN_DOCUMENTS in s.reasons)
        assertEquals(15_000, s.dueKm, "plan value stays visible as the basis")
    }

    @Test
    fun `no history on a new car is computed from vehicle start`() {
        val s = one(oil, odometer = listOf(OdometerReading(d("2026-11-01"), 4_000)), inService = d("2026-06-01"))
        assertEquals(Category.NO_URGENCY, s.category)
        assertEquals(15_000, s.dueKm)
        assertEquals(d("2027-06-01"), s.dueDate)
        assertFalse(Reason.NO_CONFIRMATION_IN_DOCUMENTS in s.reasons)
    }

    @Test
    fun `missing odometer blocks a verdict unless time alone is already overdue`() {
        one(oil, listOf(PerformedTask("oil", d("2026-06-01"), null))).let {
            assertEquals(Category.NEEDS_DATA, it.category)
            assertEquals(setOf(Reason.ODOMETER_MISSING), it.reasons)
            assertEquals(d("2027-06-01"), it.dueDate, "known dimension is still reported")
        }
        assertEquals(Category.OVERDUE, one(oil, listOf(PerformedTask("oil", d("2025-06-01"), null))).category)
    }

    @Test
    fun `last service without odometer asks for that value`() {
        val s = one(oil, listOf(PerformedTask("oil", d("2026-06-01"), null)), listOf(OdometerReading(d("2026-11-01"), 70_000)))
        assertEquals(Category.NEEDS_DATA, s.category)
        assertEquals(setOf(Reason.LAST_SERVICE_ODOMETER_MISSING), s.reasons)
        assertNull(s.dueKm)
    }

    @Test
    fun `missing in-service date blocks first time-based term`() {
        val s = one(rule("x", Schedule.Fixed(Interval(months = 24))), inService = null)
        assertEquals(Category.NEEDS_DATA, s.category)
        assertEquals(setOf(Reason.IN_SERVICE_DATE_MISSING), s.reasons)
    }

    @Test
    fun `entries dated in the future are ignored and flagged`() {
        val s = one(
            oil,
            listOf(PerformedTask("oil", d("2026-06-01"), 50_000), PerformedTask("oil", d("2027-01-01"), 99_000)),
            listOf(OdometerReading(d("2026-11-01"), 52_000), OdometerReading(d("2026-12-24"), 90_000)),
        )
        assertEquals(65_000, s.dueKm)
        assertEquals(52_000, s.basis.odometerUsed?.km)
        assertTrue(Reason.FUTURE_ENTRIES_IGNORED in s.reasons)
    }

    // --- harmonogramy zmienne ---

    private val variable = rule("insp", Schedule.Variable(Interval(km = 30_000, months = 24)))

    @Test
    fun `variable schedule without indicator never claims no urgency`() {
        val s = one(variable, listOf(PerformedTask("insp", d("2026-06-01"), 50_000)), listOf(OdometerReading(d("2026-11-01"), 52_000)))
        assertEquals(Category.NEEDS_DATA, s.category)
        assertTrue(Reason.VARIABLE_SCHEDULE in s.reasons)
        assertEquals(IntervalKind.UPPER_LIMIT, s.basis.intervalKind)
        assertEquals(80_000, s.dueKm)
    }

    @Test
    fun `variable schedule without any limit only asks for data`() {
        val s = one(rule("insp", Schedule.Variable()), listOf(PerformedTask("insp", d("2020-01-01"), 10_000)), listOf(OdometerReading(d("2026-11-01"), 150_000)))
        assertEquals(Category.NEEDS_DATA, s.category)
        assertNull(s.dueKm)
        assertNull(s.dueDate)
    }

    @Test
    fun `variable schedule is overdue once the source upper limit is exceeded`() {
        val s = one(variable, listOf(PerformedTask("insp", d("2026-01-01"), 50_000)), listOf(OdometerReading(d("2026-11-01"), 80_001)))
        assertEquals(Category.OVERDUE, s.category)
    }

    @Test
    fun `indicator reading taken after last service drives the variable schedule`() {
        val done = listOf(PerformedTask("insp", d("2026-01-01"), 50_000))
        val s = one(
            variable, done, listOf(OdometerReading(d("2026-11-05"), 61_000)),
            listOf(IndicatorReading("insp", d("2026-10-01"), 60_000, remainingKm = 4_000, remainingDays = 90)),
        )
        assertEquals(Category.NO_URGENCY, s.category)
        assertEquals(64_000, s.dueKm)
        assertEquals(d("2026-12-30"), s.dueDate)
        assertEquals(AnchorKind.VEHICLE_INDICATOR, s.basis.anchorKind)
        assertFalse(Reason.VARIABLE_SCHEDULE in s.reasons)

        val staleIndicator = one(
            variable, done, listOf(OdometerReading(d("2026-11-05"), 61_000)),
            listOf(IndicatorReading("insp", d("2025-12-01"), 49_000, remainingKm = 500)),
        )
        assertEquals(IntervalKind.UPPER_LIMIT, staleIndicator.basis.intervalKind, "indicator from before the service is void")
    }

    @Test
    fun `indicator saying overdue is overdue`() {
        val s = one(variable, indicators = listOf(IndicatorReading("insp", d("2026-10-01"), 60_000, remainingDays = 10)))
        assertEquals(Category.OVERDUE, s.category)
    }

    // --- aktualność przebiegu i szacunki ---

    @Test
    fun `stale odometer is flagged but fresh one is not`() {
        val done = listOf(PerformedTask("oil", d("2026-06-01"), 50_000))
        assertTrue(Reason.ODOMETER_STALE in one(oil, done, listOf(OdometerReading(d("2026-08-11"), 51_000))).reasons) // 91 dni
        assertFalse(Reason.ODOMETER_STALE in one(oil, done, listOf(OdometerReading(d("2026-08-12"), 51_000))).reasons) // 90 dni
    }

    @Test
    fun `stale odometer with projection past the limit requires an update instead of a verdict`() {
        // ~100 km/dzień; ostatni odczyt 120 dni temu, do progu brakowało 5000 km.
        val s = one(
            rule("x", Schedule.Fixed(Interval(km = 20_000))),
            listOf(PerformedTask("x", d("2026-03-01"), 40_000)),
            listOf(OdometerReading(d("2026-03-04"), 40_300), OdometerReading(d("2026-07-13"), 55_000)),
        )
        assertEquals(Category.NEEDS_DATA, s.category)
        assertTrue(Reason.ODOMETER_UPDATE_REQUIRED in s.reasons)
        assertTrue(Reason.ODOMETER_STALE in s.reasons)
        assertEquals(5_000, s.remainingKm)
    }

    @Test
    fun `estimate is month-level only and based on average mileage`() {
        val s = one(
            rule("x", Schedule.Fixed(Interval(km = 30_000))),
            listOf(PerformedTask("x", d("2026-07-01"), 50_000)), // 50 km/dzień
            listOf(OdometerReading(d("2026-11-08"), 56_500)),
        )
        assertEquals(Category.NO_URGENCY, s.category)
        assertEquals(YearMonth.of(2028, 2), s.estimatedKmDueMonth) // 23 500 km / 50 = 470 dni od 2026-11-08
    }

    @Test
    fun `no estimate from a single reading, a short span or inconsistent readings`() {
        val r = rule("x", Schedule.Fixed(Interval(km = 30_000)))
        val done = listOf(PerformedTask("x", d("2026-11-01"), 50_000))
        assertNull(one(r, done).estimatedKmDueMonth)
        assertNull(one(r, done, listOf(OdometerReading(d("2026-11-09"), 50_400))).estimatedKmDueMonth)

        val bad = one(r, done, listOf(OdometerReading(d("2026-01-01"), 70_000), OdometerReading(d("2026-11-09"), 50_400)))
        assertNull(bad.estimatedKmDueMonth)
        assertTrue(Reason.ODOMETER_INCONSISTENT in bad.reasons)
    }

    @Test
    fun `estimate uses recent window when it is long enough`() {
        // Dawno: 10 km/dzień, ostatnie pół roku: 100 km/dzień.
        val s = one(
            rule("x", Schedule.Fixed(Interval(km = 100_000))),
            listOf(PerformedTask("x", d("2021-01-01"), 0)),
            listOf(OdometerReading(d("2026-05-01"), 20_000), OdometerReading(d("2026-11-07"), 39_000)),
            inService = d("2021-01-01"),
        )
        assertEquals(YearMonth.from(d("2026-11-07").plusDays(610)), s.estimatedKmDueMonth)
    }

    // --- bieżący odczyt wybrany w aplikacji ---

    private fun withCurrent(current: CurrentOdometer, history: List<PerformedTask>, odometer: List<OdometerReading> = emptyList()) =
        ServiceEngine.evaluate(EvaluationInput(today, d("2022-03-01"), listOf(oil), history, odometer, currentOdometer = current)).single()

    @Test
    fun `selected current reading is used even when a later invoice has a higher odometer`() {
        val selected = OdometerReading(d("2026-09-01"), 52_000)
        val history = listOf(PerformedTask("oil", d("2026-06-01"), 50_000), PerformedTask("other", d("2026-10-20"), 66_000))
        val s = withCurrent(CurrentOdometer.Selected(selected), history, listOf(selected))
        assertEquals(selected, s.basis.odometerUsed, "the engine must not silently replace the user's choice")
        assertEquals(13_000, s.remainingKm)
        assertEquals(Category.NO_URGENCY, s.category)
        assertTrue(Reason.NEWER_ODOMETER_IN_HISTORY in s.reasons, "but it says that newer data exists")

        val automatic = withCurrent(CurrentOdometer.LatestKnown, history, listOf(selected))
        assertEquals(66_000, automatic.basis.odometerUsed?.km)
        assertEquals(Category.OVERDUE, automatic.category)
    }

    @Test
    fun `no selected reading means missing data even though history has odometer values`() {
        val history = listOf(PerformedTask("oil", d("2026-06-01"), 50_000), PerformedTask("other", d("2026-10-20"), 53_000))
        val s = withCurrent(CurrentOdometer.NotSelected, history, listOf(OdometerReading(d("2026-08-01"), 51_000)))
        assertEquals(Category.NEEDS_DATA, s.category)
        assertEquals(setOf(Reason.ODOMETER_MISSING), s.reasons)
        assertNull(s.basis.odometerUsed)
        assertNull(s.remainingKm)
        assertEquals(65_000, s.dueKm, "the due point itself is still known")
    }

    @Test
    fun `selected reading staleness and estimate use the selected reading`() {
        val selected = OdometerReading(d("2026-07-01"), 52_000) // 132 dni temu
        val history = listOf(PerformedTask("oil", d("2026-06-01"), 50_000))
        val s = withCurrent(CurrentOdometer.Selected(selected), history, listOf(selected))
        assertTrue(Reason.ODOMETER_STALE in s.reasons)
        assertFalse(Reason.NEWER_ODOMETER_IN_HISTORY in s.reasons)
        assertEquals(selected, s.basis.odometerUsed)
    }

    @Test
    fun `selected reading dated in the future is ignored and flagged`() {
        val s = withCurrent(CurrentOdometer.Selected(OdometerReading(d("2027-01-01"), 60_000)), listOf(PerformedTask("oil", d("2026-06-01"), 50_000)))
        assertEquals(Category.NEEDS_DATA, s.category)
        assertTrue(Reason.FUTURE_ENTRIES_IGNORED in s.reasons && Reason.ODOMETER_MISSING in s.reasons)
    }

    @Test
    fun `time-only rules do not depend on the selected reading`() {
        val r = rule("x", Schedule.Fixed(Interval(months = 12)))
        val s = ServiceEngine.evaluate(EvaluationInput(today, d("2022-03-01"), listOf(r), listOf(PerformedTask("x", d("2026-06-01"), 50_000)), currentOdometer = CurrentOdometer.NotSelected)).single()
        assertEquals(Category.NO_URGENCY, s.category)
        assertTrue(s.reasons.isEmpty())
    }

    // --- źródła, kolejność, dane demo ---

    @Test
    fun `expired source is flagged on the result`() {
        val expired = src.copy(validUntil = d("2026-11-09"))
        val s = one(rule("x", Schedule.Fixed(Interval(months = 12)), expired), listOf(PerformedTask("x", d("2026-06-01"), null)))
        assertTrue(Reason.SOURCE_EXPIRED in s.reasons)
        val valid = src.copy(validUntil = today)
        assertTrue(one(rule("x", Schedule.Fixed(Interval(months = 12)), valid), listOf(PerformedTask("x", d("2026-06-01"), null))).reasons.isEmpty())
    }

    @Test
    fun `user defined rule keeps its origin in the result`() {
        val user = src.copy(id = "USER", origin = DataOrigin.USER_DEFINED)
        val s = one(rule("x", Schedule.Fixed(Interval(months = 6)), user), listOf(PerformedTask("x", d("2026-06-01"), null)))
        assertEquals(DataOrigin.USER_DEFINED, s.rule.source.origin)
    }

    @Test
    fun `dashboard order - overdue, due soon, needs data, no urgency`() {
        val result = eval(
            rule("ok", Schedule.Fixed(Interval(months = 48))),
            rule("data", Schedule.Variable()),
            rule("soon", Schedule.Fixed(Interval(months = 12))),
            rule("late", Schedule.Fixed(Interval(months = 6))),
            history = listOf(
                PerformedTask("ok", d("2026-01-01"), null),
                PerformedTask("soon", d("2025-11-20"), null),
                PerformedTask("late", d("2026-01-01"), null),
            ),
        )
        assertEquals(
            listOf(Category.OVERDUE, Category.DUE_SOON, Category.NEEDS_DATA, Category.NO_URGENCY),
            result.map { it.category },
        )
    }

    @Test
    fun `demo plan end to end`() {
        val result = eval(
            *DemoCatalog.plan.rules.toTypedArray(),
            history = listOf(PerformedTask(DemoCatalog.OIL, d("2026-02-01"), 42_000)),
            odometer = listOf(OdometerReading(d("2026-11-01"), 56_000)),
        ).associateBy { it.rule.taskCode }

        assertEquals(Category.DUE_SOON, result.getValue(DemoCatalog.OIL).category)
        assertEquals(Category.NEEDS_DATA, result.getValue(DemoCatalog.BRAKE_FLUID).category) // 36 mies. minęło, brak wpisu
        assertTrue(Reason.NO_CONFIRMATION_IN_DOCUMENTS in result.getValue(DemoCatalog.BRAKE_FLUID).reasons)
        assertEquals(Category.NEEDS_DATA, result.getValue(DemoCatalog.INSPECTION).category)
        assertTrue(result.values.all { it.rule.source.origin == DataOrigin.DEMO })
    }

    @Test
    fun `interval rejects empty and non-positive values`() {
        assertTrue(runCatching { Interval() }.isFailure)
        assertTrue(runCatching { Interval(km = 0) }.isFailure)
        assertTrue(runCatching { Interval(months = -1) }.isFailure)
        assertTrue(runCatching { IndicatorReading("x", today, 1) }.isFailure)
    }
}
