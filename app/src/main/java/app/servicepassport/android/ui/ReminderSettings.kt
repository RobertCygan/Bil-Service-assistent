package app.servicepassport.android.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.servicepassport.android.R
import app.servicepassport.android.reminders.ReminderStore
import app.servicepassport.reminders.ReminderSettings

/** Reminders are opt-in. Refusing the system permission never blocks the rest of the app. */
@Composable internal fun ReminderSettingsSection(store: ReminderStore, onChanged: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(store.settings) }
    fun allowed() = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    var permitted by remember { mutableStateOf(allowed()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = allowed() }
    // The driver may have changed the permission in the system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permitted = allowed() }
    fun update(value: ReminderSettings) { settings = value; store.settings = value; onChanged() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.reminders), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.reminders_help), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminders_enable), Modifier.weight(1f))
            Switch(checked = settings.enabled, modifier = Modifier.testTag("reminders_enabled"), onCheckedChange = { on ->
                update(settings.copy(enabled = on))
                if (on && !allowed() && Build.VERSION.SDK_INT >= 33) request.launch(Manifest.permission.POST_NOTIFICATIONS)
                permitted = allowed()
            })
        }
        if (settings.enabled) {
            if (!permitted) {
                NoteCard(stringResource(R.string.reminders_blocked))
                // After two refusals Android no longer shows its question; the system settings are the only way back.
                TextButton(modifier = Modifier.testTag("reminders_open_settings"), onClick = {
                    val settingsPage = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    try { context.startActivity(settingsPage) } catch (_: ActivityNotFoundException) { /* no such page on this phone */ }
                }) { Text(stringResource(R.string.reminders_open_settings)) }
            }
            Text(stringResource(R.string.reminders_repeat), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Pair<Int?, Int>>(null to R.string.reminders_repeat_never, 7 to R.string.reminders_repeat_7, 14 to R.string.reminders_repeat_14, 30 to R.string.reminders_repeat_30).forEach { (days, label) ->
                    FilterChip(selected = settings.repeatEveryDays == days, modifier = Modifier.testTag("reminders_repeat_${days ?: 0}"), onClick = { update(settings.copy(repeatEveryDays = days)) }, label = { Text(stringResource(label)) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reminders_odometer), Modifier.weight(1f))
                Switch(checked = settings.odometerReminders, modifier = Modifier.testTag("reminders_odometer"), onCheckedChange = { update(settings.copy(odometerReminders = it)) })
            }
            Text(stringResource(R.string.reminders_timing_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}
