package app.servicepassport.android.reminders

import android.content.Context
import app.servicepassport.reminders.DeliveredReminder
import app.servicepassport.reminders.ReminderSettings
import java.time.LocalDate

/**
 * Reminder preferences and the record of what was already shown.
 * Kept outside Room: the delivery record is derived state that must not travel in a backup.
 */
class ReminderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    var settings: ReminderSettings
        get() = ReminderSettings(
            enabled = prefs.getBoolean(ENABLED, false), // off until the driver chooses reminders
            repeatEveryDays = prefs.getInt(REPEAT, DEFAULT_REPEAT_DAYS).takeIf { it > 0 },
            odometerReminders = prefs.getBoolean(ODOMETER, true),
        )
        set(value) {
            prefs.edit().putBoolean(ENABLED, value.enabled).putInt(REPEAT, value.repeatEveryDays ?: 0).putBoolean(ODOMETER, value.odometerReminders).apply()
        }

    fun delivered(): List<DeliveredReminder> = prefs.getStringSet(DELIVERED, emptySet()).orEmpty().mapNotNull { line ->
        val parts = line.split(SEPARATOR)
        if (parts.size != 3) null else runCatching { DeliveredReminder(parts[0], parts[1], LocalDate.parse(parts[2])) }.getOrNull()
    }

    fun record(reminder: DeliveredReminder) = replace(delivered().filterNot { it.key == reminder.key } + reminder)
    fun forget(keys: Set<String>) { if (keys.isNotEmpty()) replace(delivered().filterNot { it.key in keys }) }
    fun forgetAll() = replace(emptyList())

    private fun replace(all: List<DeliveredReminder>) {
        prefs.edit().putStringSet(DELIVERED, all.map { listOf(it.key, it.fingerprint, it.lastNotifiedOn.toString()).joinToString(SEPARATOR) }.toSet()).apply()
    }

    /** Back to the state of a fresh installation. Shown notifications are cancelled by the coordinator. */
    fun reset() { prefs.edit().clear().apply() }

    /** Preferences that belong in a backup. The delivery record is deliberately absent. */
    fun exportSettings(): Map<String, String> = settings.let {
        mapOf(ENABLED to it.enabled.toString(), REPEAT to (it.repeatEveryDays ?: 0).toString(), ODOMETER to it.odometerReminders.toString())
    }

    fun importSettings(values: Map<String, String>) {
        val current = settings
        val repeat = values[REPEAT]?.toIntOrNull()?.takeIf { it in 0..365 }
        settings = ReminderSettings(
            enabled = values[ENABLED]?.toBooleanStrictOrNull() ?: current.enabled,
            repeatEveryDays = if (repeat == null) current.repeatEveryDays else repeat.takeIf { it > 0 },
            odometerReminders = values[ODOMETER]?.toBooleanStrictOrNull() ?: current.odometerReminders,
        )
        forgetAll() // restored data starts with a clean delivery record
    }

    companion object {
        const val DEFAULT_REPEAT_DAYS = 14
        const val ENABLED = "reminders.enabled"
        const val REPEAT = "reminders.repeatEveryDays"
        const val ODOMETER = "reminders.odometer"
        private const val DELIVERED = "delivered"
        private const val SEPARATOR = "\u0001"
    }
}
