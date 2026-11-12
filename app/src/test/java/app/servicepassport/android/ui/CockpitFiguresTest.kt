package app.servicepassport.android.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.dashboard.DashboardEvaluator
import app.servicepassport.android.data.*
import app.servicepassport.engine.Category
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The figures of the first screen come from the same result as the service overview. What is already overdue is
 * always said first; nothing is shown that the result does not contain. Data is invented.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CockpitFiguresTest {
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private lateinit var repo: ProfileRepository
    private lateinit var car: String
    private val name = "cockpit-${UUID.randomUUID()}.db"
    private val today = LocalDate.of(2026, 11, 10)
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private fun day(text: String) = LocalDate.parse(text).toEpochDay()
    private fun id() = UUID.randomUUID().toString()

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = GarageDatabase.open(context, name); repo = ProfileRepository(db, clock)
        repo.save(ProfileInput("Familiebilen", 2019)); car = requireNotNull(db.vehicles().getProfile()).id
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    private suspend fun service(date: String, km: Long, vararg actions: String) { repo.saveService(ServiceInput(id(), car, null, day(date), km, actions.map { WorkInput(it, "replace") })) }
    private suspend fun reading(km: Long) { repo.saveReading(ReadingInput(id(), car, null, day("2026-11-03"), km, useAsCurrent = true)) }
    private suspend fun interval(action: String, km: Int?, months: Int?) { repo.saveInterval(UserIntervalInput(id(), car, null, action, "replace", km, months)) }
    private suspend fun figures(): CockpitFigures {
        val snapshot = requireNotNull(db.vehicles().garage(car))
        return cockpitFigures(snapshot, DashboardEvaluator().evaluate(snapshot, today))
    }

    @Test fun aLimitAlreadyPassedIsSaidBeforeTheOneAhead() = runBlocking {
        service("2025-10-02", 78_200, "engine_oil"); service("2024-05-14", 61_300, "cabin_filter"); reading(92_480)
        interval("engine_oil", 15_000, null); interval("cabin_filter", 30_000, null)
        val result = figures()
        assertEquals(Headline.OVERDUE_KM, result.headline)
        assertEquals("92 480 is 1 180 km past the cabin filter's limit of 91 300", 1_180L, result.headlineValue)
        assertEquals(Category.OVERDUE, result.headlineCategory)
        assertEquals(91_300L, result.overdueLimitKm); assertEquals(93_200L, result.upcomingLimitKm)
        assertTrue(result.arcStartsOverdue); assertEquals(91_300L, result.arcStartKm)
        assertEquals("between 91 300 and 93 200", 1_180f / 1_900f, result.arcFraction!!, 0.001f)
        assertEquals("the gauge is full", 1f, result.gaugeFraction!!, 0f)
    }

    @Test fun withNothingOverdueTheDistanceToTheNearestLimitIsShownFromTheLastService() = runBlocking {
        service("2026-06-02", 78_200, "engine_oil"); reading(84_100); interval("engine_oil", 15_000, null)
        val result = figures()
        assertEquals(Headline.NEXT_KM, result.headline); assertEquals(9_100L, result.headlineValue)
        assertNull(result.overdueLimitKm); assertFalse(result.arcStartsOverdue)
        assertEquals(78_200L, result.arcStartKm); assertEquals(93_200L, result.upcomingLimitKm)
        assertEquals(5_900f / 15_000f, result.arcFraction!!, 0.001f)
        assertEquals(5_900f / 15_000f, result.gaugeFraction!!, 0.001f)
    }

    @Test fun somethingOverdueByDateAloneIsStillSaidFirst() = runBlocking {
        service("2024-05-14", 61_300, "brake_fluid"); service("2026-06-02", 78_200, "engine_oil"); reading(84_100)
        interval("brake_fluid", null, 24); interval("engine_oil", 15_000, null)
        val result = figures()
        assertEquals(Headline.OVERDUE_DAYS, result.headline)
        assertEquals("two years from 14 May 2024 ended 180 days before 10 November 2026", 180L, result.headlineValue)
        assertEquals(Category.OVERDUE, result.headlineCategory)
        assertNull("no distance limit has been passed", result.overdueLimitKm)
        assertEquals("the arc still shows the distance to the oil change", 93_200L, result.upcomingLimitKm)
    }

    @Test fun aCarWithNothingToCalculateFromShowsNoFigures() = runBlocking {
        val result = figures()
        assertEquals(Headline.NONE, result.headline)
        assertNull(result.headlineValue); assertNull(result.gaugeFraction); assertNull(result.arcFraction)
        assertNull(result.currentKm); assertNull(result.arcStartKm); assertNull(result.upcomingLimitKm)
    }

    @Test fun withoutACurrentMileageNoDistanceIsInvented() = runBlocking {
        service("2026-06-02", 78_200, "engine_oil"); interval("engine_oil", 15_000, null)
        val result = figures()
        assertNull(result.currentKm)
        assertEquals(Headline.NONE, result.headline)
        assertNull("the marker cannot be placed without a mileage", result.arcFraction)
        assertNull("the last service is not an end of the arc when nothing can be placed on it", result.arcStartKm)
    }

    @Test fun whenEveryDistanceLimitIsPassedTheMarkerStandsAtTheFarEnd() = runBlocking {
        service("2024-05-14", 61_300, "cabin_filter"); reading(92_480); interval("cabin_filter", 30_000, null)
        val result = figures()
        assertEquals(Headline.OVERDUE_KM, result.headline)
        assertNull(result.upcomingLimitKm)
        assertEquals(1f, result.arcFraction!!, 0f)
    }
}
