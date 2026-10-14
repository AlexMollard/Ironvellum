package com.ironvellum.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.domain.SetClock
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.TimedSetControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The hold clock on the live set row: a 5 s get-ready, then a count up toward a target that
 * only cues and never stops or logs, then an adjust step on Stop. Time is a variable here, so
 * nothing waits on the real clock.
 */
@RunWith(AndroidJUnit4::class)
class TimedSetControlTest {

    @get:Rule
    val compose = createComposeRule()

    private var now = 100_000L
    private var clock by mutableStateOf<SetClock?>(null)
    private val logged = mutableListOf<Int>()
    private val cues = mutableListOf<SetClock.Cue>()

    private fun show(target: Int = 10) {
        compose.mainClock.autoAdvance = false
        compose.setContent { Content(target) }
        settle()
    }

    @androidx.compose.runtime.Composable
    private fun Content(target: Int) {
        IronvellumTheme {
            TimedSetControl(
                metric = ExerciseMetric.HOLD,
                targetSec = target,
                clock = clock,
                alert = RestAlert.SILENT,
                onClock = { clock = it },
                onLog = { logged += it },
                onFill = {},
                nowMs = { now },
                onCue = { cues += it },
            )
        }
    }

    /** Moves the fake clock on and lets the control's own 100 ms tick see it. */
    private fun advance(ms: Long) {
        now += ms
        settle()
    }

    /** The control ticks on a delay loop, so the test clock is advanced by hand, never auto. */
    private fun settle() = compose.mainClock.advanceTimeBy(250)

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        settle()
    }

    private fun start() = click("Start")

    @Test
    fun getReadyLastsFiveSecondsThenCountsUpFromZero() {
        show()
        start()
        compose.onNodeWithText("GET READY").assertExists()
        compose.onNodeWithContentDescription("Get ready, 5").assertExists()
        advance(2_000)
        compose.onNodeWithContentDescription("Get ready, 3").assertExists()
        advance(2_700)
        compose.onNodeWithText("GET READY").assertExists()
        advance(500)
        compose.onNodeWithText("GET READY").assertDoesNotExist()
        compose.onNodeWithContentDescription("0:00 elapsed, target 0:10").assertExists()
        advance(4_000)
        compose.onNodeWithContentDescription("0:04 elapsed, target 0:10").assertExists()
    }

    @Test
    fun stoppingDuringGetReadyRecordsNothingAndGoesIdle() {
        show()
        start()
        advance(2_000)
        click("Stop")
        assertNull(clock)
        assertTrue(logged.isEmpty())
        compose.onNodeWithText("Start").assertExists()
    }

    @Test
    fun reachingTheTargetCuesOnceAndKeepsCountingWithoutLogging() {
        show()
        start()
        advance(5_000)
        advance(10_000)
        compose.onNodeWithText("TARGET REACHED", substring = true).assertExists()
        compose.onNodeWithContentDescription("0:10 elapsed, target 0:10 reached").assertExists()
        advance(20_000)
        compose.onNodeWithContentDescription("0:30 elapsed, target 0:10 reached").assertExists()
        assertEquals("one cue at the go and one at the target", 1, cues.count { it == SetClock.Cue.DONE })
        assertTrue("it never logs by itself", logged.isEmpty())
        assertTrue(clock!!.running)
    }

    @Test
    fun adjustChangesTheLoggedFigureAndHasAOneSecondFloor() {
        show()
        start()
        advance(5_000)
        advance(12_000)
        click("Stop")
        compose.onNodeWithText("Log 0:12").assertExists()
        click("+1s")
        compose.onNodeWithText("Log 0:13").assertExists()
        click("−1s")
        click("−1s")
        compose.onNodeWithText("Log 0:11").assertExists()
        click("Log 0:11")
        assertEquals(listOf(11), logged)
        assertNull(clock)
    }

    @Test
    fun theAdjustFloorIsOneSecond() {
        show()
        start()
        advance(5_000)
        advance(3_000)
        click("Stop")
        repeat(6) { click("−1s") }
        compose.onNodeWithText("Log 0:01").assertExists()
        click("Log 0:01")
        assertEquals(listOf(1), logged)
    }

    @Test
    fun discardLogsNothingAndGoesIdle() {
        show()
        start()
        advance(5_000)
        advance(12_000)
        click("Stop")
        click("Discard")
        assertTrue(logged.isEmpty())
        assertNull(clock)
        compose.onNodeWithText("Start").assertExists()
    }

    @Test
    fun rotatingMidCountKeepsTheElapsedTimeAndDoesNotRepeatACue() {
        val restoration = StateRestorationTester(compose)
        compose.mainClock.autoAdvance = false
        restoration.setContent { Content(10) }
        settle()
        start()
        advance(5_000)
        advance(14_000)
        compose.onNodeWithContentDescription("0:14 elapsed, target 0:10 reached").assertExists()
        val before = cues.toList()
        restoration.emulateSavedInstanceStateRestore()
        settle()
        compose.onNodeWithContentDescription("0:14 elapsed, target 0:10 reached").assertExists()
        assertEquals("a rotation plays no cue again", before, cues.toList())
    }
}
