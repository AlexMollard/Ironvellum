package com.ironvellum.app.ui.program

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onFirst
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.FatigueModel
import com.ironvellum.app.domain.FatigueSession
import com.ironvellum.app.domain.LoggedSet
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumColors
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

    private val catalogue = listOf(
        Exercise(name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
        Exercise(name = "Dip", muscleGroup = MuscleGroup.PUSH, isWeighted = false),
    )

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        driver.takeTheClock()
    }

    private fun show(vararg sessions: FatigueSession, expanded: Boolean = false, controlledWidth: Boolean = false): androidx.compose.runtime.MutableState<Muscle?> {
        val selection = mutableStateOf<Muscle?>(null)
        val paneLight = InstrumentationRegistry.getArguments().getString("paneLight") == "true"
        val ui = FatigueUi(FatigueModel.estimate(sessions.toList(), now, long), hasHistory = true, loaded = true, exercises = catalogue)
        compose.activityRule.scenario.onActivity {
            it.setContent {
                val density = LocalDensity.current
                val width = LocalWindowInfo.current.containerSize.width
                // Keep the real pixel constraints while exercising tablet/phone logical widths.
                val testDensity = if (controlledWidth || expanded) Density(width / if (expanded) 1000f else 400f, density.fontScale) else density
                CompositionLocalProvider(LocalDensity provides testDensity) {
                    IronvellumTheme(light = paneLight) {
                        Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss)) {
                            FatigueContent(ui, selection, ZoneId.of("UTC"), expanded = expanded)
                        }
                    }
                }
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
    fun aCauseRowOpensTheExerciseSheetWithItsOwnHistory() {
        val selection = show(session(1, 20, "Bench Press", "incline, deficit", 4))
        light(selection, Muscle.UPPER_CHEST)
        driver.awaitText("What it came from")
        driver.click("Bench Press · deficit, incline")
        // The exercise sheet the rest of the app opens: its pages are About, Form, History (Chronicle) and Fatigue.
        driver.awaitText("Form")
        val text = driver.allText()
        assertTrue(text.toString(), text.any { it == "About" } && text.any { it == "History" || it == "Chronicle" } && text.any { it == "Fatigue" })
    }

    @Test
    fun lightingAMuscleMovesNothingUnderTheFigure() {
        val selection = show(session(1, 20, "Back Squat", "", 5))
        driver.awaitText("MOST WORKED")
        fun tops() = listOf("Estimated from your last 7 days", "MOST WORKED").map {
            compose.onAllNodesWithText(it).onFirst().fetchSemanticsNode().boundsInRoot.top
        }
        val before = tops()
        light(selection, Muscle.QUADS)
        driver.awaitText("What it came from")
        assertEquals(before, tops())
    }

    @Test
    fun expandedSelectionShowsBreakdownBesideMapAndCanChangeMuscle() {
        val selection = show(
            session(1, 40, "Romanian Deadlift", "", 4),
            session(2, 20, "Back Squat", "", 5),
            expanded = true,
        )
        driver.awaitText("MOST WORKED")
        driver.click("Hamstrings")
        assertEquals(Muscle.HAMSTRINGS, selection.value)
        driver.awaitText("What it came from")
        val caption = compose.onAllNodesWithText("Estimated from your last 7 days").onFirst()
            .fetchSemanticsNode().boundsInRoot
        val breakdown = compose.onAllNodesWithText("What it came from").onFirst()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Map=$caption breakdown=$breakdown", breakdown.left >= caption.right)
        assertEquals(0, compose.onAllNodes(isDialog()).fetchSemanticsNodes().size)
        driver.click("Quads")
        assertEquals(Muscle.QUADS, selection.value)
        driver.awaitText("Back Squat")
        assertTrue(driver.allText().any { it == "Quads" })
        if (InstrumentationRegistry.getArguments().getString("shots") == "true") {
            ShotRecorder.take(compose, "expanded-muscle-fatigue-breakdown")
        }
    }

    @Test
    fun phoneSelectionOpensDismissibleSheet() {
        val selection = show(session(1, 20, "Back Squat", "", 5), controlledWidth = true)
        driver.awaitText("MOST WORKED")
        driver.click("Quads")
        driver.awaitText("What it came from")
        assertEquals(Muscle.QUADS, selection.value)
        assertTrue(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        driver.settle()
        assertEquals(null, selection.value)
        assertTrue(driver.allText().none { it == "What it came from" })
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
