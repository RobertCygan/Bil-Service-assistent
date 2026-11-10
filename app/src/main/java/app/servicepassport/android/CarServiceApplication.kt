package app.servicepassport.android

import android.app.Application
import androidx.work.Configuration
import app.servicepassport.android.backup.DataFiles
import app.servicepassport.android.backup.GarageBackup
import app.servicepassport.android.data.GarageDatabase
import app.servicepassport.android.data.ProfileRepository
import app.servicepassport.android.reminders.AndroidReminderNotifier
import app.servicepassport.android.reminders.ReminderCoordinator
import app.servicepassport.android.reminders.ReminderStore
import app.servicepassport.android.reminders.ReminderWorker

/** WorkManager is initialised on demand (see the manifest), so nothing starts before it is needed. */
class CarServiceApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()

    private val database: GarageDatabase by lazy { GarageDatabase.open(this) }
    val repository: ProfileRepository by lazy { ProfileRepository(database) }
    val reminderStore: ReminderStore by lazy { ReminderStore(this) }
    val reminders: ReminderCoordinator by lazy { ReminderCoordinator(database, reminderStore, AndroidReminderNotifier(this)) }
    val backup: GarageBackup by lazy { GarageBackup(database, settings = reminderStore) }
    val dataFiles: DataFiles by lazy { DataFiles(this, backup, reminders) }

    override fun onCreate() {
        super.onCreate()
        ReminderWorker.schedule(this)
    }
}
