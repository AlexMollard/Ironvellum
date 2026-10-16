package com.ironvellum.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import com.ironvellum.app.data.Words
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings > Health Connect offers saving finished workouts back, off until the lifter turns it on. Turning it on
 * opens Health Connect's own permission dialog, which belongs to the provider and may not exist on this emulator,
 * so the test looks at the page as it opens and never taps the switch.
 */
@RunWith(AndroidJUnit4::class)
class HealthConnectSettingsTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun openTheHealthConnectPage() {
        TestProfile.ensureSetUp()
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
        app.healthWriteBack.setEnabled(false)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        compose.onNode(hasText("Health Connect") and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
    }

    @Test
    fun savingFinishedWorkoutsIsOffByDefaultAndSaysWhatItWrites() {
        compose.onNodeWithText("Save ${Words.sealed.s} ${Words.trial.pl} to Health Connect").assertIsDisplayed()
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText("public note", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Writes only what you turn on below.", substring = true).assertIsDisplayed()
    }

    private companion object {
        const val FRAME_BUDGET_MS = 1_200L
    }
}
