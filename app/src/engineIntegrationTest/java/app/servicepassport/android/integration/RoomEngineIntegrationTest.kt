package app.servicepassport.android.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
import app.servicepassport.engine.*
import app.servicepassport.reminders.ReminderPlanner
import app.servicepassport.reminders.ReminderKind
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real file-backed Room -> app mapping -> real pinned engine and reminder planner. All rules here are fictional. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomEngineIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private lateinit var repository: ProfileRepository
    private lateinit var car: String
    private lateinit var file: String
    private val today = LocalDate.of(2026, 11, 10)
    private val source = SourceRef("integration-demo", "Fictional integration data", "1", today, null, DataOrigin.DEMO, "Tests only; no manufacturer advice")
    private fun id() = UUID.randomUUID().toString()
    private fun oil(source: SourceRef = this.source) = ServiceRule("demo-oil", "engine_oil:replace", Schedule.Fixed(Interval(km = 20000)), source)
    private suspend fun snapshot() = requireNotNull(db.vehicles().garage(car))
    private suspend fun facts() = EngineFactsMapper.prepare(snapshot())
    private suspend fun evaluate(rule: ServiceRule = oil()) = ServiceEngine.evaluate(EngineBinding.input(facts(), today, listOf(rule))).single()
    private suspend fun service(task: WorkInput, daysAgo: Long, km: Long?) {
        repository.saveService(ServiceInput(id(), car, null, today.minusDays(daysAgo).toEpochDay(), km, listOf(task)))
    }
    private suspend fun current(km: Long, daysAgo: Long = 0): ReadingInput {
        val reading = ReadingInput(id(), car, null, today.minusDays(daysAgo).toEpochDay(), km, true)
        repository.saveReading(reading)
        return reading
    }

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        file = "room-engine-${id()}.db"
        db = GarageDatabase.open(context, file)
        repository = ProfileRepository(db, Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC))
        repository.save(ProfileInput("Integration test car", 2022))
        car = requireNotNull(db.vehicles().getProfile()).id
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(file) }

    @Test fun deletedCurrentReadingStaysMissingEvenWhenAServiceHasNewerMileage() = runBlocking {
        service(WorkInput("engine_oil", "replace"), 100, 48000)
        val selected = current(59000, 1)
        service(WorkInput("brakes", "inspect"), 0, 60000)
        var result = evaluate()
        assertEquals(OdometerReading(today.minusDays(1), 59000), result.basis.odometerUsed)
        assertEquals(9000, result.remainingKm)
        assertTrue(Reason.NEWER_ODOMETER_IN_HISTORY in result.reasons)
        repository.deleteReading(car, selected.id, 1)
        result = evaluate()
        assertNull(result.basis.odometerUsed)
        assertNull(result.remainingKm)
        assertEquals(Category.NEEDS_DATA, result.category)
        assertTrue(Reason.ODOMETER_MISSING in result.reasons)
    }

    @Test fun inspectionAndCustomTextDoNotResetAReplacementRule() = runBlocking {
        service(WorkInput("engine_oil", "replace"), 300, 48000)
        service(WorkInput("engine_oil", "inspect"), 10, 58000)
        service(WorkInput(null, null, "Engine oil mentioned in a custom note"), 5, null)
        current(59000)
        val result = evaluate()
        assertEquals(68000, result.dueKm)
        assertEquals(today.minusDays(300), result.basis.anchorDate)
        assertEquals(48000, result.basis.anchorKm)
        assertEquals(9000, result.remainingKm)
    }

    @Test fun unknownServiceMileageNeverBecomesZero() = runBlocking {
        service(WorkInput("engine_oil", "replace"), 100, null)
        current(59000)
        val result = evaluate()
        assertNull(result.dueKm)
        assertNull(result.basis.anchorKm)
        assertEquals(Category.NEEDS_DATA, result.category)
        assertTrue(Reason.LAST_SERVICE_ODOMETER_MISSING in result.reasons)
    }

    @Test fun expiredSourceKeepsItsWarningAndCannotGenerateAnOrdinaryReminder() = runBlocking {
        service(WorkInput("engine_oil", "replace"), 100, 48000)
        current(69000)
        val result = evaluate(oil(source.copy(verifiedOn = today.minusDays(300), validUntil = today.minusDays(1))))
        assertEquals(Category.OVERDUE, result.category)
        assertTrue(Reason.SOURCE_EXPIRED in result.reasons)
        assertTrue(ReminderPlanner.plan(car, listOf(result), today).notifyNow.isEmpty())
    }

    @Test fun anExplainedCounterResetIsStillAnAnomalyAndDoesNotProduceAConfidentReminder() = runBlocking {
        service(WorkInput("engine_oil", "replace"), 300, 48000)
        current(59000, 1)
        val reset = ReadingInput(id(), car, null, today.toEpochDay(), 100, true)
        val token = ReadingConsistency.token(snapshot(), null, reset.observedDay, reset.km, true)
        repository.saveReading(reset.copy(exceptionReason = "Counter replaced", warningsAcknowledged = true, reviewToken = token))
        val result = evaluate()
        assertEquals(100, result.basis.odometerUsed?.km)
        assertTrue(Reason.ODOMETER_INCONSISTENT in result.reasons)
        assertNull(result.estimatedKmDueMonth)
        assertEquals(listOf(ReminderKind.UPDATE_ODOMETER), ReminderPlanner.plan(car, listOf(result), today).notifyNow.map { it.kind })
    }

    @Test fun incompleteConfigurationDoesNotAcquireAVerifiedPlanAndDemoIsRejectedForProduction() = runBlocking {
        repository.save(ProfileInput("Integration test car", 2022, make = "Example Make", model = "Example Model", powertrain = "OTHER"))
        val plan = ServicePlan("demo-plan", source, mapOf(ConfigAttribute.MAKE to setOf("Example Make"), ConfigAttribute.MODEL to setOf("Example Model"), ConfigAttribute.ENGINE_CODE to setOf("EX123"), ConfigAttribute.POWERTRAIN to setOf("PETROL")), listOf(oil()))
        val resolution = PlanResolver.resolve(EngineBinding.configuration(facts()), listOf(plan))
        assertTrue(resolution is PlanResolution.NoVerifiedSchedule)
        assertEquals(setOf(ConfigAttribute.ENGINE_CODE, ConfigAttribute.POWERTRAIN), (resolution as PlanResolution.NoVerifiedSchedule).missingAttributes)
        assertTrue(CatalogValidator.productionIssues(listOf(plan), today).any { it.code == CatalogIssueCode.NOT_VERIFIED_ORIGIN })
    }
}
