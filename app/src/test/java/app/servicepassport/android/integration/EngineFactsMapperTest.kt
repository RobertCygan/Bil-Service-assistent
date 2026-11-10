package app.servicepassport.android.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EngineFactsMapperTest {
    private lateinit var context: Context
    private lateinit var database: GarageDatabase
    private lateinit var repository: ProfileRepository
    private lateinit var car: String
    private lateinit var file: String
    private val today = LocalDate.of(2026, 11, 10)
    private fun id() = UUID.randomUUID().toString()
    private suspend fun snapshot() = requireNotNull(database.vehicles().garage(car))
    private suspend fun facts() = EngineFactsMapper.prepare(snapshot())

    @Before fun openGarage() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        file = "engine-facts-${id()}.db"
        database = GarageDatabase.open(context, file)
        repository = ProfileRepository(database, Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC))
        repository.save(ProfileInput("Test car", 2022))
        car = requireNotNull(database.vehicles().getProfile()).id
    }

    @After fun closeGarage() { database.close(); context.deleteDatabase(file) }

    @Test fun newerServiceDoesNotOverrideTheSelectionAndDeletionDoesNotInventAFallback() = runBlocking {
        val current = ReadingInput(id(), car, null, today.minusDays(1).toEpochDay(), 59000, true)
        repository.saveReading(current)
        val service = ServiceInput(id(), car, null, today.toEpochDay(), 60000, listOf(WorkInput("engine_oil", "replace")))
        repository.saveService(service)
        var input = facts()
        assertEquals(current.id, input.currentOdometer?.id)
        assertEquals(59000, input.currentOdometer?.km)
        assertEquals(listOf(59000, 60000), input.historicalOdometers.map { it.km })
        assertEquals(60000, input.performedActions.single().odometerKm)
        repository.deleteReading(car, current.id, 1)
        input = facts()
        assertNull(input.currentOdometer)
        assertEquals(listOf(60000), input.historicalOdometers.map { it.km })
        assertEquals(service.id, input.performedActions.single().eventId)
    }

    @Test fun inspectionReplacementAndCustomWorkStaySeparateAndUnknownMileageStaysNull() = runBlocking {
        val service = ServiceInput(id(), car, null, today.minusDays(300).toEpochDay(), null, listOf(
            WorkInput("engine_oil", "inspect"), WorkInput("engine_oil", "replace"), WorkInput(null, null, "Repaired window"),
        ))
        repository.saveService(service)
        database.close(); database = GarageDatabase.open(context, file)
        val input = facts()
        assertEquals(listOf("engine_oil:inspect", "engine_oil:replace"), input.performedActions.map { it.task.taskCode })
        assertTrue(input.performedActions.all { it.odometerKm == null })
        assertEquals(listOf("Repaired window"), input.unmappedActions.map { it.description })
        assertEquals(1, input.unmappedActions.map { it.eventId }.distinct().size)
        assertTrue(input.historicalOdometers.isEmpty())
        assertNull(input.currentOdometer)
    }

    @Test fun modelYearNeverBecomesARegistrationDateAndUnknownConfigurationIsOmitted() = runBlocking {
        var input = facts()
        assertEquals(mapOf("MODEL_YEAR" to "2022"), input.configuration)
        assertNull(input.inServiceDate)
        val registered = LocalDate.of(2021, 12, 20)
        repository.save(ProfileInput("Test car", 2022, registered.toEpochDay(), make = "Example Make", model = "Example Model", powertrain = "OTHER", gearboxType = "OTHER", serviceRegime = "VARIABLE", usageProfile = "NORMAL"))
        input = facts()
        assertEquals(registered, input.inServiceDate)
        assertEquals("2022", input.configuration["MODEL_YEAR"])
        assertEquals("Example Make", input.configuration["MAKE"])
        assertFalse(input.configuration.containsKey("POWERTRAIN"))
        assertFalse(input.configuration.containsKey("GEARBOX_TYPE"))
        assertEquals("VARIABLE", input.configuration["SERVICE_REGIME"])
        assertEquals(2L, input.configurationRevision)
        assertEquals(1500, input.policy.dueSoonKm)
        assertEquals(30, input.policy.dueSoonDays)
        assertEquals(90, input.policy.odometerStaleAfterDays)
    }

    @Test fun unexplainedScaleChangesAreNotNormalizedOrHiddenFromTheFutureAdapter() = runBlocking {
        repository.saveReading(ReadingInput(id(), car, null, today.minusDays(1).toEpochDay(), 59000, true))
        val reset = ReadingInput(id(), car, null, today.toEpochDay(), 100, true)
        val token = ReadingConsistency.token(snapshot(), null, reset.observedDay, reset.km, true)
        repository.saveReading(reset.copy(exceptionReason = "Counter replaced", warningsAcknowledged = true, reviewToken = token))
        val input = facts()
        assertEquals(100, input.currentOdometer?.km)
        assertEquals(listOf(59000, 100), input.historicalOdometers.map { it.km })
        assertTrue(OdometerCaution.DECREASING_VALUES in input.odometerCautions)
        assertTrue(OdometerCaution.RECORDED_EXCEPTION in input.odometerCautions)
    }

    @Test fun differentValuesOnTheSameDayKeepBothReadingsAndTheirCaution() = runBlocking {
        repository.saveReading(ReadingInput(id(), car, null, today.toEpochDay(), 59000, true))
        val other = ReadingInput(id(), car, null, today.toEpochDay(), 60000, false)
        val token = ReadingConsistency.token(snapshot(), null, other.observedDay, other.km, false)
        repository.saveReading(other.copy(exceptionReason = "Later reading the same day", warningsAcknowledged = true, reviewToken = token))
        val input = facts()
        assertEquals(59000, input.currentOdometer?.km)
        assertEquals(listOf(59000, 60000), input.historicalOdometers.map { it.km })
        assertTrue(OdometerCaution.DIFFERENT_VALUES_ON_SAME_DAY in input.odometerCautions)
    }

    @Test fun anOutOfRangeStoredReadingIsRejectedBeforeConvertingToInt() = runBlocking {
        database.vehicles().saveReading(OdometerReadingEntity(id(), car, null, today.toEpochDay(), Int.MAX_VALUE.toLong() + 1, "", 1, 0, 0))
        val saved = snapshot()
        assertThrows(IllegalArgumentException::class.java) { EngineFactsMapper.prepare(saved) }
        Unit
    }

    @Test fun anUnknownStoredCanonicalCodeIsRejectedInsteadOfBeingGuessed() = runBlocking {
        val event = ServiceInput(id(), car, null, today.toEpochDay(), null, listOf(WorkInput("engine_oil", "replace")))
        repository.saveService(event)
        database.vehicles().clearActions(event.id)
        database.vehicles().insertActions(listOf(ServiceActionEntity(id(), event.id, 0, "ENGINE_OIL", null, null)))
        val saved = snapshot()
        assertThrows(IllegalArgumentException::class.java) { EngineFactsMapper.prepare(saved) }
        Unit
    }
}
