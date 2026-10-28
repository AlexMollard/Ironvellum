package com.ironvellum.app.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FatigueRestPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aLoneRestButtonTakesTheWholeRow() {
        compose.setContent {
            IronvellumTheme {
                Box(Modifier.width(320.dp)) {
                    FatigueRestPanel("Rest today", "Your muscles need recovery", null, null, {}, {})
                }
            }
        }
        // 320dp less the panel's 15dp and 12dp side padding.
        val width = compose.onNode(hasClickAction()).getUnclippedBoundsInRoot().let { it.right - it.left }
        assertTrue("the lone Rest button is only $width wide", width >= 290.dp)
        ShotRecorder.take(compose, "fatigue-rest-lone-button")
    }

    @Test fun alternateWorkoutLabelFitsOnOneLine() {
        compose.setContent {
            IronvellumTheme {
                Box(Modifier.width(320.dp)) {
                    FatigueRestPanel("Rest today", "Your muscles need recovery", null, "Do Push Strength", {}, {})
                }
            }
        }
        val layouts = ArrayList<TextLayoutResult>()
        compose.onNodeWithText("Do Push Strength").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        assertEquals(1, layouts.single().lineCount)
        ShotRecorder.take(compose, "fatigue-rest-buttons")
    }
}
