package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bringing a preset in line with the session the lifter actually did. */
class RoutineUpdateTest {

    private val metrics = mapOf(1L to ExerciseMetric.REPS, 2L to ExerciseMetric.REPS, 3L to ExerciseMetric.HOLD, 4L to ExerciseMetric.DURATION)
    private val metricOf: (Long) -> ExerciseMetric? = { metrics[it] }

    private fun entry(id: Long, exerciseId: Long, position: Int, sets: Int, reps: Int, kg: Double?, modifiers: String = "") =
        PresetEntry(
            id = id,
            exerciseId = exerciseId,
            exerciseName = "Ex$exerciseId",
            targetSets = sets,
            targetReps = reps,
            targetWeightKg = kg,
            modifiers = modifiers,
            position = position,
        )

    private fun set(exerciseId: Long, position: Int, reps: Int, kg: Double?, done: Boolean = true, seconds: Int? = null, modifiers: String = "") =
        SessionSet(
            exerciseId = exerciseId,
            exercisePosition = position,
            setIndex = 0,
            reps = reps,
            weightKg = kg,
            done = done,
            durationSec = seconds,
            modifiers = modifiers,
        )

    @Test
    fun `sets are the done sets and reps the median rounded down`() {
        val pull = entry(10, 1, 0, sets = 6, reps = 5, kg = 15.2)
        val sets = listOf(5, 5, 5, 3).map { set(1, 0, it, 15.2) } + set(1, 0, 5, 15.2, done = false) + set(1, 0, 5, 15.2, done = false)
        val change = RoutineUpdate.propose(listOf(pull), sets, metricOf).single()
        assertEquals(pull, change.before)
        assertEquals(pull.copy(targetSets = 4, targetReps = 5), change.after)
        // Odd count: the middle one.
        val dips = entry(11, 2, 0, sets = 3, reps = 12, kg = null)
        val odd = RoutineUpdate.propose(listOf(dips), listOf(10, 9, 7).map { set(2, 0, it, null) }, metricOf).single()
        assertEquals(dips.copy(targetReps = 9), odd.after)
    }

    @Test
    fun `load is the one on most sets, a tie going to the heavier`() {
        val pull = entry(10, 1, 0, sets = 4, reps = 5, kg = 10.0)
        val mostly = listOf(20.0, 20.0, 20.0, 15.0).map { set(1, 0, 5, it) }
        assertEquals(20.0, RoutineUpdate.propose(listOf(pull), mostly, metricOf).single().after.targetWeightKg)
        val tie = listOf(15.0, 20.0, 15.0, 20.0).map { set(1, 0, 5, it) }
        assertEquals(20.0, RoutineUpdate.propose(listOf(pull), tie, metricOf).single().after.targetWeightKg)
        // Bodyweight stays bodyweight, and loses a tie to any load.
        val bodyweight = entry(11, 2, 0, sets = 4, reps = 10, kg = null)
        assertEquals(
            null,
            RoutineUpdate.propose(listOf(bodyweight), List(3) { set(2, 0, 8, null) }, metricOf).single().after.targetWeightKg,
        )
        val bwTie = listOf(set(2, 0, 10, null), set(2, 0, 10, null), set(2, 0, 10, 5.0), set(2, 0, 10, 5.0))
        assertEquals(5.0, RoutineUpdate.propose(listOf(bodyweight), bwTie, metricOf).single().after.targetWeightKg)
    }

    @Test
    fun `skipped and unchanged entries are not listed, added movements are ignored`() {
        val pull = entry(10, 1, 0, sets = 3, reps = 5, kg = 15.0)
        val dips = entry(11, 2, 1, sets = 3, reps = 10, kg = null)
        val sets = List(3) { set(1, 0, 5, 15.0) } + // done exactly as planned
            List(3) { set(2, 1, 10, null, done = false) } + // never ticked off
            List(4) { set(99, 2, 12, 30.0) } // added mid-session
        assertTrue(RoutineUpdate.propose(listOf(pull, dips), sets, metricOf).isEmpty())
    }

    @Test
    fun `a hold proposes its seconds into targetReps`() {
        val lSit = entry(12, 3, 0, sets = 3, reps = 20, kg = null)
        val sets = listOf(30, 25, 28).map { set(3, 0, 0, null, seconds = it) }
        assertEquals(lSit.copy(targetReps = 28), RoutineUpdate.propose(listOf(lSit), sets, metricOf).single().after)
        assertTrue(RoutineUpdate.propose(listOf(lSit), sets, metricOf).single().isHold)
    }

    @Test
    fun `changed modifiers are proposed, a reordering of the same tags is not`() {
        val hspu = entry(13, 2, 0, sets = 2, reps = 6, kg = null, modifiers = "Deficit, Pause")
        val same = List(2) { set(2, 0, 6, null, modifiers = "Pause, Deficit") }
        assertTrue(RoutineUpdate.propose(listOf(hspu), same, metricOf).isEmpty())
        val changed = List(2) { set(2, 0, 6, null, modifiers = "Deficit") }
        assertEquals(hspu.copy(modifiers = "Deficit"), RoutineUpdate.propose(listOf(hspu), changed, metricOf).single().after)
    }

    @Test
    fun `the same movement twice matches block by block, activities are left alone`() {
        val heavy = entry(20, 1, 0, sets = 3, reps = 3, kg = 25.0)
        val run = entry(21, 4, 1, sets = 1, reps = 20, kg = null)
        val backoff = entry(22, 1, 2, sets = 3, reps = 8, kg = null)
        val sets = List(3) { set(1, 0, 3, 25.0) } + // heavy as planned
            set(4, 1, 30, null, seconds = 1800) +
            List(2) { set(1, 2, 8, null) } // back-off: one set short
        val change = RoutineUpdate.propose(listOf(heavy, run, backoff), sets, metricOf).single()
        assertEquals(backoff.copy(targetSets = 2), change.after)
    }
}
