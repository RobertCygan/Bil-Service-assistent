package app.servicepassport.android.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
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
class UserIntervalRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private lateinit var repo: ProfileRepository
    private lateinit var car: String
    private lateinit var file: String
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private fun id() = UUID.randomUUID().toString()
    private fun input() = UserIntervalInput(id(), car, null, "engine_oil", "replace", 20000, 12)
    private suspend fun snapshot() = requireNotNull(db.vehicles().garage(car))
    private suspend fun reject(problem: InputProblem, block: suspend () -> Unit) {
        try { block(); fail("Expected $problem") } catch (e: InputRejected) { assertEquals(problem, e.problem) }
    }
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext(); file = "interval-${id()}.db"
        db = GarageDatabase.open(context, file); repo = ProfileRepository(db, clock)
        repo.save(ProfileInput("Test car", 2022)); car = requireNotNull(db.vehicles().getProfile()).id
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(file) }

    @Test fun enteredValuesAndIdentitySurviveReopeningAndDeletingTheRulePreservesHistory() = runBlocking {
        repo.saveService(ServiceInput(id(), car, null, LocalDate.of(2025, 12, 15).toEpochDay(), 48000, listOf(WorkInput("engine_oil", "replace"))))
        val rule = input(); repo.saveInterval(rule)
        val historyBefore = snapshot().history
        db.close(); db = GarageDatabase.open(context, file); repo = ProfileRepository(db, clock)
        val saved = snapshot().intervals.single()
        assertEquals(UserIntervalEntity(rule.id, car, "engine_oil", "replace", 20000, 12, 1, clock.millis(), clock.millis()), saved)
        repo.saveInterval(rule.copy(expectedRevision = 1, distanceKm = null, months = 24))
        assertEquals(2L, snapshot().intervals.single().revision)
        assertNull(snapshot().intervals.single().distanceKm)
        repo.deleteInterval(car, rule.id, 2)
        assertTrue(snapshot().intervals.isEmpty()); assertEquals(historyBefore, snapshot().history)
    }

    @Test fun aRetriedSaveIsIdempotentButAStaleEditorCannotOverwriteOrDelete() = runBlocking {
        val rule = input(); repo.saveInterval(rule); repo.saveInterval(rule)
        assertEquals(1L, snapshot().intervals.single().revision)
        repo.saveInterval(rule.copy(expectedRevision = 1, distanceKm = 15000))
        reject(InputProblem.CONFLICT) { repo.saveInterval(rule.copy(expectedRevision = 1, months = 24)) }
        reject(InputProblem.CONFLICT) { repo.deleteInterval(car, rule.id, 1) }
        assertEquals(15000, snapshot().intervals.single().distanceKm)
    }

    @Test fun theSameActionAndOperationCannotAcquireTwoRulesButInspectionStaysSeparate() = runBlocking {
        repo.saveInterval(input())
        reject(InputProblem.DUPLICATE_INTERVAL) { repo.saveInterval(input()) }
        repo.saveInterval(input().copy(operationCode = "inspect"))
        assertEquals(setOf("engine_oil:replace", "engine_oil:inspect"), snapshot().intervals.map { "${it.actionCode}:${it.operationCode}" }.toSet())
    }

    @Test fun invalidAndIncompleteInputsNeverReachTheDatabase() = runBlocking {
        val rule = input()
        for (invalid in listOf(rule.copy(distanceKm = null, months = null), rule.copy(distanceKm = 0), rule.copy(distanceKm = -1), rule.copy(months = 0), rule.copy(months = 1201))) {
            reject(InputProblem.INVALID_INTERVAL) { repo.saveInterval(invalid) }
        }
        reject(InputProblem.INVALID_ACTION) { repo.saveInterval(rule.copy(actionCode = "made_up")) }
        reject(InputProblem.INVALID_ACTION) { repo.saveInterval(rule.copy(operationCode = null)) }
        assertTrue(snapshot().intervals.isEmpty())
    }

    @Test fun oneCarsRuleCannotBeEditedOrDeletedThroughAnotherCarsForm() = runBlocking {
        val rule = input(); repo.saveInterval(rule)
        val other = id(); db.vehicles().save(VehicleEntity(other, "Other", null, 0, 0))
        reject(InputProblem.NOT_FOUND) { repo.saveInterval(rule.copy(vehicleId = other, expectedRevision = 1, distanceKm = 5000)) }
        reject(InputProblem.NOT_FOUND) { repo.deleteInterval(other, rule.id, 1) }
        reject(InputProblem.NOT_FOUND) { repo.saveInterval(input().copy(expectedRevision = 1)) }
        assertEquals(20000, snapshot().intervals.single().distanceKm)
        assertTrue(requireNotNull(db.vehicles().garage(other)).intervals.isEmpty())
    }
}
