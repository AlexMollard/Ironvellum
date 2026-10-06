package com.ironvellum.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.BuildConfig
import com.ironvellum.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings is a hub of rows, each opening one sub-screen. Every row must be on
 * screen without scrolling (the hub's whole point is fitting one phone
 * screen), and a row must open its sub-screen with BACK returning to the hub.
 */
@RunWith(AndroidJUnit4::class)
class SettingsHubTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun takeTheClock() {
        TestProfile.ensureSetUp()
        // The ink animations never idle; drive the clock by hand.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
    }

    private fun row(label: String) = compose.onNode(hasText(label) and hasClickAction())

    @Test
    fun hubShowsEveryRowWithoutScrolling() {
        val rows = listOf(
            "Profile", "Account", "Training mode", "Armoury", "The Summons",
            "Health Connect", "Data", "Advanced",
            if (BuildConfig.SUPPORT_LINKS) "Support Ironvellum" else "About Ironvellum",
        )
        rows.forEach { row(it).assertIsDisplayed() }
        compose.onNodeWithText("readings stay on this device", substring = true).assertIsDisplayed()
    }

    @Test
    fun aRowOpensItsSubScreenAndBackReturnsToTheHub() {
        row("Profile").performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        compose.onNodeWithText("Your name").assertIsDisplayed()
        compose.onNodeWithText("Height (cm)").assertIsDisplayed()

        compose.onNodeWithText("BACK").performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        row("Training mode").assertIsDisplayed()
    }

    private companion object {
        const val FRAME_BUDGET_MS = 1_200L
    }
}
