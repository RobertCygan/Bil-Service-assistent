package app.servicepassport.android

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun saveProfileNavigateAndSwitchBothLanguages() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("language_en").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Settings").onFirst().assertExists()
        compose.onNodeWithTag("nav_overview").performClick()
        val add = compose.onAllNodesWithTag("add_car").fetchSemanticsNodes().isNotEmpty()
        compose.onNodeWithTag(if (add) "add_car" else "edit_car").performClick()
        compose.onNodeWithTag("car_name").performTextClearance()
        compose.onNodeWithTag("car_name").performTextInput("Foundation test car")
        compose.onNodeWithTag("model_year").performTextClearance()
        compose.onNodeWithTag("model_year").performTextInput("2022")
        compose.onNodeWithTag("save_car").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_name").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile_name").assertTextEquals("Foundation test car")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile_name").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("profile_name").assertTextEquals("Foundation test car")
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("language_nb").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Innstillinger").onFirst().assertExists()
        compose.onNodeWithTag("nav_history").performClick()
        compose.onNodeWithText("Ingen service registrert ennå").assertExists()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("language_en").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nav_overview").performClick()
        compose.onNodeWithTag("profile_name").assertTextEquals("Foundation test car")
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.filesDir, "foundation-en.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        // Finish in a language different from the emulator default; the external process test
        // then force-stops the app and checks that both this choice and the database survived.
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("language_nb").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Innstillinger").onFirst().assertExists()
    }
}
