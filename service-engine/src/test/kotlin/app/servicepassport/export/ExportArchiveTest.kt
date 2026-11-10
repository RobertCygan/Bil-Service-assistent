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
        vehicles = listOf(
            ExportVehicle(
                "v1", "Familiebilen", modelYear = 2022, firstRegistrationDate = "2022-03-01", make = "Demo Motors", model = "Example",
                vin = "XXDEMA0000000T123", registrationNumber = "AB12345", market = "NO", configurationRevision = 3,
                createdAtUtc = 1_700_000_000_000, updatedAtUtc = 1_700_000_500_000, currentReadingId = "o2",
            ),
        ),
        odometerReadings = listOf(
            ExportOdometerReading("o1", "v1", eventId = "e1", date = "2025-03-12", km = 70_000, revision = 2, createdAtUtc = 1, updatedAtUtc = 2),
            ExportOdometerReading("o2", "v1", date = "2026-11-01", km = 84_512, exceptionReason = "Byttet instrumentpanel"),
        ),
        serviceEvents = listOf(
            ExportServiceEvent(
                "e1", "v1", "2025-03-12", 371_700, "NOK", "Blåbær æøå", revision = 4, createdAtUtc = 5, updatedAtUtc = 6,
                actions = listOf(
                    ExportAction("a1", "engine_oil", "replace"),
                    ExportAction("a2", "brake_fluid", "inspect"),
                    ExportAction("a3", customLabel = "Byttet vindusviskere"),
                ),
                documentIds = listOf("d1"),
            ),
        ),
        documents = listOf(
            ExportDocument("d1", "v1", "documents/d1.pdf", "application/pdf", "2025-03-14"),
            ExportDocument("d2", "v1", "documents/d2.jpg", "image/jpeg", "2025-03-14"),
        ),
        userRules = listOf(ExportUserRule("r1", "v1", "air_filter:replace", months = 12)),
        indicatorReadings = listOf(ExportIndicatorReading("v1", "inspection_service:inspect", "2026-10-01", 84_000, remainingKm = 4_000)),
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
        val broken = data.copy(serviceEvents = data.serviceEvents.map { it.copy(vehicleId = "ghost", documentIds = listOf("nope")) })
        val e = assertFailsWith<ExportException> { ExportArchive.write(out, broken, sources, "t", "1", today) }
        assertEquals(setOf(ExportIssueCode.UNKNOWN_VEHICLE, ExportIssueCode.UNKNOWN_DOCUMENT, ExportIssueCode.UNKNOWN_EVENT), e.issues.map { it.code }.toSet())
        assertEquals(0, out.size())

        assertFailsWith<ExportException> { export(s = sources.take(1)) } // brak pliku dokumentu d2
        assertFailsWith<ExportException> { export(s = sources + DocumentSource("documents/extra.pdf") { ByteArrayInputStream(photo) }) }
    }

    @Test
    fun `validator reports each kind of problem`() {
        fun codes(d: ExportData, paths: Set<String> = setOf("documents/d1.pdf", "documents/d2.jpg")) =
            ExportValidator.validate(d, today, paths).map { it.code }.toSet()
        fun readings(change: (ExportOdometerReading) -> ExportOdometerReading) = data.copy(odometerReadings = data.odometerReadings.map(change))
        fun events(change: (ExportServiceEvent) -> ExportServiceEvent) = data.copy(serviceEvents = data.serviceEvents.map(change))
        fun actions(vararg a: ExportAction) = events { it.copy(actions = a.toList()) }

        assertTrue(codes(data).isEmpty())
        assertEquals(setOf(ExportIssueCode.DUPLICATE_ID), codes(data.copy(vehicles = data.vehicles + data.vehicles)))
        assertEquals(setOf(ExportIssueCode.INVALID_DATE), codes(readings { it.copy(date = "12.03.2025") }))
        assertEquals(setOf(ExportIssueCode.DATE_IN_FUTURE), codes(readings { it.copy(date = "2026-11-11") }))
        assertEquals(setOf(ExportIssueCode.VALUE_OUT_OF_RANGE), codes(readings { it.copy(km = -1) }))
        assertEquals(setOf(ExportIssueCode.VALUE_OUT_OF_RANGE), codes(readings { it.copy(km = Int.MAX_VALUE + 1L) }))
        assertEquals(setOf(ExportIssueCode.VALUE_OUT_OF_RANGE), codes(events { it.copy(costMinor = -1) }))
        assertEquals(setOf(ExportIssueCode.EVENT_WITHOUT_ACTIONS), codes(actions()))
        assertEquals(setOf(ExportIssueCode.RULE_WITHOUT_INTERVAL), codes(data.copy(userRules = listOf(ExportUserRule("r", "v1", "x:replace")))))
        assertEquals(setOf(ExportIssueCode.VALUE_OUT_OF_RANGE), codes(data.copy(userRules = listOf(ExportUserRule("r", "v1", "x:replace", km = 0)))))
        assertEquals(setOf(ExportIssueCode.INDICATOR_WITHOUT_VALUE), codes(data.copy(indicatorReadings = listOf(ExportIndicatorReading("v1", "x:inspect", "2026-01-01", 1)))))
        assertEquals(setOf(ExportIssueCode.EMPTY_FIELD), codes(data.copy(vehicles = listOf(data.vehicles[0].copy(displayName = " ")))))
        assertEquals(setOf(ExportIssueCode.DOCUMENT_FILE_MISSING), codes(data, setOf("documents/d1.pdf")))
        assertEquals(
            setOf(ExportIssueCode.DOCUMENT_PATH_INVALID),
            codes(data.copy(documents = listOf(data.documents[0].copy(path = "documents/../x")), serviceEvents = events { it.copy(documentIds = emptyList()) }.serviceEvents)),
        )
        assertEquals(setOf(ExportIssueCode.DUPLICATE_DOCUMENT_PATH), codes(data.copy(documents = data.documents.map { it.copy(path = "documents/d1.pdf") })))
    }

    @Test
    fun `vehicle saved with only a name is a valid export`() {
        // Aplikacja pozwala zapisać auto z samą nazwą — kopia nie może tego odrzucić.
        val minimal = ExportData(vehicles = listOf(ExportVehicle("v1", "Min bil")))
        assertTrue(ExportValidator.validate(minimal, today, emptySet()).isEmpty())
        assertEquals(minimal, assertIs<ImportResult.Success>(import(export(minimal, emptyList()))).data)
    }

    @Test
    fun `current reading selection and reading-event links follow the database rules`() {
        fun codes(d: ExportData) = ExportValidator.validate(d, today, setOf("documents/d1.pdf", "documents/d2.jpg")).map { it.code }.toSet()
        val other = ExportVehicle("v2", "Andre bil")
        val foreign = ExportOdometerReading("o9", "v2", date = "2026-01-01", km = 5)

        assertTrue(codes(data.copy(vehicles = listOf(data.vehicles[0].copy(currentReadingId = null)))).isEmpty(), "no selection is a valid state")
        assertEquals(setOf(ExportIssueCode.UNKNOWN_READING), codes(data.copy(vehicles = listOf(data.vehicles[0].copy(currentReadingId = "missing")))))
        assertEquals(
            setOf(ExportIssueCode.UNKNOWN_READING),
            codes(data.copy(vehicles = listOf(data.vehicles[0].copy(currentReadingId = "o9"), other), odometerReadings = data.odometerReadings + foreign)),
            "a reading of another vehicle cannot be the current one",
        )
        assertEquals(setOf(ExportIssueCode.UNKNOWN_EVENT), codes(data.copy(odometerReadings = data.odometerReadings.map { if (it.id == "o1") it.copy(eventId = "nope") else it })))
        assertEquals(
            setOf(ExportIssueCode.UNKNOWN_EVENT),
            codes(data.copy(vehicles = data.vehicles + other, odometerReadings = data.odometerReadings.map { it.copy(eventId = null) } + foreign.copy(eventId = "e1"))),
            "a reading cannot belong to another vehicle's service",
        )
        assertEquals(
            setOf(ExportIssueCode.EVENT_WITH_SEVERAL_READINGS),
            codes(data.copy(odometerReadings = data.odometerReadings.map { it.copy(eventId = "e1") })),
        )
    }

    @Test
    fun `an action is either a coded pair or a custom label`() {
        fun codes(vararg a: ExportAction) = ExportValidator.validate(
            data.copy(serviceEvents = data.serviceEvents.map { it.copy(actions = a.toList()) }), today, setOf("documents/d1.pdf", "documents/d2.jpg"),
        ).map { it.code }.toSet()
        assertTrue(codes(ExportAction("a", "engine_oil", "replace"), ExportAction("b", customLabel = "Vask")).isEmpty())
        for (bad in listOf(
            ExportAction("a"), ExportAction("a", "engine_oil"), ExportAction("a", operationCode = "replace"),
            ExportAction("a", "engine_oil", "replace", "also a label"), ExportAction("a", customLabel = " "),
        )) assertEquals(setOf(ExportIssueCode.ACTION_INVALID), codes(bad), bad.toString())
        assertEquals(setOf(ExportIssueCode.DUPLICATE_ID), codes(ExportAction("a", customLabel = "x"), ExportAction("a", customLabel = "y")))
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
        val bad = data.copy(odometerReadings = data.odometerReadings.map { it.copy(vehicleId = "ghost", eventId = null) }, vehicles = data.vehicles.map { it.copy(currentReadingId = null) })
        val json = kotlinx.serialization.json.Json.encodeToString(ExportData.serializer(), bad).toByteArray()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(json).joinToString("") { "%02x".format(it) }
        val forged = rewrite(export()) {
            it["data.json"] = json
            val m = it.getValue("manifest.json").toString(Charsets.UTF_8)
            it["manifest.json"] = m.replace(Regex("\\{\"path\":\"data.json\",\"sha256\":\"[0-9a-f]+\",\"bytes\":\\d+}"), "{\"path\":\"data.json\",\"sha256\":\"$sha\",\"bytes\":${json.size}}").toByteArray()
        }
        val f = assertIs<ImportResult.Failure>(import(forged))
        assertEquals(ImportFailure.CONTENT_INCONSISTENT, f.reason)
        assertEquals(setOf(ExportIssueCode.UNKNOWN_VEHICLE), f.issues.map { it.code }.toSet())
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
