package com.ironvellum.app.ui.program

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.isDialog
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
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumColors
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

    private fun show(expanded: Boolean = false): MutableState<Muscle?> {
        val lit = mutableStateOf<Muscle?>(null)
        val paneLight = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("paneLight") == "true"
        compose.activityRule.scenario.onActivity {
            it.setContent {
                val density = LocalDensity.current
                val width = LocalWindowInfo.current.containerSize.width
                val testDensity = if (expanded) Density(width / 1000f, density.fontScale) else density
                CompositionLocalProvider(LocalDensity provides testDensity) {
                    IronvellumTheme(light = paneLight) {
                        Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss)) {
                            CoveragePage(ui, lit, openExercise = null, onOpenExercise = {}, dismissed = emptySet(), onDismiss = {}, expanded = expanded)
                        }
                    }
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

    @Test
    fun expandedCoverageSelectionShowsInlineBesideMap() {
        val lit = show(expanded = true)
        driver.awaitText("This week, Monday to Sunday")
        driver.click("Lats")
        assertEquals(Muscle.LATS, lit.value)
        driver.awaitText("Nothing in this view trains it.")
        val mapCaption = compose.onAllNodesWithText("This week, Monday to Sunday", substring = true)
            .onFirst().fetchSemanticsNode().boundsInRoot
        val breakdown = compose.onAllNodesWithText("Nothing in this view trains it.").onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue("Map=$mapCaption breakdown=$breakdown", breakdown.left >= mapCaption.right)
        assertEquals(0, compose.onAllNodes(isDialog()).fetchSemanticsNodes().size)
        assertTrue(driver.allText().contains("Lats"))
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("shots") == "true") {
            ShotRecorder.take(compose, "expanded-coverage-breakdown")
        }
    }
}
