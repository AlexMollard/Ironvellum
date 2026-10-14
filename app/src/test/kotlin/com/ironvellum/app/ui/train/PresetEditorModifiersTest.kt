package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.ModifierFlag
import com.ironvellum.app.domain.applicableModifiers
import com.ironvellum.app.domain.modifierChips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The rite editor's modifiers: "weighted" follows the load, including after Change exercise; nothing else is touched. */
class PresetEditorModifiersTest {

    private val pullUp = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
    private val dip = Exercise(id = 2, name = "Dip", muscleGroup = MuscleGroup.PUSH, isWeighted = false)
    private val bench = Exercise(id = 3, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
    private val run = Exercise(id = 4, name = "Running", muscleGroup = MuscleGroup.CORE, isWeighted = false, metric = ExerciseMetric.DISTANCE_TIME, category = "Cardio")
    private val catalogue = listOf(pullUp, dip, bench, run)

    private fun entry(exercise: Exercise, weight: String = "", modifiers: String = "") =
        EditorEntry(exercise.id, exercise.name, sets = "3", reps = "8", weight = weight, modifiers = modifiers)

    @Test
    fun `typing a load on a bodyweight lift sets weighted and clearing it removes it`() {
        val bare = entry(pullUp)
        val loaded = bare.copy(weight = "10").followingLoad(bare, catalogue)
        assertEquals("weighted", loaded.modifiers)
        val cleared = loaded.copy(weight = "").followingLoad(loaded, catalogue)
        assertEquals("", cleared.modifiers)
        // 0 is no load either.
        assertEquals("", loaded.copy(weight = "0").followingLoad(loaded, catalogue).modifiers)
    }

    @Test
    fun `weighted keeps company with the other modifiers`() {
        val before = entry(pullUp, modifiers = "archer")
        val loaded = before.copy(weight = "5").followingLoad(before, catalogue)
        assertEquals("archer, weighted", loaded.modifiers)
        assertEquals("archer", loaded.copy(weight = "").followingLoad(loaded, catalogue).modifiers)
    }

    @Test
    fun `changing the exercise re-reads the load`() {
        // A loaded pull-up becomes a dip with the load kept: still weighted.
        val loaded = entry(pullUp, weight = "10", modifiers = "weighted")
        assertEquals("weighted", loaded.copy(exerciseId = dip.id, exerciseName = dip.name).followingLoad(loaded, catalogue).modifiers)
        // The load is empty after the swap (the picker clears it for another metric, or it was never typed): weighted goes.
        val emptied = entry(pullUp, weight = "", modifiers = "weighted, tempo")
        assertEquals("tempo", emptied.copy(exerciseId = dip.id, exerciseName = dip.name).followingLoad(emptied, catalogue).modifiers)
        // A stale weighted never rides onto a barbell lift with no load either.
        assertEquals("", emptied.copy(exerciseId = bench.id, exerciseName = bench.name, modifiers = "weighted").followingLoad(emptied, catalogue).modifiers)
    }

    @Test
    fun `a swap to an activity drops weighted`() {
        val loaded = entry(pullUp, weight = "10", modifiers = "weighted")
        val swapped = loaded.copy(exerciseId = run.id, exerciseName = run.name, reps = "", weight = "").followingLoad(loaded, catalogue)
        assertEquals("", swapped.modifiers)
    }

    @Test
    fun `an edit of anything else touches no modifier`() {
        val before = entry(pullUp, weight = "", modifiers = "weighted")
        // A hand-written entry whose tag disagrees with its load is left alone until the load or the exercise is edited.
        val edited = before.copy(sets = "4")
        assertSame(edited, edited.followingLoad(before, catalogue))
        assertEquals("weighted", edited.modifiers)
    }

    @Test
    fun `a swap keeps tokens the new movement does not offer, flagged`() {
        val before = entry(dip, modifiers = "assisted, tempo")
        val swapped = before.copy(exerciseId = bench.id, exerciseName = bench.name).followingLoad(before, catalogue)
        assertEquals("assisted, tempo", swapped.modifiers)
        val chips = modifierChips(applicableModifiers(bench), swapped.modifiers)
        assertEquals(ModifierFlag.NOT_APPLICABLE, chips.single { it.token == "assisted" }.flag)
    }

    @Test
    fun `a legacy token on a saved entry is flagged and stays until removed`() {
        val saved = entry(pullUp, modifiers = "banded, weighted")
        val chips = modifierChips(applicableModifiers(pullUp), saved.modifiers)
        assertEquals(ModifierFlag.UNKNOWN, chips.single { it.token == "banded" }.flag)
        assertEquals("banded, weighted", saved.modifiers)
    }
}
