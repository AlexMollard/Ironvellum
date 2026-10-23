package com.ironvellum.app.ui.program

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.FatigueModel
import com.ironvellum.app.domain.FatigueSession
import com.ironvellum.app.domain.LoggedSet
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumTheme
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tapping a muscle opens its breakdown; a cause row opens that lift on the variation it was logged with. */
@RunWith(AndroidJUnit4::class)
class MuscleBreakdownTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    private val hour = 3_600_000L
    private val now = 1_792_000_000_000L
    private val long = now - 400 * 24 * hour

    private fun sets(name: String, mods: String, count: Int, at: Long) =
        List(count) { LoggedSet(name, mods, 100.0, 5, 9, SetKind.WORKING, true, at) }

    private fun session(id: Long, hoursAgo: Long, name: String, mods: String, count: Int) =
        FatigueSession(id, now - hoursAgo * hour, sets(name, mods, count, now - hoursAgo * hour))

    private val opened = mutableListOf<Pair<String, String>>()

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        opened.clear()
        driver.takeTheClock()
    }

    private fun show(vararg sessions: FatigueSession): androidx.compose.runtime.MutableState<Muscle?> {
        val selection = mutableStateOf<Muscle?>(null)
        val ui = FatigueUi(FatigueModel.estimate(sessions.toList(), now, long), hasHistory = true, loaded = true)
        compose.activityRule.scenario.onActivity {
            it.setContent {
                IronvellumTheme { FatigueContent(ui, selection, ZoneId.of("UTC")) { n, v -> opened += n to v } }
            }
        }
        driver.settle()
        return selection
    }

    private fun light(selection: androidx.compose.runtime.MutableState<Muscle?>, muscle: Muscle) {
        compose.runOnUiThread { selection.value = muscle }
        driver.settle()
    }

    @Test
    fun aRowOpensTheBreakdownWithCausesSorenessAndTheEstimateLink() {
        show(session(1, 40, "Romanian Deadlift", "", 4), session(2, 20, "Back Squat", "", 5))
        driver.awaitText("MOST WORKED")
        driver.click("Hamstrings")
        driver.awaitText("What it came from")
        val text = driver.allText()
        assertTrue(text.toString(), text.any { it.startsWith("about ") && it.endsWith("%") })
        assertTrue(text.any { it.startsWith("Soreness likely") || it == "Soreness likely" })
        assertTrue(text.any { it.contains("was new to you") })
        assertTrue(text.any { it.contains("How this is estimated") })
        assertTrue(text.any { it.startsWith("Back to light") })
    }

    @Test
    fun aCauseRowOpensTheLiftOnItsVariation() {
        val selection = show(session(1, 20, "Bench Press", "incline, deficit", 4))
        light(selection, Muscle.UPPER_CHEST)
        driver.awaitText("What it came from")
        driver.click("Bench Press · deficit, incline")
        assertEquals(listOf("Bench Press" to "deficit,incline"), opened)
    }

    @Test
    fun theExplainerStatesSorenessIsNoQualitySignal() {
        val selection = show(session(1, 20, "Back Squat", "", 5))
        light(selection, Muscle.QUADS)
        driver.awaitText("What it came from")
        driver.click("How this is estimated ›")
        driver.awaitText("How fatigue is estimated")
        assertTrue(driver.allText().any { it.contains("isn't proof") })
    }

    @Test
    fun moreThanThreeCausesAddAnOtherRow() {
        val selection = show(
            session(1, 60, "Bench Press", "", 4), session(2, 50, "Bench Press", "incline", 4),
            session(3, 30, "Overhead Press", "", 4), session(4, 20, "Dip", "", 4),
        )
        light(selection, Muscle.TRICEPS)
        driver.awaitText("What it came from")
        assertTrue(driver.allText().contains("Other sources"))
    }
}
