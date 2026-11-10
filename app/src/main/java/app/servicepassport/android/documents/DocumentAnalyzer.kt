package app.servicepassport.android.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import app.servicepassport.extraction.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** One page handed to text recognition: a picked image (kept as a Uri so its rotation is honoured) or a rendered PDF page. */
sealed interface PageImage {
    data class FromUri(val uri: Uri) : PageImage
    data class FromBitmap(val bitmap: Bitmap) : PageImage
}

/** Text recognition. The production implementation runs on the device; tests use a fake. */
interface TextOcr {
    suspend fun recognize(context: Context, page: PageImage): List<OcrLine>
}

/** ML Kit with the bundled Latin model: recognition happens on the phone, without uploading the document. */
class MlKitOcr : TextOcr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(context: Context, page: PageImage): List<OcrLine> {
        val image = when (page) {
            is PageImage.FromUri -> InputImage.fromFilePath(context, page.uri)
            is PageImage.FromBitmap -> InputImage.fromBitmap(page.bitmap, 0)
        }
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    val lines = text.textBlocks.flatMap { it.lines }
                        .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                        .map { line -> OcrLine(line.text, line.boundingBox?.let { Box(it.left, it.top, it.right, it.bottom) }) }
                    continuation.resume(lines)
                }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }
}

enum class AnalysisProblem { UNSUPPORTED_TYPE, EMPTY_FILE, TOO_LARGE, TOO_MANY_PAGES, UNREADABLE }

sealed interface DocumentAnalysis {
    data class Ready(val draft: DraftProposal, val pages: Int) : DocumentAnalysis
    data class Failed(val problem: AnalysisProblem) : DocumentAnalysis
}

/** Reads a picked image or PDF and returns a draft for review. It stores nothing and changes nothing. */
class DocumentAnalyzer(private val context: Context, private val ocr: TextOcr, private val extractor: DocumentExtractor = DocumentExtractor()) {

    suspend fun analyse(uri: Uri, vehicle: VehicleIdentity, today: LocalDate): DocumentAnalysis = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val type = DocumentTypes.of(context, uri)
            val bytes = size(uri)
            val pdf = type == "application/pdf"
            if (!pdf) {
                ImportLimits.check(type, bytes, 1)?.let { return@withContext failed(it) }
                return@withContext ready(listOf(OcrPage(0, ocr.recognize(context, PageImage.FromUri(uri)))), vehicle, today)
            }
            val descriptor = resolver.openFileDescriptor(uri, "r") ?: return@withContext DocumentAnalysis.Failed(AnalysisProblem.UNREADABLE)
            descriptor.use { file ->
                PdfRenderer(file).use { renderer ->
                    ImportLimits.check(type, bytes, renderer.pageCount)?.let { return@withContext failed(it) }
                    val pages = (0 until renderer.pageCount).map { index ->
                        val bitmap = renderer.openPage(index).use { page ->
                            // About 150 dpi for A4: enough for printed invoices without exhausting memory.
                            val scale = RENDER_WIDTH.toFloat() / page.width
                            Bitmap.createBitmap(RENDER_WIDTH, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                                Canvas(it).drawColor(Color.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                        try { OcrPage(index, ocr.recognize(context, PageImage.FromBitmap(bitmap))) } finally { bitmap.recycle() }
                    }
                    ready(pages, vehicle, today)
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Encrypted or damaged PDFs, unreadable images and recognition failures end here. Details are not logged:
            // file names and contents are private.
            DocumentAnalysis.Failed(AnalysisProblem.UNREADABLE)
        }
    }

    private fun ready(pages: List<OcrPage>, vehicle: VehicleIdentity, today: LocalDate) = DocumentAnalysis.Ready(extractor.extract(pages, vehicle, today), pages.size)

    private fun failed(rejection: ImportRejection) = DocumentAnalysis.Failed(when (rejection) {
        ImportRejection.UNSUPPORTED_TYPE -> AnalysisProblem.UNSUPPORTED_TYPE
        ImportRejection.EMPTY_FILE -> AnalysisProblem.EMPTY_FILE
        ImportRejection.TOO_LARGE -> AnalysisProblem.TOO_LARGE
        ImportRejection.TOO_MANY_PAGES -> AnalysisProblem.TOO_MANY_PAGES
    })

    private fun size(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        }
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it >= 0 } ?: 1L
    }

    private companion object { const val RENDER_WIDTH = 1240 }
}
