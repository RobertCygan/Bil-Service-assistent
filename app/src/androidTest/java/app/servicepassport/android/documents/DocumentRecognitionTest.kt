package app.servicepassport.android.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.servicepassport.android.data.WorkInput
import app.servicepassport.extraction.DraftWarning
import app.servicepassport.extraction.FieldState
import app.servicepassport.extraction.VehicleIdentity
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real on-device recognition: a PDF is generated on the device, rendered page by page and read by ML Kit.
 * The invoice is invented and printed in a clean font, so this proves the pipeline works end to end —
 * it says nothing about photographed or badly scanned real invoices.
 */
@RunWith(AndroidJUnit4::class)
class DocumentRecognitionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 11, 10)

    private fun pdf(name: String, vararg pages: List<String>): Uri {
        val document = PdfDocument()
        val paint = Paint().apply { color = Color.BLACK; textSize = 20f; isAntiAlias = true }
        pages.forEachIndexed { index, lines ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
            page.canvas.drawColor(Color.WHITE)
            lines.forEachIndexed { row, line -> page.canvas.drawText(line, 40f, 70f + row * 34f, paint) }
            document.finishPage(page)
        }
        val file = File(context.cacheDir, name)
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return Uri.fromFile(file)
    }

    @Test fun aGeneratedNorwegianInvoiceIsReadIntoAReviewableDraft() = runBlocking {
        val uri = pdf(
            "invoice.pdf",
            listOf("Eksempel Bilverksted AS", "FAKTURA nr 10457", "Fakturadato: 14.03.2025", "Reg.nr: AB12345", "Km-stand: 84512", "Utført dato: 12.03.2025"),
            listOf("Utført arbeid", "Oljeskift inkl. arbeid 1290,00", "Motorolje 5W-30 899,00", "Å betale NOK 2189,00"),
        )
        val result = DocumentAnalyzer(context, MlKitOcr()).analyse(uri, VehicleIdentity(plate = "AB12345"), today)
        assertTrue("analysis failed: $result", result is DocumentAnalysis.Ready)
        val draft = (result as DocumentAnalysis.Ready).draft
        assertEquals(2, result.pages)
        assertFalse("text must be readable", DraftWarning.NO_READABLE_TEXT in draft.warnings)
        assertFalse(DraftWarning.VEHICLE_MISMATCH in draft.warnings)
        assertEquals(LocalDate.of(2025, 3, 12), draft.serviceDate.value)
        assertEquals(FieldState.RECOGNIZED, draft.serviceDate.state)
        assertEquals(84512, draft.odometerKm.value)
        assertEquals("AB12345", draft.plate.value)

        val prefill = (ImportReview.prefill(draft, ImportReview.initialChoice(draft), today) as ReviewResult.Ready).prefill
        assertEquals(listOf(WorkInput("engine_oil", "replace")), prefill.work)
        assertEquals(84512L, prefill.km)
        assertEquals(1, draft.items.first { it.selectedByDefault }.evidence.page)
    }

    /** A page as a phone camera stores it: pixels lying on their side, with a note saying which way is up. */
    private fun sidewaysPhoto(name: String, lines: List<String>): File {
        val upright = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { color = Color.BLACK; textSize = 44f; isAntiAlias = true }
        Canvas(upright).apply { drawColor(Color.WHITE); lines.forEachIndexed { row, line -> drawText(line, 80f, 160f + row * 90f, paint) } }
        val sideways = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, Matrix().apply { postRotate(270f) }, true)
        val file = File(context.cacheDir, name)
        file.outputStream().use { sideways.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        return file
    }

    @Test fun aPhotoStoredSidewaysIsReadUpright() = runBlocking {
        val file = sidewaysPhoto("sideways.jpg", listOf("Eksempel Bilverksted AS", "Fakturadato: 14.03.2025", "Reg.nr: AB12345", "Km-stand: 84512", "Oljeskift inkl. arbeid 1290,00", "Å betale NOK 1290,00"))
        val photo = PhotoLoader.load(context, Uri.fromFile(file))
        assertEquals("pixels stay as stored", 1754 to 1240, photo.bitmap.width to photo.bitmap.height)
        assertEquals(90, photo.rotationDegrees)

        val result = DocumentAnalyzer(context, MlKitOcr()).analyse(Uri.fromFile(file), VehicleIdentity(plate = "AB12345"), today)
        assertTrue("analysis failed: $result", result is DocumentAnalysis.Ready)
        val draft = (result as DocumentAnalysis.Ready).draft
        assertFalse("text must be readable", DraftWarning.NO_READABLE_TEXT in draft.warnings)
        assertEquals(84512, draft.odometerKm.value)
        assertEquals(LocalDate.of(2025, 3, 14), draft.serviceDate.value)
        assertTrue("the oil change is offered", draft.items.any { it.selectedByDefault })
    }

    @Test fun aPhotoLargerThanNeededIsLoadedSmaller() {
        val file = sidewaysPhoto("large.jpg", listOf("Km-stand: 84512"))
        val photo = PhotoLoader.load(context, Uri.fromFile(file), maxSide = 1000)
        assertEquals(877 to 620, photo.bitmap.width to photo.bitmap.height)
        assertEquals(90, photo.rotationDegrees)
    }

    /** A drawn instrument cluster: light figures on black, a speed scale, a trip meter, a clock and the total mileage. */
    @Test fun theMileageIsReadFromAPhotoOfAnInstrumentCluster() = runBlocking {
        val picture = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888)
        val small = Paint().apply { color = Color.LTGRAY; textSize = 46f; isAntiAlias = true }
        val large = Paint().apply { color = Color.WHITE; textSize = 84f; isAntiAlias = true }
        Canvas(picture).apply {
            drawColor(Color.BLACK)
            listOf("20", "40", "60", "80", "100", "120", "140").forEachIndexed { i, speed -> drawText(speed, 120f + i * 200f, 140f, small) }
            drawText("ODO 93 231 km", 420f, 460f, large)
            drawText("TRIP A 512.4 km", 420f, 600f, small)
            drawText("12:45", 120f, 780f, small); drawText("21.5 °C", 1200f, 780f, small)
        }
        val file = File(context.cacheDir, "cluster.jpg")
        file.outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val offered = DocumentAnalyzer(context, MlKitOcr()).odometer(Uri.fromFile(file), lastKnownKm = 92_480)
        assertEquals("offered: $offered", 93_231, offered.firstOrNull()?.km)
    }

    @Test fun aFileThatIsNotAnImageIsRefusedCleanly() = runBlocking {
        val file = File(context.cacheDir, "broken.jpg").apply { writeText("this is not a photo") }
        assertEquals(DocumentAnalysis.Failed(AnalysisProblem.UNREADABLE), DocumentAnalyzer(context, MlKitOcr()).analyse(Uri.fromFile(file), VehicleIdentity(), today))
    }

    @Test fun aFileThatIsNotAPdfIsRefusedCleanly() = runBlocking {
        val file = File(context.cacheDir, "broken.pdf").apply { writeText("this is not a pdf") }
        assertEquals(DocumentAnalysis.Failed(AnalysisProblem.UNREADABLE), DocumentAnalyzer(context, MlKitOcr()).analyse(Uri.fromFile(file), VehicleIdentity(), today))
    }
}
