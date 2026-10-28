package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.ui.components.CAPPED_DIALOG_TAG
import com.ironvellum.app.ui.components.CappedDialog
import com.ironvellum.app.ui.components.InkPickerSheet
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.WorkoutLogScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The 600dp cap and the expanded switch at tablet widths. The test device is a phone, so the density is overridden
 * the way the pane tests do: the real pixels stay, and the window measures as [widthDp] wide.
 */
@RunWith(AndroidJUnit4::class)
class LargeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val widthDp = mutableFloatStateOf(700f)

    private fun show(width: Float, content: @Composable () -> Unit) {
        widthDp.floatValue = width
        compose.setContent {
            val pixels = LocalWindowInfo.current.containerSize.width
            val fontScale = LocalDensity.current.fontScale
            CompositionLocalProvider(LocalDensity provides Density(pixels / widthDp.floatValue, fontScale)) {
                IronvellumTheme(light = false) { content() }
            }
        }
    }

    /** [tag] is at most 600dp wide and centred in the window; on a window wider than 600dp it is exactly 600dp. */
    private fun assertCappedAndCentred(tag: String, width: Float) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val windowPx = compose.activity.window.decorView.width.toFloat()
        val capPx = 600f * windowPx / width
        assertTrue("$tag is ${bounds.width}px wide, over the ${capPx}px cap at ${width}dp", bounds.width <= capPx + 1f)
        assertEquals("$tag is not centred at ${width}dp: $bounds in $windowPx", windowPx, bounds.left + bounds.right, 2f)
        assertEquals("$tag should fill its 600dp cap (or the window) at ${width}dp", minOf(capPx, windowPx), bounds.width, 2f)
    }

    @Test
    fun rootContentIsCentredAndCappedAt700Dp() {
        show(700f) { IronvellumRoot() }
        assertCappedAndCentred("rootContent", 700f)
    }

    @Test
    fun rootContentIsCentredAndCappedBelowTheExpandedWidth() {
        // 839dp is the widest window that is not expanded: always capped, whatever route is showing.
        show(839f) { IronvellumRoot() }
        assertCappedAndCentred("rootContent", 839f)
    }

    @Test
    fun cappedDialogIsCentredAndCappedAt700And900Dp() {
        show(700f) { CappedDialog(onDismissRequest = {}) { Box(Modifier.fillMaxSize()) } }
        assertCappedAndCentred(CAPPED_DIALOG_TAG, 700f)
        compose.runOnIdle { widthDp.floatValue = 900f }
        compose.waitForIdle()
        assertCappedAndCentred(CAPPED_DIALOG_TAG, 900f)
    }

    /** The real window and density, whatever the device is: on a tablet (`adb shell wm size`) the picker sits centred. */
    @Test
    fun inkPickerIsCentredInTheRealWindow() {
        compose.setContent {
            IronvellumTheme(light = false) {
                InkPickerSheet(
                    title = "Fictional picker", onDismiss = {}, query = "", onQueryChange = {}, searchLabel = "Search", count = 3,
                ) {
                    items(3) { Text("Fictional row ${it + 1}", color = IronvellumColors.Ink, modifier = Modifier.padding(vertical = 12.dp)) }
                }
            }
        }
        val widthDp = compose.activity.window.decorView.width / compose.density.density
        assertCappedAndCentred(CAPPED_DIALOG_TAG, widthDp)
        ShotRecorder.take(compose, "tablet-ink-picker-centred")
    }

    @Test
    fun historyIsTwoPanesFrom840DpAndOneBelow() {
        show(700f) { WorkoutLogScreen(onOpenWorkout = {}, onBack = {}) }
        compose.onAllNodesWithTag("workoutLogDetailPlaceholder").assertCountEquals(0)
        compose.runOnIdle { widthDp.floatValue = 900f }
        compose.waitForIdle()
        compose.onNodeWithTag("workoutLogDetailPlaceholder").assertIsDisplayed()
    }
}
