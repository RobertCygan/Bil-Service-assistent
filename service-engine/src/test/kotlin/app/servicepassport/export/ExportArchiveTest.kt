package app.servicepassport.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Dane w testach są zmyślone. */
class ExportArchiveTest {
    private val today = LocalDate.parse("2026-11-10")
    private val pdf = ByteArray(200_000) { (it % 251).toByte() }
    private val photo = "fake-jpeg".toByteArray()

    private val data = ExportData(
        vehicles = listOf(ExportVehicle("v1", "Demo Motors", "Example", vin = "XXDEMA0000000T123", plate = "AB12345", inServiceDate = "2022-03-01", attributes = mapOf("MARKET" to "NO"))),
        odometerReadings = listOf(ExportOdometerReading("v1", "2026-11-01", 84_512)),
        serviceEntries = listOf(
            ExportServiceEntry("e1", "v1", "2025-03-12", 70_000, listOf(ExportTask("ENGINE_OIL", "Oljeskift inkl. arbeid"), ExportTask("BRAKE_FLUID")), 371_700, "NOK", "Blåbær æøå", listOf("d1")),
        ),
        documents = listOf(
            ExportDocument("d1", "v1", "documents/d1.pdf", "application/pdf", "2025-03-14"),
            ExportDocument("d2", "v1", "documents/d2.jpg", "image/jpeg", "2025-03-14"),
        ),
        userRules = listOf(ExportUserRule("r1", "v1", "WIPERS", months = 12)),
        indicatorReadings = listOf(ExportIndicatorReading("v1", "INSPECTION", "2026-10-01", 84_000, remainingKm = 4_000)),
        settings = mapOf("language" to "nb-NO"),
    )
    private val sources = listOf(
        DocumentSource("documents/d1.pdf") { ByteArrayInputStream(pdf) },
        DocumentSource("documents/d2.jpg") { ByteArrayInputStream(photo) },
    )

    private fun export(d: ExportData = data, s: List<DocumentSource> = sources): ByteArray =
        ByteArrayOutputStream().also { ExportArchive.write(it, d, s, "2026-11-10T12:00:00Z", "1.0.0", today) }.toByteArray()

    private fun import(bytes: ByteArray, limits: ImportLimits = ImportLimits(), sink: MutableMap<String, ByteArrayOutputStream> = mutableMapOf()) =
        ExportArchive.read(ByteArrayInputStream(bytes), today, limits) { path -> ByteArrayOutputStream().also { sink[path] = it } }

    /** Przepisuje archiwum, pozwalając podmienić, dodać lub usunąć pliki. */
    private fun rewrite(bytes: ByteArray, change: (MutableMap<String, ByteArray>) -> Unit): ByteArray {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; files[e.name] = z.readBytes() } }
        change(files)
        return ByteArrayOutputStream().also { o -> ZipOutputStream(o).use { z -> files.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } } }.toByteArray()
    }

    private fun failure(bytes: ByteArray, limits: ImportLimits = ImportLimits()) = assertIs<ImportResult.Failure>(import(bytes, limits)).reason

    @Test
    fun `export then import restores identical data and documents`() {
        val sink = mutableMapOf<String, ByteArrayOutputStream>()
        val result = assertIs<ImportResult.Success>(import(export(), sink = sink))
        assertEquals(data, result.data)
        assertEquals(1, result.manifest.formatVersion)
        assertEquals(listOf("data.json", "documents/d1.pdf", "documents/d2.jpg"), result.manifest.files.map { it.path })
        assertTrue(pdf.contentEquals(sink.getValue("documents/d1.pdf").toByteArray()))
        assertTrue(photo.contentEquals(sink.getValue("documents/d2.jpg").toByteArray()))
        assertEquals(pdf.size.toLong(), result.manifest.files[1].bytes)
    }

    @Test
    fun `empty export is valid`() {
        val bytes = export(ExportData(), emptyList())
        assertEquals(ExportData(), assertIs<ImportResult.Success>(import(bytes)).data)
    }

    @Test
    fun `inconsistent data is refused before anything is written`() {
        val out = ByteArrayOutputStream()
        val broken = data.copy(serviceEntries = data.serviceEntries.map { it.copy(vehicleId = "ghost", documentIds = listOf("nope")) })
        val e = assertFailsWith<ExportException> { ExportArchive.write(out, broken, sources, "t", "1", today) }
        assertEquals(setOf(ExportIssueCode.UNKNOWN_VEHICLE, ExportIssueCode.UNKNOWN_DOCUMENT), e.issues.map { it.code }.toSet())
        assertEquals(0, out.size())

        assertFailsWith<ExportException> { export(s = sources.take(1)) } // brak pliku dokumentu d2
        assertFailsWith<ExportException> { export(s = sources + DocumentSource("documents/extra.pdf") { ByteArrayInputStream(photo) }) }
    }

    @Test
    fun `validator reports each kind of problem`() {
        fun codes(d: ExportData, paths: Set<String> = setOf("documents/d1.pdf", "documents/d2.jpg")) =
            ExportValidator.validate(d, today, paths).map { it.code }.toSet()
        assertTrue(codes(data).isEmpty())
        assertEquals(setOf(ExportIssueCode.DUPLICATE_ID), codes(data.copy(vehicles = data.vehicles + data.vehicles)))
        assertEquals(setOf(ExportIssueCode.INVALID_DATE), codes(data.copy(odometerReadings = listOf(ExportOdometerReading("v1", "12.03.2025", 1)))))
        assertEquals(setOf(ExportIssueCode.DATE_IN_FUTURE), codes(data.copy(odometerReadings = listOf(ExportOdometerReading("v1", "2026-11-11", 1)))))
        assertEquals(setOf(ExportIssueCode.NEGATIVE_VALUE), codes(data.copy(odometerReadings = listOf(ExportOdometerReading("v1", "2026-01-01", -1)))))
        assertEquals(setOf(ExportIssueCode.ENTRY_WITHOUT_TASKS), codes(data.copy(serviceEntries = data.serviceEntries.map { it.copy(tasks = emptyList()) })))
        assertEquals(setOf(ExportIssueCode.RULE_WITHOUT_INTERVAL), codes(data.copy(userRules = listOf(ExportUserRule("r", "v1", "X")))))
        assertEquals(setOf(ExportIssueCode.NEGATIVE_VALUE), codes(data.copy(userRules = listOf(ExportUserRule("r", "v1", "X", km = 0)))))
        assertEquals(setOf(ExportIssueCode.INDICATOR_WITHOUT_VALUE), codes(data.copy(indicatorReadings = listOf(ExportIndicatorReading("v1", "X", "2026-01-01", 1)))))
        assertEquals(setOf(ExportIssueCode.EMPTY_FIELD), codes(data.copy(vehicles = listOf(data.vehicles[0].copy(make = " ")))))
        assertEquals(setOf(ExportIssueCode.DOCUMENT_FILE_MISSING), codes(data, setOf("documents/d1.pdf")))
        assertEquals(
            setOf(ExportIssueCode.DOCUMENT_PATH_INVALID),
            codes(data.copy(documents = listOf(data.documents[0].copy(path = "documents/../x")), serviceEntries = emptyList())),
        )
        val samePath = data.copy(documents = data.documents.map { it.copy(path = "documents/d1.pdf") })
        assertEquals(setOf(ExportIssueCode.DUPLICATE_DOCUMENT_PATH), codes(samePath))
    }

    @Test
    fun `document paths cannot escape the documents folder`() {
        for (ok in listOf("documents/a.pdf", "documents/Faktura 2025 æøå.jpg")) assertTrue(ExportArchive.isSafeDocumentPath(ok), ok)
        for (bad in listOf("a.pdf", "documents/", "documents/../a", "documents/a/b.pdf", "documents/..", "documents\\a", "documents/a\\b", "/documents/a", "documents/C:a", "documents/a\u0000b")) {
            assertTrue(!ExportArchive.isSafeDocumentPath(bad), bad)
        }
    }

    @Test
    fun `tampered or damaged archives are rejected as a whole`() {
        val good = export()
        assertEquals(ImportFailure.FILE_CORRUPTED, failure(rewrite(good) { it["documents/d2.jpg"] = "other".toByteArray() }))
        assertEquals(ImportFailure.FILE_CORRUPTED, failure(rewrite(good) { it["data.json"] = it.getValue("data.json") + ' '.code.toByte() }))
        assertEquals(ImportFailure.FILE_MISSING, failure(rewrite(good) { it.remove("documents/d1.pdf") }))
        assertEquals(ImportFailure.UNEXPECTED_FILE, failure(rewrite(good) { it["documents/sneaky.pdf"] = photo }))
        assertEquals(ImportFailure.UNEXPECTED_FILE, failure(rewrite(good) { it["readme.txt"] = photo }))
        assertEquals(ImportFailure.UNSAFE_PATH, failure(rewrite(good) { it["documents/../../evil"] = photo }))
        assertEquals(ImportFailure.NOT_AN_EXPORT, failure(rewrite(good) { it.remove("manifest.json") }))
        assertEquals(ImportFailure.NOT_AN_EXPORT, failure("this is not a zip file".toByteArray()))
        assertEquals(ImportFailure.MANIFEST_INVALID, failure(rewrite(good) { it["manifest.json"] = "{broken".toByteArray() }))
    }

    @Test
    fun `newer format version is refused, unknown fields in the same version are tolerated`() {
        val good = export()
        fun manifest(files: Map<String, ByteArray>) = files.getValue("manifest.json").toString(Charsets.UTF_8)
        assertEquals(ImportFailure.NEWER_FORMAT, failure(rewrite(good) { it["manifest.json"] = manifest(it).replace("\"formatVersion\":1", "\"formatVersion\":2").toByteArray() }))
        val extra = rewrite(good) { it["manifest.json"] = manifest(it).replace("{\"formatVersion\"", "{\"futureField\":true,\"formatVersion\"").toByteArray() }
        assertIs<ImportResult.Success>(import(extra))
    }

    @Test
    fun `archive with valid hashes but inconsistent content is rejected with details`() {
        // Poprawne technicznie archiwum zbudowane poza aplikacją: wpis wskazuje nieistniejący pojazd.
        val bad = data.copy(serviceEntries = data.serviceEntries.map { it.copy(vehicleId = "ghost") })
        val json = kotlinx.serialization.json.Json.encodeToString(ExportData.serializer(), bad).toByteArray()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(json).joinToString("") { "%02x".format(it) }
        val forged = rewrite(export()) {
            it["data.json"] = json
            val m = it.getValue("manifest.json").toString(Charsets.UTF_8)
            it["manifest.json"] = m.replace(Regex("\\{\"path\":\"data.json\",\"sha256\":\"[0-9a-f]+\",\"bytes\":\\d+}"), "{\"path\":\"data.json\",\"sha256\":\"$sha\",\"bytes\":${json.size}}").toByteArray()
        }
        val f = assertIs<ImportResult.Failure>(import(forged))
        assertEquals(ImportFailure.CONTENT_INCONSISTENT, f.reason)
        assertEquals(listOf(ExportIssueCode.UNKNOWN_VEHICLE), f.issues.map { it.code })
    }

    @Test
    fun `size limits stop oversized archives`() {
        val good = export()
        assertEquals(ImportFailure.TOO_LARGE, failure(good, ImportLimits(maxDocumentBytes = 1_000)))
        assertEquals(ImportFailure.TOO_LARGE, failure(good, ImportLimits(maxTotalBytes = 50_000)))
        assertEquals(ImportFailure.TOO_LARGE, failure(good, ImportLimits(maxEntries = 2)))
        assertEquals(ImportFailure.TOO_LARGE, failure(good, ImportLimits(maxJsonBytes = 10)))
    }

    // --- opcjonalne hasło ---

    private val fast = ExportEncryption.MIN_ITERATIONS // testy nie potrzebują pełnej liczby iteracji
    private fun exportEncrypted(password: String, d: ExportData = data, s: List<DocumentSource> = sources): ByteArray =
        ByteArrayOutputStream().also { ExportArchive.write(it, d, s, "2026-11-10T12:00:00Z", "1.0.0", today, password.toCharArray(), fast) }.toByteArray()
    private fun importWith(bytes: ByteArray, password: String?, sink: MutableMap<String, ByteArrayOutputStream> = mutableMapOf()) =
        ExportArchive.read(ByteArrayInputStream(bytes), today, password = password?.toCharArray()) { path -> ByteArrayOutputStream().also { sink[path] = it } }

    @Test
    fun `password protected export round trips and hides its content`() {
        val bytes = exportEncrypted("korrekt hest batteri")
        assertTrue(ExportEncryption.isEncrypted(bytes))
        val text = bytes.toString(Charsets.ISO_8859_1)
        for (leak in listOf("XXDEMA0000000T123", "manifest.json", "documents/", "AB12345", "PK")) assertTrue(leak !in text.take(40) && (leak == "PK" || leak !in text), leak)

        val sink = mutableMapOf<String, ByteArrayOutputStream>()
        val result = assertIs<ImportResult.Success>(importWith(bytes, "korrekt hest batteri", sink))
        assertEquals(data, result.data)
        assertTrue(pdf.contentEquals(sink.getValue("documents/d1.pdf").toByteArray()))
    }

    @Test
    fun `missing or wrong password and plain exports`() {
        val bytes = exportEncrypted("hemmelig")
        assertEquals(ImportFailure.PASSWORD_REQUIRED, assertIs<ImportResult.Failure>(importWith(bytes, null)).reason)
        assertEquals(ImportFailure.WRONG_PASSWORD_OR_DAMAGED, assertIs<ImportResult.Failure>(importWith(bytes, "Hemmelig")).reason)
        assertIs<ImportResult.Success>(importWith(export(), "ignored for a plain export"))
        assertTrue(!ExportEncryption.isEncrypted(export()))
    }

    @Test
    fun `any change to an encrypted export is detected`() {
        val bytes = exportEncrypted("hemmelig")
        fun reason(b: ByteArray) = assertIs<ImportResult.Failure>(importWith(b, "hemmelig")).reason
        val flipped = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertEquals(ImportFailure.WRONG_PASSWORD_OR_DAMAGED, reason(flipped))
        assertEquals(ImportFailure.WRONG_PASSWORD_OR_DAMAGED, reason(bytes.copyOf(bytes.size - 1)))
        assertEquals(ImportFailure.WRONG_PASSWORD_OR_DAMAGED, reason(bytes + 0))
        val header = bytes.copyOf().also { it[20] = (it[20] + 1).toByte() } // sól
        assertEquals(ImportFailure.WRONG_PASSWORD_OR_DAMAGED, reason(header))
        val hugeIterations = bytes.copyOf().also { it[8] = 0x7F }
        assertEquals(ImportFailure.WRONG_PASSWORD_OR_DAMAGED, reason(hugeIterations), "absurd iteration counts are refused, not computed")
    }

    @Test
    fun `stream encryption handles sizes around the chunk boundary`() {
        val chunk = ExportEncryption.CHUNK
        for (size in listOf(0, 1, chunk - 1, chunk, chunk + 1, 2 * chunk, 2 * chunk + 17)) {
            val plain = ByteArray(size) { (it * 31 % 256).toByte() }
            val encrypted = ByteArrayOutputStream().also { o -> ExportEncryption.encrypting(o, "pw".toCharArray(), fast).use { it.write(plain) } }.toByteArray()
            val chunks = if (size == 0) 1 else (size + chunk - 1) / chunk
            assertEquals(ExportEncryption.HEADER_BYTES + size + 16 * chunks, encrypted.size, "size $size")
            val back = ExportEncryption.decrypting(ByteArrayInputStream(encrypted), "pw".toCharArray()).use { it.readBytes() }
            assertTrue(plain.contentEquals(back), "size $size")
        }
    }

    @Test
    fun `cutting an encrypted stream at a chunk boundary does not pass as a shorter file`() {
        val chunk = ExportEncryption.CHUNK
        val plain = ByteArray(3 * chunk) { (it % 97).toByte() }
        val encrypted = ByteArrayOutputStream().also { o -> ExportEncryption.encrypting(o, "pw".toCharArray(), fast).use { it.write(plain) } }.toByteArray()
        val twoWholeChunks = encrypted.copyOf(ExportEncryption.HEADER_BYTES + 2 * (chunk + 16))
        assertFailsWith<ExportDecryptionException> {
            ExportEncryption.decrypting(ByteArrayInputStream(twoWholeChunks), "pw".toCharArray()).use { it.readBytes() }
        }
        val swapped = encrypted.copyOf().also { e ->
            val a = ExportEncryption.HEADER_BYTES; val b = a + chunk + 16
            val first = e.copyOfRange(a, b); System.arraycopy(e, b, e, a, chunk + 16); System.arraycopy(first, 0, e, b, chunk + 16)
        }
        assertFailsWith<ExportDecryptionException> {
            ExportEncryption.decrypting(ByteArrayInputStream(swapped), "pw".toCharArray()).use { it.readBytes() }
        }
    }

    @Test
    fun `two exports of the same data with the same password differ`() {
        assertTrue(!exportEncrypted("pw").contentEquals(exportEncrypted("pw")), "fresh salt and nonce every time")
    }

    @Test
    fun `empty password is refused`() {
        assertFailsWith<IllegalArgumentException> { exportEncrypted("") }
    }
}
