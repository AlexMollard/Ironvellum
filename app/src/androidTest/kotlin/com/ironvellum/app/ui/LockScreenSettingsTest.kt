package com.ironvellum.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.MainActivity
import com.ironvellum.app.data.TrialPrefs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings > During a workout > Lock screen and shortcuts: it opens from the During-a-workout page (the hub is full),
 * shows the live-update and lock-screen switches and the tile and shortcut rows, and the lifts switch writes the
 * device setting the notification reads. Android 16's own promotion switch is not driven here.
 */
@RunWith(AndroidJUnit4::class)
class LockScreenSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = TrialPrefs(context)

    @Before fun clean() {
        context.getSharedPreferences("trial_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun restore() {
        context.getSharedPreferences("trial_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun advance() = compose.mainClock.advanceTimeBy(1_200L)

    private fun openPage() {
        TestProfile.ensureSetUp()
        compose.mainClock.autoAdvance = false
        compose.waitUntil(timeoutMillis = 10_000) {
            advance()
            compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        advance()
        compose.onNode(hasText("During a trial") and hasClickAction()).performClick()
        advance()
        compose.onNode(hasText("Lock screen and shortcuts") and hasClickAction()).performScrollTo().performClick()
        advance()
    }

    @Test fun thePageShowsItsRowsAndTheLiftsSwitchWritesTheSetting() {
        openPage()
        compose.onNodeWithText("Live trial update").assertIsDisplayed()
        compose.onNodeWithText("Show lifts on the lock screen").assertIsDisplayed()
        compose.onNodeWithText("Rest alert").assertIsDisplayed()
        compose.onNodeWithText("Quick Settings tile").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("App-icon shortcuts").performScrollTo().assertIsDisplayed()
        assertTrue("on by default, as the notification always was", prefs.showLiftsOnLock)

        compose.onNodeWithText("Show lifts on the lock screen").performClick()
        advance()
        assertFalse(prefs.showLiftsOnLock)
        ShotRecorder.take(compose, "lock-screen-settings")
    }

    @Test fun theRestAlertRowReturnsToTheRestSettings() {
        openPage()
        compose.onNode(hasText("Rest alert") and hasClickAction()).performClick()
        advance()
        compose.onNodeWithText("WHEN A REST ENDS").assertIsDisplayed()
    }

    @Test fun restDefaultsCanBeSetAndReset() {
        openPage()
        compose.onNode(hasText("Rest alert") and hasClickAction()).performClick()
        advance()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Custom compound rest").performScrollTo().performClick()
        advance()
        val compound = requireNotNull(prefs.compoundRest)
        compose.onNodeWithContentDescription("Increase rest duration").performScrollTo().performClick()
        assertTrue(prefs.compoundRest == compound + 15)
        compose.onNodeWithContentDescription("Decrease rest duration").performScrollTo().performClick()
        assertTrue(prefs.compoundRest == compound)
        ShotRecorder.take(compose, "rest-category-defaults")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Custom compound rest").performScrollTo().performClick()
        assertTrue(prefs.compoundRest == null)
        assertTrue(com.ironvellum.app.domain.RestRules.resolved("Bench Press", com.ironvellum.app.domain.TrainingFocus.MUSCLE, preferences = prefs.restPreferences("Bench Press")) ==
            com.ironvellum.app.domain.RestRules.window("Bench Press", com.ironvellum.app.domain.TrainingFocus.MUSCLE))
        compose.onNodeWithText("Custom isolation rest").performScrollTo().performClick()
        val isolation = requireNotNull(prefs.isolationRest)
        compose.onNodeWithContentDescription("Increase rest duration").performScrollTo().performClick()
        assertTrue(prefs.isolationRest == isolation + 15)
        compose.onNodeWithContentDescription("Decrease rest duration").performScrollTo().performClick()
        assertTrue(prefs.isolationRest == isolation)
        compose.onNodeWithText("Custom isolation rest").performScrollTo().performClick()
        assertTrue(prefs.isolationRest == null)
        assertTrue(com.ironvellum.app.domain.RestRules.resolved("Lateral Raise", com.ironvellum.app.domain.TrainingFocus.MUSCLE, preferences = prefs.restPreferences("Lateral Raise")) ==
            com.ironvellum.app.domain.RestRules.window("Lateral Raise", com.ironvellum.app.domain.TrainingFocus.MUSCLE))
    }
}
