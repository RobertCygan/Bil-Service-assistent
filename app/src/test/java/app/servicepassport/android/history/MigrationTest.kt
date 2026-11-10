package app.servicepassport.android.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationTest {
    @Test fun theActualStageTwoSchemaUpgradesWithoutGuessingOrLosingData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = "migration-${UUID.randomUUID()}.db"
        val schema = JSONObject(requireNotNull(javaClass.classLoader?.getResourceAsStream("app.servicepassport.android.data.GarageDatabase/1.json")).bufferedReader().use { it.readText() }).getJSONObject("database")
        context.openOrCreateDatabase(file, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO vehicles(id, display_name, model_year, created_at_utc, updated_at_utc) VALUES ('legacy-car', 'My existing car', 2022, 100, 200)")
            old.version = 1
        }
        val upgraded = GarageDatabase.open(context, file)
        try {
            val garage = requireNotNull(upgraded.vehicles().garage("legacy-car"))
            assertEquals("My existing car", garage.vehicle.displayName)
            assertEquals(2022, garage.vehicle.modelYear)
            assertEquals(100L, garage.vehicle.createdAtUtc)
            assertEquals(200L, garage.vehicle.updatedAtUtc)
            assertNull(garage.vehicle.firstRegistrationDay)
            assertNull(garage.currentReading)
            assertTrue(garage.history.isEmpty())
            assertTrue(garage.indicators.isEmpty())
            assertEquals(4, upgraded.openHelper.writableDatabase.version)
        } finally { upgraded.close(); context.deleteDatabase(file) }
    }
}
