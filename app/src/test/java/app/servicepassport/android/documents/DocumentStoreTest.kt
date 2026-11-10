package app.servicepassport.android.documents

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.backup.*
import app.servicepassport.android.data.*
import app.servicepassport.export.*
import app.servicepassport.extraction.DuplicateKind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Document files next to the database: kept only with a saved entry, carried by backups, gone when deleted. Data is fictional. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DocumentStoreTest {
    private lateinit var context: Context
    private val names = mutableListOf<String>()
    private val open = mutableListOf<GarageDatabase>()
    private val folders = mutableListOf<File>()
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private fun id() = UUID.randomUUID().toString()
    private fun day(text: String) = LocalDate.parse(text).toEpochDay()

    private class Installation(val db: GarageDatabase, val base: File, val store: DocumentStore, val repo: ProfileRepository, val backup: GarageBackup) {
        fun live() = File(base, DocumentStore.LIVE).listFiles().orEmpty().map { it.name }.sorted()
        fun staged() = File(base, DocumentStore.STAGED).listFiles().orEmpty().map { it.name }
        fun waiting() = File(base, DocumentStore.RESTORE).listFiles().orEmpty().map { it.name }
    }

    private fun installation(): Installation {
        val name = "documents-${id()}.db"; names += name
        val db = GarageDatabase.open(context, name).also { open += it }
        val base = File(context.cacheDir, "install-${id()}").also { it.mkdirs(); folders += it }
        val store = DocumentStore(context, db, clock, base)
        return Installation(db, base, store, ProfileRepository(db, clock), GarageBackup(db, clock, documents = store))
    }

    private fun picked(content: ByteArray, name: String = "faktura-${id()}.pdf"): Uri {
        val folder = File(context.cacheDir, "picked").also { it.mkdirs(); folders += it }
        return Uri.fromFile(File(folder, name).also { it.writeBytes(content) })
    }

    private suspend fun car(install: Installation): String {
        install.repo.save(ProfileInput("Familiebilen", 2022))
        return requireNotNull(install.db.vehicles().getProfile()).id
    }

    private suspend fun service(install: Installation, car: String, date: String = "2025-03-12", km: Long? = 70_000): String =
        install.repo.saveService(ServiceInput(id(), car, null, day(date), km, listOf(WorkInput("engine_oil", "replace"))))

    private suspend fun record(install: Installation, car: String, event: String) = requireNotNull(install.db.vehicles().garage(car)).events.single { it.event.id == event }

    @Before fun setup() { context = ApplicationProvider.getApplicationContext() }
    @After fun cleanup() { open.forEach { it.close() }; names.forEach { context.deleteDatabase(it) }; folders.forEach { it.deleteRecursively() } }

    @Test fun aPickedDocumentIsOnlyStagedUntilItIsAttachedToASavedEntry() = runBlocking {
        val install = installation(); val car = car(install)
        val content = "%PDF fictional invoice".toByteArray()
        val staged = requireNotNull(install.store.stage(picked(content)))
        assertEquals("application/pdf", staged.mimeType)
        assertEquals(content.size.toLong(), staged.bytes)
        assertEquals(64, staged.sha256.length)
        assertEquals(listOf(staged.fileName), install.staged())
        assertTrue(install.live().isEmpty())

        val event = service(install, car)
        assertTrue(install.store.attach(staged, car, event))
        val document = record(install, car, event).documents.single()
        assertEquals(DocumentEntity(staged.id, car, event, staged.fileName, "application/pdf", staged.sha256, content.size.toLong(), day("2026-11-10"), clock.millis()), document)
        assertTrue(install.store.exists(document))
        assertArrayEquals(content, install.store.open(document).use { it.readBytes() })
        assertTrue(install.staged().isEmpty())
    }

    @Test fun aNewPickReplacesTheStagedCopyAndDiscardingLeavesNothing() = runBlocking {
        val install = installation()
        requireNotNull(install.store.stage(picked("first".toByteArray())))
        val second = requireNotNull(install.store.stage(picked("second".toByteArray(), "bilde-${id()}.jpg")))
        assertEquals("image/jpeg", second.mimeType)
        assertEquals(listOf(second.fileName), install.staged())
        install.store.discardStaged()
        assertTrue(install.staged().isEmpty())
    }

    @Test fun unsupportedEmptyOrOversizedFilesAreNotKept() = runBlocking {
        val install = installation()
        assertNull(install.store.stage(picked("text".toByteArray(), "notat-${id()}.txt")))
        assertNull(install.store.stage(picked(ByteArray(0))))
        assertNull(install.store.stage(picked(ByteArray(app.servicepassport.extraction.ImportLimits.MAX_BYTES.toInt() + 1))))
        assertTrue(install.staged().isEmpty())
    }

    @Test fun attachingToAnEntryThatDoesNotExistKeepsTheCopyStagedForARetry() = runBlocking {
        val install = installation(); val car = car(install)
        val staged = requireNotNull(install.store.stage(picked("x".toByteArray())))
        assertFalse(install.store.attach(staged, car, "no-such-entry"))
        assertEquals(listOf(staged.fileName), install.staged())
        assertTrue(install.live().isEmpty())
        assertTrue(install.store.attach(staged, car, service(install, car)))
    }

    @Test fun deletingTheEntryOrTheDocumentRemovesTheFile() = runBlocking {
        val install = installation(); val car = car(install)
        val first = service(install, car); val second = service(install, car, "2024-02-01", 50_000)
        val a = requireNotNull(install.store.stage(picked("a".toByteArray()))); assertTrue(install.store.attach(a, car, first))
        val b = requireNotNull(install.store.stage(picked("b".toByteArray()))); assertTrue(install.store.attach(b, car, second))
        assertEquals(listOf(a.fileName, b.fileName).sorted(), install.live())

        install.repo.deleteService(car, first, 1)
        install.store.sweep()
        assertEquals(listOf(b.fileName), install.live())

        assertFalse(install.store.remove("another-car", b.id))
        assertTrue(install.store.remove(car, b.id))
        assertTrue(install.live().isEmpty())
        assertTrue(record(install, car, second).documents.isEmpty())
    }

    @Test fun aBackupCarriesDocumentsIntoAnotherInstallationUnchanged() = runBlocking {
        val source = installation(); val car = car(source)
        val event = service(source, car)
        val content = ByteArray(200_000) { (it * 31).toByte() }
        assertTrue(source.store.attach(requireNotNull(source.store.stage(picked(content))), car, event))
        val archive = ByteArrayOutputStream().also { source.backup.write(it, "test") }.toByteArray()

        val target = installation(); val oldCar = car(target)
        val old = requireNotNull(target.store.stage(picked("old".toByteArray())))
        assertTrue(target.store.attach(old, oldCar, service(target, oldCar)))

        assertEquals(RestoreOutcome.Restored, target.backup.restore(ByteArrayInputStream(archive)))
        val original = record(source, car, event).documents.single()
        val restored = record(target, car, event).documents.single()
        assertEquals(original, restored)
        assertEquals(listOf(original.fileName), target.live())
        assertArrayEquals(content, target.store.open(restored).use { it.readBytes() })
        assertTrue(target.waiting().isEmpty())
    }

    @Test fun anEncryptedBackupCarriesDocumentsToo() = runBlocking {
        val source = installation(); val car = car(source); val event = service(source, car)
        assertTrue(source.store.attach(requireNotNull(source.store.stage(picked("secret invoice".toByteArray()))), car, event))
        val archive = ByteArrayOutputStream().also { source.backup.write(it, "test", "correct horse".toCharArray()) }.toByteArray()
        val target = installation()
        assertEquals(RestoreOutcome.ArchiveRefused(ImportFailure.WRONG_PASSWORD_OR_DAMAGED), target.backup.restore(ByteArrayInputStream(archive), "wrong password".toCharArray()))
        assertTrue(target.waiting().isEmpty()); assertTrue(target.live().isEmpty())
        assertEquals(RestoreOutcome.Restored, target.backup.restore(ByteArrayInputStream(archive), "correct horse".toCharArray()))
        assertEquals("secret invoice", target.store.open(record(target, car, event).documents.single()).use { it.readBytes() }.decodeToString())
    }

    @Test fun aDamagedBackupChangesNeitherTheDatabaseNorTheFiles() = runBlocking {
        val source = installation(); val car = car(source); val event = service(source, car)
        assertTrue(source.store.attach(requireNotNull(source.store.stage(picked(java.util.Random(1).let { random -> ByteArray(50_000).also(random::nextBytes) }))), car, event))
        val archive = ByteArrayOutputStream().also { source.backup.write(it, "test") }.toByteArray()
        // One changed byte inside the document: the archive still opens, but the checksum in the manifest no longer matches.
        val damaged = archive.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }

        val target = installation(); val kept = car(target); val keptEvent = service(target, kept)
        val staged = requireNotNull(target.store.stage(picked("kept".toByteArray())))
        assertTrue(target.store.attach(staged, kept, keptEvent))
        val outcome = target.backup.restore(ByteArrayInputStream(damaged))
        assertTrue(outcome is RestoreOutcome.ArchiveRefused)
        assertEquals(listOf(staged.fileName), target.live())
        assertEquals(staged.id, record(target, kept, keptEvent).documents.single().id)
        assertTrue(target.waiting().isEmpty())
    }

    @Test fun aDocumentWhoseFileIsGoneIsLeftOutOfTheBackupInsteadOfBreakingIt() = runBlocking {
        val source = installation(); val car = car(source); val event = service(source, car)
        val staged = requireNotNull(source.store.stage(picked("lost".toByteArray())))
        assertTrue(source.store.attach(staged, car, event))
        File(File(source.base, DocumentStore.LIVE), staged.fileName).delete()
        assertFalse(source.store.exists(record(source, car, event).documents.single()))

        val archive = ByteArrayOutputStream().also { source.backup.write(it, "test") }.toByteArray()
        val target = installation()
        assertEquals(RestoreOutcome.Restored, target.backup.restore(ByteArrayInputStream(archive)))
        assertTrue(record(target, car, event).documents.isEmpty())
    }

    @Test fun deletingEverythingRemovesTheFiles() = runBlocking {
        val install = installation(); val car = car(install)
        assertTrue(install.store.attach(requireNotNull(install.store.stage(picked("a".toByteArray()))), car, service(install, car)))
        requireNotNull(install.store.stage(picked("b".toByteArray())))
        install.backup.deleteAll()
        assertTrue(install.live().isEmpty()); assertTrue(install.staged().isEmpty())
        assertNull(install.db.vehicles().getProfile())
    }

    @Test fun aBackupDocumentMustBelongToExactlyOneEntryAndHaveASupportedType() {
        val vehicle = ExportVehicle("v1", "Familiebilen")
        fun event(id: String, documents: List<String>) = ExportServiceEvent(id, "v1", "2025-03-12", actions = listOf(ExportAction("$id:0", "engine_oil", "replace")), documentIds = documents)
        val document = ExportDocument("d1", "v1", "documents/d1.pdf", "application/pdf", "2026-11-10", 5)
        val files = listOf(ExportedFile("documents/d1.pdf", "ab".repeat(32), 12))
        fun problem(data: ExportData, manifest: List<ExportedFile> = files) = try { BackupMapper.rows(data, manifest); null } catch (refused: BackupRefused) { refused.problem }

        val good = ExportData(vehicles = listOf(vehicle), serviceEvents = listOf(event("e1", listOf("d1"))), documents = listOf(document))
        assertEquals(DocumentEntity("d1", "v1", "e1", "d1.pdf", "application/pdf", "ab".repeat(32), 12, day("2026-11-10"), 5), BackupMapper.rows(good, files).documents.single())
        assertEquals(RestoreProblem.UNSUPPORTED_CONTENT, problem(good.copy(serviceEvents = listOf(event("e1", emptyList())))))
        assertEquals(RestoreProblem.UNSUPPORTED_CONTENT, problem(good.copy(serviceEvents = listOf(event("e1", listOf("d1")), event("e2", listOf("d1"))))))
        assertEquals(RestoreProblem.UNSUPPORTED_CONTENT, problem(good.copy(documents = listOf(document.copy(mimeType = "text/html")))))
        assertEquals(RestoreProblem.UNSUPPORTED_CONTENT, problem(good.copy(documents = listOf(document.copy(path = "documents/.hidden")))))
        assertEquals(RestoreProblem.UNSUPPORTED_CONTENT, problem(good, emptyList()))
    }

    @Test fun aRepeatedFileOrTheSameDateAndMileageIsPointedOut() = runBlocking {
        val install = installation(); val car = car(install)
        val event = service(install, car)
        val staged = requireNotNull(install.store.stage(picked("invoice".toByteArray())))
        assertTrue(install.store.attach(staged, car, event))
        service(install, car, "2024-02-01", null)
        val history = requireNotNull(install.db.vehicles().garage(car)).events
        fun choice(date: String?, km: Int?) = ReviewChoice(date?.let(LocalDate::parse), km, emptySet(), false, emptySet())

        val again = requireNotNull(install.store.stage(picked("invoice".toByteArray())))
        assertEquals(staged.sha256, again.sha256)
        assertEquals(setOf(DuplicateKind.SAME_FILE), ImportReview.duplicates(history, again.sha256, choice("2020-01-01", 1)))
        assertEquals(setOf(DuplicateKind.SAME_DATE_AND_ODOMETER), ImportReview.duplicates(history, "00", choice("2025-03-12", 70_000)))
        assertEquals(emptySet<DuplicateKind>(), ImportReview.duplicates(history, "00", choice("2025-03-12", 70_001)))
        // Without a mileage on either side nothing can be concluded from the date alone.
        assertEquals(emptySet<DuplicateKind>(), ImportReview.duplicates(history, null, choice("2024-02-01", null)))
    }
}
