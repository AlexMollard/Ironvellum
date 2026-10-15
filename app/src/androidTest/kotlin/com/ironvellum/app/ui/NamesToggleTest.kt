package com.ironvellum.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import com.ironvellum.app.data.Words
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The names switch applies at once: the words on screen change with no restart, from the Settings row, and the
 * first-run decision is stored so a later launch never changes it. Every other test runs themed (the listener
 * starts each one that way); this one puts the mode back when it is done.
 */
@RunWith(AndroidJUnit4::class)
class NamesToggleTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp

    @Before
    fun takeTheClock() {
        TestProfile.ensureSetUp()
        // The ink animations never idle; drive the clock by hand.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
    }

    @After
    fun themedAgain() {
        Words.setThemed(app, true)
    }

    private fun setMode(themed: Boolean) {
        compose.runOnUiThread { Words.setThemed(app, themed) }
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
    }

    @Test
    fun flippingTheModeRelabelsTheNavBarWithoutARestart() {
        setMode(themed = true)
        compose.onNodeWithContentDescription("Ledger").assertExists()
        compose.onNodeWithContentDescription("Codex").assertExists()
        compose.onNodeWithContentDescription("Allies").assertExists()

        setMode(themed = false)
        compose.onNodeWithContentDescription("Stats").assertExists()
        compose.onNodeWithContentDescription("Progress").assertExists()
        compose.onNodeWithContentDescription("Friends").assertExists()
        compose.onNodeWithContentDescription("Ledger").assertDoesNotExist()

        setMode(themed = true)
        compose.onNodeWithContentDescription("Ledger").assertExists()
    }

    @Test
    fun theSettingsSwitchFlipsTheModeBothWays() {
        setMode(themed = true)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        compose.onNode(hasText("Appearance") and hasClickAction()).performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)

        compose.onNodeWithText("Themed names").assertIsDisplayed()
        compose.onNodeWithText("Themed names").performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        assertFalse(Words.themed)

        compose.onNodeWithText("Themed names").performClick()
        compose.mainClock.advanceTimeBy(FRAME_BUDGET_MS)
        assertTrue(Words.themed)
    }

    @Test
    fun theFirstRunDecisionIsStoredAndNeverChangesAfterwards() {
        val prefs = app.getSharedPreferences(Words.PREFS, Context.MODE_PRIVATE)
        val before = if (prefs.contains(Words.KEY)) prefs.getBoolean(Words.KEY, true) else null
        try {
            prefs.edit { remove(Words.KEY) }
            // A fresh install: no database, no welcome seen yet.
            Words.init(app, databaseExists = false, welcomeSeen = false)
            assertFalse(Words.themed)
            assertTrue(prefs.contains(Words.KEY))
            // The database exists by the next launch, but the stored answer stands.
            Words.init(app, databaseExists = true, welcomeSeen = true)
            assertFalse(Words.themed)

            // An install from before the setting existed keeps the themed names.
            prefs.edit { remove(Words.KEY) }
            Words.init(app, databaseExists = true, welcomeSeen = false)
            assertTrue(Words.themed)
        } finally {
            prefs.edit { if (before == null) remove(Words.KEY) else putBoolean(Words.KEY, before) }
        }
    }

    private companion object {
        const val FRAME_BUDGET_MS = 1_200L
    }
}
