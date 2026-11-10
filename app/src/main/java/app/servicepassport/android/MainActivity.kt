package app.servicepassport.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.servicepassport.android.ui.CarServiceApp

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as CarServiceApplication).repository
        val app = application as CarServiceApplication
        // While the app is open only stale notifications are removed; new ones come from the background check.
        fun syncReminders() { lifecycleScope.launch { app.reminders.sync(mayNotify = false) } }
        setContent { CarServiceApp(repository, app.reminderStore, ::syncReminders, app.dataFiles, app.documents) }
    }
}
