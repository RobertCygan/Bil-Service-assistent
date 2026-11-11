package app.servicepassport.android.screens

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
 * The whole document path through the real screens: pick a file, review what was read, continue to the service form,
 * save, and find the document with the entry. Text recognition and the system file dialog are replaced by fakes;
 * everything between them is the app's own code. The invoice is invented.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp")
class ImportFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private lateinit var base: File
    private lateinit var store: DocumentStore
    private lateinit var repository: ProfileRepository
    private val name = "import-flow-${UUID.randomUUID()}.db"
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val invoice = listOf(
        "Eksempel Bilverksted AS", "FAKTURA nr 10457", "Fakturadato: 14.03.2025", "Reg.nr: AB12345", "Km-stand: 84512",
        "Utført dato: 12.03.2025", "Utført arbeid", "Oljeskift inkl. arbeid 1290,00", "Motorolje 5W-30 899,00", "Å betale NOK 2189,00",
    )

    /** Stands for text recognition: returns the same lines for any page. */
    private class FixedText(private val lines: List<String>) : TextOcr {
        override suspend fun recognize(context: Context, page: PageImage) = lines.map { OcrLine(it, null) }
    }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = GarageDatabase.open(context, name)
        base = File(context.cacheDir, "flow-${UUID.randomUUID()}").also { it.mkdirs() }
        store = DocumentStore(context, db, clock, base)
        repository = ProfileRepository(db, clock)
        runBlocking { repository.save(ProfileInput("Familiebilen", 2019, registrationNumber = "AB12345")) }
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name); base.deleteRecursively() }

    private fun picked(content: String = "invented invoice photo"): Uri =
        Uri.fromFile(File(base, "faktura-${UUID.randomUUID()}.jpg").also { it.writeText(content) })

    private fun live() = File(base, DocumentStore.LIVE).listFiles().orEmpty().map { it.name }
    private fun staged() = File(base, DocumentStore.STAGED).listFiles().orEmpty().map { it.name }
    private fun events() = runBlocking { requireNotNull(db.vehicles().garage(requireNotNull(db.vehicles().getProfile()).id)).events }

    private fun start(pickedFile: Uri?) {
        compose.setAppContent(Answering { pickedFile }) {
            CarServiceApp(repository, documents = DocumentAnalyzer(context, FixedText(invoice)), documentStore = store)
        }
        await("profile_name")
    }
    private fun await(tag: String) = compose.await(tag)
    private fun gone(tag: String) = compose.awaitGone(tag)
    private fun click(tag: String) = compose.click(tag)
    private fun text(id: Int) = context.getString(id)

    private fun readDocument() { click("import_document"); await("pick_document"); click("pick_document"); await("import_review") }
    private fun saveFromReview() { click("import_continue"); await("save_service"); click("save_service"); await("history_entry") }

    @Test fun aPickedInvoiceBecomesAServiceEntryWithItsDocumentOnlyAfterSaving() {
        start(picked())
        readDocument()
        // Reviewing changes nothing: no entry yet, and the copy is only staged.
        assertTrue(events().isEmpty())
        assertEquals(1, staged().size); assertTrue(live().isEmpty())
        compose.onAllNodesWithTag("import_item").assertCountEquals(2)

        click("import_continue"); await("save_service")
        compose.onNodeWithTag("service_km").assertTextContains("84512")
        compose.onNodeWithTag("service_cost").assertTextContains("2189", substring = true)
        compose.onNodeWithText(text(R.string.service_document_attached)).assertExists()
        assertTrue("the form alone must not save anything", events().isEmpty())

        click("save_service"); await("history_entry")
        val record = events().single()
        assertEquals(LocalDate.of(2025, 3, 12).toEpochDay(), record.event.performedDay)
        assertEquals(84_512L, record.reading?.km)
        assertEquals(218_900L, record.event.costMinor)
        assertEquals(listOf("engine_oil" to "replace"), record.orderedActions.map { it.actionCode to it.operationCode })
        val document = record.documents.single()
        assertEquals("image/jpeg", document.mimeType)
        assertEquals(listOf(document.fileName), live()); assertTrue(staged().isEmpty())

        compose.onNodeWithTag("history_entry").performScrollTo().performClick()
        await("service_document")
        compose.onNodeWithTag("open_document").assertExists()
    }

    @Test fun decliningTheCopySavesTheEntryWithoutADocument() {
        start(picked())
        readDocument()
        click("import_keep")
        saveFromReview()
        assertTrue(events().single().documents.isEmpty())
        assertTrue(live().isEmpty()); assertTrue(staged().isEmpty())
    }

    @Test fun readingTheSameInvoiceAgainIsPointedOutBeforeAnythingIsSaved() {
        val uri = picked("the very same file")
        start(uri)
        readDocument(); saveFromReview()
        compose.tap("nav_overview"); await("profile_name")
        readDocument()
        compose.onNodeWithText(text(R.string.import_duplicate_file)).assertExists()
        assertEquals(1, events().size)
    }

    @Test fun leavingTheFormWithoutSavingKeepsNothing() {
        start(picked())
        readDocument()
        click("import_continue"); await("save_service")
        compose.tap("nav_history")
        await("confirm_discard"); compose.tap("confirm_discard")
        gone("save_service")
        assertTrue(events().isEmpty())
        assertTrue(live().isEmpty()); assertTrue(staged().isEmpty())
    }

    @Test fun aReviewInterruptedByClosingTheAppCanBeContinued() {
        // The app was closed during a review: only the staged copy is left.
        val earlier = requireNotNull(runBlocking { store.stage(picked("left behind")) })
        start(null)
        click("import_document"); await("import_pending")
        click("import_resume"); await("import_review")
        saveFromReview()
        assertEquals(earlier.id, events().single().documents.single().id)
    }

    @Test fun anInterruptedReviewCanBeDiscarded() {
        requireNotNull(runBlocking { store.stage(picked("left behind")) })
        start(null)
        click("import_document"); await("import_pending")
        compose.onNodeWithText(text(R.string.import_discard)).performScrollTo().performClick()
        gone("import_pending")
        assertTrue(staged().isEmpty()); assertTrue(events().isEmpty())
    }

    @Test fun cancellingTheFileDialogChangesNothing() {
        start(null)
        click("import_document"); await("pick_document"); click("pick_document")
        compose.onAllNodesWithTag("import_review").assertCountEquals(0)
        assertTrue(staged().isEmpty()); assertTrue(events().isEmpty())
    }
}
