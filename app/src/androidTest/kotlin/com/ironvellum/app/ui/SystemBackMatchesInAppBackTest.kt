package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.ui.social.AuthPanels
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * System back must do what the visible in-app back does on the same screen in the same state.
 * Pattern covered: a local step state that the in-app control walks back (the sign-in form's mode link).
 */
@RunWith(AndroidJUnit4::class)
class SystemBackMatchesInAppBackTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun showForm() {
        compose.activityRule.scenario.onActivity {
            it.setContent {
                IronvellumTheme {
                    AuthPanels(
                        error = null, notice = null, busy = false, googleEnabled = false,
                        onGoogleSignIn = { _, _ -> }, onSignIn = { _, _ -> }, onSignUp = { _, _, _ -> },
                        resetCodeSentTo = null, onSendResetCode = {}, onResetPassword = { _, _, _ -> },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun systemBackStepsTheSignInFormBackExactlyAsItsLinkDoes() {
        showForm()
        // In-app: the link under the form returns from sign-up to sign-in.
        compose.onNodeWithText("New here? Create an account").performClick()
        compose.onNodeWithText("Have an account? Sign in").assertIsDisplayed()
        compose.onNodeWithText("Have an account? Sign in").performClick()
        compose.onNodeWithText("New here? Create an account").assertIsDisplayed()

        // System back from sign-up lands on the same form instead of leaving the screen.
        compose.onNodeWithText("New here? Create an account").performClick()
        compose.onNodeWithText("Have an account? Sign in").assertIsDisplayed()
        compose.waitForIdle()
        var armed = false
        compose.activityRule.scenario.onActivity { armed = it.onBackPressedDispatcher.hasEnabledCallbacks() }
        assertTrue("a back callback is armed while the form is on sign-up", armed)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("New here? Create an account").assertIsDisplayed()
    }
}
