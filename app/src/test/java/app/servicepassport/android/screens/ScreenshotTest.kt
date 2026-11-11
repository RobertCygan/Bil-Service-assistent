package app.servicepassport.android.screens

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.servicepassport.android.data.*
import app.servicepassport.android.ui.CarServiceApp
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens with invented data. With `-Pscreenshots` the images are written to app/build/screenshots;
 * without it the test only checks that every screen can be opened.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-420dpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var db: GarageDatabase
    private val name = "screens-${UUID.randomUUID()}.db"
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private fun day(text: String) = LocalDate.parse(text).toEpochDay()
    private fun id() = UUID.randomUUID().toString()

    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); db = GarageDatabase.open(context, name) }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    private fun seeded(): ProfileRepository = runBlocking {
        val repo = ProfileRepository(db, clock)
        repo.save(ProfileInput("Familiebilen", 2019, day("2019-04-02"), "Volvo", "V60"))
        val car = requireNotNull(db.vehicles().getProfile()).id
        repo.saveService(ServiceInput(id(), car, null, day("2025-10-02"), 78_200, listOf(WorkInput("engine_oil", "replace"), WorkInput("oil_filter", "replace")), costMinor = 389_000))
        repo.saveService(ServiceInput(id(), car, null, day("2024-05-14"), 61_300, listOf(WorkInput("brake_fluid", "replace"), WorkInput("cabin_filter", "replace"))))
        repo.saveReading(ReadingInput(id(), car, null, day("2026-11-03"), 92_480, useAsCurrent = true))
        repo.saveInterval(UserIntervalInput(id(), car, null, "engine_oil", "replace", 15_000, 12))
        repo.saveInterval(UserIntervalInput(id(), car, null, "brake_fluid", "replace", null, 24))
        repo.saveInterval(UserIntervalInput(id(), car, null, "cabin_filter", "replace", 30_000, 24))
        repo.saveInterval(UserIntervalInput(id(), car, null, "timing_belt", "replace", 120_000, 96))
        repo.saveInterval(UserIntervalInput(id(), car, null, "air_filter", "replace", 60_000, 48))
        repo
    }

    private fun shot(file: String) { compose.waitForIdle(); compose.onRoot().captureRoboImage("build/screenshots/$file.png") }
    private fun tab(tag: String) { compose.onNodeWithTag(tag).performClick(); compose.waitForIdle() }
    private fun open(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick(); compose.waitForIdle() }

    private fun tour(prefix: String, repository: ProfileRepository) {
        compose.setContent { CarServiceApp(repository) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("profile_name").fetchSemanticsNodes().isNotEmpty() }
        shot("$prefix-1-overview")
        open("open_dashboard"); shot("$prefix-2-dashboard")
        tab("nav_history"); shot("$prefix-3-history")
        tab("nav_settings"); shot("$prefix-4-settings")
    }

    @Test fun light() = tour("light", seeded())
    @Test @Config(qualifiers = "+night") fun dark() = tour("dark", seeded())

    @Test fun welcome() {
        compose.setContent { CarServiceApp(ProfileRepository(db, clock)) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("add_car").fetchSemanticsNodes().isNotEmpty() }
        shot("light-0-welcome")
    }
}
