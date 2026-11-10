package app.servicepassport.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfilePersistenceTest {
    @Test fun realFileDatabaseRetainsProfileAndIdentityAfterCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-${UUID.randomUUID()}.db"
        val first = GarageDatabase.open(context, name)
        val id: String
        try {
            ProfileRepository(first).save(ProfileInput("  Test car  ", 2022))
            id = requireNotNull(first.vehicles().getProfile()).id
            ProfileRepository(first).save(ProfileInput("Updated car", null))
            assertEquals(id, first.vehicles().getProfile()?.id)
        } finally { first.close() }
        val reopened = GarageDatabase.open(context, name)
        try {
            val saved = requireNotNull(reopened.vehicles().getProfile())
            assertEquals(id, saved.id)
            assertEquals("Updated car", saved.displayName)
            assertNull(saved.modelYear)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun anUnsupportedSchemaFailsWithoutDestructivelyReplacingTheGarage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-policy-${UUID.randomUUID()}.db"
        val first = GarageDatabase.open(context, name)
        ProfileRepository(first).save(ProfileInput("Keep this car", 2022))
        first.close()
        context.openOrCreateDatabase(name, 0, null).use { it.version = 99 }
        val reopened = GarageDatabase.open(context, name)
        try {
            assertThrows(IllegalStateException::class.java) { reopened.openHelper.writableDatabase }
        } finally { reopened.close() }
        context.openOrCreateDatabase(name, 0, null).use { raw ->
            raw.rawQuery("SELECT display_name FROM vehicles", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Keep this car", cursor.getString(0))
            }
        }
        context.deleteDatabase(name)
        Unit
    }
}
