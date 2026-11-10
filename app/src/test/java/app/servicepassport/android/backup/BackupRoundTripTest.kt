package app.servicepassport.android.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
import app.servicepassport.export.ExportEncryption
import app.servicepassport.export.ImportFailure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Room v3 -> archive -> a new Room v3 database. Every row must come back identical. All data is fictional. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupRoundTripTest {
    private lateinit var context: Context
    private val files = mutableListOf<String>()
    private val open = mutableListOf<GarageDatabase>()
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private fun id() = UUID.randomUUID().toString()
    private fun day(text: String) = LocalDate.parse(text).toEpochDay()
    private fun database(): GarageDatabase {
        val file = "backup-${id()}.db"; files += file
        return GarageDatabase.open(context, file).also { open += it }
    }

    @Before fun setup() { context = ApplicationProvider.getApplicationContext() }
    @After fun cleanup() { open.forEach { it.close() }; files.forEach { context.deleteDatabase(it) } }

    private suspend fun rows(db: GarageDatabase): GarageRows {
        val snapshots = db.vehicles().vehicleIds().map { requireNotNull(db.vehicles().garage(it)) }
        return GarageRows(
            snapshots.map { it.vehicle },
            snapshots.flatMap { s -> s.events.map { it.event } }.sortedBy { it.id },
            snapshots.flatMap { s -> s.events.flatMap { it.actions } }.sortedBy { it.id },
            snapshots.flatMap { it.readings }.sortedBy { it.id },
            snapshots.flatMap { it.selections },
            snapshots.flatMap { it.intervals }.sortedBy { it.id },
            snapshots.flatMap { it.indicators }.sortedBy { it.id },
        )
    }

    /** A database that exercises every column the backup has to carry. */
    private suspend fun populated(): GarageDatabase {
        val db = database(); val repo = ProfileRepository(db, clock)
        repo.save(ProfileInput(
            "Familiebilen", 2022, day("2022-03-01"), "Demo Motors", "Example", "B8", "xxdema0000000t123", "ab 12345",
            "dgea", "DCT", "dq400", "PHEV", "no", "VARIABLE", "NORMAL",
        ))
        repo.save(ProfileInput("Familiebilen", 2022, day("2022-03-01"), "Demo Motors", "Example", "B8", "xxdema0000000t123", "ab 12345", "dgea", "DCT", "dq400", "PHEV", "no", "FIXED", "NORMAL"))
        val car = requireNotNull(db.vehicles().getProfile()).id
        val service = id()
        repo.saveService(ServiceInput(
            service, car, null, day("2025-03-12"), 70_000,
            listOf(WorkInput("engine_oil", "replace"), WorkInput("brake_fluid", "inspect"), WorkInput(null, null, "Byttet vindusviskere æøå")),
            costMinor = 371_700, note = "Blåbær verksted",
        ))
        repo.saveService(ServiceInput(service, car, 1, day("2025-03-12"), 70_000, listOf(WorkInput("engine_oil", "replace"), WorkInput(null, null, "Byttet vindusviskere æøå"), WorkInput("brake_fluid", "inspect")), costMinor = 371_700, note = "Blåbær verksted, rettet"))
        repo.saveService(ServiceInput(id(), car, null, day("2024-01-10"), null, listOf(WorkInput("air_filter", "replace"))))
        repo.saveReading(ReadingInput(id(), car, null, day("2026-11-01"), 84_512, useAsCurrent = true))
        // An older reading chosen on purpose, with an explanation: the selection must survive as chosen.
        val older = id()
        val snapshot = requireNotNull(db.vehicles().garage(car))
        val token = ReadingConsistency.token(snapshot, older, day("2026-06-01"), 80_000, true)
        repo.saveReading(ReadingInput(older, car, null, day("2026-06-01"), 80_000, useAsCurrent = true, exceptionReason = "Instrumentpanel byttet", warningsAcknowledged = true, reviewToken = token))
        repo.saveInterval(UserIntervalInput(id(), car, null, "engine_oil", "replace", 15_000, 12))
        val interval = id()
        repo.saveInterval(UserIntervalInput(interval, car, null, "brakes", "inspect", null, 24))
        repo.saveInterval(UserIntervalInput(interval, car, 1, "brakes", "inspect", 30_000, 24))
        repo.saveIndicator(IndicatorInput(id(), car, day("2026-09-01"), 82_000, 6_000, 120))
        repo.saveIndicator(IndicatorInput(id(), car, day("2026-11-05"), 84_600, -200, null))
        return db
    }

    private suspend fun archive(db: GarageDatabase, password: String? = null): ByteArray =
        ByteArrayOutputStream().also { GarageBackup(db, clock).write(it, "0.3.0-test", password?.toCharArray()) }.toByteArray()

    private suspend fun restore(db: GarageDatabase, bytes: ByteArray, password: String? = null) =
        GarageBackup(db, clock).restore(ByteArrayInputStream(bytes), password?.toCharArray())

    @Test fun everyRowSurvivesExportAndRestoreIntoANewDatabase() = runBlocking {
        val source = populated()
        val before = rows(source)
        assertEquals(1, before.vehicles.size); assertEquals(2, before.events.size); assertEquals(4, before.actions.size)
        assertEquals(3, before.readings.size); assertEquals(2, before.intervals.size); assertEquals(2, before.indicators.size)
        assertEquals("the deliberately older reading is the selected one", 80_000L, before.readings.single { it.id == before.selections.single().readingId }.km)

        val target = database()
        assertEquals(RestoreOutcome.Restored, restore(target, archive(source)))
        assertEquals(before, rows(target))

        // The restored database is a working one: the next edit continues from the restored revisions.
        val repo = ProfileRepository(target, clock)
        val event = before.events.first { it.costMinor != null }
        repo.deleteService(event.vehicleId, event.id, event.revision)
        assertEquals(1, rows(target).events.size)
    }

    @Test fun aVehicleSavedWithOnlyANameRoundTrips() = runBlocking {
        val source = database(); ProfileRepository(source, clock).save(ProfileInput("Min bil", null))
        val target = database()
        assertEquals(RestoreOutcome.Restored, restore(target, archive(source)))
        assertEquals(rows(source), rows(target))
    }

    @Test fun passwordProtectedBackupRoundTripsAndNeedsTheRightPassword() = runBlocking {
        val source = populated()
        val bytes = archive(source, "korrekt hest batteri")
        assertTrue(ExportEncryption.isEncrypted(bytes))
        val target = database()
        assertEquals(RestoreOutcome.ArchiveRefused(ImportFailure.PASSWORD_REQUIRED), restore(target, bytes))
        assertEquals(RestoreOutcome.ArchiveRefused(ImportFailure.WRONG_PASSWORD_OR_DAMAGED), restore(target, bytes, "feil"))
        assertTrue("a refused restore changes nothing", rows(target).vehicles.isEmpty())
        assertEquals(RestoreOutcome.Restored, restore(target, bytes, "korrekt hest batteri"))
        assertEquals(rows(source), rows(target))
    }

    @Test fun restoreReplacesExistingDataAndARefusedRestoreKeepsIt() = runBlocking {
        val source = populated()
        val target = database(); val repo = ProfileRepository(target, clock)
        repo.save(ProfileInput("Gammel bil", 2015))
        val car = requireNotNull(target.vehicles().getProfile()).id
        repo.saveReading(ReadingInput(id(), car, null, day("2026-10-01"), 150_000, useAsCurrent = true))
        val existing = rows(target)

        val damaged = archive(source).also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertTrue(restore(target, damaged) is RestoreOutcome.ArchiveRefused)
        assertEquals(existing, rows(target))

        assertEquals(RestoreOutcome.Restored, restore(target, archive(source)))
        assertEquals(rows(source), rows(target))
        assertTrue(rows(target).vehicles.none { it.displayName == "Gammel bil" })
    }

    @Test fun contentTheAppCannotStoreIsRefusedInsteadOfReinterpreted() {
        val data = BackupMapper.export(emptyList())
        fun problem(block: () -> Unit) = try { block(); null } catch (e: BackupRefused) { e.problem }
        val vehicle = app.servicepassport.export.ExportVehicle("v1", "Bil")
        fun event(vararg actions: app.servicepassport.export.ExportAction, cost: Long? = null, currency: String? = null) =
            app.servicepassport.export.ExportServiceEvent("e1", "v1", "2025-01-01", cost, currency, actions = actions.toList())
        val coded = app.servicepassport.export.ExportAction("a1", "engine_oil", "replace")

        assertEquals(RestoreProblem.EMPTY_BACKUP, problem { BackupMapper.rows(data) })
        assertEquals(RestoreProblem.SEVERAL_VEHICLES, problem { BackupMapper.rows(data.copy(vehicles = listOf(vehicle, vehicle.copy(id = "v2")))) })
        val one = data.copy(vehicles = listOf(vehicle))
        assertNull(problem { BackupMapper.rows(one.copy(serviceEvents = listOf(event(coded, cost = 100, currency = "NOK")))) })
        assertEquals(RestoreProblem.UNKNOWN_TASK, problem { BackupMapper.rows(one.copy(serviceEvents = listOf(event(coded.copy(actionCode = "ENGINE_OIL"))))) })
        assertEquals(RestoreProblem.UNKNOWN_TASK, problem { BackupMapper.rows(one.copy(serviceEvents = listOf(event(coded.copy(operationCode = "test"))))) })
        assertEquals(RestoreProblem.UNKNOWN_TASK, problem { BackupMapper.rows(one.copy(userRules = listOf(app.servicepassport.export.ExportUserRule("r", "v1", "tyres", "replace", km = 1)))) })
        assertEquals(RestoreProblem.UNSUPPORTED_CURRENCY, problem { BackupMapper.rows(one.copy(serviceEvents = listOf(event(coded, cost = 100, currency = "EUR")))) })
        assertEquals(RestoreProblem.UNSUPPORTED_CURRENCY, problem { BackupMapper.rows(one.copy(serviceEvents = listOf(event(coded, cost = 100)))) })
        assertEquals(RestoreProblem.UNSUPPORTED_CONTENT, problem { BackupMapper.rows(one.copy(indicatorReadings = listOf(app.servicepassport.export.ExportIndicatorReading("i", "v1", "engine_oil:replace", "2026-01-01", 1, remainingKm = 1)))) })
        assertNull(problem { BackupMapper.rows(one.copy(indicatorReadings = listOf(app.servicepassport.export.ExportIndicatorReading("i", "v1", "inspection_service:inspect", "2026-01-01", 1, remainingKm = 1)))) })
    }
}
