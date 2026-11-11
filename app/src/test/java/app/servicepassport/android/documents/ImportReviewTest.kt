package app.servicepassport.android.documents

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.WorkInput
import app.servicepassport.extraction.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Review rules and the analysis path with a fake recogniser. Documents are invented. Real recognition is tested on a device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImportReviewTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val car = VehicleIdentity("XXDEMA0000000T123", "AB12345")
    private fun draft(text: String, vehicle: VehicleIdentity = car) =
        DocumentExtractor().extract(listOf(OcrPage(0, text.trimIndent().lines().map { OcrLine(it) })), vehicle, today)
    private fun ready(draft: DraftProposal, choice: ReviewChoice = ImportReview.initialChoice(draft)) = ImportReview.prefill(draft, choice, today)

    private val invoice = """
        Eksempel Bilverksted AS
        FAKTURA nr 10457
        Fakturadato: 14.03.2025
        Reg.nr: AB12345
        Km-stand: 84 512
        Utført dato: 12.03.2025
        Oljeskift inkl. arbeid            1 290,00
        Motorolje 5W-30 4,5 l               899,00
        Kontroll av bremsevæske             190,00
        Bremseklosser foran bør skiftes snart
        Å betale NOK                      2 379,00
    """

    @Test fun aClearInvoiceBecomesAPrefillWithOnlyCompletedWorkSelected() {
        val result = ready(draft(invoice)) as ReviewResult.Ready
        assertEquals(LocalDate.of(2025, 3, 12).toEpochDay(), result.prefill.day)
        assertEquals(84_512L, result.prefill.km)
        assertEquals(listOf(WorkInput("engine_oil", "replace"), WorkInput("brake_fluid", "inspect")), result.prefill.work)
        assertEquals(237_900L, result.prefill.costMinor)
    }

    @Test fun uncertainValuesAreNotPreselected() {
        val d = draft("Faktura\nFakturadato: 14.03.2025\nBilen har gått 45 000 km\nOljeskift\nTotalt 500,00")
        val choice = ImportReview.initialChoice(d)
        assertNull("an invoice date is only a candidate", choice.date)
        assertNull("an unlabeled number is only a candidate", choice.odometerKm)
        assertFalse("no currency, weak label", choice.includeCost)
        val result = ready(d, choice) as ReviewResult.Ready
        assertNull("the form will show an empty date, never today", result.prefill.day)
        assertNull(result.prefill.km); assertNull(result.prefill.costMinor)
        assertEquals(listOf(WorkInput("engine_oil", "replace")), result.prefill.work)
    }

    @Test fun theDriverCanPickACandidateAndAddAnUnselectedItem() {
        val d = draft(invoice)
        val parts = d.items.indexOfFirst { it.kind == ItemKind.PARTS_ONLY }
        val recommended = d.items.indexOfFirst { it.kind == ItemKind.RECOMMENDED }
        val initial = ImportReview.initialChoice(d)
        assertFalse(parts in initial.selectedItems); assertFalse(recommended in initial.selectedItems)
        val result = ready(d, initial.copy(date = LocalDate.of(2025, 3, 14), selectedItems = initial.selectedItems + recommended)) as ReviewResult.Ready
        assertEquals(LocalDate.of(2025, 3, 14).toEpochDay(), result.prefill.day)
        assertTrue(WorkInput("brake_pads", "replace") in result.prefill.work)
    }

    @Test fun aDocumentForAnotherCarOrAQuoteNeedsASeparateAcknowledgement() {
        val other = draft(invoice, VehicleIdentity(plate = "ZZ99999"))
        assertEquals(setOf(ConfirmationError.WARNING_NOT_ACKNOWLEDGED), (ready(other) as ReviewResult.Blocked).errors)
        assertTrue(ready(other, ImportReview.initialChoice(other).copy(acknowledged = setOf(DraftWarning.VEHICLE_MISMATCH))) is ReviewResult.Ready)

        val quote = draft("Eksempel Bilverksted AS\nPRISTILBUD\nOljeskift 1 290,00")
        val choice = ImportReview.initialChoice(quote)
        assertTrue("nothing from a quote is preselected", choice.selectedItems.isEmpty())
        assertEquals(setOf(ConfirmationError.WARNING_NOT_ACKNOWLEDGED, ConfirmationError.NO_TASKS), (ready(quote, choice) as ReviewResult.Blocked).errors)
    }

    @Test fun nothingSelectedOrAFutureDateIsBlocked() {
        val d = draft(invoice)
        val none = ImportReview.initialChoice(d).copy(selectedItems = emptySet())
        assertEquals(setOf(ConfirmationError.NO_TASKS), (ready(d, none) as ReviewResult.Blocked).errors)
        val future = ImportReview.initialChoice(d).copy(date = today.plusDays(1))
        assertEquals(setOf(ConfirmationError.DATE_IN_FUTURE), (ready(d, future) as ReviewResult.Blocked).errors)
    }

    @Test fun anAmountInAnotherCurrencyIsNeverCarriedOver() {
        val d = draft("Invoice\nService date 2025-03-12\nOil change\nTotal inc. VAT £360.00")
        assertFalse(ImportReview.costAllowed(d))
        val forced = ImportReview.initialChoice(d).copy(includeCost = true)
        assertNull((ready(d, forced) as ReviewResult.Ready).prefill.costMinor)
    }

    @Test fun duplicateLinesForTheSameWorkGiveOneEntry() {
        val d = draft("Faktura\nUtført 12.03.2025\nOljeskift\nSkift av motorolje")
        assertEquals(1, ImportReview.initialChoice(d).selectedItems.size)
        val both = ImportReview.initialChoice(d).copy(selectedItems = d.items.indices.toSet())
        assertEquals(listOf(WorkInput("engine_oil", "replace")), (ready(d, both) as ReviewResult.Ready).prefill.work)
    }

    // --- analysis path with a fake recogniser ---

    private class FakeOcr(private val lines: List<String>, private val fail: Boolean = false) : TextOcr {
        var calls = 0
        override suspend fun recognize(context: Context, page: PageImage): List<OcrLine> {
            calls++
            if (fail) throw IllegalStateException("recognition failed")
            return lines.map { OcrLine(it) }
        }
    }
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun file(name: String, bytes: ByteArray): Uri = Uri.fromFile(File(context.cacheDir, name).apply { writeBytes(bytes) })

    @Test fun aPickedImageIsReadAndTurnedIntoADraft() = runBlocking {
        val ocr = FakeOcr(invoice.trimIndent().lines())
        val result = DocumentAnalyzer(context, ocr).analyse(file("invoice.jpg", ByteArray(2048) { 1 }), car, today) as DocumentAnalysis.Ready
        assertEquals(1, result.pages); assertEquals(1, ocr.calls)
        assertEquals(84_512, result.draft.odometerKm.value)
    }

    @Test fun unsupportedEmptyAndUnreadableFilesAreRefusedWithoutADraft() = runBlocking {
        val ocr = FakeOcr(emptyList())
        val analyzer = DocumentAnalyzer(context, ocr)
        assertEquals(DocumentAnalysis.Failed(AnalysisProblem.UNSUPPORTED_TYPE), analyzer.analyse(file("notes.txt", "hello".toByteArray()), car, today))
        assertEquals(DocumentAnalysis.Failed(AnalysisProblem.EMPTY_FILE), analyzer.analyse(file("empty.jpg", ByteArray(0)), car, today))
        assertEquals(DocumentAnalysis.Failed(AnalysisProblem.UNREADABLE), analyzer.analyse(Uri.fromFile(File(context.cacheDir, "missing.png")), car, today))
        assertEquals(0, ocr.calls)
        assertEquals(DocumentAnalysis.Failed(AnalysisProblem.UNREADABLE), DocumentAnalyzer(context, FakeOcr(emptyList(), fail = true)).analyse(file("photo.png", ByteArray(64) { 1 }), car, today))
    }

    @Test fun anImageWithoutReadableTextGivesADraftWithAWarningNotAGuess() = runBlocking {
        val result = DocumentAnalyzer(context, FakeOcr(listOf("~", "|"))).analyse(file("blurry.jpg", ByteArray(64) { 1 }), car, today) as DocumentAnalysis.Ready
        assertTrue(DraftWarning.NO_READABLE_TEXT in result.draft.warnings)
        assertTrue(result.draft.items.isEmpty())
    }

    @Test fun aReviewLeftHalfwayCanBeWrittenDownAndReadBackExactly() {
        val draft = draft(invoice)
        val choice = ImportReview.initialChoice(draft).copy(includeCost = false, acknowledged = draft.warnings)
        val saved = SavedReview(ReviewPersistence.fingerprint(draft), choice, keepDocument = false)
        assertEquals(saved, ReviewPersistence.decode(ReviewPersistence.encode(saved)))
        assertEquals(choice, ReviewPersistence.restore(draft, saved))

        val nothingChosen = SavedReview(ReviewPersistence.fingerprint(draft), ReviewChoice(null, null, emptySet(), false, emptySet()), true)
        assertEquals(nothingChosen, ReviewPersistence.decode(ReviewPersistence.encode(nothingChosen)))
    }

    @Test fun savedChoicesAreNotAppliedToADocumentThatReadsDifferently() {
        val draft = draft(invoice)
        val saved = SavedReview(ReviewPersistence.fingerprint(draft), ImportReview.initialChoice(draft), true)
        val other = draft(invoice.replace("Oljeskift inkl. arbeid", "Skift av bremsevæske"))
        assertNotEquals(ReviewPersistence.fingerprint(draft), ReviewPersistence.fingerprint(other))
        assertNull(ReviewPersistence.restore(other, saved))
        // Even with a matching fingerprint, a choice must still be something the draft offers.
        val forged = saved.copy(choice = saved.choice.copy(selectedItems = setOf(99)))
        assertNull(ReviewPersistence.restore(draft, forged))
        assertNull(ReviewPersistence.restore(draft, saved.copy(choice = saved.choice.copy(odometerKm = 1))))
        assertNull(ReviewPersistence.restore(draft, saved.copy(choice = saved.choice.copy(date = LocalDate.of(2001, 1, 1)))))
    }

    @Test fun aDamagedSavedReviewIsIgnored() {
        val draft = draft(invoice)
        val text = ReviewPersistence.encode(SavedReview(ReviewPersistence.fingerprint(draft), ImportReview.initialChoice(draft), true))
        for (broken in listOf("", "garbage", text.replaceFirst("1|", "2|"), text.dropLast(1) + "x", text.substringBeforeLast('|'), text.replace(Regex("^1\\|[0-9a-f]{64}"), "1|zz"), "$text|extra")) {
            assertNull(broken, ReviewPersistence.decode(broken))
        }
    }
}
