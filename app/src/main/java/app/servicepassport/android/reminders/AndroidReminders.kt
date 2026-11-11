package app.servicepassport.android.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.servicepassport.android.AppLanguage
import app.servicepassport.android.CarServiceApplication
import app.servicepassport.android.MainActivity
import app.servicepassport.android.R
import app.servicepassport.android.dashboard.DashboardTask
import app.servicepassport.contract.Operation
import app.servicepassport.contract.ServiceAction
import app.servicepassport.contract.ServiceTask
import app.servicepassport.reminders.Reminder
import app.servicepassport.reminders.ReminderKind
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/** Notification title and text. Separate from Android plumbing so the wording can be tested. */
object ReminderText {
    data class Content(val title: String, val text: String)

    private fun action(action: ServiceAction): Int = when (action) {
        ServiceAction.ENGINE_OIL -> R.string.action_engine_oil; ServiceAction.OIL_FILTER -> R.string.action_oil_filter
        ServiceAction.GEARBOX_OIL -> R.string.action_gearbox_oil; ServiceAction.BRAKE_FLUID -> R.string.action_brake_fluid
        ServiceAction.COOLANT -> R.string.action_coolant; ServiceAction.SPARK_PLUGS -> R.string.action_spark_plugs
        ServiceAction.TIMING_BELT -> R.string.action_timing_belt; ServiceAction.AIR_FILTER -> R.string.action_air_filter
        ServiceAction.CABIN_FILTER -> R.string.action_cabin_filter; ServiceAction.FUEL_FILTER -> R.string.action_fuel_filter
        ServiceAction.BRAKE_PADS -> R.string.action_brake_pads; ServiceAction.BRAKE_DISCS -> R.string.action_brake_discs
        ServiceAction.INSPECTION_SERVICE -> R.string.action_inspection_service; ServiceAction.BRAKES -> R.string.action_brakes
        ServiceAction.BATTERY_12V -> R.string.action_battery_12v
    }
    private fun operation(operation: Operation): Int = when (operation) {
        Operation.REPLACE -> R.string.operation_replace; Operation.INSPECT -> R.string.operation_inspect; Operation.TEST -> R.string.operation_test
    }
    fun label(context: Context, task: ServiceTask): String = context.getString(action(task.action)) + " · " + context.getString(operation(task.operation))

    fun content(context: Context, reminder: Reminder, task: DashboardTask?, vehicleName: String, odometerAgeDays: Long?): Content {
        val locale = context.resources.configuration.locales[0]
        if (reminder.kind == ReminderKind.UPDATE_ODOMETER) {
            val text = if (odometerAgeDays == null) context.getString(R.string.reminder_odometer_missing)
            else context.getString(R.string.reminder_odometer_text, odometerAgeDays.toInt())
            return Content(context.getString(R.string.reminder_odometer_title, vehicleName), text)
        }
        val title = context.getString(if (reminder.kind == ReminderKind.OVERDUE) R.string.reminder_overdue_title else R.string.reminder_due_soon_title, vehicleName)
        val what = task?.let { label(context, it.task) } ?: context.getString(R.string.service_overview)
        val limits = listOfNotNull(
            task?.dueKm?.let { context.getString(R.string.reminder_limit_km, NumberFormat.getIntegerInstance(locale).format(it)) },
            task?.dueDate?.let { context.getString(R.string.reminder_limit_date, it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))) },
        )
        return Content(title, (listOf(what) + limits).joinToString(" · "))
    }
}

class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun canPost(): Boolean = manager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    override fun post(reminder: Reminder, task: DashboardTask?, vehicleName: String, odometerAgeDays: Long?) {
        if (!canPost()) return
        // A notification is built in the background, where older Android versions do not apply the app's own language.
        val texts = AppLanguage.localized(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, texts.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply { description = texts.getString(R.string.reminder_channel_description) },
        )
        val content = ReminderText.content(texts, reminder, task, vehicleName, odometerAgeDays)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_service)
            .setContentTitle(content.title).setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .build()
        try { manager.notify(reminder.key, NOTIFICATION_ID, notification) } catch (_: SecurityException) { /* permission revoked meanwhile */ }
    }

    override fun cancel(key: String) = manager.cancel(key, NOTIFICATION_ID)

    private companion object {
        const val CHANNEL = "service_reminders"
        const val NOTIFICATION_ID = 1
    }
}

/** Daily background check. Android decides the exact time; it is not an alarm. */
class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        (applicationContext as CarServiceApplication).reminders.sync(mayNotify = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "service-reminders"
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
