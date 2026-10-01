package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftRecordsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 200 * day

    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
    private val run = Exercise(
        id = 2, name = "Run", muscleGroup = MuscleGroup.CARDIO, isWeighted = false,
        metric = ExerciseMetric.DISTANCE_TIME, category = "Cardio",
    )

    private fun session(id: Long, daysAgo: Long) =
        WorkoutSession(id = id, label = "t$id", startedAtMs = now - daysAgo * day, completedAtMs = now - daysAgo * day)

    private fun set(ex: Exercise, kg: Double?, reps: Int, done: Boolean = true) =
        SessionSet(exerciseId = ex.id, exerciseName = ex.name, setIndex = 0, reps = reps, weightKg = kg, done = done)

    private fun board(vararg trials: Pair<WorkoutSession, List<SessionSet>>) = LiftRecords.board(
        trials.map { it.first },
        trials.associate { it.first.id to it.second },
        listOf(bench, run).associateBy { it.id },
        now,
    )

    @Test
    fun `best is the Epley estimate of the strongest working set`() {
        val rows = board(session(1, 10) to listOf(set(bench, 100.0, 5), set(bench, 80.0, 10)))
        // 100 x (1 + 5/30) = 116.67 beats 80 x (1 + 10/30) = 106.67
        assertEquals(116.666, rows.single().bestE1rmKg, 0.01)
        assertEquals(ProgramRules.epley(100.0, 5), rows.single().bestE1rmKg, 1e-9)
    }

    @Test
    fun `sets past twelve reps, undone sets, unloaded sets and timed work do not count`() {
        val rows = board(
            session(1, 5) to listOf(
                set(bench, 60.0, 13), set(bench, 90.0, 5, done = false), set(bench, null, 8),
                set(bench, 0.0, 8), set(run, 10.0, 1),
            ),
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `trend is the best now against the best as of the window start`() {
        val rows = board(
            session(1, 150) to listOf(set(bench, 90.0, 1)),
            session(2, 40) to listOf(set(bench, 95.0, 1)),
            session(3, 3) to listOf(set(bench, 100.0, 1)),
        )
        val lift = rows.single()
        assertEquals(listOf(90.0 * (1 + 1 / 30.0), 95.0 * (1 + 1 / 30.0), 100.0 * (1 + 1 / 30.0)), lift.series)
        assertEquals(10.0 * (1 + 1 / 30.0), lift.deltaKg!!, 1e-9)
        assertEquals(now - 3 * day, lift.bestAtMs)
        assertTrue(LiftRecords.isFresh(lift, now))
    }

    @Test
    fun `a lift with no history that old has no trend yet and an old best is not fresh`() {
        val lift = board(session(1, 30) to listOf(set(bench, 100.0, 1))).single()
        assertNull(lift.deltaKg)
        assertFalse(LiftRecords.isFresh(lift, now))
    }
}
