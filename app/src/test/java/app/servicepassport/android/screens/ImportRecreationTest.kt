package app.servicepassport.android.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.R
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
 * What happens to a document on its way into the history when Android rebuilds the screen (the phone is turned, the
 * theme or font size changes) or closes the app in the background and starts it again (common while the camera is
 * open). The screen is rebuilt from its saved state as Android would do it; for a new process everything the app
 * only held in memory is thrown away first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp")
class ImportRecreationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private lateinit var base: File
    private lateinit var store: DocumentStore
    private lateinit var repository: ProfileRepository
    private lateinit var screen: StateRestorationTester
    private val name = "import-recreation-${UUID.randomUUID()}.db"
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val invoice = listOf(
        "Eksempel Bilverksted AS", "FAKTURA nr 10457", "Fakturadato: 14.03.2025", "Reg.nr: AB12345", "Km-stand: 84512",
        "Utført dato: 12.03.2025", "Utført arbeid", "Oljeskift inkl. arbeid 1290,00", "Motorolje 5W-30 899,00", "Å betale NOK 2189,00",
    )

    private class FixedText(private val lines: List<String>) : TextOcr {
        override suspend fun recognize(context: Context, page: PageImage) = lines.map { OcrLine(it, null) }
    }

    /**
     * Holds what outlives a rebuilt screen. A new process starts with nothing — and, as when Android ends a process,
     * nothing in the old memory is told that it is over before the screen's state has been saved.
     */
    private class Memory : ViewModelStoreOwner {
        override var viewModelStore = ViewModelStore(); private set
        fun lose(): ViewModelStore = viewModelStore.also { viewModelStore = ViewModelStore() }
    }
    private val memory = Memory()

    /** A system dialog that stays open: the request is noted and answered later with [deliver]. */
    private class StaysOpen(private val opened: (input: Any?) -> Unit) : ActivityResultRegistry(), ActivityResultRegistryOwner {
        private var request: Int? = null
        override val activityResultRegistry: ActivityResultRegistry get() = this
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) { request = requestCode; opened(input) }
        fun deliver(result: Any?) = dispatchResult(requireNotNull(request) { "nothing was opened" }, result)
    }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = GarageDatabase.open(context, name)
        base = File(context.cacheDir, "recreation-${UUID.randomUUID()}").also { it.mkdirs() }
        store = DocumentStore(context, db, clock, base)
        repository = ProfileRepository(db, clock)
        runBlocking { repository.save(ProfileInput("Familiebilen", 2019, registrationNumber = "AB12345")) }
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name); base.deleteRecursively() }

    private fun start(dialogs: ActivityResultRegistryOwner, files: DocumentStore = store) {
        screen = StateRestorationTester(compose)
        screen.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides dialogs, LocalViewModelStoreOwner provides memory) {
                CarServiceApp(repository, documents = DocumentAnalyzer(context, FixedText(invoice)), documentStore = files)
            }
        }
        compose.await("profile_name")
    }
    private fun turnThePhone() { screen.emulateSavedInstanceStateRestore(); compose.waitForIdle() }
    private fun closeInTheBackgroundAndReturn() {
        val old = compose.runOnUiThread<ViewModelStore> { memory.lose() }
        screen.emulateSavedInstanceStateRestore()
        compose.runOnUiThread { old.clear() }
        compose.waitForIdle()
    }

    private fun picked(): Uri = Uri.fromFile(File(base, "faktura-${UUID.randomUUID()}.jpg").also { it.writeText("invented invoice photo") })
    private fun click(tag: String) = compose.click(tag)
    private fun await(tag: String) = compose.await(tag)
    private fun text(id: Int) = context.getString(id)
    private fun live(files: File = base) = File(files, DocumentStore.LIVE).listFiles().orEmpty().map { it.name }
    private fun staged() = File(base, DocumentStore.STAGED).listFiles().orEmpty().map { it.name }.filter { it != "review.txt" }
    private fun events() = runBlocking { requireNotNull(db.vehicles().garage(requireNotNull(db.vehicles().getProfile()).id)).events }
    private fun attachedNote() = compose.onAllNodesWithText(text(R.string.service_document_attached)).fetchSemanticsNodes().isNotEmpty()

    private fun reviewADocument() { click("import_document"); await("pick_document"); click("pick_document"); await("import_review") }
    private fun openTheForm() { click("import_continue"); await("save_service") }
    private fun saveAndExpectTheDocument() {
        click("save_service"); await("history_entry")
        val record = events().single()
        assertEquals("the entry must keep its document", 1, record.documents.size)
        assertEquals(84_512L, record.reading?.km)
        assertTrue(staged().isEmpty()); assertEquals(listOf(record.documents.single().fileName), live())
    }

    @Test fun turningThePhoneKeepsTheReviewOnScreen() {
        val file = picked(); start(Answering { _, _ -> file })
        reviewADocument()
        turnThePhone()
        compose.onNodeWithTag("import_review").assertExists()
        compose.onAllNodesWithTag("import_item").assertCountEquals(2)
        compose.onAllNodesWithTag("import_pending").assertCountEquals(0)
        openTheForm(); saveAndExpectTheDocument()
    }

    @Test fun turningThePhoneOnTheFormKeepsTheDocumentWithTheEntry() {
        val file = picked(); start(Answering { _, _ -> file })
        reviewADocument(); openTheForm()
        assertTrue(attachedNote())
        turnThePhone()
        await("save_service")
        assertTrue("the form still says a document will be saved", attachedNote())
        compose.onNodeWithTag("service_km").assertTextContains("84512")
        saveAndExpectTheDocument()
    }

    @Test fun theFormKeepsItsDocumentWhenTheAppWasClosedInTheBackground() {
        val file = picked(); start(Answering { _, _ -> file })
        reviewADocument(); openTheForm()
        closeInTheBackgroundAndReturn()
        await("save_service")
        compose.onNodeWithTag("service_km").assertTextContains("84512")
        compose.waitUntil(15_000) { attachedNote() }
        saveAndExpectTheDocument()
    }

    @Test fun aFormOpenedWithoutADocumentGetsNoneAfterReturning() = runBlocking<Unit> {
        // A document left from an unfinished review must not slip into an entry that was typed in by hand.
        store.stage(picked())
        start(Answering { _, _ -> null })
        click("add_service"); await("save_service")
        closeInTheBackgroundAndReturn()
        await("save_service"); compose.waitForIdle()
        assertFalse(attachedNote())
        assertEquals("the unfinished document is still waiting", 1, staged().size)
    }

    @Test fun aPhotoIsReadWhenTheAppWasClosedWhileTheCameraWasOpen() {
        // The camera hand-over goes through the app's file provider, which serves only the real files directory and
        // remembers the first one it saw in this JVM.
        androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { field ->
            @Suppress("UNCHECKED_CAST") (field.get(null) as MutableMap<String, Any>).clear()
        }
        val real = DocumentStore(context, db, clock); real.deleteAll()
        val photo = ByteArray(3000) { (it % 89).toByte() }
        val camera = StaysOpen { target -> requireNotNull(context.contentResolver.openOutputStream(target as Uri)).use { it.write(photo) } }
        try {
            start(camera, real)
            click("import_document"); await("take_photo"); click("take_photo")
            closeInTheBackgroundAndReturn()
            await("take_photo")
            compose.onAllNodesWithTag("import_review").assertCountEquals(0)
            compose.runOnUiThread { assertTrue(camera.deliver(true)) }
            await("import_review")
            click("import_continue"); await("save_service"); click("save_service"); await("history_entry")
            val document = events().single().documents.single()
            assertEquals("image/jpeg", document.mimeType)
            assertArrayEquals(photo, real.open(document).use { it.readBytes() })
        } finally { real.deleteAll() }
    }
}
