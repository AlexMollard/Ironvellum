package com.ironvellum.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.MainActivity
import com.ironvellum.app.data.EffortSetting
import com.ironvellum.app.data.TrialPrefs
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.domain.EffortScale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rating effort from the rest bar: tick a set, and while the rest runs tap a chip under the timer.
 * The set's RPE is stored, the chips collapse to "RPE 8 ✓" with Change, and the header shows the XP tally.
 *
 * Deterministic: the trial is planted here (three bodyweight pull-up sets, no rite, no superset), rest is
 * switched on and the effort question OFF in Settings, so the rest bar asks by default. Settings the test
 * touches are put back afterwards.
 */
@RunWith(AndroidJUnit4::class)
class RestBarEffortTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
    private lateinit var flow: FlowDriver
    private var effortWasOn = false
    private var scaleWas = EffortScale.RPE
    private var restWasOff = false

    @Before
    fun plantATrial() {
        TestProfile.ensureSetUp()
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
        effortWasOn = EffortSetting.enabled
        scaleWas = EffortSetting.scale
        EffortSetting.setEnabled(app, false)
        EffortSetting.setScale(app, EffortScale.RPE)
        val prefs = TrialPrefs(app)
        restWasOff = prefs.restOff
        prefs.restOff = false
        prefs.setRestOffFor("Pull-up", false)
        runBlocking {
            val exerciseId = checkNotNull(app.repository.exerciseIdByName("Pull-up")) { "Pull-up is seeded" }
            val sessionId = app.database.sessionDao().insertSession(
                SessionEntity(
                    presetId = null, label = "Rest bar test", startedAtMs = System.currentTimeMillis() - 10 * 60_000L,
                    completedAtMs = null, xpAwarded = 0, strengthScore = 0, title = "", note = "", privateNote = "",
                ),
            )
            app.database.sessionDao().insertSets(
                List(3) { i ->
                    SetLogEntity(
                        sessionId = sessionId, exerciseId = exerciseId, exercisePosition = 0, setIndex = i,
                        reps = 5, weightKg = null, modifiers = "", done = false,
                    )
                },
            )
        }
    }

    @After
    fun putSettingsBack() {
        EffortSetting.setEnabled(app, effortWasOn)
        EffortSetting.setScale(app, scaleWas)
        TrialPrefs(app).restOff = restWasOff
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
    }

    private fun ratedSets(): List<Int> {
        val cursor = app.database.openHelper.writableDatabase
            .query("SELECT rpe FROM set_logs WHERE done = 1 AND rpe IS NOT NULL")
        return cursor.use { c -> buildList { while (c.moveToNext()) add(c.getInt(0)) } }
    }

    @Test
    fun aChipInTheRestBarRatesTheSetJustLogged() {
        ActivityScenario.launch(MainActivity::class.java).use {
            flow = FlowDriver(compose)
            flow.takeTheClock()
            flow.awaitText("Trial in progress")

            // Tick the open exercise's next set.
            compose.onAllNodes(hasContentDescription("Log set", substring = true), useUnmergedTree = true)
                .onFirst().performSemanticsAction(SemanticsActions.OnClick)
            flow.settle()

            // The rest bar asks, with the effort question off in Settings.
            flow.awaitAnyText { it.contains("how hard?", ignoreCase = true) }
            compose.onAllNodes(hasContentDescription("RPE 8") and hasClickAction()).onFirst()
                .performSemanticsAction(SemanticsActions.OnClick)
            flow.settle()

            repeat(30) {
                if (ratedSets().isNotEmpty()) return@repeat
                Thread.sleep(100)
            }
            // Stored: RPE 8 is RIR 2.
            assertEquals(listOf(8), ratedSets())
            // The chips collapse to a quiet line that can be changed, and the header chip counts the XP.
            flow.awaitAnyText { it.contains("✓") }
            assertTrue(flow.allText().any { it == "Change" })
            assertTrue("the XP tally shows in the trial header: ${flow.allText()}", flow.allText().any { it.matches(Regex("""\+\d+ XP""")) })
        }
    }

    @Test
    fun theLastSetIsAskedAboveSlideToSeal() {
        // Sets 1 and 2 are already logged: ticking set 3 turns the bar to slide to seal, and it still asks about set 3.
        app.database.openHelper.writableDatabase.execSQL("UPDATE set_logs SET done = 1 WHERE setIndex < 2")
        ActivityScenario.launch(MainActivity::class.java).use {
            flow = FlowDriver(compose)
            flow.takeTheClock()
            flow.awaitText("Trial in progress")

            compose.onAllNodes(hasContentDescription("Log set", substring = true), useUnmergedTree = true)
                .onFirst().performSemanticsAction(SemanticsActions.OnClick)
            flow.settle()
            flow.awaitAnyText { it.contains("how hard?", ignoreCase = true) && it.contains("3") }
            compose.onAllNodes(hasContentDescription("RPE 9") and hasClickAction()).onFirst()
                .performSemanticsAction(SemanticsActions.OnClick)
            flow.settle()

            repeat(30) {
                if (ratedSets().isNotEmpty()) return@repeat
                Thread.sleep(100)
            }
            assertEquals(listOf(9), ratedSets())
            flow.awaitAnyText { it.contains("✓") }
        }
    }
}
