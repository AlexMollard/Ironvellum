package com.ironvellum.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import com.ironvellum.app.data.WarmupStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Marking a rite's first exercise as a warm-up in the editor, then beginning the rite: the exercise is gone from the
 * trial's sets and sits in the warm-up step as a tickable item, and ticking it is remembered for the trial.
 */
@RunWith(AndroidJUnit4::class)
class WarmupExerciseFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val driver by lazy { FlowDriver(compose) }

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
        driver.takeTheClock()
    }

    @Test
    fun markingAnEntryAsAWarmupPutsItInTheTrialsWarmupStepToBeTicked() {
        driver.tab("Train")
        driver.click(driver.awaitAnyText { it == "Heavy Pull" })
        driver.awaitAnyText { it == "Begin Heavy Pull" }
        // The rite's first movement is the pull-up; the rite page says so before and "Warm-up" after.
        driver.click("Edit")
        driver.awaitText("Edit rite")
        // The editor loads the rite asynchronously.
        driver.awaitAnyText { it == "Pull-up" }
        driver.click("Pull-up")
        driver.awaitAnyText { it == "Warm-up" }
        driver.click("Warm-up")
        driver.settle()
        driver.click("Save rite")
        driver.awaitAnyText { it == "Begin Heavy Pull" }
        // The page reloads the saved rite a beat after its Begin button is back; waiting for the heading, not sampling it, keeps this from racing the reload.
        driver.awaitAnyText { it == "WARM-UP" }

        driver.click("Begin Heavy Pull")
        driver.awaitText("Trial in progress")
        // The step lists the drill; it is no longer a block of sets, so its name shows once, in the step.
        driver.awaitAnyText { it == "WARM UP" }
        assertEquals("the pull-up is a warm-up item, not a block of sets", 1, driver.allText().count { it == "Pull-up" })

        val sessionId = app.database.openHelper.writableDatabase.query("SELECT id FROM sessions WHERE completedAtMs IS NULL").use { c ->
            assertTrue(c.moveToFirst())
            c.getLong(0)
        }
        val pullUp = app.database.openHelper.writableDatabase.query("SELECT id FROM exercises WHERE name = 'Pull-up'").use { c ->
            assertTrue(c.moveToFirst())
            c.getLong(0)
        }
        val store = WarmupStore(app)
        assertFalse(pullUp in store.tickedFor(sessionId))
        driver.click("Pull-up")
        driver.settle()
        assertTrue("ticking it is remembered for the trial", pullUp in store.tickedFor(sessionId))
        // And it is no set: the trial's own count never mentions it.
        app.database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM set_logs WHERE exerciseId = $pullUp").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }
}
