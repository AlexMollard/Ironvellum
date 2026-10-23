package com.ironvellum.app.ui.program

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Lighting a muscle on Coverage opens the breakdown Fatigue uses, with the week's numbers, and moves nothing under the map. */
@RunWith(AndroidJUnit4::class)
class CoverageBreakdownTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    private val planned = listOf(
        PlannedPreset("Push B", "", 1, listOf(PlannedEntry("Bench Press", 3, 8, null), PlannedEntry("Dip", 2, 10, null))),
    )
    private val done = listOf(PlannedPreset("Done this week", "", null, listOf(PlannedEntry("Bench Press", 4, 8, null))))
    private val catalogue = listOf(
        Exercise(name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
        Exercise(name = "Dip", muscleGroup = MuscleGroup.PUSH, isWeighted = false),
    )

    private val ui = CoverageUi(
        week = weekCoverage(mapOf(Muscle.MID_CHEST to 4.0), mapOf(Muscle.MID_CHEST to 4.0), planned),
        plannedPresets = planned,
        donePresets = done,
        hasAnyPreset = true,
        exercises = catalogue,
    )

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        driver.takeTheClock()
    }

    private fun show(): MutableState<Muscle?> {
        val lit = mutableStateOf<Muscle?>(null)
        compose.activityRule.scenario.onActivity {
            it.setContent {
                IronvellumTheme {
                    CoveragePage(ui, lit, openExercise = null, onOpenExercise = {}, dismissed = emptySet(), onDismiss = {})
                }
            }
        }
        driver.settle()
        return lit
    }

    private fun light(lit: MutableState<Muscle?>, muscle: Muscle) {
        compose.runOnUiThread { lit.value = muscle }
        driver.settle()
    }

    @Test
    fun aLitMuscleOpensTheSameSheetWithDoneAndPlannedSets() {
        val lit = show()
        light(lit, Muscle.MID_CHEST)
        driver.awaitText("What trains it")
        val text = driver.allText()
        assertTrue(text.toString(), text.contains("Mid chest"))
        assertTrue("the week's numbers", text.any { it.contains("4") && it.contains("done") && it.contains("planned") })
        assertTrue("an exercise that trains it", text.contains("Bench Press"))
        assertTrue("planned work is named by its rite", text.contains("Push B"))
        assertTrue("done work is named too", text.contains("Done this week"))
        assertFalse("the older filter is gone", text.any { it.startsWith("Trains ") })
    }

    @Test
    fun aSourceOpensTheExerciseSheet() {
        val lit = show()
        light(lit, Muscle.MID_CHEST)
        driver.awaitText("What trains it")
        driver.click("Dip")
        driver.awaitText("Form")
        val text = driver.allText()
        assertTrue(text.toString(), text.contains("About") && text.contains("Fatigue"))
    }

    @Test
    fun lightingAMuscleMovesNothingUnderTheMap() {
        val lit = show()
        fun tops() = listOf("This week, Monday to Sunday: done so far", "Lats").map {
            compose.onAllNodesWithText(it, substring = true).onFirst().fetchSemanticsNode().boundsInRoot.top
        }
        val before = tops()
        light(lit, Muscle.MID_CHEST)
        driver.awaitText("What trains it")
        assertEquals(before, tops())
    }
}
