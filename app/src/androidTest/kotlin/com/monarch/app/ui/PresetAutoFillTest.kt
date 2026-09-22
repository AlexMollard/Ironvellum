package com.monarch.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.MainActivity
import com.monarch.app.MonarchApp
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Beginning a preset must auto-fill the session with that preset's exercises.
 *
 * This is the feature the app was asked for in the first place: pick the day's
 * program and only weights and reps are left to enter. A session that opens
 * empty, or filled from the wrong preset, is the failure — and it is invisible
 * to every unit test, because the auto-fill happens when the preset's entries
 * are copied into a new session and then rendered.
 */
@RunWith(AndroidJUnit4::class)
class PresetAutoFillTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val driver by lazy { FlowDriver(compose) }

    /**
     * Clears sessions so "Begin" always starts a fresh one rather than
     * resuming. Uses the app's own database instance on purpose: a second
     * handle, or deleting the file underneath the running app, is the
     * divergence trap documented on DbSnapshot.
     */
    @Before
    fun clearSessions() {
        // Onboarding gates the whole app until a profile height exists.
        TestProfile.ensureSetUp()
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as MonarchApp
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
    }

    @Before
    fun takeTheClock() = driver.takeTheClock()

    @Test
    fun beginningAPresetFillsTheSessionWithItsExercises() {
        driver.tab("Train")

        // "Heavy Pull" belongs to the starter week, which a fresh install no
        // longer imposes - TestProfile writes it for every UI test instead.
        val preset = driver.awaitAnyText { it == "Heavy Pull" }
        val exercisesOnCard = driver.allText()

        // The preset cards are not themselves clickable: each carries its own
        // BEGIN. Presets are served in name order, so the first card is the one
        // asserted above.
        assertTrue("Heavy Pull must be the first preset card", preset == "Heavy Pull")
        driver.click(driver.awaitAnyText { it == "BEGIN" })
        driver.awaitText("TRIAL IN PROGRESS")

        // The session must carry the preset's own movements. Pull-up is the
        // first entry of Heavy Pull, so its absence means the copy did not
        // happen — or happened from the wrong preset.
        val inSession = driver.allText()
        assertTrue(
            "the session must be filled from the preset; saw: $inSession",
            inSession.any { it == "Pull-up" },
        )
        assertTrue(
            "the preset card listed Pull-up, so the session must too",
            exercisesOnCard.any { it == "Pull-up" },
        )

        // Auto-filled sets are what the hunter then edits, so the session has
        // to arrive with set rows rather than an empty shell.
        val conquered = driver.awaitAnyText { it.contains("sets conquered") }
        val total = conquered.substringAfter('/').trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        assertTrue("an auto-filled session must contain sets, saw \"$conquered\"", total > 0)
    }
}
