package app.servicepassport.android.screens

import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
import app.servicepassport.android.documents.*
import app.servicepassport.android.ui.CarServiceApp
import app.servicepassport.extraction.OcrLine
import java.io.File
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A photo of the instrument cluster instead of typing the mileage, through the real screens. The camera app and text
 * recognition are replaced by fakes; choosing the number and everything after it is the app's own code. What the
 * "photo" shows is invented.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp")
class OdometerPhotoFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private lateinit var store: DocumentStore
    private lateinit var repository: ProfileRepository
    private val name = "odometer-photo-${UUID.randomUUID()}.db"
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val cluster = listOf("20", "40", "60", "80", "100", "120", "140", "160", "ODO 93 231 km", "TRIP A 512.4 km", "12:45", "21.5 °C", "Range 350 km")

    private class Shows(private val lines: List<String>) : TextOcr {
        override suspend fun recognize(context: Context, page: PageImage) = lines.map { OcrLine(it, null) }
    }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = GarageDatabase.open(context, name)
        repository = ProfileRepository(db, clock)
        // The camera hand-over goes through the app's file provider, which serves only the real files directory and
        // remembers the first one it saw in this JVM.
        androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { field ->
            @Suppress("UNCHECKED_CAST") (field.get(null) as MutableMap<String, Any>).clear()
        }
        store = DocumentStore(context, db, clock); store.deleteAll()
        runBlocking {
            repository.save(ProfileInput("Familiebilen", 2019))
            val car = requireNotNull(db.vehicles().getProfile()).id
            repository.saveReading(ReadingInput(UUID.randomUUID().toString(), car, null, LocalDate.parse("2026-10-01").toEpochDay(), 92_480, useAsCurrent = true))
        }
    }
    @After fun cleanup() { store.deleteAll(); db.close(); context.deleteDatabase(name) }

    private fun start(seen: List<String>, photoTaken: Boolean = true) {
        val camera = Answering { contract, input ->
            if (contract !is ActivityResultContracts.TakePicture) null
            else if (!photoTaken) false
            else { requireNotNull(context.contentResolver.openOutputStream(input as Uri)).use { it.write(ByteArray(2000) { 7 }) }; true }
        }
        compose.setAppContent(camera) { CarServiceApp(repository, documents = DocumentAnalyzer(context, Shows(seen)), documentStore = store) }
        compose.await("profile_name")
        compose.click("add_reading"); compose.await("odometer_photo")
    }
    private fun currentKm() = runBlocking { requireNotNull(db.vehicles().garage(requireNotNull(db.vehicles().getProfile()).id)).currentReading?.km }
    private fun photoFile() = File(context.filesDir, "${DocumentStore.STAGED}/odometer.jpg")

    @Test fun aPhotoOfTheClusterFillsInTheMileageAndTheDriverSavesIt() {
        start(cluster)
        compose.click("odometer_photo"); compose.await("odometer_photo_result")
        compose.onNodeWithTag("reading_km").assertTextContains("93231")
        assertEquals("reading the photo alone saves nothing", 92_480L, currentKm())
        assertFalse("the photo is not kept", photoFile().exists())
        compose.click("save_reading"); compose.await("profile_name")
        compose.awaitText("current_km", "93,231 km")
        assertEquals(93_231L, currentKm())
    }

    @Test fun anotherNumberFromTheSamePhotoIsOneTapAway() {
        start(listOf("93231", "94100"))
        compose.click("odometer_photo"); compose.await("odometer_photo_result")
        compose.onNodeWithTag("reading_km").assertTextContains("93231")
        compose.onAllNodesWithTag("odometer_photo_choice").assertCountEquals(2)
        compose.onNode(hasTestTag("odometer_photo_choice") and hasText("94,100 km")).performScrollTo().performClick()
        compose.onNodeWithTag("reading_km").assertTextContains("94100")
    }

    @Test fun aPhotoWithNoMileageSaysSoAndLeavesTheFieldForTyping() {
        start(listOf("20", "40", "60", "TRIP 512.4 km", "12:45"))
        compose.click("odometer_photo"); compose.await("odometer_photo_nothing")
        compose.onAllNodesWithTag("odometer_photo_result").assertCountEquals(0)
        compose.onNodeWithTag("reading_km").assert(hasText("93231").not())
        assertFalse(photoFile().exists())
        assertEquals(92_480L, currentKm())
    }

    @Test fun leavingTheCameraWithoutAPhotoChangesNothing() {
        start(cluster, photoTaken = false)
        compose.click("odometer_photo")
        compose.onAllNodesWithTag("odometer_photo_result").assertCountEquals(0)
        compose.onAllNodesWithTag("odometer_photo_nothing").assertCountEquals(0)
        assertEquals(92_480L, currentKm())
    }
}
