package app.servicepassport.android.screens

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.R
import app.servicepassport.android.backup.DataFiles
import app.servicepassport.android.backup.GarageBackup
import app.servicepassport.android.data.*
import app.servicepassport.android.documents.DocumentStore
import app.servicepassport.android.reminders.ReminderStore
import app.servicepassport.android.ui.CarServiceApp
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

/**
 * Settings and forms driven through the real screens: backup, deletion and restore with a document, reminder choices,
 * the car's service display, and the make/model/VIN hints. System dialogs are answered by a fake; data is invented.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp")
class SettingsAndFormsFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private lateinit var base: File
    private lateinit var store: DocumentStore
    private lateinit var repository: ProfileRepository
    private lateinit var reminders: ReminderStore
    private lateinit var car: String
    private val name = "settings-flow-${UUID.randomUUID()}.db"
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private fun day(text: String) = LocalDate.parse(text).toEpochDay()
    private fun id() = UUID.randomUUID().toString()
    private fun text(id: Int) = context.getString(id)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
        db = GarageDatabase.open(context, name)
        base = File(context.cacheDir, "settings-${UUID.randomUUID()}").also { it.mkdirs() }
        store = DocumentStore(context, db, clock, base)
        repository = ProfileRepository(db, clock)
        reminders = ReminderStore(context)
        runBlocking {
            repository.save(ProfileInput("Familiebilen", 2019))
            car = requireNotNull(db.vehicles().getProfile()).id
            repository.saveReading(ReadingInput(id(), car, null, day("2026-11-03"), 92_480, useAsCurrent = true))
        }
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name); base.deleteRecursively() }

    private fun garage() = runBlocking { db.vehicles().getProfile()?.let { db.vehicles().garage(it.id) } }
    private fun live() = File(base, DocumentStore.LIVE).listFiles().orEmpty().map { it.name }

    private fun start(backupFile: Uri? = null) {
        val files = DataFiles(context, GarageBackup(db, clock, reminders, store), null)
        compose.setAppContent(Answering { _, _ -> backupFile }) { CarServiceApp(repository, reminders, dataFiles = files, documentStore = store) }
        compose.await("profile_name")
    }

    /** A service entry with a kept document, so that backups have a file to carry. */
    private fun serviceWithDocument(content: ByteArray): String = runBlocking {
        val event = repository.saveService(ServiceInput(id(), car, null, day("2025-10-02"), 78_200, listOf(WorkInput("engine_oil", "replace")), costMinor = 389_000))
        val picked = Uri.fromFile(File(base, "faktura-${id()}.pdf").also { it.writeBytes(content) })
        assertTrue(store.attach(requireNotNull(store.stage(picked)), car, event))
        event
    }

    private fun outcome(message: Int) = compose.awaitText("data_outcome", text(message))

    @Test fun aBackupMadeInSettingsBringsEverythingBackAfterDeletingAllData() {
        val content = java.util.Random(7).let { random -> ByteArray(40_000).also(random::nextBytes) }
        val event = serviceWithDocument(content)
        val before = requireNotNull(garage())
        val file = File(base, "backup.zip")
        start(Uri.fromFile(file))

        compose.tap("nav_settings"); compose.await("backup_save")
        compose.click("backup_save"); outcome(R.string.data_backup_saved)
        assertTrue(file.length() > content.size / 2)

        compose.click("delete_all"); compose.await("confirm_delete_all"); compose.tap("confirm_delete_all")
        outcome(R.string.data_deleted)
        assertNull(garage()); assertTrue(live().isEmpty())

        compose.click("backup_restore"); compose.await("confirm_restore"); compose.tap("confirm_restore")
        outcome(R.string.data_restored)
        val after = requireNotNull(garage())
        assertEquals(before.vehicle, after.vehicle)
        assertEquals(before.currentReading, after.currentReading)
        val document = after.events.single { it.event.id == event }.documents.single()
        assertEquals(before.events.single().documents.single(), document)
        assertArrayEquals(content, store.open(document).use { it.readBytes() })

        // The restored car is shown again without restarting the app.
        compose.tap("nav_overview"); compose.await("profile_name")
        compose.onNodeWithTag("current_km").assertTextEquals("92,480 km")
    }

    // Restoring a password-protected backup is tested on the emulator (BackupOnDeviceTest): this JVM environment
    // cannot settle a text field inside a dialog, with or without this app's code.

    @Test fun cancellingTheSaveDialogWritesNothingAndReportsNothing() {
        start(null)
        compose.tap("nav_settings"); compose.await("backup_save")
        compose.click("backup_save")
        compose.onAllNodesWithTag("data_outcome").assertCountEquals(0)
        assertNotNull(garage())
    }

    @Test fun reminderChoicesAreOffAtFirstAndStoredAsChosen() {
        assertFalse(reminders.settings.enabled)
        start()
        compose.tap("nav_settings"); compose.await("reminders_enabled")
        compose.onAllNodesWithTag("reminders_repeat_30").assertCountEquals(0)
        compose.click("reminders_enabled"); compose.await("reminders_repeat_30")
        compose.click("reminders_repeat_30")
        compose.click("reminders_odometer")
        assertEquals(ReminderSettings(enabled = true, repeatEveryDays = 30, odometerReminders = false), ReminderStore(context).settings)
        compose.click("reminders_enabled")
        assertFalse(ReminderStore(context).settings.enabled)
    }

    @Test fun whatTheCarsServiceDisplayShowsAppearsOnTheServiceOverview() {
        start()
        compose.click("open_dashboard"); compose.await("open_indicator")
        compose.onAllNodesWithText(text(R.string.indicator_source)).assertCountEquals(0)
        compose.click("open_indicator"); compose.await("save_indicator")
        compose.onNodeWithTag("indicator_km").performScrollTo().performTextInput("1200")
        compose.onNodeWithTag("indicator_days").performScrollTo().performTextInput("40")
        compose.click("save_indicator"); compose.await("indicator_latest")
        val reading = requireNotNull(requireNotNull(garage()).latestIndicator)
        assertEquals(1200, reading.remainingKm); assertEquals(40, reading.remainingDays); assertEquals(92_480L, reading.odometerKm)

        compose.onNodeWithText(text(R.string.service_overview)).performScrollTo().performClick()
        compose.await("dashboard_title")
        // 1 200 km left is inside the 1 500 km "due soon" window; the task is labelled as coming from the car's display.
        compose.awaitText("count_DUE_SOON", "1")
        compose.onNodeWithText(text(R.string.indicator_source)).assertExists()
    }

    @Test fun aCarWithNothingToCalculateFromIsPointedToBothWaysOfGettingAResult() {
        start()
        compose.await("getting_started")
        compose.click("start_interval"); compose.await("save_interval")
        compose.tap("back"); compose.await("getting_started")
        compose.click("start_indicator"); compose.await("save_indicator")
        compose.onNodeWithTag("indicator_km").performScrollTo().performTextInput("1200")
        compose.click("save_indicator"); compose.await("indicator_latest")
        // With something to show, the hint gives way to the result.
        compose.tap("nav_overview"); compose.await("profile_name")
        compose.awaitGone("getting_started")
        compose.awaitText("count_DUE_SOON", "1")
    }

    @Test fun makeAndModelSuggestionsFillTheFormAndABadVinIsExplained() {
        start()
        compose.click("edit_car"); compose.await("save_car")
        compose.click("profile_optional")
        compose.onNodeWithText(text(R.string.make)).performScrollTo().performTextInput("Vol")
        compose.await("make_suggestion")
        compose.onNode(hasTestTag("make_suggestion") and hasText("Volvo")).performScrollTo().performClick()
        compose.await("model_suggestion")
        val model = compose.onAllNodesWithTag("model_suggestion").onFirst().fetchSemanticsNode().config
            .first { it.key.name == "Text" }.value.toString().trim('[', ']')
        compose.onAllNodesWithTag("model_suggestion").onFirst().performScrollTo().performClick()
        compose.awaitGone("model_suggestion")

        compose.onNodeWithText(text(R.string.vin)).performScrollTo().performTextInput("123")
        compose.await("vin_problem")
        compose.onNodeWithTag("vin_problem").assertTextEquals(text(R.string.vin_wrong_length))

        compose.click("save_car"); compose.await("profile_name")
        val vehicle = requireNotNull(garage()).vehicle
        assertEquals("Volvo", vehicle.make); assertEquals(model, vehicle.model)
        // The hint is advice: an odd VIN is stored as typed, and no schedule appears because of the chosen make.
        assertEquals("123", vehicle.vin)
    }
}
