package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading a swapped lift back from the rite and the trial. */
class RiteSwapTest {

    private val metrics = mapOf(1L to ExerciseMetric.REPS, 2L to ExerciseMetric.REPS, 3L to ExerciseMetric.REPS, 5L to ExerciseMetric.REPS, 4L to ExerciseMetric.HOLD)
    private val metricOf: (Long) -> ExerciseMetric? = { metrics[it] }

    private fun entry(id: Long, exerciseId: Long, position: Int, name: String = "Ex$exerciseId", reps: Int = 12) =
        PresetEntry(id = id, exerciseId = exerciseId, exerciseName = name, targetSets = 3, targetReps = reps, targetWeightKg = 10.0, modifiers = "Pause", position = position)

    private fun set(exerciseId: Long, position: Int, done: Boolean = true, reps: Int = 10, seconds: Int? = null, name: String = "Ex$exerciseId") =
        SessionSet(exerciseId = exerciseId, exerciseName = name, exercisePosition = position, setIndex = 0, reps = reps, done = done, durationSec = seconds)

    @Test
    fun `a lift replaced by another is offered, keeping the entry's sets, reps and order`() {
        val rite = listOf(entry(10, 1, 0, "Leg Raise"), entry(11, 2, 1))
        val trial = listOf(set(2, 1), set(3, 2, name = "Knee Raise"))
        val swap = RiteSwap.detect(rite, trial, metricOf).single()
        assertEquals("Leg Raise → Knee Raise", swap.line)
        assertEquals(rite[0].copy(exerciseId = 3, exerciseName = "Knee Raise"), swap.after)
    }

    @Test
    fun `nothing is offered when the lift was swapped back, was only removed, or the replacement was never done`() {
        val rite = listOf(entry(10, 1, 0), entry(11, 2, 1))
        // Swapped back: the original is present again, the stand-in is gone.
        assertTrue(RiteSwap.detect(rite, listOf(set(1, 0), set(2, 1)), metricOf).isEmpty())
        // Removed outright: nothing took its place.
        assertTrue(RiteSwap.detect(rite, listOf(set(2, 1)), metricOf).isEmpty())
        // Added but never ticked.
        assertTrue(RiteSwap.detect(rite, listOf(set(2, 1), set(3, 2, done = false)), metricOf).isEmpty())
    }

    @Test
    fun `an ambiguous pairing offers nothing`() {
        val rite = listOf(entry(10, 1, 0), entry(11, 2, 1))
        // One lift gone, two new ones done: which replaced it is a guess.
        assertTrue(RiteSwap.detect(rite, listOf(set(2, 1), set(3, 2), set(5, 3)), metricOf).isEmpty())
    }

    @Test
    fun `two swaps pair in rite order against trial order`() {
        val rite = listOf(entry(10, 1, 0), entry(11, 2, 1))
        val trial = listOf(set(5, 3), set(3, 2))
        val swaps = RiteSwap.detect(rite, trial, metricOf)
        assertEquals(listOf(3L, 5L), swaps.map { it.toId })
        assertEquals(listOf(10L, 11L), swaps.map { it.entry.id })
    }

    @Test
    fun `across a change of metric the reps come from what was done and load and modifiers clear`() {
        val rite = listOf(entry(10, 1, 0, reps = 12))
        val plank = listOf(set(4, 0, reps = 0, seconds = 40), set(4, 0, reps = 0, seconds = 30))
        val after = RiteSwap.detect(rite, plank, metricOf).single().after
        assertEquals(35, after.targetReps)
        assertEquals(null, after.targetWeightKg)
        assertEquals("", after.modifiers)
    }
}
