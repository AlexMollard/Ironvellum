package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.WorkoutPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Train board's order and the workout editor's add / unsaved-changes rules. */
class WorkoutBoardTest {

    @Test
    fun `the board runs Monday to Sunday with unscheduled workouts last`() {
        // Arrives by name, as the DAO returns it: Legs FRI, Pull WED, Push MON.
        val byName = listOf(
            WorkoutPreset(id = 1, name = "Arms", scheduledDay = null),
            WorkoutPreset(id = 2, name = "Legs", scheduledDay = 5),
            WorkoutPreset(id = 3, name = "Mobility", scheduledDay = null),
            WorkoutPreset(id = 4, name = "Pull", scheduledDay = 3),
            WorkoutPreset(id = 5, name = "Push", scheduledDay = 1),
        )
        assertEquals(listOf("Push", "Pull", "Legs", "Arms", "Mobility"), weekOrder(byName).map { it.name })
    }

    @Test
    fun `a picked exercise joins with targets that suit its metric`() {
        val row = newEntry(Exercise(id = 7, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false))
        assertEquals(EditorEntry(7, "Pull-up", sets = "3", reps = "10", weight = "", modifiers = ""), row)
        val run = newEntry(
            Exercise(id = 9, name = "Run", muscleGroup = MuscleGroup.PULL, isWeighted = false, metric = ExerciseMetric.DISTANCE_TIME),
        )
        assertEquals(9L, run.exerciseId)
        assertEquals("", run.reps)
    }

    @Test
    fun `only an edit the lifter made counts as unsaved`() {
        val entry = EditorEntry(7, "Pull-up", "3", "10", "", "")
        val loaded = EditorUi(presetId = 1, name = "Pull", scheduledDay = 3, entries = listOf(entry))
        // The catalogue arriving is not an edit.
        assertFalse(editorChanged(loaded, loaded.copy(exercises = listOf(Exercise(name = "X", muscleGroup = MuscleGroup.PULL, isWeighted = false)))))
        assertTrue(editorChanged(loaded, loaded.copy(name = "Pull A")))
        assertTrue(editorChanged(loaded, loaded.copy(note = "slow")))
        assertTrue(editorChanged(loaded, loaded.copy(scheduledDay = null)))
        assertTrue(editorChanged(loaded, loaded.copy(entries = listOf(entry.copy(reps = "8")))))
        assertTrue(editorChanged(loaded, loaded.copy(entries = emptyList())))
    }
}
