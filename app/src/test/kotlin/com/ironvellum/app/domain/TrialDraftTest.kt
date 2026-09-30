package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TrialDraftTest {

    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
    private val plank = Exercise(id = 2, name = "Plank", muscleGroup = MuscleGroup.CORE, isWeighted = false, metric = ExerciseMetric.HOLD)
    private val pushUp = Exercise(id = 3, name = "Push-up", muscleGroup = MuscleGroup.PUSH, isWeighted = false)

    private val draft = TrialDraft.of(
        listOf(
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", exercisePosition = 0, setIndex = 1, reps = 6, weightKg = 70.0, modifiers = "paused", done = true),
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", exercisePosition = 0, setIndex = 0, reps = 8, weightKg = 60.0, modifiers = "paused", done = true),
            SessionSet(exerciseId = 2, exerciseName = "Plank", exercisePosition = 1, setIndex = 0, reps = 0, durationSec = 45, done = false),
        ),
    )

    @Test
    fun `blocks come out in trial order with sets in set order`() {
        assertEquals(listOf(1L, 2L), draft.blocks.map { it.exerciseId })
        assertEquals(listOf(8, 6), draft.blocks[0].sets.map { it.reps })
        assertEquals(2, draft.tickedCount)
    }

    @Test
    fun `a swap across metrics gives every set the new movement's own figure`() {
        val swapped = draft.swapExercise(0, bench, plank).blocks[0]
        assertEquals(2L, swapped.exerciseId)
        assertEquals(listOf(0, 0), swapped.sets.map { it.reps })
        assertEquals(listOf(30, 30), swapped.sets.map { it.durationSec })
        assertEquals("ticks survive a swap", listOf(true, true), swapped.sets.map { it.done })
        val back = draft.swapExercise(1, plank, bench).blocks[1].sets.single()
        assertEquals(10, back.reps)
        assertEquals(null, back.durationSec)
    }

    @Test
    fun `a swap within a metric keeps the figures and only the modifiers that fit`() {
        val swapped = draft.swapExercise(0, bench, pushUp).blocks[0]
        assertEquals(listOf(8, 6), swapped.sets.map { it.reps })
        assertEquals("paused", swapped.modifiers)
        val toPlank = draft.swapExercise(0, bench, plank).blocks[0]
        assertEquals("a hold takes no rep modifiers", "", toPlank.modifiers)
    }

    @Test
    fun `an added set copies the last one unticked and removing a block's last set drops it`() {
        val added = draft.addSet(0).blocks[0].sets
        assertEquals(3, added.size)
        assertEquals(6, added.last().reps)
        assertFalse(added.last().done)
        val removed = draft.removeSet(1, 0)
        assertEquals(listOf(1L), removed.blocks.map { it.exerciseId })
    }
}
