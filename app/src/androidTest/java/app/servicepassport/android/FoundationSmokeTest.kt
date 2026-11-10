package app.servicepassport.android

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitTag(tag: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun input(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextClearance()
        compose.onNodeWithTag(tag).performTextInput(text)
        closeSoftKeyboard()
    }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun pick(tag: String, code: String) {
        click(tag)
        compose.onNodeWithTag("${tag}_$code").performScrollTo().performClick()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.filesDir, name).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }

    @Test fun manualHistorySurvivesEditingRecreationAndLanguageChange() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("nav_settings").performClick()
        click("language_en")
        compose.waitForIdle()
        compose.onAllNodesWithText("Settings").onFirst().assertExists()
        compose.onNodeWithTag("nav_overview").performClick()
        val add = compose.onAllNodesWithTag("add_car").fetchSemanticsNodes().isNotEmpty()
        click(if (add) "add_car" else "edit_car")
        input("car_name", "Foundation test car")
        input("model_year", "2022")
        click("save_car")
        awaitTag("profile_name")
        compose.onNodeWithTag("profile_name").assertTextEquals("Foundation test car")

        click("add_reading")
        input("reading_km", "59000")
        click("save_reading")
        awaitTag("current_km")
        compose.onNodeWithTag("current_km").assertTextEquals("59,000 km")

        click("add_service")
        input("service_date", "2025-12-15")
        input("service_km", "48000")
        click("add_work")
        pick("work_action_0", "engine_oil")
        pick("work_operation_0", "replace")
        click("add_work")
        pick("work_action_1", "brakes")
        input("service_cost", "1250.50")
        input("service_notes", "Manual history test")
        compose.activityRule.scenario.recreate()
        awaitTag("service_date")
        compose.onNodeWithTag("service_date").assertTextContains("2025-12-15")
        compose.onAllNodesWithTag("work_row").assertCountEquals(2)
        compose.onNodeWithTag("service_notes").assertTextContains("Manual history test")
        click("save_service")
        awaitTag("history_entry")
        compose.onAllNodesWithTag("history_entry").assertCountEquals(1)
        compose.onNodeWithTag("nav_overview").performClick()
        compose.onNodeWithTag("current_km").assertTextEquals("59,000 km")
        compose.onNodeWithTag("nav_history").performClick()
        click("history_entry")
        compose.onAllNodesWithTag("confirmed_work").assertCountEquals(2)
        compose.onNodeWithTag("service_note").assertTextEquals("Manual history test")

        click("edit_service")
        input("service_km", "49000")
        input("service_notes", "Edited manual history test")
        click("save_service")
        awaitTag("history_entry")
        click("history_entry")
        compose.onNodeWithTag("service_note").assertTextEquals("Edited manual history test")
        compose.onNodeWithText("49,000 km").assertExists()
        compose.onAllNodesWithTag("confirmed_work").assertCountEquals(2)
        compose.onNodeWithTag("back").performClick()

        compose.onNodeWithTag("nav_overview").performClick()
        compose.onNodeWithTag("current_km").assertTextEquals("59,000 km")
        click("reading_history")
        fun chooseExisting(value: String) {
            val card = hasTestTag("reading_entry") and hasAnyDescendant(hasText(value))
            compose.onNode(hasText(compose.activity.getString(R.string.use_as_current)) and hasAnyAncestor(card)).performScrollTo().performClick()
        }
        chooseExisting("49,000 km")
        compose.onNodeWithTag("current_reading_review").assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        compose.onNodeWithTag("nav_overview").performClick()
        compose.onNodeWithTag("current_km").assertTextEquals("59,000 km")
        click("reading_history")
        chooseExisting("49,000 km")
        compose.onNodeWithTag("confirm_current_reading").performClick()
        compose.onAllNodesWithTag("input_error").onLast().assertExists()
        input("exception_reason", "Deliberate historical choice")
        click("reading_reviewed")
        compose.onNodeWithTag("confirm_current_reading").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("current_reading_review").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("nav_overview").performClick()
        compose.onNodeWithTag("current_km").assertTextEquals("49,000 km")
        click("reading_history")
        chooseExisting("59,000 km")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Deliberate historical choice").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_overview").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("current_km") and hasText("59,000 km")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_history").performClick()

        // A service with unknown mileage can be saved and deleted without creating a zero reading.
        click("add_service")
        input("service_date", "2025-12-14")
        click("add_work")
        pick("work_action_0", "custom")
        input("custom_work_0", "Window repair")
        click("save_service")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("history_entry").fetchSemanticsNodes().size == 2 }
        compose.onNode(hasTestTag("history_entry") and hasText("Window repair")).performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.odometer_unknown)).assertExists()
        click("delete_service")
        compose.onNodeWithTag("confirm_delete").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("history_entry").fetchSemanticsNodes().size == 1 }
        screenshot("history-en.png")

        // A discarded form must not reappear with an old UUID or its previous values.
        click("add_service")
        input("service_notes", "Discard me")
        compose.onNodeWithTag("nav_history").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("service_notes").assertTextContains("Discard me")
        compose.onNodeWithTag("nav_history").performClick()
        compose.onNodeWithTag("confirm_discard").performClick()
        click("add_service")
        compose.onNodeWithTag("service_notes").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onAllNodesWithTag("work_row").assertCountEquals(0)
        compose.onNodeWithTag("back").performClick()
        compose.onAllNodesWithTag("history_entry").assertCountEquals(1)

        compose.onNodeWithTag("nav_settings").performClick()
        click("language_nb")
        compose.waitForIdle()
        compose.onAllNodesWithText("Innstillinger").onFirst().assertExists()
        compose.onNodeWithTag("nav_history").performClick()
        compose.onNodeWithText("15.12.2025").assertExists()
        screenshot("history-no.png")
        compose.onNodeWithTag("nav_overview").performClick()
        compose.onNodeWithTag("current_km").assertTextContains("59", substring = true)
        // The external test now starts a fresh process and verifies the persisted NO UI and records.
    }
}
