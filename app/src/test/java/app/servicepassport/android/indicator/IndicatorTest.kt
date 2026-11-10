package app.servicepassport.android.indicator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.dashboard.*
import app.servicepassport.android.data.*
import app.servicepassport.android.ui.signedWhole
import app.servicepassport.engine.AnchorKind
import app.servicepassport.engine.Category
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The car's own service display as a dashboard source. Numbers are fictional. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IndicatorTest {
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private lateinit var repo: ProfileRepository
    private lateinit var car: String
    private lateinit var file: String
    private val today = LocalDate.of(2026, 11, 10)
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private fun id() = UUID.randomUUID().toString()
    private fun day(daysAgo: Long) = today.minusDays(daysAgo).toEpochDay()
    private suspend fun snapshot() = requireNotNull(db.vehicles().garage(car))
    private suspend fun tasks() = DashboardEvaluator().evaluate(snapshot(), today).tasks
    private suspend fun display() = tasks().single { it.origin == TaskOrigin.VEHICLE_DISPLAY }
    private suspend fun indicator(daysAgo: Long, odometer: Long, km: Int?, days: Int?) = IndicatorInput(id(), car, day(daysAgo), odometer, km, days).also { repo.saveIndicator(it) }
    private suspend fun current(km: Long, daysAgo: Long = 0) = repo.saveReading(ReadingInput(id(), car, null, day(daysAgo), km, true))
    private suspend fun rejected(problem: InputProblem, block: suspend () -> Unit) { try { block(); fail("Expected $problem") } catch (e: InputRejected) { assertEquals(problem, e.problem) } }

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext(); file = "indicator-${id()}.db"
        db = GarageDatabase.open(context, file); repo = ProfileRepository(db, clock)
        repo.save(ProfileInput("Test car", 2022)); car = requireNotNull(db.vehicles().getProfile()).id
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(file) }

    @Test fun withoutAReadingTheDisplayIsNotOnTheDashboard() = runBlocking {
        current(60_000)
        assertTrue(tasks().isEmpty())
    }

    @Test fun theEnteredValuesBecomeTheDueDistanceAndDate() = runBlocking {
        current(61_000); indicator(10, 60_000, 4_000, 90)
        val task = display()
        assertEquals("inspection_service:inspect", task.task.taskCode)
        assertEquals(Category.NO_URGENCY, task.category)
        assertEquals(64_000, task.dueKm); assertEquals(3_000, task.remainingKm)
        assertEquals(today.minusDays(10).plusDays(90), task.dueDate)
        assertEquals(AnchorKind.VEHICLE_INDICATOR, task.basis?.anchorKind)
        assertNull("no interval is ever assumed", task.basis?.interval)
    }

    @Test fun closeAndPassedLimitsFollowTheSameCategoriesAsOtherItems() = runBlocking {
        current(63_000); indicator(10, 60_000, 4_000, null) // 1 000 km left
        assertEquals(Category.DUE_SOON, display().category)
        indicator(0, 63_000, -300, null) // the car itself says overdue
        display().let { assertEquals(Category.OVERDUE, it.category); assertEquals(62_700, it.dueKm) }
    }

    @Test fun onlyTheNewestReadingCountsAndDeletingItFallsBackToThePreviousOne() = runBlocking {
        current(61_000); indicator(30, 59_000, 9_000, null)
        val newest = indicator(5, 60_500, 2_000, null)
        assertEquals(62_500, display().dueKm)
        repo.deleteIndicator(car, newest.id)
        assertEquals(68_000, display().dueKm)
    }

    @Test fun aLaterConfirmedInspectionServiceMakesTheOldReadingVoid() = runBlocking {
        current(61_000); indicator(10, 60_000, 4_000, 90)
        repo.saveService(ServiceInput(id(), car, null, day(2), 60_800, listOf(WorkInput("inspection_service", "inspect"))))
        val task = display()
        assertEquals(Category.NEEDS_DATA, task.category)
        assertTrue(DashboardReason.VARIABLE_SCHEDULE in task.reasons)
        assertNull(task.dueKm); assertNull(task.dueDate)
        indicator(0, 61_000, 15_000, 365)
        assertEquals(76_000, display().dueKm)
    }

    @Test fun aDaysOnlyReadingNeedsNoMileage() = runBlocking {
        indicator(10, 60_000, null, 25)
        val task = display()
        assertEquals(Category.DUE_SOON, task.category)
        assertNull(task.dueKm); assertEquals(today.plusDays(15), task.dueDate)
    }

    @Test fun aDistanceReadingWithoutACurrentMileageAsksForIt() = runBlocking {
        indicator(10, 60_000, 4_000, null)
        val task = display()
        assertEquals(Category.NEEDS_DATA, task.category)
        assertTrue(DashboardReason.ODOMETER_MISSING in task.reasons)
    }

    @Test fun theDisplayAndTheDriversOwnIntervalAreSeparateItems() = runBlocking {
        current(61_000); indicator(10, 60_000, 4_000, null)
        repo.saveService(ServiceInput(id(), car, null, day(200), 50_000, listOf(WorkInput("engine_oil", "replace"))))
        repo.saveInterval(UserIntervalInput(id(), car, null, "engine_oil", "replace", 15_000, null))
        assertEquals(setOf(TaskOrigin.USER, TaskOrigin.VEHICLE_DISPLAY), tasks().map { it.origin }.toSet())
    }

    @Test fun invalidReadingsNeverReachTheDatabase() = runBlocking {
        rejected(InputProblem.INVALID_INDICATOR) { repo.saveIndicator(IndicatorInput(id(), car, day(0), 60_000, null, null)) }
        rejected(InputProblem.INVALID_INDICATOR) { repo.saveIndicator(IndicatorInput(id(), car, day(0), 60_000, 200_001, null)) }
        rejected(InputProblem.INVALID_INDICATOR) { repo.saveIndicator(IndicatorInput(id(), car, day(0), 60_000, null, 4_000)) }
        rejected(InputProblem.DATE_IN_FUTURE) { repo.saveIndicator(IndicatorInput(id(), car, day(-1), 60_000, 100, null)) }
        rejected(InputProblem.INVALID_KM) { repo.saveIndicator(IndicatorInput(id(), car, day(0), -1, 100, null)) }
        rejected(InputProblem.NOT_FOUND) { repo.saveIndicator(IndicatorInput(id(), "other-car", day(0), 60_000, 100, null)) }
        assertTrue(snapshot().indicators.isEmpty())
        val saved = indicator(0, 60_000, 100, null)
        repo.saveIndicator(saved) // a retried save is not a second reading
        assertEquals(1, snapshot().indicators.size)
        rejected(InputProblem.CONFLICT) { repo.saveIndicator(saved.copy(remainingKm = 200)) }
        rejected(InputProblem.NOT_FOUND) { repo.deleteIndicator("other-car", saved.id) }
    }

    @Test fun signedNumbersAreParsedStrictly() {
        assertNull(signedWhole("  "))
        assertEquals(4000, signedWhole("4000")); assertEquals(-500, signedWhole(" -500 ")); assertEquals(-500, signedWhole("−500"))
        for (bad in listOf("4 000", "4,5", "abc", "--5", "12345678")) {
            try { signedWhole(bad); fail(bad) } catch (e: InputRejected) { assertEquals(InputProblem.INVALID_INDICATOR, e.problem) }
        }
    }

    @Test fun theArchivedVersionThreeDatabaseUpgradesAndKeepsItsData() = runBlocking {
        val name = "upgrade3-${id()}.db"
        val schema = JSONObject(requireNotNull(javaClass.classLoader?.getResourceAsStream("app.servicepassport.android.data.GarageDatabase/3.json")).bufferedReader().use { it.readText() }).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO vehicles(id, display_name, model_year, created_at_utc, updated_at_utc, configuration_revision) VALUES ('v3-car', 'Kept car', 2021, 1, 2, 5)")
            old.execSQL("INSERT INTO user_intervals(id, vehicle_id, action_code, operation_code, distance_km, months, revision, created_at_utc, updated_at_utc) VALUES ('rule', 'v3-car', 'engine_oil', 'replace', 15000, 12, 3, 4, 5)")
            old.version = 3
        }
        val upgraded = GarageDatabase.open(context, name)
        try {
            val garage = requireNotNull(upgraded.vehicles().garage("v3-car"))
            assertEquals("Kept car", garage.vehicle.displayName); assertEquals(5L, garage.vehicle.configurationRevision)
            assertEquals(UserIntervalEntity("rule", "v3-car", "engine_oil", "replace", 15000, 12, 3, 4, 5), garage.intervals.single())
            assertTrue(garage.indicators.isEmpty())
            assertEquals(5, upgraded.openHelper.writableDatabase.version)
            ProfileRepository(upgraded, clock).saveIndicator(IndicatorInput("first", "v3-car", day(0), 60_000, 1_000, null))
            assertEquals(1, requireNotNull(upgraded.vehicles().garage("v3-car")).indicators.size)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
