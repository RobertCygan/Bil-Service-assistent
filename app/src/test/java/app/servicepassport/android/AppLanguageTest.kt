package app.servicepassport.android

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.reminders.AndroidReminderNotifier
import app.servicepassport.reminders.Reminder
import app.servicepassport.reminders.ReminderKind
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The phone here is set to English and the driver chose Norwegian inside the app. Before Android 13 the system applies
 * that choice only to screens, so a notification built in the background has to ask for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en")
class AppLanguageTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val odometer = Reminder("odometer", ReminderKind.UPDATE_ODOMETER, "car", null, "f")

    @After fun reset() = AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())

    private fun posted(): Notification {
        AndroidReminderNotifier(context).post(odometer, null, "Familiebilen", 120)
        return shadowOf(manager).allNotifications.single()
    }

    @Test fun withoutAChoiceTheNotificationFollowsThePhone() {
        assertSame(context, AppLanguage.localized(context))
        assertEquals("Familiebilen: update the mileage", posted().extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Service reminders", manager.getNotificationChannel("service_reminders").name.toString())
    }

    @Test fun aNotificationUsesTheLanguageChosenInTheApp() {
        AppLanguage.choose(context, "nb-NO")
        val notification = posted()
        assertEquals("Familiebilen: oppdater kilometerstanden", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(), "120 dager gammel" in notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Servicepåminnelser", manager.getNotificationChannel("service_reminders").name.toString())
    }

    @Test fun changingBackToEnglishOnANorwegianPhoneIsHonouredToo() {
        AppLanguage.choose(context, "nb-NO"); AppLanguage.choose(context, "en")
        assertEquals("Service reminders", AppLanguage.localized(context).getString(R.string.reminder_channel))
    }

    @Test fun aChoiceMadeBeforeThisVersionIsPickedUpWhenAScreenOpens() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("nb-NO"))
        assertSame("nothing is remembered yet", context, AppLanguage.localized(context))
        AppLanguage.adopt(context)
        assertEquals("Servicepåminnelser", AppLanguage.localized(context).getString(R.string.reminder_channel))
    }

    @Test fun adoptingNeverOverwritesAnExistingChoice() {
        AppLanguage.choose(context, "en")
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("nb-NO"))
        AppLanguage.adopt(context)
        assertEquals("Service reminders", AppLanguage.localized(context).getString(R.string.reminder_channel))
    }

    @Test fun fromAndroid13TheSystemAlreadyAppliesTheChoice() {
        AppLanguage.choose(context, "nb-NO")
        assertSame(context, AppLanguage.localized(context, sdk = 33))
    }

    @Test fun theStatusBarIconIsTheShapeOnly() {
        // Android draws a status bar icon from its outline alone. The launcher icon has a filled background and would
        // show as a plain square.
        assertEquals(R.drawable.ic_stat_service, posted().smallIcon.resId)
        val icon = java.io.File("src/main/res/drawable/ic_stat_service.xml").readText()
        assertEquals("one path: the car, no background", 1, Regex("<path").findAll(icon).count())
        assertFalse(icon, "M0,0h48v48" in icon)
    }
}
