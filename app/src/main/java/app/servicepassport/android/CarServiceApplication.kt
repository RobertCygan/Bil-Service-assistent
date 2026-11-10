package app.servicepassport.android

import android.app.Application
import app.servicepassport.android.data.GarageDatabase
import app.servicepassport.android.data.ProfileRepository

class CarServiceApplication : Application() {
    val repository: ProfileRepository by lazy {
        ProfileRepository(GarageDatabase.open(this))
    }
}
