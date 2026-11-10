package app.servicepassport.android.documents

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.servicepassport.android.data.*
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The file provider configuration and the camera path on real Android storage. The "photo" is invented bytes. */
@RunWith(AndroidJUnit4::class)
class DocumentFilesOnDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun aPhotoWrittenToTheCameraTargetIsStagedAttachedAndRemovedWithItsEntry() = runBlocking {
        val name = "documents-${UUID.randomUUID()}.db"
        val db = GarageDatabase.open(context, name)
        try {
            // The provider only serves the real files directory, so this test uses it and cleans up after itself.
            val store = DocumentStore(context, db)
            val repo = ProfileRepository(db)
            repo.save(ProfileInput("Test car", 2022))
            val car = requireNotNull(db.vehicles().getProfile()).id

            val target = store.cameraTarget()
            assertEquals("content", target.scheme)
            val photo = ByteArray(4096) { (it % 251).toByte() }
            requireNotNull(context.contentResolver.openOutputStream(target)).use { it.write(photo) }
            assertEquals("image/jpeg", DocumentTypes.of(context, target))

            val staged = requireNotNull(store.stage(target))
            assertEquals("image/jpeg", staged.mimeType); assertEquals(photo.size.toLong(), staged.bytes)
            val event = repo.saveService(ServiceInput(UUID.randomUUID().toString(), car, null, LocalDate.now().toEpochDay(), null, listOf(WorkInput("engine_oil", "replace"))))
            assertTrue(store.attach(staged, car, event))
            val document = requireNotNull(db.vehicles().garage(car)).events.single().documents.single()
            assertTrue(store.exists(document))
            assertArrayEquals(photo, store.open(document).use { it.readBytes() })

            repo.deleteService(car, event, 1)
            store.sweep()
            assertFalse(store.exists(document))
        } finally {
            DocumentStore(context, db).deleteAll()
            db.close(); context.deleteDatabase(name)
        }
    }
}
