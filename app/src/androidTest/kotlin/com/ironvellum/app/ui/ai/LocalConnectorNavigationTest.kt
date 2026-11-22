package com.ironvellum.app.ui.ai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ironvellum.app.MainActivity
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.TestProfile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocalConnectorNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val oldWording = Words.themed

    @Before fun setup() {
        TestProfile.ensureSetUp()
        compose.activityRule.scenario.onActivity { Words.setThemed(it, false) }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_200)
    }

    @After fun resetWording() {
        compose.activityRule.scenario.onActivity { Words.setThemed(it, oldWording) }
    }

    @Test fun signedOutTrainOpensLocalToolsAndCloudIsAnOptionalDestination() {
        compose.onNodeWithContentDescription("Train").performClick()
        compose.mainClock.advanceTimeBy(1_200)
        shot("train_dark_plain")
        compose.onNodeWithText("AI tools").performScrollTo().assertIsDisplayed().performClick()
        shown("Share training")
        shown("Import a workout draft")
        shot("tools_dark_plain")
        compose.onNodeWithText("Optional cloud services").performScrollTo().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(8_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Cloud AI connections").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Cloud AI connections").performScrollTo().performClick()
        val status = if (Cloud.configured) R.string.ai_signed_out else R.string.ai_unconfigured
        shown(compose.activity.getString(status))
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1_200)
        shown("Preview training file").performClick()
        compose.waitUntil(8_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Save on this phone").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Save on this phone").assertIsDisplayed()
    }

    private fun shown(text: String) = compose.onNodeWithText(text).also { node ->
        compose.waitUntil(8_000) {
            compose.mainClock.advanceTimeByFrame()
            node.isDisplayed()
        }
        node.assertIsDisplayed()
    }

    private fun shot(name: String) = ShotRecorder.take(compose, "local_connector_$name")
}
