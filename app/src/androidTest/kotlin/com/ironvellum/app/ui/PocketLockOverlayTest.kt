package com.ironvellum.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.PocketLock
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.PocketLockOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pocket lock holds until a deliberate hold, a tap does nothing, and a
 * TalkBack user can unlock with the "Unlock the trial" action instead.
 */
@RunWith(AndroidJUnit4::class)
class PocketLockOverlayTest {

    @get:Rule
    val compose = createComposeRule()

    private var unlocked = 0

    private fun show() {
        compose.setContent {
            IronvellumTheme {
                PocketLockOverlay(rest = null, nextLine = "Set 2 · Squat · 100 kg × 5", onUnlock = { unlocked++ })
            }
        }
    }

    @Test
    fun theCoverShowsTheSetInHandAndAHint() {
        show()
        compose.onNodeWithText("Hold to unlock", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Set 2 · Squat · 100 kg × 5", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aTapDoesNotUnlock() {
        show()
        compose.onRoot().performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(0, unlocked)
    }

    @Test
    fun aFullHoldUnlocks() {
        show()
        compose.onRoot().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(PocketLock.HOLD_MS + 500L)
        compose.waitForIdle()
        compose.onRoot().performTouchInput { up() }
        assertTrue("held long enough, it unlocks", unlocked >= 1)
    }

    @Test
    fun screenReadersUnlockWithAnAction() {
        show()
        val hasAction = SemanticsMatcher("has the Unlock the trial action") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "Unlock the trial" } == true
        }
        val node = compose.onNode(hasAction).fetchSemanticsNode()
        val unlock = node.config[SemanticsActions.CustomActions].first { it.label == "Unlock the trial" }
        compose.runOnUiThread { unlock.action() }
        assertEquals(1, unlocked)
    }
}
