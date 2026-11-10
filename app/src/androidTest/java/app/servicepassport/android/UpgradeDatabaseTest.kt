package app.servicepassport.android

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.servicepassport.android.data.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpgradeDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun archivedDatabase(file: String, version: Int, populate: (SQLiteDatabase) -> Unit) {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = JSONObject(assets.open("app.servicepassport.android.data.GarageDatabase/$version.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        context.openOrCreateDatabase(file, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indexes = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            populate(old); old.version = version
        }
    }

    @Test fun upgradesTheArchivedSchemaOnAndroidWithoutDeletingTheCar() = runBlocking {
        val file = "upgrade-v1-${UUID.randomUUID()}.db"
        archivedDatabase(file, 1) { old ->
            old.execSQL("INSERT INTO vehicles(id, display_name, model_year, created_at_utc, updated_at_utc) VALUES ('legacy-car', 'Preserved car', 2022, 100, 200)")
        }
        val upgraded = GarageDatabase.open(context, file)
        try {
            val garage = requireNotNull(upgraded.vehicles().garage("legacy-car"))
            assertEquals("legacy-car", garage.vehicle.id)
            assertEquals("Preserved car", garage.vehicle.displayName)
            assertEquals(2022, garage.vehicle.modelYear)
            assertEquals(100L, garage.vehicle.createdAtUtc); assertEquals(200L, garage.vehicle.updatedAtUtc)
            assertNull(garage.vehicle.firstRegistrationDay); assertNull(garage.currentReading)
            assertTrue(garage.history.isEmpty()); assertTrue(garage.intervals.isEmpty())
            assertTrue(garage.indicators.isEmpty()); assertEquals(5, upgraded.openHelper.writableDatabase.version)
        } finally { upgraded.close(); context.deleteDatabase(file) }
    }

    @Test fun stageThreeHistorySelectionAndConfigurationSurviveUpgradeAndANewInterval() = runBlocking {
        val file = "upgrade-v2-${UUID.randomUUID()}.db"
        archivedDatabase(file, 2) { old ->
            old.execSQL("""INSERT INTO vehicles VALUES ('legacy-car', 'Preserved car', 2022, 100, 200, 19000, 'Example Make', 'Example Model', 'Example Generation', 'WVWZZZ1JZXW000001', 'AB12345', 'EX123', 'automatic', 'EXG', 'diesel', 'NO', 'fixed', 'normal', 7)""")
            old.execSQL("INSERT INTO service_events VALUES ('event', 'legacy-car', 20000, 125050, 'NOK', 'Saved service note', 4, 300, 400)")
            old.execSQL("INSERT INTO service_actions VALUES ('action-1', 'event', 0, 'engine_oil', 'replace', NULL)")
            old.execSQL("INSERT INTO service_actions VALUES ('action-2', 'event', 1, NULL, NULL, 'Custom workshop work')")
            old.execSQL("INSERT INTO odometer_readings VALUES ('service-reading', 'legacy-car', 'event', 20000, 48000, 'Saved odometer note', 4, 300, 400)")
            old.execSQL("INSERT INTO odometer_readings VALUES ('newer-reading', 'legacy-car', NULL, 20100, 59000, '', 1, 500, 500)")
            // Selection is intentional, even when another reading has a later date.
            old.execSQL("INSERT INTO current_odometer VALUES ('legacy-car', 'service-reading')")
        }
        var upgraded = GarageDatabase.open(context, file)
        try {
            val saved = requireNotNull(upgraded.vehicles().garage("legacy-car"))
            assertEquals(VehicleEntity("legacy-car", "Preserved car", 2022, 100, 200, 19000, "Example Make", "Example Model", "Example Generation", "WVWZZZ1JZXW000001", "AB12345", "EX123", "automatic", "EXG", "diesel", "NO", "fixed", "normal", 7), saved.vehicle)
            assertEquals(ServiceEventEntity("event", "legacy-car", 20000, 125050, "NOK", "Saved service note", 4, 300, 400), saved.history.single().event)
            assertEquals(listOf(ServiceActionEntity("action-1", "event", 0, "engine_oil", "replace", null), ServiceActionEntity("action-2", "event", 1, null, null, "Custom workshop work")), saved.history.single().orderedActions)
            assertEquals(OdometerReadingEntity("service-reading", "legacy-car", "event", 20000, 48000, "Saved odometer note", 4, 300, 400), saved.currentReading)
            assertEquals(saved.currentReading, saved.history.single().reading)
            assertEquals(2, saved.readings.size); assertTrue(saved.intervals.isEmpty())
            ProfileRepository(upgraded).saveInterval(UserIntervalInput("interval", "legacy-car", null, "engine_oil", "replace", 20000, 12))
            upgraded.close(); upgraded = GarageDatabase.open(context, file)
            val reopened = requireNotNull(upgraded.vehicles().garage("legacy-car"))
            assertEquals(saved.vehicle, reopened.vehicle); assertEquals(saved.history, reopened.history)
            assertEquals(saved.currentReading, reopened.currentReading)
            assertEquals(setOf("service-reading", "newer-reading"), reopened.readings.map { it.id }.toSet())
            assertEquals(20000, reopened.intervals.single().distanceKm); assertEquals(12, reopened.intervals.single().months)
            assertTrue(reopened.indicators.isEmpty()); assertEquals(5, upgraded.openHelper.writableDatabase.version)
        } finally { upgraded.close(); context.deleteDatabase(file) }
    }
}
