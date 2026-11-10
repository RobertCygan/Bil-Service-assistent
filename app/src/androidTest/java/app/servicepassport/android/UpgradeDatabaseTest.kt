package app.servicepassport.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.servicepassport.android.data.GarageDatabase
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpgradeDatabaseTest {
    @Test fun upgradesTheArchivedSchemaOnAndroidWithoutDeletingTheCar() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = "upgrade-${UUID.randomUUID()}.db"
        val schema = JSONObject(instrumentation.context.assets.open("app.servicepassport.android.data.GarageDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        context.openOrCreateDatabase(file, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO vehicles(id, display_name, model_year, created_at_utc, updated_at_utc) VALUES ('legacy-car', 'Preserved car', 2022, 100, 200)")
            old.version = 1
        }
        val upgraded = GarageDatabase.open(context, file)
        try {
            val garage = requireNotNull(upgraded.vehicles().garage("legacy-car"))
            assertEquals("legacy-car", garage.vehicle.id)
            assertEquals("Preserved car", garage.vehicle.displayName)
            assertEquals(2022, garage.vehicle.modelYear)
            assertEquals(100L, garage.vehicle.createdAtUtc)
            assertEquals(200L, garage.vehicle.updatedAtUtc)
            assertNull(garage.vehicle.firstRegistrationDay)
            assertNull(garage.currentReading)
            assertTrue(garage.history.isEmpty())
            assertEquals(2, upgraded.openHelper.writableDatabase.version)
        } finally { upgraded.close(); context.deleteDatabase(file) }
    }
}
