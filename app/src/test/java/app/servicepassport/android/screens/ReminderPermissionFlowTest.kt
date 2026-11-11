package app.servicepassport.android.screens

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
import app.servicepassport.android.reminders.ReminderStore
import app.servicepassport.android.ui.CarServiceApp
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Reminders switched on while notifications are blocked for the app: the driver is told, is taken to the right system
 * page, and the note goes away after coming back with notifications allowed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp")
class ReminderPermissionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private val name = "permission-flow-${UUID.randomUUID()}.db"
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val system get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
        db = GarageDatabase.open(context, name)
        runBlocking { ProfileRepository(db, clock).save(ProfileInput("Familiebilen", 2019)) }
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun blockedNotificationsAreExplainedAndTheSystemPageIsOneTapAway() {
        system.setNotificationsEnabled(false)
        val store = ReminderStore(context)
        compose.setAppContent(Answering { _, _ -> false }) { CarServiceApp(ProfileRepository(db, clock), store) }
        compose.await("profile_name")
        compose.tap("nav_settings"); compose.await("reminders_enabled")
        compose.onAllNodesWithTag("reminders_open_settings").assertCountEquals(0)

        compose.click("reminders_enabled"); compose.await("reminders_open_settings")
        assertTrue("the choice is kept even though nothing can be shown yet", store.settings.enabled)

        compose.click("reminders_open_settings")
        val opened = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened.action)
        assertEquals(context.packageName, opened.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        // The driver allows notifications there and comes back.
        system.setNotificationsEnabled(true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.awaitGone("reminders_open_settings")
        compose.onNodeWithTag("reminders_repeat_14").assertExists()
    }

    @Test fun withNotificationsAllowedThereIsNothingToExplain() {
        compose.setAppContent(Answering { _, _ -> true }) { CarServiceApp(ProfileRepository(db, clock), ReminderStore(context)) }
        compose.await("profile_name")
        compose.tap("nav_settings"); compose.await("reminders_enabled")
        compose.click("reminders_enabled"); compose.await("reminders_repeat_14")
        compose.onAllNodesWithTag("reminders_open_settings").assertCountEquals(0)
    }
}
