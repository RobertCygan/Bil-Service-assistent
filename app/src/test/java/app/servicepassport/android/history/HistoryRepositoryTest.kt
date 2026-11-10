package app.servicepassport.android.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HistoryRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private lateinit var repo: ProfileRepository
    private lateinit var file: String
    private lateinit var car: String
    private val today = LocalDate.of(2026, 11, 10).toEpochDay()
    private fun id() = UUID.randomUUID().toString()
    private fun event(km: Long? = 48000, day: Long = today - 300, vararg actions: WorkInput) = ServiceInput(id(), car, null, day, km, actions.toList().ifEmpty { listOf(WorkInput("engine_oil", "replace")) })
    private suspend fun snapshot() = requireNotNull(db.vehicles().garage(car))

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        file = "history-${id()}.db"; db = GarageDatabase.open(context, file)
        repo = ProfileRepository(db, Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC))
        repo.save(ProfileInput("Test car", 2022)); car = requireNotNull(db.vehicles().getProfile()).id
    }
    @After fun tearDown() { db.close(); context.deleteDatabase(file) }

    @Test fun anOldServiceDoesNotReplaceTheExplicitCurrentReading() = runBlocking {
        val current = ReadingInput(id(), car, null, today, 59000, true)
        repo.saveReading(current)
        val service = event(48000)
        repo.saveService(service)
        val saved = snapshot()
        assertEquals(current.id, saved.currentReading?.id)
        assertEquals(59000L, saved.currentReading?.km)
        assertEquals(48000L, saved.history.single().reading?.km)
    }

    @Test fun multipleActionsCostAndNotesAreAtomicAndSurviveReopening() = runBlocking {
        val service = event(48000, today - 300, WorkInput("engine_oil", "replace"), WorkInput("brakes", "inspect")).copy(costMinor = 123450, note = "Workshop visit")
        repo.saveService(service)
        db.close(); db = GarageDatabase.open(context, file)
        val record = snapshot().history.single()
        assertEquals(2, record.actions.size)
        assertEquals(listOf("replace", "inspect"), record.orderedActions.map { it.operationCode })
        assertEquals(123450L, record.event.costMinor)
        assertEquals("NOK", record.event.currency)
        assertEquals("Workshop visit", record.event.note)
    }

    @Test fun retryDoesNotDuplicateAServiceAndStaleEditsDoNotOverwrite() = runBlocking {
        val service = event()
        repo.saveService(service); repo.saveService(service)
        assertEquals(1, snapshot().events.size)
        val updated = service.copy(expectedRevision = 1, note = "Corrected note")
        repo.saveService(updated)
        try { repo.saveService(updated.copy(note = "Stale edit")); fail("Expected conflict") } catch (e: InputRejected) { assertEquals(InputProblem.CONFLICT, e.problem) }
        assertEquals("Corrected note", snapshot().history.single().event.note)
    }

    @Test fun unknownMileageRemainsUnknownAndDoesNotInventAReading() = runBlocking {
        repo.saveService(event(null))
        assertNull(snapshot().history.single().reading)
        assertTrue(snapshot().readings.isEmpty())
    }

    @Test fun deletingTheSelectedServiceClearsSelectionWithoutFallingBackToAnotherReading() = runBlocking {
        repo.saveReading(ReadingInput(id(), car, null, today - 10, 59000, true))
        val service = event(61000, today).copy(useAsCurrent = true)
        repo.saveService(service)
        assertEquals(61000L, snapshot().currentReading?.km)
        repo.deleteService(car, service.id, 1)
        assertTrue(snapshot().events.isEmpty())
        assertNull(snapshot().currentReading)
        assertEquals(59000L, snapshot().readings.single().km)
    }

    @Test fun changedSelectedMileageMustBeExplicitlyKeptCurrent() = runBlocking {
        val service = event(61000, today).copy(useAsCurrent = true)
        repo.saveService(service)
        repo.saveService(service.copy(expectedRevision = 1, km = 62000, useAsCurrent = false))
        assertNull(snapshot().currentReading)
        assertEquals(62000L, snapshot().history.single().reading?.km)
    }

    @Test fun inconsistentReadingsRequireAReasonAndReviewOfTheCurrentData() = runBlocking {
        repo.saveReading(ReadingInput(id(), car, null, today - 1, 59000, true))
        val reset = ReadingInput(id(), car, null, today, 100, true)
        try { repo.saveReading(reset); fail("Expected review") } catch (e: InputRejected) { assertEquals(InputProblem.WARNINGS_NEED_REVIEW, e.problem) }
        assertEquals(1, snapshot().readings.size)
        val token = ReadingConsistency.token(snapshot(), null, reset.observedDay, reset.km, true)
        repo.saveReading(reset.copy(exceptionReason = "Counter replaced", warningsAcknowledged = true, reviewToken = token))
        assertEquals(100L, snapshot().currentReading?.km)
        assertEquals("Counter replaced", snapshot().currentReading?.exceptionReason)
    }

    @Test fun anOldWarningAcknowledgementCannotApproveChangedReadings() = runBlocking {
        repo.saveReading(ReadingInput(id(), car, null, today - 2, 59000, true))
        val reset = ReadingInput(id(), car, null, today, 100, true, "Counter replaced", true)
        val token = ReadingConsistency.token(snapshot(), null, today, 100, true)
        repo.saveReading(ReadingInput(id(), car, null, today - 1, 60000, true))
        try { repo.saveReading(reset.copy(reviewToken = token)); fail("Expected fresh review") } catch (e: InputRejected) { assertEquals(InputProblem.WARNINGS_NEED_REVIEW, e.problem) }
    }

    @Test fun selectingAnotherCarsReadingIsRejectedByRepositoryAndForeignKey() = runBlocking {
        val reading = ReadingInput(id(), car, null, today, 59000, true); repo.saveReading(reading)
        val other = id(); db.vehicles().save(VehicleEntity(other, "Other", null, 0, 0))
        try { repo.selectCurrent(CurrentReadingInput(reading.id, other, 1)); fail("Expected ownership check") } catch (e: InputRejected) { assertEquals(InputProblem.NOT_FOUND, e.problem) }
        try { db.vehicles().selectReading(CurrentOdometerEntity(other, reading.id)); fail("Expected foreign key") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(reading.id, snapshot().currentReading?.id)
    }

    @Test fun editsReplaceAllActionsAndDeletionRemovesTheLinkedReading() = runBlocking {
        val service = event()
        repo.saveService(service)
        repo.saveService(service.copy(expectedRevision = 1, actions = listOf(WorkInput("air_filter", "replace"), WorkInput(null, null, "Window repair"))))
        assertEquals(listOf("air_filter", null), snapshot().history.single().orderedActions.map { it.actionCode })
        repo.deleteService(car, service.id, 2)
        assertTrue(snapshot().readings.isEmpty())
        assertTrue(snapshot().events.isEmpty())
    }

    @Test fun manualReadingRetryDoesNotDuplicateAndDeletionClearsOnlyTheSelection() = runBlocking {
        repo.saveReading(ReadingInput(id(), car, null, today - 1, 58000, false))
        val selected = ReadingInput(id(), car, null, today, 59000, true)
        repo.saveReading(selected); repo.saveReading(selected)
        assertEquals(2, snapshot().readings.size)
        repo.deleteReading(car, selected.id, 1)
        assertNull(snapshot().currentReading)
        assertEquals(58000L, snapshot().readings.single().km)
    }

    @Test fun configurationChangesPreserveHistoryAndInvalidateTheConfigurationRevision() = runBlocking {
        repo.saveService(event())
        repo.save(ProfileInput("Renamed car", 2022))
        assertEquals(1L, snapshot().vehicle.configurationRevision)
        val registration = LocalDate.of(2021, 12, 20).toEpochDay()
        repo.save(ProfileInput("Renamed car", 2022, firstRegistrationDay = registration, engineCode = " example "))
        val saved = snapshot()
        assertEquals(car, saved.vehicle.id)
        assertEquals(registration, saved.vehicle.firstRegistrationDay)
        assertEquals(2022, saved.vehicle.modelYear)
        assertEquals("EXAMPLE", saved.vehicle.engineCode)
        assertEquals(2L, saved.vehicle.configurationRevision)
        assertEquals(1, saved.history.size)
    }

    @Test fun selectingAnExistingOlderReadingRequiresReasonAndFreshReview() = runBlocking {
        val older = ReadingInput(id(), car, null, today - 300, 48000, false)
        repo.saveReading(older)
        val current = ReadingInput(id(), car, null, today, 59000, true)
        repo.saveReading(current)
        val input = CurrentReadingInput(older.id, car, 1)
        val token = ReadingConsistency.token(snapshot(), older.id, older.observedDay, older.km, true)
        for (invalid in listOf(input, input.copy(exceptionReason = "Deliberate historical choice"), input.copy(warningsAcknowledged = true, reviewToken = token))) {
            try { repo.selectCurrent(invalid); fail("Expected review") } catch (e: InputRejected) { assertEquals(InputProblem.WARNINGS_NEED_REVIEW, e.problem) }
            assertEquals(current.id, snapshot().currentReading?.id)
            assertEquals("", snapshot().readings.first { it.id == older.id }.exceptionReason)
        }
        repo.selectCurrent(input.copy(exceptionReason = "Deliberate historical choice", warningsAcknowledged = true, reviewToken = token))
        val saved = requireNotNull(snapshot().currentReading)
        assertEquals(older.id, saved.id)
        assertEquals(48000L, saved.km)
        assertEquals(older.observedDay, saved.observedDay)
        assertEquals("Deliberate historical choice", saved.exceptionReason)
    }

    @Test fun selectingAnExistingReadingRejectsAReviewAfterTheDataChanged() = runBlocking {
        val older = ReadingInput(id(), car, null, today - 300, 48000, false)
        repo.saveReading(older)
        val current = ReadingInput(id(), car, null, today, 59000, true)
        repo.saveReading(current)
        val token = ReadingConsistency.token(snapshot(), older.id, older.observedDay, older.km, true)
        repo.saveReading(ReadingInput(id(), car, null, today - 1, 58000, false))
        try {
            repo.selectCurrent(CurrentReadingInput(older.id, car, 1, "Historical choice", true, token))
            fail("Expected fresh review")
        } catch (e: InputRejected) { assertEquals(InputProblem.WARNINGS_NEED_REVIEW, e.problem) }
        assertEquals(current.id, snapshot().currentReading?.id)
    }

    @Test fun reviewingAServiceReadingAlsoProtectsTheServiceFromAStaleEdit() = runBlocking {
        val service = event()
        repo.saveService(service)
        repo.saveReading(ReadingInput(id(), car, null, today, 59000, true))
        val reading = requireNotNull(snapshot().history.single().reading)
        val token = ReadingConsistency.token(snapshot(), reading.id, reading.observedDay, reading.km, true)
        repo.selectCurrent(CurrentReadingInput(reading.id, car, reading.revision, "Historical choice", true, token))
        val saved = snapshot().history.single()
        assertEquals(2L, saved.event.revision)
        assertEquals(2L, saved.reading?.revision)
        assertEquals("Historical choice", saved.reading?.exceptionReason)
        assertEquals(service.performedDay, saved.event.performedDay)
        assertEquals(48000L, saved.reading?.km)
        try { repo.saveService(service.copy(expectedRevision = 1, note = "Stale editor")); fail("Expected conflict") }
        catch (e: InputRejected) { assertEquals(InputProblem.CONFLICT, e.problem) }
    }

    @Test fun selectingALaterConsistentReadingNeedsNoException() = runBlocking {
        repo.saveReading(ReadingInput(id(), car, null, today - 1, 58000, true))
        val later = ReadingInput(id(), car, null, today, 59000, false)
        repo.saveReading(later)
        repo.selectCurrent(CurrentReadingInput(later.id, car, 1))
        val saved = requireNotNull(snapshot().currentReading)
        assertEquals(later.id, saved.id)
        assertEquals(1L, saved.revision)
        assertEquals("", saved.exceptionReason)
    }

    @Test fun priorSameDayExplanationDoesNotBypassASelectionReview() = runBlocking {
        repo.saveReading(ReadingInput(id(), car, null, today, 59000, true))
        val other = ReadingInput(id(), car, null, today, 60000, false)
        val creationToken = ReadingConsistency.token(snapshot(), null, today, 60000, false)
        repo.saveReading(other.copy(exceptionReason = "Two readings today", warningsAcknowledged = true, reviewToken = creationToken))
        try { repo.selectCurrent(CurrentReadingInput(other.id, car, 1)); fail("Expected selection review") }
        catch (e: InputRejected) { assertEquals(InputProblem.WARNINGS_NEED_REVIEW, e.problem) }
        val selectionToken = ReadingConsistency.token(snapshot(), other.id, today, 60000, true)
        repo.selectCurrent(CurrentReadingInput(other.id, car, 1, warningsAcknowledged = true, reviewToken = selectionToken))
        assertEquals(other.id, snapshot().currentReading?.id)
        assertEquals("Two readings today", snapshot().currentReading?.exceptionReason)
    }
}
