package com.ironvellum.app.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun openSettings() {
        TestProfile.ensureSetUp()
        compose.mainClock.autoAdvance = false
        compose.waitUntil(timeoutMillis = 10_000) {
            advance()
            compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        advance()
    }

    @Test fun presetsPersistWhenReturningFromAppearance() {
        compose.onNode(hasText("Appearance") and hasClickAction()).performClick()
        advance()
        compose.onNodeWithText("Sapphire & Amber").performClick()
        advance()
        compose.onNodeWithText("BACK").performClick()
        advance()
        compose.onNodeWithText("Sapphire & Amber").assertIsDisplayed()
        compose.onNode(hasText("Appearance") and hasClickAction()).performClick()
        advance()
        compose.onNodeWithText("Reset to emerald & gold").performScrollTo().performClick()
        advance()
    }

    @Test fun invalidCustomHexCannotReplaceSavedPalette() {
        compose.onNode(hasText("Appearance") and hasClickAction()).performClick()
        advance()
        compose.onNodeWithText("Reset to emerald & gold").performScrollTo().performClick()
        advance()
        compose.onNodeWithText("Primary accent").performScrollTo().performClick()
        advance()
        compose.onNodeWithTag("accent-hex").performTextReplacement("#ZZZZZZ")
        advance()
        compose.onNodeWithText("Enter a six-digit hex colour").assertIsDisplayed()
        compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        advance()
        compose.onNodeWithText("BACK").performClick()
        advance()
        compose.onNodeWithText("Emerald & Gold").assertIsDisplayed()
    }

    @Test fun hueChoiceSurvivesZeroBrightnessAndSaturation() {
        compose.onNode(hasText("Appearance") and hasClickAction()).performClick()
        advance()
        compose.onNodeWithText("Primary accent").performScrollTo().performClick()
        advance()
        compose.onNodeWithTag("accent-hex").performTextReplacement("#000000")
        advance()
        compose.onNodeWithContentDescription("Hue").performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        advance()
        repeat(20) {
            val actions = compose.onNodeWithContentDescription("Saturation and brightness")
                .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            compose.runOnIdle { actions.first { it.label == "Increase saturation" }.action() }
            advance()
            val updatedActions = compose.onNodeWithContentDescription("Saturation and brightness")
                .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            compose.runOnIdle { updatedActions.first { it.label == "Increase brightness" }.action() }
            advance()
        }
        compose.onNodeWithTag("accent-hex").assert(hasText("#00FF00"))
        compose.onNodeWithText("Apply").performClick()
        advance()
        compose.onNodeWithText("#00FF00").assertIsDisplayed()
        compose.onNodeWithText("Reset to emerald & gold").performScrollTo().performClick()
        advance()
    }
    @Test fun padTapAndDragChoosesSaturationAndBrightnessTogether() {
        compose.onNode(hasText("Appearance") and hasClickAction()).performClick()
        advance()
        compose.onNodeWithText("Primary accent").performScrollTo().performClick()
        advance()
        compose.onNodeWithTag("accent-hex").performTextReplacement("#000000")
        advance()
        compose.onNodeWithContentDescription("Hue").performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        advance()
        compose.onNodeWithContentDescription("Saturation and brightness").performTouchInput { click(center) }
        advance()
        compose.onNodeWithTag("accent-hex").assert(hasText("#408040"))
        compose.onNodeWithContentDescription("Saturation and brightness").performTouchInput {
            swipe(center, androidx.compose.ui.geometry.Offset(width - 0.1f, center.y))
        }
        advance()
        compose.onNodeWithTag("accent-hex").assert(hasText("#008000"))
        compose.onNodeWithText("Apply").performClick()
        advance()
        compose.onNodeWithText("#008000").assertIsDisplayed()
        compose.onNodeWithText("Reset to emerald & gold").performScrollTo().performClick()
        advance()
    }
    private fun advance() = compose.mainClock.advanceTimeBy(1_200L)
}
