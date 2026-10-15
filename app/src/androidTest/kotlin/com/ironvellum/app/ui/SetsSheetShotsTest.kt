package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.EffortSetting
import com.ironvellum.app.domain.EffortScale
import com.ironvellum.app.domain.LiftLoading
import com.ironvellum.app.domain.SchemeType
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.SetScheme
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.EditorEntry
import com.ironvellum.app.ui.train.SetsSheet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Sets sheet in each of its schemes, for a loaded barbell lift and a bodyweight one: every variant must compose and
 * stay usable, and with `-Pandroid.testInstrumentationRunnerArguments.shots=true` each is photographed
 * (see [ShotRecorder]) for a human to compare against the mockup.
 */
@RunWith(AndroidJUnit4::class)
class SetsSheetShotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val bench = EditorEntry(1, "Bench Press", sets = "3", reps = "8", weight = "100", modifiers = "")
    private val barbell = LiftLoading(stepKg = 2.5, bodyweightKg = 80.0)
    private val pullUp = EditorEntry(2, "Pull-Up", sets = "3", reps = "6", weight = "", modifiers = "")
    private val bodyweight = LiftLoading(stepKg = 2.5, bodyweightKg = 80.0, bodyweightLift = true)

    @After
    fun effortOff() {
        EffortSetting.setEnabled(context, false)
        EffortSetting.setScale(context, EffortScale.RPE)
    }

    private fun show(entry: EditorEntry, lift: LiftLoading, inSuperset: Boolean = false) {
        compose.activityRule.scenario.onActivity { activity ->
            ShotRecorder.darkWindow(activity)
            activity.setContent {
                IronvellumTheme {
                    SetsSheet(entry = entry, lift = lift, worn = null, inSuperset = inSuperset, onDone = {}, onDismiss = {})
                }
            }
        }
        compose.onNodeWithText("Sets for ${entry.exerciseName}").assertExistsAfterSettle()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsAfterSettle() {
        compose.waitForIdle()
        assertExists()
    }

    /** The sheet as it opens, then scrolled to the link under the cards. */
    private fun shoot(name: String, link: String = "Rest, warm-up ramp") {
        ShotRecorder.take(compose, "sets_${name}_a")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(link).performScrollTo()
        ShotRecorder.take(compose, "sets_${name}_b")
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun straightBench() {
        show(bench, barbell)
        shoot("straight_bench", "Warm-up ramp")
    }

    @Test
    fun straightBenchWithARange() {
        show(bench.copy(scheme = SetScheme(workingReps = 8..12)), barbell)
        shoot("straight_bench_range", "Warm-up ramp")
    }

    @Test
    fun topAndBackoffsBench() {
        show(bench.copy(sets = "3", scheme = SetScheme(type = SchemeType.TOP_BACKOFF)), barbell)
        shoot("top_bench")
        compose.onNodeWithText("Rest, warm-up ramp").performClick()
        compose.onNodeWithText("Rest after the top set").performScrollTo()
        ShotRecorder.take(compose, "sets_top_bench_more")
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun topAndBackoffsFixedBench() {
        show(bench.copy(sets = "4", scheme = SetScheme(type = SchemeType.TOP_BACKOFF, backoffSets = 3, backoffFixedKg = 80.0)), barbell)
        shoot("top_bench_fixed")
    }

    @Test
    fun topAndBackoffsWithRpe() {
        EffortSetting.setEnabled(context, true)
        EffortSetting.setScale(context, EffortScale.RPE)
        show(bench.copy(scheme = SetScheme(type = SchemeType.TOP_BACKOFF, topRir = 1..2)), barbell)
        shoot("top_bench_rpe")
    }

    @Test
    fun dropsBench() {
        show(bench.copy(scheme = SetScheme(type = SchemeType.DROPS, dropSets = 1, dropCount = 2)), barbell)
        shoot("drops_bench", "Warm-up ramp")
    }

    @Test
    fun manyDropsBench() {
        show(bench.copy(sets = "4", scheme = SetScheme(type = SchemeType.DROPS, dropSets = 3, dropCount = 2)), barbell)
        shoot("drops_many", "Warm-up ramp")
    }

    @Test
    fun customBench() {
        val custom = listOf(SetKind.WORKING, SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF, SetKind.DROP)
        show(bench.copy(sets = "4", scheme = SetScheme(type = SchemeType.CUSTOM, custom = custom)), barbell)
        shoot("custom_bench", "Warm-up ramp")
    }

    @Test
    fun straightPullUpReadsBodyweight() {
        show(pullUp, bodyweight)
        shoot("straight_pullup", "Warm-up ramp")
    }

    @Test
    fun topAndBackoffsPullUp() {
        show(pullUp.copy(scheme = SetScheme(type = SchemeType.TOP_BACKOFF)), bodyweight)
        shoot("top_pullup")
    }

    @Test
    fun topAndBackoffsWeightedPullUp() {
        show(pullUp.copy(weight = "22", scheme = SetScheme(type = SchemeType.TOP_BACKOFF)), bodyweight)
        shoot("top_pullup_weighted")
    }

    @Test
    fun dropsAreRefusedInASuperset() {
        show(bench.copy(supersetGroup = 1), barbell, inSuperset = true)
        compose.onNodeWithText("Drops").assertIsNotEnabled()
        shoot("superset_bench", "Warm-up ramp")
    }
}
