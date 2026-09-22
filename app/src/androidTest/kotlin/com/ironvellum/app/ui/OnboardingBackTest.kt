package com.ironvellum.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.MainActivity
import com.ironvellum.app.IronvellumApp
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * System back belongs to the setup flow, not the task stack.
 *
 * The defect this pins was found on a device, not in a build: with no back
 * contract at all, one press during a weigh-in dismissed the keyboard AND
 * finished the activity, and a lifter on the training questions who wanted to
 * correct her height was thrown out of the app instead of back a step.
 *
 * Reaching the flow means standing the first-run gate back up, so the height
 * is cleared before the activity launches and restored afterwards - the rest
 * of the suite shares this database and expects a profile to exist.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingBackTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun backOnTheTrainingQuestionsReturnsToTheProfileInsteadOfLeavingTheApp() {
        compose.onNodeWithText("WHO YOU ARE").assertIsDisplayed()

        compose.onNodeWithText("Claim your name").performTextInput("Sam")
        compose.onNodeWithText("Height (cm)").performTextInput("165")
        compose.onNodeWithText("Weight (kg)").performTextInput("61")
        compose.onNodeWithText("CONTINUE").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("HOW YOU TRAIN").assertIsDisplayed()

        // The step the flow owns: back walks it, and the field focus left behind
        // by the profile form must not swallow the press.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithText("WHO YOU ARE").assertIsDisplayed()
    }

    companion object {
        private val app
            get() = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext as IronvellumApp

        @BeforeClass
        @JvmStatic
        fun standTheGateUp() = runBlocking {
            app.repository.ensureSeeded()
            app.database.profileDao().setHeight(null)
        }

        @AfterClass
        @JvmStatic
        fun letTheAppBackIn() = runBlocking {
            app.database.profileDao().setHeight(TestProfile.HEIGHT_CM)
        }
    }
}
