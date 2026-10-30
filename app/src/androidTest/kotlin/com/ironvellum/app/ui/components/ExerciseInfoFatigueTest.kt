package com.ironvellum.app.ui.components

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.CustomExercises
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The exercise sheet's fourth page: a derived footprint, tags only when true, the typical line and the why line. */
@RunWith(AndroidJUnit4::class)
class ExerciseInfoFatigueTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = TestProfile.ensureSetUp()

    @After
    fun forget() = CustomExercises.reset()

    private fun show(exercise: Exercise) {
        compose.setContent {
            IronvellumTheme { ExerciseInfoSheet(exercise = exercise, lastLine = null, onDismiss = {}, onPick = null) }
        }
        compose.waitForIdle()
    }

    private fun openFatigue() {
        compose.onNodeWithText("Fatigue").performClick()
        compose.waitForIdle()
    }

    private val bench = Exercise(name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
    private val rdl = Exercise(name = "Romanian Deadlift", muscleGroup = MuscleGroup.LEGS, isWeighted = true)

    @Test
    fun theFatiguePageIsTheFourthAfterAboutFormHistory() {
        show(bench)
        // The tab row lists them in order: About, Form, History, Fatigue.
        // History reads Chronicle in themed words.
        val tabs = listOf(hasText("About"), hasText("Form"), hasText("History").or(hasText("Chronicle")), hasText("Fatigue"))
        val xs = tabs.map { tab -> compose.onNode(tab).fetchSemanticsNode().boundsInRoot.left }
        assertEquals(xs.sorted(), xs)
    }

    @Test
    fun benchListsBandsAndTheTypicalLineWithoutTags() {
        show(bench)
        openFatigue()
        compose.onNodeWithText("Tap a muscle on the body or in the list").assertIsDisplayed()
        compose.onAllNodesWithText("Strong").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Medium").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Three hard sets, one rep left").assertIsDisplayed()
        compose.onNode(hasText("back to light in about", substring = true)).assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasText("Stretch-heavy", ignoreCase = true)).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("Heavy whole-body", ignoreCase = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun romanianDeadliftShowsBothTagsAndAComparison() {
        show(rdl)
        openFatigue()
        compose.onNode(hasText("Stretch-heavy", ignoreCase = true)).assertIsDisplayed()
        compose.onNode(hasText("Heavy whole-body", ignoreCase = true)).assertIsDisplayed()
        compose.onNode(hasText("long and stretched", substring = true)).assertExists()
        compose.onNode(hasText("works the same muscle", substring = true)).assertExists()
    }

    @Test
    fun aCustomExerciseHasTheFootprintOfItsOwnShares() {
        val shares = CustomExercises.shares(listOf(Muscle.QUADS), emptyList())
        CustomExercises.sync(listOf(CustomExercises.Row("My Squat", MuscleGroup.LEGS, "", emptySet(), shares)))
        show(Exercise(name = "My Squat", muscleGroup = MuscleGroup.LEGS, isWeighted = true, custom = true, shares = shares))
        openFatigue()
        compose.onNode(hasText("Quads")).assertExists()
        assertEquals(0, compose.onAllNodes(hasText("Stretch-heavy", ignoreCase = true)).fetchSemanticsNodes().size)
        compose.onNode(hasText("Nothing about this lift", substring = true)).assertExists()
    }

    private fun assertEquals(expected: Any?, actual: Any?) = org.junit.Assert.assertEquals(expected, actual)
}
