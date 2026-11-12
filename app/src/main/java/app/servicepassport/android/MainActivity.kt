package app.servicepassport.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.servicepassport.android.ui.CarServiceApp

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark throughout, whatever the phone's own theme: the system bars always get light icons.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val repository = (application as CarServiceApplication).repository
        val app = application as CarServiceApplication
        // While the app is open only stale notifications are removed; new ones come from the background check.
        fun syncReminders() { lifecycleScope.launch { app.reminders.sync(mayNotify = false) } }
        AppLanguage.adopt(this)
        // Finishes a restore that was cut short and removes document files that no entry refers to.
        lifecycleScope.launch { runCatching { app.documentStore.repair() } }
        setContent { CarServiceApp(repository, app.reminderStore, ::syncReminders, app.dataFiles, app.documents, app.documentStore) }
    }
}
