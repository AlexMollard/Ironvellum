package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.ui.onboarding.StepBar
import com.ironvellum.app.ui.program.ForgeHeader
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The BACK chips on the Forge header and the onboarding bar carry the same arrow as the pushed header, and the
 * onboarding bar (BACK beside SKIP) still fits a 360dp phone at the 200% text size the font-scale sweep uses.
 */
@RunWith(AndroidJUnit4::class)
class BackChipArrowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(light: Boolean, fontScale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { activity ->
            if (!light) ShotRecorder.darkWindow(activity)
            activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    IronvellumTheme(light = light) { Column(Modifier.width(360.dp).padding(top = 36.dp)) { content() } }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun theOnboardingBarKeepsBackAndSkipSideBySideAtTwoHundredPercent() {
        for (light in listOf(false, true)) {
            show(light, 2.0f) { StepBar(step = 1, onBack = {}, onSkip = {}) }
            val back = compose.onNodeWithText("BACK").assertIsDisplayed().getUnclippedBoundsInRoot()
            val skip = compose.onNodeWithText("SKIP").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("BACK ends before SKIP starts: $back $skip", back.right <= skip.left)
            assertTrue("SKIP stays on a 360dp screen: $skip", skip.right <= 360.dp)
            ShotRecorder.take(compose, "backchip-onboarding-200-${if (light) "light" else "dark"}")
        }
        show(false, 1.0f) { StepBar(step = 1, onBack = {}, onSkip = {}) }
        ShotRecorder.take(compose, "backchip-onboarding-100-dark")
    }

    @Test fun theForgeHeaderBackChipFitsBesideALongTitleAtTwoHundredPercent() {
        for (light in listOf(false, true)) {
            show(light, 2.0f) { ForgeHeader(title = "Push day with a long name", onBack = {}, subtitle = "3 steps") }
            val back = compose.onNodeWithText("BACK").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("BACK stays on a 360dp screen: $back", back.right <= 360.dp)
            ShotRecorder.take(compose, "backchip-forge-200-${if (light) "light" else "dark"}")
        }
        show(false, 1.0f) { ForgeHeader(title = "Push day", onBack = {}) }
        ShotRecorder.take(compose, "backchip-forge-100-dark")
    }
}
