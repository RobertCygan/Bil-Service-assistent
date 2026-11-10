package app.servicepassport.android.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
import app.servicepassport.android.reminders.ReminderStore
import app.servicepassport.reminders.ReminderSettings
import java.io.File
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The same path the settings screen uses, against real files. Data is fictional. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DataFilesTest {
    private lateinit var context: Context
    private val files = mutableListOf<String>()
    private val open = mutableListOf<GarageDatabase>()
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private fun id() = UUID.randomUUID().toString()
    private fun database(): GarageDatabase { val file = "files-${id()}.db"; files += file; return GarageDatabase.open(context, file).also { open += it } }
    private fun target(): Uri = Uri.fromFile(File(context.cacheDir, "backup-${id()}.zip"))
    /** Order-independent view of the whole database (the export mapping sorts every table). */
    private suspend fun vehicles(db: GarageDatabase) = BackupMapper.export(db.vehicles().vehicleIds().map { requireNotNull(db.vehicles().garage(it)) }).let { it.vehicles + it.odometerReadings + it.serviceEvents + it.userRules + it.indicatorReadings }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun cleanup() { open.forEach { it.close() }; files.forEach { context.deleteDatabase(it) } }

    private suspend fun filled(): GarageDatabase {
        val db = database(); val repo = ProfileRepository(db, clock)
        repo.save(ProfileInput("Familiebilen", 2022))
        val car = requireNotNull(db.vehicles().getProfile()).id
        repo.saveReading(ReadingInput(id(), car, null, LocalDate.of(2026, 11, 1).toEpochDay(), 84_512, true))
        repo.saveService(ServiceInput(id(), car, null, LocalDate.of(2025, 3, 12).toEpochDay(), 70_000, listOf(WorkInput("engine_oil", "replace"))))
        repo.saveInterval(UserIntervalInput(id(), car, null, "engine_oil", "replace", 15_000, 12))
        return db
    }

    @Test fun aSavedFileRestoresIntoAnotherInstallationWithItsReminderSettings() = runBlocking {
        val source = filled(); val store = ReminderStore(context)
        store.settings = ReminderSettings(enabled = true, repeatEveryDays = 30, odometerReminders = false)
        val file = target()
        assertEquals(DataOutcome.BACKUP_SAVED, DataFiles(context, GarageBackup(source, clock, store), null).save(file, null))

        store.reset(); assertFalse(store.settings.enabled)
        val other = database()
        assertEquals(DataOutcome.RESTORED, DataFiles(context, GarageBackup(other, clock, store), null).restore(file, null))
        assertEquals(vehicles(source), vehicles(other))
        assertEquals(ReminderSettings(true, 30, false), store.settings)
    }

    @Test fun aProtectedFileAsksForItsPasswordAndRefusesAWrongOne() = runBlocking {
        val source = filled(); val file = target()
        assertEquals(DataOutcome.BACKUP_SAVED, DataFiles(context, GarageBackup(source, clock), null).save(file, "korrekt hest"))
        val other = database(); val files = DataFiles(context, GarageBackup(other, clock), null)
        assertEquals(DataOutcome.PASSWORD_NEEDED, files.restore(file, null))
        assertEquals(DataOutcome.PASSWORD_NEEDED, files.restore(file, ""))
        assertEquals(DataOutcome.WRONG_PASSWORD_OR_DAMAGED, files.restore(file, "feil passord"))
        assertTrue(vehicles(other).isEmpty())
        assertEquals(DataOutcome.RESTORED, files.restore(file, "korrekt hest"))
        assertEquals(vehicles(source), vehicles(other))
    }

    @Test fun otherFilesAreRefusedWithoutTouchingTheData() = runBlocking {
        val db = filled(); val before = vehicles(db); val files = DataFiles(context, GarageBackup(db, clock), null)
        val text = File(context.cacheDir, "notes-${id()}.txt").apply { writeText("not a backup") }
        assertEquals(DataOutcome.NOT_A_BACKUP, files.restore(Uri.fromFile(text), null))
        assertEquals(DataOutcome.FILE_UNREADABLE, files.restore(Uri.fromFile(File(context.cacheDir, "missing-${id()}.zip")), null))
        assertEquals(before, vehicles(db))
    }

    @Test fun deletingEverythingLeavesAnEmptyAppAndDefaultReminderSettings() = runBlocking {
        val db = filled(); val store = ReminderStore(context)
        store.settings = ReminderSettings(enabled = true)
        assertEquals(DataOutcome.DELETED, DataFiles(context, GarageBackup(db, clock, store), null).deleteEverything())
        assertTrue(vehicles(db).isEmpty())
        assertNull(db.vehicles().getProfile())
        assertFalse(store.settings.enabled)
        // The emptied app accepts a new car.
        ProfileRepository(db, clock).save(ProfileInput("Ny bil", 2024))
        assertEquals("Ny bil", db.vehicles().getProfile()?.displayName)
    }

    @Test fun passwordRules() {
        assertNull(DataFiles.passwordProblem("", ""))
        assertEquals(PasswordProblem.TOO_SHORT, DataFiles.passwordProblem("kort", "kort"))
        assertEquals(PasswordProblem.TOO_SHORT, DataFiles.passwordProblem("", "x"))
        assertEquals(PasswordProblem.MISMATCH, DataFiles.passwordProblem("langt nok", "langt nok!"))
        assertNull(DataFiles.passwordProblem("langt nok", "langt nok"))
        assertEquals("car-service-backup-2026-11-10.zip", DataFiles(context, GarageBackup(database(), clock), null).suggestedName(LocalDate.of(2026, 11, 10)))
    }
}
