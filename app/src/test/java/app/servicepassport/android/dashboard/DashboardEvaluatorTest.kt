package app.servicepassport.android.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
import app.servicepassport.engine.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DashboardEvaluatorTest {
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private lateinit var repo: ProfileRepository
    private lateinit var car: String
    private lateinit var file: String
    private val today = LocalDate.of(2026, 11, 10)
    private fun id() = UUID.randomUUID().toString()
    private suspend fun snapshot() = requireNotNull(db.vehicles().garage(car))
    private suspend fun evaluate() = DashboardEvaluator().evaluate(snapshot(), today).tasks.single()
    private suspend fun service(km: Long?, days: Long = 300, operation: String = "replace") {
        repo.saveService(ServiceInput(id(), car, null, today.minusDays(days).toEpochDay(), km, listOf(WorkInput("engine_oil", operation))))
    }
    private suspend fun current(km: Long, days: Long = 0): ReadingInput {
        val reading = ReadingInput(id(), car, null, today.minusDays(days).toEpochDay(), km, true)
        repo.saveReading(reading); return reading
    }
    private suspend fun interval(km: Int? = 20000, months: Int? = null): UserIntervalInput {
        val input = UserIntervalInput(id(), car, null, "engine_oil", "replace", km, months)
        repo.saveInterval(input); return input
    }
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext(); file = "dashboard-${id()}.db"
        db = GarageDatabase.open(context, file)
        repo = ProfileRepository(db, Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC))
        repo.save(ProfileInput("Test car", 2022, LocalDate.of(2021, 12, 20).toEpochDay(), make = "Example Make", model = "Example Model"))
        car = requireNotNull(db.vehicles().getProfile()).id
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(file) }

    @Test fun aDriversRepeatIntervalNeverInventsAFirstServiceAtRegistrationOrZero() = runBlocking {
        current(1000); interval()
        val task = evaluate()
        assertEquals(Category.NEEDS_DATA, task.category)
        assertEquals(TaskOrigin.USER, task.origin)
        assertNull(task.dueKm); assertNull(task.dueDate); assertNull(task.basis)
        assertTrue(DashboardReason.CONFIRMED_ACTION_MISSING in task.reasons)
    }

    @Test fun inspectionDoesNotResetReplacementAndBothLimitsUseTheMatchingConfirmedAnchor() = runBlocking {
        service(48000); service(58000, 10, "inspect"); current(59000); interval(20000, 12)
        val task = evaluate()
        assertEquals(Category.NO_URGENCY, task.category)
        assertEquals(68000, task.dueKm)
        assertEquals(today.minusDays(300).plusMonths(12), task.dueDate)
        assertEquals(today.minusDays(300), task.basis?.anchorDate)
        assertEquals(9000, task.remainingKm)
    }

    @Test fun zeroRemainingIsDueSoonAndOnePastTheLimitIsOverdue() = runBlocking {
        service(48000, 30); current(59000, 1); interval(11000)
        assertEquals(Category.DUE_SOON, evaluate().category)
        current(59001)
        assertEquals(Category.OVERDUE, evaluate().category)
    }

    @Test fun aLimitAlreadyPassedAtTheSelectedReadingStaysOverdueWhenANewerHigherReadingExists() = runBlocking {
        service(48000); current(70000, days = 30); interval() // limit 68 000 km, selected reading is 2 000 km past it
        repo.saveReading(ReadingInput(id(), car, null, today.minusDays(5).toEpochDay(), 72000, false))
        val task = evaluate()
        assertEquals(Category.OVERDUE, task.category)
        assertEquals(68000, task.dueKm); assertEquals(-2000, task.remainingKm)
        assertTrue(DashboardReason.NEWER_ODOMETER_IN_HISTORY in task.reasons)
        assertFalse(DashboardReason.ODOMETER_INCONSISTENT in task.reasons)
    }

    @Test fun aNewerHigherReadingStillBlocksAdviceWhileTheSelectedReadingIsBelowTheLimit() = runBlocking {
        service(48000); current(60000, days = 30); interval()
        repo.saveReading(ReadingInput(id(), car, null, today.minusDays(5).toEpochDay(), 62000, false))
        val task = evaluate()
        assertEquals(Category.NEEDS_DATA, task.category)
        assertNull(task.dueKm); assertNull(task.remainingKm)
        assertTrue(DashboardReason.NEWER_ODOMETER_IN_HISTORY in task.reasons)
    }

    @Test fun deletionOfCurrentReadingDoesNotFallBackToServiceHistory() = runBlocking {
        service(48000); val reading = current(59000); interval()
        repo.deleteReading(car, reading.id, 1)
        val task = evaluate()
        assertEquals(Category.NEEDS_DATA, task.category)
        assertNull(task.dueKm); assertNull(task.remainingKm); assertNull(task.estimatedMonth)
        assertTrue(DashboardReason.ODOMETER_MISSING in task.reasons)
    }

    @Test fun staleMileageCannotProveNoUrgencyButDoesNotBlockATimeOnlyRule() = runBlocking {
        service(48000); current(49000, 100); val rule = interval()
        var task = evaluate()
        assertEquals(Category.NEEDS_DATA, task.category)
        assertTrue(DashboardReason.ODOMETER_STALE in task.reasons)
        assertNull(task.estimatedMonth)
        repo.saveInterval(rule.copy(expectedRevision = 1, distanceKm = null, months = 12))
        task = evaluate()
        assertEquals(Category.NO_URGENCY, task.category)
        assertEquals(today.minusDays(300).plusMonths(12), task.dueDate)
    }

    @Test fun aCounterResetBlocksDistanceAdviceButAnIndependentExpiredDateIsStillProven() = runBlocking {
        service(48000, 400); current(59000, 1); val rule = interval(20000, 12)
        val reset = ReadingInput(id(), car, null, today.toEpochDay(), 100, true)
        val token = ReadingConsistency.token(snapshot(), null, reset.observedDay, reset.km, true)
        repo.saveReading(reset.copy(exceptionReason = "Counter replaced", warningsAcknowledged = true, reviewToken = token))
        var task = evaluate()
        assertEquals(Category.OVERDUE, task.category)
        assertEquals(Dimension.TIME, task.basis?.decidedBy)
        assertNull(task.dueKm); assertNull(task.remainingKm); assertNull(task.estimatedMonth)
        assertTrue(DashboardReason.ODOMETER_INCONSISTENT in task.reasons)
        repo.saveInterval(rule.copy(expectedRevision = 1, months = null))
        task = evaluate()
        assertEquals(Category.NEEDS_DATA, task.category)
    }

    @Test fun sameDayDifferentMileageCannotHideBehindTheEngineSortOrder() = runBlocking {
        service(48000); current(59000); interval()
        val other = ReadingInput(id(), car, null, today.toEpochDay(), 60000, false)
        val token = ReadingConsistency.token(snapshot(), null, other.observedDay, other.km, false)
        repo.saveReading(other.copy(exceptionReason = "Another reading today", warningsAcknowledged = true, reviewToken = token))
        val task = evaluate()
        assertEquals(Category.NEEDS_DATA, task.category)
        assertTrue(DashboardReason.ODOMETER_INCONSISTENT in task.reasons)
        assertNull(task.dueKm); assertNull(task.estimatedMonth)
    }

    @Test fun distanceAdditionCannotWrapAndInventAnOverdueLimit() = runBlocking {
        service(Int.MAX_VALUE.toLong() - 500, 1); current(Int.MAX_VALUE.toLong() - 500); interval(1000)
        val task = evaluate()
        assertEquals(Category.NEEDS_DATA, task.category)
        assertNull(task.dueKm)
        assertTrue(DashboardReason.CALCULATION_LIMIT in task.reasons)
    }

    // These source/authorization fixtures are fictional and exist only in test sources.
    private fun source() = SourceRef("test-source", "Fictional source — tests only", "1", today.minusDays(30), today.plusDays(30), DataOrigin.VERIFIED_SOURCE, "Test fixture; no actual manufacturer licence")
    private fun plan(source: SourceRef) = ServicePlan("test-plan", source, mapOf(ConfigAttribute.MAKE to setOf("Example Make"), ConfigAttribute.MODEL to setOf("Example Model")), listOf(ServiceRule("test-oil", "engine_oil:replace", Schedule.Fixed(Interval(km = 20000)), source)))
    private fun authorization() = SourceAuthorization("test-source", "1", "Fictional test authorization", today.minusDays(30), today.plusDays(30))

    @Test fun provenanceTextAloneDoesNotAuthorizeAManufacturerSchedule() = runBlocking {
        service(48000); current(59000)
        val source = source(); val plan = plan(source); val saved = snapshot()
        var state = DashboardEvaluator(ManufacturerCatalog(listOf(plan))).evaluate(saved, today)
        assertEquals(CatalogState.SOURCE_UNAVAILABLE, state.catalogState); assertTrue(state.tasks.isEmpty())
        state = DashboardEvaluator(ManufacturerCatalog(listOf(plan), listOf(authorization()))).evaluate(saved, today)
        assertEquals(CatalogState.MATCHED, state.catalogState); assertEquals(TaskOrigin.MANUFACTURER, state.tasks.single().origin)
        assertEquals(68000, state.tasks.single().dueKm)
        for (auth in listOf(authorization().copy(withdrawn = true), authorization().copy(sourceVersion = "2"), authorization().copy(validThrough = today.minusDays(1)))) {
            state = DashboardEvaluator(ManufacturerCatalog(listOf(plan), listOf(auth))).evaluate(saved, today)
            assertEquals(CatalogState.SOURCE_UNAVAILABLE, state.catalogState); assertTrue(state.tasks.isEmpty())
        }
        val expired = source.copy(validUntil = today.minusDays(1))
        state = DashboardEvaluator(ManufacturerCatalog(listOf(plan(expired)), listOf(authorization()))).evaluate(saved, today)
        assertEquals(CatalogState.SOURCE_UNAVAILABLE, state.catalogState); assertTrue(state.tasks.isEmpty())
    }

    @Test fun missingConfigurationAmbiguityAndDemoNeverAcquireAManufacturerPlan() = runBlocking {
        val plan = plan(source()); val saved = snapshot()
        val incomplete = plan.copy(scope = plan.scope + (ConfigAttribute.ENGINE_CODE to setOf("EX123")))
        var state = DashboardEvaluator(ManufacturerCatalog(listOf(incomplete), listOf(authorization()))).evaluate(saved, today)
        assertEquals(CatalogState.CONFIGURATION_MISSING, state.catalogState)
        assertEquals(setOf(ConfigAttribute.ENGINE_CODE), state.missingConfiguration); assertTrue(state.tasks.isEmpty())
        state = DashboardEvaluator(ManufacturerCatalog(listOf(plan, plan.copy(id = "another")), listOf(authorization()))).evaluate(saved, today)
        assertEquals(CatalogState.AMBIGUOUS, state.catalogState); assertTrue(state.tasks.isEmpty())
        val demoSource = source().copy(origin = DataOrigin.DEMO)
        state = DashboardEvaluator(ManufacturerCatalog(listOf(plan(demoSource)), listOf(authorization()))).evaluate(saved, today)
        assertEquals(CatalogState.INVALID_CATALOG, state.catalogState); assertTrue(state.tasks.isEmpty())
    }

    @Test fun aVariablePlanNeverSubstitutesAFixedRepeatWithoutItsIndicator() = runBlocking {
        service(48000); current(59000)
        val source = source()
        val plan = plan(source).copy(rules = listOf(ServiceRule("variable-oil", "engine_oil:replace", Schedule.Variable(Interval(km = 20000, months = 24)), source)))
        val state = DashboardEvaluator(ManufacturerCatalog(listOf(plan), listOf(authorization()))).evaluate(snapshot(), today)
        assertEquals(Category.NEEDS_DATA, state.tasks.single().category)
        assertTrue(DashboardReason.VARIABLE_SCHEDULE in state.tasks.single().reasons)
        assertNull(state.tasks.single().estimatedMonth)
    }

    @Test fun missingManufacturerHistoryNeverBecomesOverdueThroughTheMileageCautionPath() = runBlocking {
        val source = source()
        val plan = plan(source).copy(rules = listOf(ServiceRule("test-time-and-km", "engine_oil:replace", Schedule.Fixed(Interval(km = 20000, months = 12)), source)))
        val state = DashboardEvaluator(ManufacturerCatalog(listOf(plan), listOf(authorization()))).evaluate(snapshot(), today)
        assertEquals(Category.NEEDS_DATA, state.tasks.single().category)
        assertTrue(DashboardReason.CONFIRMED_ACTION_MISSING in state.tasks.single().reasons)
    }
}
