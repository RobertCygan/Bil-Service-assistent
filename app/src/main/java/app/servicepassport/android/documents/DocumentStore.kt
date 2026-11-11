package app.servicepassport.android.documents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.servicepassport.android.data.DocumentEntity
import app.servicepassport.android.data.GarageDatabase
import app.servicepassport.extraction.ImportLimits
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A private copy of the document under review. It is not part of the history until it is attached to a saved entry. */
data class StagedDocument(val id: String, val fileName: String, val mimeType: String, val sha256: String, val bytes: Long)

object DocumentTypes {
    private val extensions = mapOf(
        "application/pdf" to "pdf", "image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/heic" to "heic", "image/heif" to "heif",
    )
    fun extension(mimeType: String): String? = extensions[mimeType.lowercase()]
    fun supported(mimeType: String): Boolean = mimeType.lowercase() in ImportLimits.MIME_TYPES && extension(mimeType) != null

    fun of(context: Context, uri: Uri): String? = context.contentResolver.getType(uri) ?: when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
        "pdf" -> "application/pdf"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "heic" -> "image/heic"; "heif" -> "image/heif"
        else -> null
    }

    /** A plain file name: no directories, no hidden files, nothing a path could be built from. */
    fun safeName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 200 && !name.startsWith(".") && name.none { it == '/' || it == '\\' || it == ':' || it.code < 0x20 }
}

/**
 * Document files in private app storage. Three directories, so a half-finished step can never be mistaken for history:
 * `documents` (files that belong to saved entries), `documents-staged` (the document being reviewed)
 * and `documents-restore` (files of a backup that is still being verified).
 */
class DocumentStore(
    private val context: Context,
    private val database: GarageDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    base: File = context.filesDir,
) {
    private val live = File(base, LIVE)
    private val staging = File(base, STAGED)
    private val incoming = File(base, RESTORE)
    private val dao get() = database.vehicles()
    /** One change to the set of document files at a time: attaching, removing, tidying and restoring never overlap. */
    private val changes = Mutex()

    /** For a restore, which reads, verifies and replaces files as one step. */
    suspend fun <T> exclusive(block: suspend () -> T): T = changes.withLock { block() }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun liveFile(fileName: String): File {
        require(DocumentTypes.safeName(fileName))
        return File(live, fileName)
    }

    fun exists(document: DocumentEntity): Boolean = DocumentTypes.safeName(document.fileName) && liveFile(document.fileName).isFile

    /** Where the camera app writes a new photo. Readable and writable only through the grant given with the intent. */
    fun cameraTarget(): Uri {
        staging.mkdirs()
        val file = File(staging, CAMERA_FILE)
        file.delete()
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** Copies the picked document into the staging directory. Returns null when it cannot be copied; nothing is kept then. */
    suspend fun stage(uri: Uri): StagedDocument? = withContext(Dispatchers.IO) {
        val mimeType = DocumentTypes.of(context, uri)?.lowercase() ?: return@withContext null
        val extension = DocumentTypes.extension(mimeType)?.takeIf { DocumentTypes.supported(mimeType) } ?: return@withContext null
        staging.mkdirs()
        val id = UUID.randomUUID().toString()
        val target = File(staging, "$id.$extension")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { source ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > ImportLimits.MAX_BYTES) throw IOException("too large")
                        out.write(buffer, 0, n); digest.update(buffer, 0, n)
                    }
                }
            }
            if (total == 0L) throw IOException("empty")
            staging.listFiles()?.filter { it != target }?.forEach { it.delete() }
            StagedDocument(id, target.name, mimeType, digest.digest().joinToString("") { "%02x".format(it) }, total)
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    /**
     * The document whose review was interrupted, for example because the app was closed. The file is still in the
     * staging directory; its description is rebuilt from the file itself, so nothing else has to be stored.
     */
    suspend fun pending(): StagedDocument? = withContext(Dispatchers.IO) {
        val file = staging.listFiles()?.filter { it.isFile && it.name != CAMERA_FILE && it.name != REVIEW_FILE && it.length() > 0 }?.maxByOrNull { it.lastModified() } ?: return@withContext null
        val mimeType = DocumentTypes.of(context, Uri.fromFile(file))?.takeIf(DocumentTypes::supported) ?: return@withContext null
        StagedDocument(file.name.substringBeforeLast('.'), file.name, mimeType, digest(file), file.length())
    }

    /** Remembers what the driver chose on the review screen for this staged copy. It disappears with the copy. */
    suspend fun saveReview(staged: StagedDocument, state: String) = withContext(Dispatchers.IO) {
        if (File(staging, staged.fileName).isFile) runCatching { File(staging, REVIEW_FILE).writeText(staged.id + "\n" + state) }
        Unit
    }

    suspend fun loadReview(staged: StagedDocument): String? = withContext(Dispatchers.IO) {
        runCatching {
            File(staging, REVIEW_FILE).takeIf { it.isFile }?.readText()?.split('\n', limit = 2)?.takeIf { it.size == 2 && it[0] == staged.id }?.get(1)
        }.getOrNull()
    }

    /** Where a staged copy can be read again by the text recognition. */
    fun stagedUri(staged: StagedDocument): Uri = Uri.fromFile(File(staging, staged.fileName))

    fun discardStaged() { staging.listFiles()?.forEach { it.delete() } }

    /** Makes the staged copy part of a saved entry. On failure the copy stays staged, so saving again can retry. */
    suspend fun attach(staged: StagedDocument, vehicleId: String, eventId: String): Boolean = withContext(Dispatchers.IO) {
        changes.withLock {
            val source = File(staging, staged.fileName)
            val target = liveFile(staged.fileName)
            live.mkdirs()
            if (!source.isFile || !source.renameTo(target)) return@withLock false
            try {
                dao.insertDocument(DocumentEntity(staged.id, vehicleId, eventId, staged.fileName, staged.mimeType, staged.sha256, staged.bytes, LocalDate.now(clock).toEpochDay(), clock.millis()))
                true
            } catch (_: Exception) {
                target.renameTo(source)
                false
            }
        }
    }

    /** Deletes the row first: a file without a row is harmless and is removed by [sweep]; a row without a file is not. */
    suspend fun remove(vehicleId: String, documentId: String): Boolean = withContext(Dispatchers.IO) {
        changes.withLock {
            val document = dao.document(documentId)?.takeIf { it.vehicleId == vehicleId } ?: return@withLock false
            dao.deleteDocument(document.id)
            if (DocumentTypes.safeName(document.fileName)) liveFile(document.fileName).delete()
            true
        }
    }

    /** Deletes files that no saved entry refers to, for example after an entry with documents was deleted. */
    suspend fun sweep() = withContext(Dispatchers.IO) {
        changes.withLock {
            val known = dao.documentFileNames().toSet()
            live.listFiles()?.filter { it.name !in known }?.forEach { it.delete() }
        }
        Unit
    }

    /**
     * Run when the app starts. If a restore was cut short after the database had been replaced, its verified files are
     * still waiting: each one that matches its entry (size and checksum) is put in place. Anything else that waits,
     * and any file no entry refers to, is removed.
     */
    suspend fun repair() = withContext(Dispatchers.IO) {
        changes.withLock {
            val rows = dao.documents().associateBy { it.fileName }
            incoming.listFiles()?.forEach { file ->
                val row = rows[file.name]
                val target = File(live, file.name)
                if (row != null && !target.exists() && file.length() == row.bytes && digest(file).equals(row.sha256, ignoreCase = true)) {
                    live.mkdirs(); file.renameTo(target)
                } else file.delete()
            }
            live.listFiles()?.filter { it.name !in rows.keys }?.forEach { it.delete() }
        }
        Unit
    }

    /** Opens the document in another app with a temporary read-only grant. False when no app can show it. */
    fun view(document: DocumentEntity): Boolean {
        if (!exists(document)) return false
        val uri = FileProvider.getUriForFile(context, authority(context), liveFile(document.fileName))
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (_: ActivityNotFoundException) { false }
    }

    // --- backup -----------------------------------------------------------------------------------------------------

    fun open(document: DocumentEntity) = liveFile(document.fileName).inputStream()

    // The three functions below belong to one restore and are called inside [exclusive].

    /** Receives one file of a backup that is still being verified. */
    fun restoreSink(fileName: String): OutputStream {
        if (!DocumentTypes.safeName(fileName)) throw IOException("unsafe name")
        incoming.mkdirs()
        return File(incoming, fileName).outputStream()
    }

    fun abortRestore() { incoming.listFiles()?.forEach { it.delete() } }

    /** Called after the database was replaced: the verified files become the only documents. */
    fun commitRestore(fileNames: Set<String>) {
        live.mkdirs()
        live.listFiles()?.forEach { it.delete() }
        fileNames.forEach { name -> File(incoming, name).takeIf { it.isFile }?.renameTo(liveFile(name)) }
        abortRestore()
        discardStaged()
    }

    fun deleteAll() {
        listOf(live, staging, incoming).forEach { directory -> directory.listFiles()?.forEach { it.delete() } }
    }

    companion object {
        const val LIVE = "documents"
        const val STAGED = "documents-staged"
        const val RESTORE = "documents-restore"
        private const val CAMERA_FILE = "camera.jpg"
        private const val REVIEW_FILE = "review.txt"
        fun authority(context: Context): String = context.packageName + ".files"
    }
}
