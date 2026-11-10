package app.servicepassport.android.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.dashboard.DashboardTask
import app.servicepassport.android.data.*
import app.servicepassport.reminders.Reminder
import app.servicepassport.reminders.ReminderKind
import app.servicepassport.reminders.ReminderSettings
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real database, real dashboard, real planner; only the notification surface is a fake. Numbers are fictional. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReminderCoordinatorTest {
    private class FakeNotifier(var allowed: Boolean = true) : ReminderNotifier {
        val shown = linkedMapOf<String, Pair<Reminder, DashboardTask?>>()
        val cancelled = mutableListOf<String>()
        var posts = 0
        override fun canPost() = allowed
        override fun post(reminder: Reminder, task: DashboardTask?, vehicleName: String, odometerAgeDays: Long?) { posts++; shown[reminder.key] = reminder to task }
        override fun cancel(key: String) { cancelled += key; shown.remove(key) }
    }

    private class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
        override fun instant() = now
    }

    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private lateinit var repo: ProfileRepository
    private lateinit var store: ReminderStore
    private lateinit var car: String
    private lateinit var file: String
    private val clock = MovableClock(Instant.parse("2026-11-10T12:00:00Z"))
    private val notifier = FakeNotifier()
    private val today get() = LocalDate.now(clock)
    private fun id() = UUID.randomUUID().toString()
    private fun coordinator() = ReminderCoordinator(db, store, notifier, clock = clock)
    private suspend fun run(mayNotify: Boolean = true) = coordinator().sync(mayNotify)
    private suspend fun service(km: Long?, daysAgo: Long, action: String = "engine_oil") =
        ServiceInput(id(), car, null, today.minusDays(daysAgo).toEpochDay(), km, listOf(WorkInput(action, "replace"))).also { repo.saveService(it) }
    private suspend fun reading(km: Long, daysAgo: Long = 0) = repo.saveReading(ReadingInput(id(), car, null, today.minusDays(daysAgo).toEpochDay(), km, true))
    private suspend fun interval(action: String = "engine_oil", km: Int? = 20000, months: Int? = null) = repo.saveInterval(UserIntervalInput(id(), car, null, action, "replace", km, months))

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext(); file = "reminders-${id()}.db"
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
        db = GarageDatabase.open(context, file); repo = ProfileRepository(db, clock); store = ReminderStore(context)
        repo.save(ProfileInput("Familiebilen", 2022)); car = requireNotNull(db.vehicles().getProfile()).id
        store.settings = ReminderSettings(enabled = true)
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(file) }

    @Test fun remindersAreOffUntilTheDriverTurnsThemOn() = runBlocking {
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
        service(48000, 300); reading(70000); interval()
        run()
        assertEquals(0, notifier.posts)
        assertFalse(ReminderStore(context).settings.enabled)
    }

    @Test fun anOverdueItemIsShownOnceThenRepeatedOnlyAfterTheChosenInterval() = runBlocking {
        service(48000, 300); reading(70000); interval() // limit 68 000 km
        run()
        assertEquals(listOf("$car|OVERDUE|user:${db.vehicles().garage(car)!!.intervals.single().id}"), notifier.shown.keys.toList())
        assertEquals(68000, notifier.shown.values.single().second?.dueKm)

        clock.now = clock.now.plus(Duration.ofDays(1)); run()
        clock.now = clock.now.plus(Duration.ofDays(12)); run()
        assertEquals("the same state is not repeated before 14 days", 1, notifier.posts)
        clock.now = clock.now.plus(Duration.ofDays(1)); run()
        assertEquals(2, notifier.posts)
    }

    @Test fun addingTheServiceRemovesTheNotificationEvenWhileTheAppIsOpen() = runBlocking {
        service(48000, 300); reading(70000); interval()
        run()
        val key = notifier.shown.keys.single()
        service(70000, 0)
        run(mayNotify = false)
        assertEquals(listOf(key), notifier.cancelled)
        assertTrue(notifier.shown.isEmpty()); assertTrue(store.delivered().isEmpty())
    }

    @Test fun nothingNewIsShownWhileTheAppIsOpen() = runBlocking {
        service(48000, 300); reading(70000); interval()
        val plan = run(mayNotify = false)
        assertEquals(1, plan?.notifyNow?.size); assertEquals(0, notifier.posts)
        run()
        assertEquals("the background run still shows it", 1, notifier.posts)
    }

    @Test fun aRefusedPermissionShowsNothingAndRecordsNothing() = runBlocking {
        service(48000, 300); reading(70000); interval()
        notifier.allowed = false; run()
        assertEquals(0, notifier.posts); assertTrue(store.delivered().isEmpty())
        notifier.allowed = true; run()
        assertEquals("shown as soon as it is allowed", 1, notifier.posts)
    }

    @Test fun theNotificationNeverSaysMoreThanTheDashboard() = runBlocking {
        // 1 000 km before the limit, but the reading is 120 days old: the dashboard asks for data instead of "due soon".
        service(48000, 300); reading(67000, daysAgo = 120); interval()
        run()
        assertEquals(listOf(ReminderKind.UPDATE_ODOMETER), notifier.shown.values.map { it.first.kind })
    }

    @Test fun aMissingCurrentReadingAsksForTheMileageOnce() = runBlocking {
        service(48000, 300); interval()
        run(); run()
        assertEquals(listOf("$car|UPDATE_ODOMETER"), notifier.shown.keys.toList()); assertEquals(1, notifier.posts)
        reading(50000); run(mayNotify = false)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test fun mileageRemindersCanBeSwitchedOffSeparately() = runBlocking {
        store.settings = ReminderSettings(enabled = true, odometerReminders = false)
        service(48000, 300); interval()
        run()
        assertEquals(0, notifier.posts)
    }

    @Test fun switchingRemindersOffRemovesWhatWasShown() = runBlocking {
        service(48000, 300); reading(70000); interval()
        run(); assertEquals(1, notifier.shown.size)
        store.settings = ReminderSettings(enabled = false)
        run(mayNotify = false)
        assertTrue(notifier.shown.isEmpty()); assertTrue(store.delivered().isEmpty())
    }

    @Test fun settingsSurviveANewStoreAndTravelInABackupWithoutTheDeliveryRecord() = runBlocking {
        store.settings = ReminderSettings(enabled = true, repeatEveryDays = null, odometerReminders = false)
        assertEquals(ReminderSettings(true, null, false), ReminderStore(context).settings)
        service(48000, 300); reading(70000); interval(); run()
        assertEquals(1, store.delivered().size)
        val exported = store.exportSettings()
        assertEquals(setOf(ReminderStore.ENABLED, ReminderStore.REPEAT, ReminderStore.ODOMETER), exported.keys)
        store.settings = ReminderSettings()
        store.importSettings(exported + ("unknown" to "x"))
        assertEquals(ReminderSettings(true, null, false), store.settings)
        assertTrue("a restore starts with a clean delivery record", store.delivered().isEmpty())
        store.importSettings(mapOf(ReminderStore.REPEAT to "-5", ReminderStore.ENABLED to "maybe"))
        assertEquals("garbage is ignored", ReminderSettings(true, null, false), store.settings)
    }

    @Test @Config(sdk = [28], qualifiers = "en") fun englishWording() = runBlocking { wording("Familiebilen: overdue", "Engine oil · Replacement · at 68,000 km", "Familiebilen: update the mileage", "120 days old") }
    @Test @Config(sdk = [28], qualifiers = "nb-rNO") fun norwegianWording() = runBlocking { wording("Familiebilen: forfalt", "Motorolje", "Familiebilen: oppdater kilometerstanden", "120 dager gammel") }

    private suspend fun wording(overdueTitle: String, overdueText: String, odometerTitle: String, odometerText: String) {
        service(48000, 300); reading(70000); interval(); run()
        val (reminder, task) = notifier.shown.values.single()
        val overdue = ReminderText.content(context, reminder, task, "Familiebilen", null)
        assertEquals(overdueTitle, overdue.title)
        assertTrue(overdue.text, overdueText in overdue.text)
        val odometer = ReminderText.content(context, Reminder("k", ReminderKind.UPDATE_ODOMETER, car, null, "f"), null, "Familiebilen", 120)
        assertEquals(odometerTitle, odometer.title)
        assertTrue(odometer.text, odometerText in odometer.text)
    }
}
