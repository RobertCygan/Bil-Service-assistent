package app.servicepassport.android.screens

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.core.app.ActivityOptionsCompat

/**
 * Stands for the system dialogs (file picker, "save as", camera, permission): every request is answered at once with
 * what [answer] returns for its contract. Null means the driver cancelled.
 */
internal class Answering(var answer: (ActivityResultContract<*, *>) -> Any?) : ActivityResultRegistry(), ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry get() = this
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        @Suppress("UNCHECKED_CAST") dispatchResult(requestCode, answer(contract) as O)
    }
}

internal fun ComposeContentTestRule.setAppContent(dialogs: Answering, content: @Composable () -> Unit) =
    setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides dialogs, content = content) }

internal fun ComposeContentTestRule.await(tag: String) = waitUntil(15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
internal fun ComposeContentTestRule.awaitGone(tag: String) = waitUntil(15_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
internal fun ComposeContentTestRule.awaitText(tag: String, text: String) =
    waitUntil(30_000) { onAllNodes(hasTestTag(tag) and hasText(text)).fetchSemanticsNodes().isNotEmpty() }
/** For anything inside a scrolling page. */
internal fun ComposeContentTestRule.click(tag: String) { onNodeWithTag(tag).performScrollTo().performClick(); waitForIdle() }
/** For bars and dialogs, which do not scroll. */
internal fun ComposeContentTestRule.tap(tag: String) { onNodeWithTag(tag).performClick(); waitForIdle() }
