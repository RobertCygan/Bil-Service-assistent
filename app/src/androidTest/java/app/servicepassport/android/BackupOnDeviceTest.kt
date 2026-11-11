package app.servicepassport.android

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.servicepassport.android.backup.DataFiles
import app.servicepassport.android.backup.GarageBackup
import app.servicepassport.android.data.*
import app.servicepassport.android.documents.DocumentStore
import app.servicepassport.android.ui.CarServiceApp
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/**
 * A password-protected backup through the real settings screen on Android: save, delete everything, restore with a
 * wrong and then the right password. The system "save as" and file dialogs are answered by a fake; data is invented.
 */
@RunWith(AndroidJUnit4::class)
class BackupOnDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "backup-device-${UUID.randomUUID()}.db"
    private lateinit var db: GarageDatabase
    private lateinit var base: File

    private class Answering(val result: Any?) : ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            @Suppress("UNCHECKED_CAST") dispatchResult(requestCode, result as O)
        }
    }

    @Before fun setup() {
        db = GarageDatabase.open(context, name)
        base = File(context.cacheDir, "backup-device-${UUID.randomUUID()}").also { it.mkdirs() }
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name); base.deleteRecursively() }

    private fun garage() = runBlocking { db.vehicles().getProfile()?.let { db.vehicles().garage(it.id) } }
    private fun await(tag: String, millis: Long = 20_000) = compose.waitUntil(millis) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun tap(tag: String) = compose.onNodeWithTag(tag).performClick()

    @Test fun aPasswordProtectedBackupIsRestoredOnlyWithTheRightPassword() {
        val store = DocumentStore(context, db, base = base)
        val repository = ProfileRepository(db)
        val content = ByteArray(60_000) { (it % 251).toByte() }
        val event = runBlocking {
            repository.save(ProfileInput("Backup test car", 2019))
            val car = requireNotNull(db.vehicles().getProfile()).id
            val id = repository.saveService(ServiceInput(UUID.randomUUID().toString(), car, null, LocalDate.of(2025, 10, 2).toEpochDay(), 78_200, listOf(WorkInput("engine_oil", "replace"))))
            val picked = Uri.fromFile(File(base, "invoice.pdf").also { it.writeBytes(content) })
            assertTrue(store.attach(requireNotNull(store.stage(picked)), car, id))
            id
        }
        val file = File(base, "backup.zip")
        val files = DataFiles(context, GarageBackup(db, settings = null, documents = store), null)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides Answering(Uri.fromFile(file))) {
                CarServiceApp(repository, dataFiles = files, documentStore = store)
            }
        }
        await("profile_name")
        tap("nav_settings"); await("backup_save")
        compose.onNodeWithTag("backup_password").performScrollTo().performTextInput("correct horse")
        await("backup_password_repeat")
        compose.onNodeWithTag("backup_password_repeat").performScrollTo().performTextInput("correct horse")
        click("backup_save"); await("data_outcome", 60_000)
        assertTrue(file.length() > 0)
        assertFalse("an encrypted file must not contain readable names", file.readBytes().toString(Charsets.ISO_8859_1).contains("Backup test car"))

        click("delete_all"); await("confirm_delete_all"); tap("confirm_delete_all")
        compose.waitUntil(20_000) { garage() == null }

        click("backup_restore"); await("confirm_restore"); tap("confirm_restore")
        await("restore_password", 60_000)
        assertNull("nothing may be restored before the password is given", garage())

        compose.onNodeWithTag("restore_password").performTextInput("wrong password")
        tap("confirm_restore")
        await("restore_problem_WRONG_PASSWORD_OR_DAMAGED", 60_000)
        assertNull(garage())

        compose.onNodeWithTag("restore_password").performTextClearance()
        compose.onNodeWithTag("restore_password").performTextInput("correct horse")
        tap("confirm_restore")
        compose.waitUntil(60_000) { garage() != null }
        val restored = requireNotNull(garage())
        assertEquals("Backup test car", restored.vehicle.displayName)
        val document = restored.events.single { it.event.id == event }.documents.single()
        assertArrayEquals(content, store.open(document).use { it.readBytes() })
    }
}
