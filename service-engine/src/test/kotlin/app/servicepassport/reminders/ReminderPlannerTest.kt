package app.servicepassport.reminders

import app.servicepassport.engine.EvaluationInput
import app.servicepassport.engine.Interval
import app.servicepassport.engine.OdometerReading
import app.servicepassport.engine.PerformedTask
import app.servicepassport.engine.Schedule
import app.servicepassport.engine.ServiceEngine
import app.servicepassport.engine.ServiceRule
import app.servicepassport.engine.demo.DemoCatalog
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Interwały fikcyjne (DEMO). Przypomnienia testujemy na prawdziwych wynikach silnika, nie na atrapach. */
class ReminderPlannerTest {
    private val car = "car-1"
    private fun d(s: String) = LocalDate.parse(s)
    private fun rule(code: String, km: Int? = null, months: Int? = null) =
        ServiceRule("r-$code", code, Schedule.Fixed(Interval(km, months)), DemoCatalog.source)

    private val oil = rule("oil", months = 12)
    private val brakes = rule("brakes", months = 24)
    private val belt = rule("belt", km = 100_000)

    private fun statuses(
        today: LocalDate,
        history: List<PerformedTask>,
        odometer: List<OdometerReading> = emptyList(),
        rules: List<ServiceRule> = listOf(oil, brakes),
    ) = ServiceEngine.evaluate(EvaluationInput(today, d("2020-01-01"), rules, history, odometer))

    private val history = listOf(
        PerformedTask("oil", d("2025-11-25"), null), // termin 2026-11-25
        PerformedTask("brakes", d("2024-10-01"), null), // termin 2026-10-01
    )

    private fun delivered(plan: ReminderPlan, on: LocalDate) = plan.notifyNow.map { DeliveredReminder(it.key, it.fingerprint, on) }

    @Test
    fun `overdue and due soon tasks are announced, overdue first`() {
        val plan = ReminderPlanner.plan(car, statuses(d("2026-11-10"), history), d("2026-11-10"))
        assertEquals(listOf(ReminderKind.OVERDUE, ReminderKind.DUE_SOON), plan.notifyNow.map { it.kind })
        assertEquals(listOf("car-1|OVERDUE|r-brakes", "car-1|DUE_SOON|r-oil"), plan.notifyNow.map { it.key })
        assertTrue(plan.cancel.isEmpty())
    }

    @Test
    fun `nothing is repeated before the configured interval and then it is`() {
        val day = d("2026-11-10")
        val first = ReminderPlanner.plan(car, statuses(day, history), day)
        val sent = delivered(first, day)

        val nextDay = ReminderPlanner.plan(car, statuses(day.plusDays(1), history), day.plusDays(1), delivered = sent)
        assertTrue(nextDay.notifyNow.isEmpty(), "same state on the next day must not notify again")

        val day13 = ReminderPlanner.plan(car, statuses(day.plusDays(13), history), day.plusDays(13), delivered = sent)
        assertEquals(emptyList(), day13.notifyNow.map { it.key })
        assertEquals(day.plusDays(14), day13.nextCheckOn)

        val day14 = ReminderPlanner.plan(car, statuses(day.plusDays(14), history), day.plusDays(14), delivered = sent)
        assertEquals(listOf("car-1|OVERDUE|r-brakes", "car-1|DUE_SOON|r-oil"), day14.notifyNow.map { it.key })
    }

    @Test
    fun `repeat can be switched off`() {
        val day = d("2026-11-10")
        val sent = delivered(ReminderPlanner.plan(car, statuses(day, history), day), day)
        val later = day.plusDays(10) // olej nadal „zbliża się”
        val plan = ReminderPlanner.plan(car, statuses(later, history), later, ReminderSettings(repeatEveryDays = null), sent)
        assertTrue(plan.notifyNow.isEmpty())
    }

    @Test
    fun `due soon escalates to a new overdue reminder and the old one is cancelled`() {
        val day = d("2026-11-10")
        val sent = delivered(ReminderPlanner.plan(car, statuses(day, history), day), day)
        val later = d("2026-11-26") // olej zaległy od dziś; 16 dni po pierwszym powiadomieniu
        val plan = ReminderPlanner.plan(car, statuses(later, history), later, ReminderSettings(repeatEveryDays = 30), sent)
        assertEquals(listOf("car-1|OVERDUE|r-oil"), plan.notifyNow.map { it.key })
        assertEquals(setOf("car-1|DUE_SOON|r-oil"), plan.cancel)
    }

    @Test
    fun `adding the service cancels its reminder and does not create a new one`() {
        val day = d("2026-11-10")
        val sent = delivered(ReminderPlanner.plan(car, statuses(day, history), day), day)
        val updated = history + PerformedTask("brakes", d("2026-11-09"), null)
        val plan = ReminderPlanner.plan(car, statuses(day, updated), day, delivered = sent)
        assertTrue(plan.notifyNow.isEmpty())
        assertEquals(setOf("car-1|OVERDUE|r-brakes"), plan.cancel)
    }

    @Test
    fun `edited entry that moves the due date notifies again despite a recent reminder`() {
        val day = d("2026-11-10")
        val sent = delivered(ReminderPlanner.plan(car, statuses(day, history), day), day)
        val corrected = listOf(PerformedTask("oil", d("2025-11-25"), null), PerformedTask("brakes", d("2024-09-01"), null))
        val plan = ReminderPlanner.plan(car, statuses(day, corrected), day, delivered = sent)
        assertEquals(listOf("car-1|OVERDUE|r-brakes"), plan.notifyNow.map { it.key })
    }

    @Test
    fun `stale odometer gives one reminder per vehicle, not one per rule`() {
        val day = d("2026-11-10")
        val rules = listOf(belt, rule("plugs", km = 60_000))
        val hist = listOf(PerformedTask("belt", d("2026-01-10"), 50_000), PerformedTask("plugs", d("2026-01-10"), 50_000))
        val st = statuses(day, hist, listOf(OdometerReading(d("2026-03-01"), 52_000)), rules)
        val plan = ReminderPlanner.plan(car, st, day)
        assertEquals(listOf("car-1|UPDATE_ODOMETER"), plan.notifyNow.map { it.key })
        assertNull(plan.notifyNow.single().status)
        assertEquals(254, ReminderPlanner.odometerAgeDays(st, day))

        val off = ReminderPlanner.plan(car, st, day, ReminderSettings(odometerReminders = false))
        assertTrue(off.notifyNow.isEmpty())

        val fresh = statuses(day, hist, listOf(OdometerReading(d("2026-11-01"), 58_000)), rules)
        val after = ReminderPlanner.plan(car, fresh, day, delivered = delivered(plan, day))
        assertTrue(after.notifyNow.isEmpty())
        assertEquals(setOf("car-1|UPDATE_ODOMETER"), after.cancel, "new reading clears the reminder")
    }

    @Test
    fun `needs data and no urgency never notify`() {
        val day = d("2026-11-10")
        val st = statuses(day, listOf(PerformedTask("oil", d("2026-06-01"), null))) // hamulce: brak potwierdzenia
        assertTrue(ReminderPlanner.plan(car, st, day).notifyNow.isEmpty())
    }

    @Test
    fun `disabled reminders cancel everything of this vehicle only`() {
        val day = d("2026-11-10")
        val sent = delivered(ReminderPlanner.plan(car, statuses(day, history), day), day) +
            DeliveredReminder("car-2|OVERDUE|r-oil", "x", day)
        val plan = ReminderPlanner.plan(car, statuses(day, history), day, ReminderSettings(enabled = false), sent)
        assertTrue(plan.notifyNow.isEmpty())
        assertEquals(setOf("car-1|OVERDUE|r-brakes", "car-1|DUE_SOON|r-oil"), plan.cancel)
        assertNull(plan.nextCheckOn)
    }

    @Test
    fun `next check is the first day something changes by itself`() {
        val quiet = d("2026-06-01") // olej: termin 2026-11-25 -> „zbliża się” od 2026-10-26; hamulce: 2026-10-01 -> od 2026-09-01
        val plan = ReminderPlanner.plan(car, statuses(quiet, history), quiet)
        assertTrue(plan.notifyNow.isEmpty())
        assertEquals(d("2026-09-01"), plan.nextCheckOn)

        val st = statuses(d("2026-11-10"), listOf(PerformedTask("belt", d("2026-10-01"), 50_000)), rules = listOf(belt))
        assertEquals(d("2026-12-31"), ReminderPlanner.plan(car, st, d("2026-11-10")).nextCheckOn, "day the odometer becomes stale")
        assertNull(ReminderPlanner.plan(car, emptyList(), quiet).nextCheckOn)
    }

    @Test
    fun `settings reject a non-positive repeat interval`() {
        assertTrue(runCatching { ReminderSettings(repeatEveryDays = 0) }.isFailure)
    }
}
