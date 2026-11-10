package app.servicepassport.android.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.servicepassport.engine.*
import app.servicepassport.export.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Executes the actual JVM library on the minimum supported Android API, not on a host JDK. */
@RunWith(AndroidJUnit4::class)
class EngineAndroidSmokeTest {
    @Test fun explicitMissingMileageAndArchiveReadingWorkOnAndroidEight() {
        val today = LocalDate.of(2026, 11, 10)
        val source = SourceRef("android-demo", "Fictional test rule", "1", today, null, DataOrigin.DEMO, "Tests only")
        val rule = ServiceRule("demo-oil", "engine_oil:replace", Schedule.Fixed(Interval(km = 10000)), source)
        val status = ServiceEngine.evaluate(EvaluationInput(
            today, null, listOf(rule),
            history = listOf(PerformedTask("engine_oil:replace", today.minusDays(30), 1000)),
            currentOdometer = CurrentOdometer.NotSelected,
        )).single()
        assertEquals(Category.NEEDS_DATA, status.category)
        assertNull(status.basis.odometerUsed)
        assertTrue(Reason.ODOMETER_MISSING in status.reasons)

        // This exercises the archive header read that previously used an API-33-only method.
        // It is a runtime smoke test, not the full Room backup/restore feature from stage 7.
        val data = ExportData(vehicles = listOf(ExportVehicle("demo-car", "Name and year only", modelYear = 2022)))
        val archive = ByteArrayOutputStream()
        ExportArchive.write(archive, data, emptyList(), "2026-11-10T12:00:00Z", "integration-test", today)
        val result = ExportArchive.read(ByteArrayInputStream(archive.toByteArray()), today) { error("No documents expected") }
        assertTrue(result is ImportResult.Success)
        assertEquals(data, (result as ImportResult.Success).data)
    }
}
