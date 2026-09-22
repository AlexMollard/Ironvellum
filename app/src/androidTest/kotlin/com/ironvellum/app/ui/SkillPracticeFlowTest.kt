package com.ironvellum.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.MainActivity
import com.ironvellum.app.IronvellumApp
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A logged skill attempt must show up in the journal.
 *
 * Practice is tracked separately from workouts, and the rule for this app is
 * that anything trackable has somewhere it can be seen — a counter that accepts
 * an attempt and then shows nothing is the defect this defends. The two halves
 * live in different screens (skill detail writes, Codex → Journal reads), so
 * only a flow test crossing both proves the write is readable.
 */
@RunWith(AndroidJUnit4::class)
class SkillPracticeFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val driver by lazy { FlowDriver(compose) }

    /**
     * Clears logged practice so the count starts from a known place. Goes
     * through the app's own database instance: a second handle to the same file,
     * or deleting it underneath the running app, is the divergence trap
     * documented on DbSnapshot.
     */
    @Before
    fun clearPractice() {
        // Onboarding gates the whole app until a profile height exists.
        TestProfile.ensureSetUp()
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as IronvellumApp
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM skill_practices")
    }

    @Before
    fun takeTheClock() = driver.takeTheClock()

    @Test
    fun aLoggedAttemptReachesTheJournal() {
        driver.tab("Codex")
        driver.click(driver.awaitAnyText { it == "SKILL TREE" })

        // Dead Hang is the root of the PULL line, so it is always unlocked and
        // always the first practisable skill on a fresh profile.
        driver.click(driver.awaitAnyText { it == "Dead Hang" })
        driver.awaitText("LOG AN ATTEMPT")

        // The quick-set chips fill the field; 15s is the lowest offered.
        driver.click(driver.awaitAnyText { it == "15s" })
        driver.click(driver.awaitAnyText { it == "LOG ATTEMPT" })

        // The attempt has to survive the trip to the reader, which is a
        // different screen reading a different query.
        driver.tab("Codex")
        driver.click(driver.awaitAnyText { it == "JOURNAL" })
        val record = driver.awaitAnyText { it.contains("attempts logged") || it.endsWith("attempts") }

        val logged = record.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        assertTrue(
            "a logged attempt must appear in the journal, saw \"$record\"",
            logged >= 1,
        )
    }
}
