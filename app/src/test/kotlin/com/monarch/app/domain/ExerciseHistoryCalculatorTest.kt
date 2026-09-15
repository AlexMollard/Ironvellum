package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-exercise history screen reports these numbers back as a record of
 * training, so each one has to mean what it says: skipped sets must not inflate
 * a total, a bodyweight movement must be credited with the body it moved, and
 * "heaviest" must not be decided by rep count.
 */
class ExerciseHistoryCalculatorTest {

    private val pullUp = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = true)

    private fun row(
        sessionId: Long,
        atMs: Long,
        setIndex: Int = 1,
        reps: Int,
        weightKg: Double?,
        done: Boolean = true,
    ) = ExerciseSetRow(
        sessionId = sessionId,
        atMs = atMs,
        setIndex = setIndex,
        reps = reps,
        weightKg = weightKg,
        modifiers = "",
        done = done,
    )

    @Test
    fun `no logged sets reads as empty rather than zeroed history`() {
        val history = ExerciseHistoryCalculator.build(pullUp, emptyList(), bodyweightKg = 80.0)

        assertTrue(history.isEmpty)
        assertEquals(0, history.sessions)
        assertEquals(0.0, history.totalVolumeKg, 1e-9)
        assertNull("never trained is not 'last trained today'", history.daysSinceLast)
        assertNull(history.heaviestWeightKg)
    }

    @Test
    fun `skipped sets count towards nothing`() {
        val rows = listOf(
            row(sessionId = 1, atMs = DAY, reps = 10, weightKg = 20.0, done = true),
            // Planned but never performed: present in the log, absent from every total.
            row(sessionId = 1, atMs = DAY, setIndex = 2, reps = 99, weightKg = 100.0, done = false),
        )

        val history = ExerciseHistoryCalculator.build(pullUp, rows, bodyweightKg = 80.0)

        assertEquals(1, history.completedSets)
        assertEquals(10, history.totalReps)
        assertEquals(200.0, history.totalVolumeKg, 1e-9)
        assertEquals(20.0, history.heaviestWeightKg!!, 1e-9)
        // The skipped set is still visible in the log itself.
        assertEquals(2, history.entries.size)
    }

    @Test
    fun `a bodyweight set is credited with the body it moved`() {
        val rows = listOf(row(sessionId = 1, atMs = DAY, reps = 10, weightKg = null))

        val history = ExerciseHistoryCalculator.build(pullUp, rows, bodyweightKg = 80.0)

        // 10 reps of 80 kg is 800 kg moved, not 0.
        assertEquals(800.0, history.totalVolumeKg, 1e-9)
        assertEquals(80.0, history.bestSetLoadKg, 1e-9)
        // Added weight stays null: the set was unweighted, and the screen says so.
        assertNull(history.heaviestWeightKg)
    }

    @Test
    fun `unknown bodyweight scores bodyweight sets at zero instead of crashing`() {
        val rows = listOf(row(sessionId = 1, atMs = DAY, reps = 10, weightKg = null))

        val history = ExerciseHistoryCalculator.build(pullUp, rows, bodyweightKg = null)

        assertEquals(0.0, history.totalVolumeKg, 1e-9)
        assertEquals(1, history.completedSets)
    }

    @Test
    fun `heaviest is decided by load first and reps only to break a tie`() {
        val rows = listOf(
            // 30 x 10 kg = 300 kg of work: the biggest set, at the lightest load.
            row(sessionId = 1, atMs = DAY, reps = 30, weightKg = 10.0),
            row(sessionId = 1, atMs = DAY, setIndex = 2, reps = 3, weightKg = 25.0),
            row(sessionId = 1, atMs = DAY, setIndex = 3, reps = 5, weightKg = 25.0),
        )

        val history = ExerciseHistoryCalculator.build(pullUp, rows, bodyweightKg = 80.0)

        // 25 kg beats any amount of 10 kg, and among the 25s the 5-rep set wins.
        assertEquals(25.0, history.heaviestWeightKg!!, 1e-9)
        assertEquals(5, history.heaviestReps)
        // Best set asks a different question — most work in one set — so the
        // light high-rep set takes it. Conflating the two would make the
        // screen report the same set twice under different headings.
        assertEquals(30, history.bestSetReps)
        assertEquals(10.0, history.bestSetLoadKg, 1e-9)
    }

    @Test
    fun `sessions are counted once and charted in time order`() {
        val rows = listOf(
            row(sessionId = 2, atMs = 5 * DAY, reps = 6, weightKg = 15.0),
            row(sessionId = 1, atMs = DAY, reps = 5, weightKg = 10.0),
            row(sessionId = 1, atMs = DAY, setIndex = 2, reps = 4, weightKg = 10.0, done = false),
            row(sessionId = 2, atMs = 5 * DAY, setIndex = 2, reps = 6, weightKg = 15.0),
        )

        val history = ExerciseHistoryCalculator.build(pullUp, rows, bodyweightKg = 80.0)

        assertEquals(2, history.sessions)
        assertEquals(listOf(DAY, 5 * DAY), history.series.map { it.atMs })
        // Per point, only performed reps: session 1 logged 5, not 9.
        assertEquals(listOf(5, 12), history.series.map { it.totalReps })
        assertEquals(DAY, history.firstLoggedAtMs)
        assertEquals(5 * DAY, history.lastLoggedAtMs)
    }

    @Test
    fun `days since last training counts whole days from the newest set`() {
        val rows = listOf(row(sessionId = 1, atMs = 10 * DAY, reps = 5, weightKg = 10.0))

        val history = ExerciseHistoryCalculator.build(
            pullUp,
            rows,
            bodyweightKg = 80.0,
            nowMs = 10 * DAY + 3 * DAY + DAY / 2,
        )

        // Three and a half days away is three days, not four.
        assertEquals(3L, history.daysSinceLast)
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
