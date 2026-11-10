package app.servicepassport.export

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

public enum class ImportFailure {
    NOT_AN_EXPORT, MANIFEST_INVALID, DATA_INVALID,
    /** Plik z nowszej wersji aplikacji — trzeba ją zaktualizować; niczego nie odtwarzamy częściowo. */
    NEWER_FORMAT,
    /** Kopia jest zaszyfrowana, a hasła nie podano. */
    PASSWORD_REQUIRED,
    /** Złe hasło albo naruszony plik — nie da się tego rozróżnić. */
    WRONG_PASSWORD_OR_DAMAGED,
    UNSAFE_PATH, UNEXPECTED_FILE, FILE_MISSING, FILE_CORRUPTED, TOO_LARGE, CONTENT_INCONSISTENT,
}

public sealed interface ImportResult {
    public data class Success(val manifest: ExportManifest, val data: ExportData) : ImportResult
    public data class Failure(val reason: ImportFailure, val issues: List<ExportIssue> = emptyList()) : ImportResult
}

public class ExportException(public val issues: List<ExportIssue>) : IllegalArgumentException("Export data is inconsistent: $issues")

/** Źródło pliku dokumentu do zapisania w archiwum. */
public class DocumentSource(public val path: String, public val open: () -> InputStream)

/** Limity chroniące przed uszkodzonym lub spreparowanym archiwum. */
public data class ImportLimits(
    val maxEntries: Int = 5_000,
    val maxDocumentBytes: Long = 25L * 1024 * 1024,
    val maxTotalBytes: Long = 4L * 1024 * 1024 * 1024,
    val maxJsonBytes: Long = 50L * 1024 * 1024,
)

public object ExportArchive {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Ścieżka dokumentu: tylko `documents/<nazwa>`, bez podkatalogów, `..`, ukośników wstecznych i znaków sterujących. */
    public fun isSafeDocumentPath(path: String): Boolean {
        if (!path.startsWith(ExportFormat.DOCUMENTS_DIR)) return false
        val name = path.removePrefix(ExportFormat.DOCUMENTS_DIR)
        return name.isNotEmpty() && name.length <= 200 && name != "." && name != ".." &&
            name.none { it == '/' || it == '\\' || it == ':' || it.code < 0x20 }
    }

    /**
     * Zapisuje archiwum. Najpierw sprawdza spójność danych; przy błędach rzuca [ExportException] i nic nie zapisuje.
     * Strumienie dokumentów są czytane jeden po drugim (bez ładowania całości do pamięci).
     */
    public fun write(
        out: OutputStream,
        data: ExportData,
        documents: List<DocumentSource>,
        createdAt: String,
        appVersion: String,
        today: LocalDate,
        /** Hasło włącza szyfrowanie całej kopii; null = kopia jawna. Zapomnianego hasła nie da się odzyskać. */
        password: CharArray? = null,
        iterations: Int = ExportEncryption.DEFAULT_ITERATIONS,
    ): ExportManifest {
        val issues = ExportValidator.validate(data, today, documents.map { it.path }.toSet()).toMutableList()
        val referenced = data.documents.map { it.path }.toSet()
        documents.filter { it.path !in referenced }.forEach { issues += ExportIssue(ExportIssueCode.UNKNOWN_DOCUMENT, it.path) }
        if (issues.isNotEmpty()) throw ExportException(issues)

        val files = mutableListOf<ExportedFile>()
        val target = if (password != null) ExportEncryption.encrypting(out, password, iterations) else out
        ZipOutputStream(target).use { zip ->
            fun put(path: String, source: InputStream) {
                zip.putNextEntry(ZipEntry(path))
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                val buffer = ByteArray(64 * 1024)
                source.use {
                    while (true) {
                        val n = it.read(buffer)
                        if (n < 0) break
                        zip.write(buffer, 0, n); digest.update(buffer, 0, n); total += n
                    }
                }
                zip.closeEntry()
                files += ExportedFile(path, digest.digest().hex(), total)
            }
            put(ExportFormat.DATA, json.encodeToString(ExportData.serializer(), data).byteInputStream())
            documents.forEach { put(it.path, it.open()) }
            val manifest = ExportManifest(ExportFormat.VERSION, createdAt, appVersion, files.toList())
            zip.putNextEntry(ZipEntry(ExportFormat.MANIFEST))
            zip.write(json.encodeToString(ExportManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            return manifest
        }
    }

    /**
     * Czyta i w całości weryfikuje archiwum. Dokumenty trafiają do [documentSink] w trakcie czytania;
     * przy wyniku [ImportResult.Failure] wywołujący MUSI usunąć to, co sink zdążył zapisać, i nie zmieniać bazy.
     */
    public fun read(
        input: InputStream,
        today: LocalDate,
        limits: ImportLimits = ImportLimits(),
        /** Hasło do zaszyfrowanej kopii; dla kopii jawnej jest ignorowane. */
        password: CharArray? = null,
        documentSink: (path: String) -> OutputStream,
    ): ImportResult {
        val buffered = java.io.BufferedInputStream(input)
        buffered.mark(16)
        val start = buffered.readNBytes(8)
        buffered.reset()
        val encrypted = ExportEncryption.isEncrypted(start)
        if (encrypted && password == null) return ImportResult.Failure(ImportFailure.PASSWORD_REQUIRED)
        return try {
            readPlain(if (encrypted) ExportEncryption.decrypting(buffered, password!!) else buffered, today, limits, documentSink)
        } catch (_: ExportDecryptionException) {
            ImportResult.Failure(ImportFailure.WRONG_PASSWORD_OR_DAMAGED)
        } catch (_: java.util.zip.ZipException) {
            ImportResult.Failure(ImportFailure.FILE_CORRUPTED)
        }
    }

    private fun readPlain(
        input: InputStream,
        today: LocalDate,
        limits: ImportLimits,
        documentSink: (path: String) -> OutputStream,
    ): ImportResult {
        var manifestBytes: ByteArray? = null
        var dataBytes: ByteArray? = null
        val seen = mutableMapOf<String, ExportedFile>()
        var total = 0L
        var entries = 0

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                if (++entries > limits.maxEntries) return ImportResult.Failure(ImportFailure.TOO_LARGE)
                val path = entry.name
                val isJson = path == ExportFormat.MANIFEST || path == ExportFormat.DATA
                if (!isJson && !isSafeDocumentPath(path)) {
                    return ImportResult.Failure(if (path.startsWith(ExportFormat.DOCUMENTS_DIR)) ImportFailure.UNSAFE_PATH else ImportFailure.UNEXPECTED_FILE)
                }
                if (path in seen || (path == ExportFormat.MANIFEST && manifestBytes != null)) return ImportResult.Failure(ImportFailure.UNEXPECTED_FILE)

                val limit = if (isJson) limits.maxJsonBytes else limits.maxDocumentBytes
                val memory = if (isJson) java.io.ByteArrayOutputStream() else null
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                (memory ?: documentSink(path)).use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        size += n; total += n
                        if (size > limit || total > limits.maxTotalBytes) return ImportResult.Failure(ImportFailure.TOO_LARGE)
                        sink.write(buffer, 0, n); digest.update(buffer, 0, n)
                    }
                }
                when (path) {
                    ExportFormat.MANIFEST -> manifestBytes = memory!!.toByteArray()
                    else -> {
                        if (path == ExportFormat.DATA) dataBytes = memory!!.toByteArray()
                        seen[path] = ExportedFile(path, digest.digest().hex(), size)
                    }
                }
            }
        }

        val manifest = manifestBytes?.let { decode(ExportManifest.serializer(), it) ?: return ImportResult.Failure(ImportFailure.MANIFEST_INVALID) }
            ?: return ImportResult.Failure(ImportFailure.NOT_AN_EXPORT)
        if (manifest.formatVersion > ExportFormat.VERSION) return ImportResult.Failure(ImportFailure.NEWER_FORMAT)
        if (manifest.formatVersion < 1) return ImportResult.Failure(ImportFailure.MANIFEST_INVALID)

        val listed = manifest.files.associateBy { it.path }
        if (listed.size != manifest.files.size) return ImportResult.Failure(ImportFailure.MANIFEST_INVALID)
        if (seen.keys.any { it !in listed }) return ImportResult.Failure(ImportFailure.UNEXPECTED_FILE)
        for ((path, expected) in listed) {
            val actual = seen[path] ?: return ImportResult.Failure(ImportFailure.FILE_MISSING)
            if (actual.bytes != expected.bytes || !actual.sha256.equals(expected.sha256, ignoreCase = true)) {
                return ImportResult.Failure(ImportFailure.FILE_CORRUPTED)
            }
        }
        val data = dataBytes?.let { decode(ExportData.serializer(), it) } ?: return ImportResult.Failure(ImportFailure.DATA_INVALID)
        val issues = ExportValidator.validate(data, today, seen.keys)
        val orphan = (seen.keys - ExportFormat.DATA - data.documents.map { it.path }.toSet())
        if (issues.isNotEmpty() || orphan.isNotEmpty()) {
            return ImportResult.Failure(ImportFailure.CONTENT_INCONSISTENT, issues + orphan.map { ExportIssue(ExportIssueCode.UNKNOWN_DOCUMENT, it) })
        }
        return ImportResult.Success(manifest, data)
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, bytes: ByteArray): T? = try {
        json.decodeFromString(serializer, bytes.toString(Charsets.UTF_8))
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
