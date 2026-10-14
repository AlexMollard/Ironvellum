package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** On a lift with a top set, peaks and records follow the top set alone; back-offs still count for everything else. */
class PeakSetsTest {

    private fun set(id: Long, index: Int, kind: SetKind, reps: Int, kg: Double?, done: Boolean = true, exerciseId: Long = 1) = SessionSet(
        id = id, exerciseId = exerciseId, exerciseName = "Pull-up", exercisePosition = 0, setIndex = index,
        reps = reps, weightKg = kg, done = done, kind = kind,
    )

    private val session = WorkoutSession(id = 1, label = "Pull", startedAtMs = 1_000, completedAtMs = 2_000)

    @Test
    fun `only the top set is eligible when a lift had one`() {
        val block = listOf(
            set(1, 0, SetKind.WARMUP, 5, 0.0, done = false),
            set(2, 1, SetKind.TOP, 4, 20.0),
            set(3, 2, SetKind.BACKOFF, 8, 15.0),
            set(4, 3, SetKind.BACKOFF, 8, 15.0),
        )
        assertEquals(listOf(2L), PeakSets.eligible(block).map { it.id })
        assertEquals(listOf(2L), PeakSets.narrowed(block).map { it.id })
        assertTrue(PeakSets.hasTop(block))
    }

    @Test
    fun `a lift with no done top set keeps every done working set`() {
        val block = listOf(
            set(1, 0, SetKind.TOP, 4, 20.0, done = false),
            set(2, 1, SetKind.BACKOFF, 8, 15.0),
            set(3, 2, SetKind.WORKING, 8, 15.0),
        )
        assertEquals(listOf(2L, 3L), PeakSets.eligible(block).map { it.id })
        // Another movement in the trial is judged on its own sets.
        val other = set(9, 0, SetKind.WORKING, 8, 50.0, exerciseId = 2)
        assertEquals(listOf(9L), PeakSets.narrowed(listOf(set(5, 0, SetKind.TOP, 4, 20.0), set(6, 1, SetKind.BACKOFF, 8, 15.0), other)).filter { it.exerciseId == 2L }.map { it.id })
    }

    @Test
    fun `a back-off with more reps is not a peak when the top set stands in the way`() {
        val history = listOf(RepMax.Mark(figure = 8, weightKg = 12.0))
        val block = listOf(set(1, 0, SetKind.TOP, 4, 10.0), set(2, 1, SetKind.BACKOFF, 9, 12.0))
        // 9 reps at 12 kg beats the old best of 8 reps at 12 kg, but it is a back-off.
        assertTrue(RepMax.hits(block, history, hold = false).isEmpty())
        // The same two sets with no top set tag are both judged: the 9 is a peak.
        val plain = block.map { it.copy(kind = SetKind.WORKING) }
        assertEquals(listOf(2L), RepMax.hits(plain, history, hold = false).map { it.set.id })
        // And a top set that does beat history peaks.
        val strong = listOf(set(1, 0, SetKind.TOP, 4, 14.0), set(2, 1, SetKind.BACKOFF, 6, 8.0))
        assertEquals(listOf(1L), RepMax.hits(strong, history, hold = false).map { it.set.id })
    }

    @Test
    fun `the book of what was lifted before holds the top set alone`() {
        val sets = listOf(set(1, 0, SetKind.TOP, 4, 20.0), set(2, 1, SetKind.BACKOFF, 8, 15.0))
        val book = RepMax.book(listOf(session to sets))
        assertEquals(listOf(RepMax.Mark(4, 20.0)), book["pull-up"])
    }

    @Test
    fun `set records follow the top set alone`() {
        val sets = listOf(set(1, 0, SetKind.TOP, 4, 20.0), set(2, 1, SetKind.BACKOFF, 8, 15.0))
        val records = SetRecords.records(listOf(session to sets), { 80.0 })
        assertEquals(setOf("pull-up" to 0), records.keys)
        assertEquals(20.0, records.getValue("pull-up" to 0).weightKg!!, 0.0)
    }

    @Test
    fun `the exercise history records and chart follow the top set, the totals still count every set`() {
        val rows = listOf(
            ExerciseSetRow(1, 1_000, 0, 4, 20.0, "", done = true, kind = "TOP"),
            ExerciseSetRow(1, 1_000, 1, 12, 15.0, "", done = true, kind = "BACKOFF"),
            ExerciseSetRow(1, 1_000, 2, 12, 15.0, "", done = true, kind = "BACKOFF"),
        )
        val exercise = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
        val history = ExerciseHistoryCalculator.build(exercise, rows, bodyweightKg = 80.0)
        assertEquals(20.0, history.heaviestWeightKg!!, 0.0)
        assertEquals(4, history.bestSetReps)
        assertEquals(3, history.completedSets)
        assertEquals(28, history.totalReps)
        assertEquals(history.bestScore, history.series.single().bestSetScore, 1e-9)
    }
}
