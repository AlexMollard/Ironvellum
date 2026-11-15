package com.ironvellum.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.MainActivity
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        driver.click(driver.awaitAnyText { it == "PATHS" })

        // PATHS opens on the grid of paths; Pull is the first tile. Dead Hang is
        // its root, so it is always unlocked and always the first practisable
        // skill on a fresh profile.
        driver.click(driver.awaitAnyText { it == "Pull" })
        driver.click(driver.awaitAnyText { it == "Dead Hang" })
        // The attempt form is on the sheet's LOG page.
        driver.click(driver.awaitAnyText { it == "LOG" })
        driver.awaitText("LOG AN ATTEMPT")

        // The quick-set chips fill the field; 15s is the lowest offered.
        driver.click(driver.awaitAnyText { it == "15s" })
        driver.click(driver.awaitAnyText { it == "Log attempt" })

        // The attempt has to survive the trip to the reader, which is a
        // different screen reading a different query.
        driver.tab("Codex")
        driver.click(driver.awaitAnyText { it == "JOURNAL" })
        // The timeline lists the attempt under its day.
        driver.awaitAnyText { it == "Dead Hang" }
    }

    @Test
    fun workoutAttemptsAppearInSkillDetailsWithoutReloggingPractice() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
        val dao = app.database.sessionDao()
        val id = runBlocking {
            val exercise = checkNotNull(app.database.exerciseDao().byName("Dead Hang"))
            val now = System.currentTimeMillis()
            val session = dao.insertSession(SessionEntity(presetId = null, label = "Skill evidence", startedAtMs = now - 60_000, completedAtMs = now, xpAwarded = 0))
            dao.insertSets(listOf(
                SetLogEntity(sessionId = session, exerciseId = exercise.id, setIndex = 0, reps = 0, durationSec = 37, weightKg = null, modifiers = "", done = true),
                SetLogEntity(sessionId = session, exerciseId = exercise.id, setIndex = 1, reps = 0, durationSec = 23, weightKg = null, modifiers = "", done = true),
            ))
            session
        }
        try {
            driver.tab("Codex")
            driver.click(driver.awaitAnyText { it == "PATHS" })
            driver.click(driver.awaitAnyText { it == "Pull" })
            driver.click(driver.awaitAnyText { it == "Dead Hang" })
            ShotRecorder.take(compose, "skill-exercise-about")
            driver.click(driver.awaitAnyText { it == "LOG" })
            driver.awaitText("Trial · 37s")
            driver.awaitText("Trial · 23s")
            ShotRecorder.take(compose, "skill-workout-attempts")
            val count = app.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM skill_practices").use {
                it.moveToFirst()
                it.getInt(0)
            }
            assertEquals("reading workout evidence must not insert practice", 0, count)
        } finally {
            runBlocking {
                dao.deleteSetsFor(id)
                dao.deleteCompleted(id)
            }
        }
    }
}
