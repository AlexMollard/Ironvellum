package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueInputTest {

    private val H = 3_600_000L
    private val DAY = 24 * H
    private val NOW = 1_000L * DAY
    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
    private val run = Exercise(id = 2, name = "Running", muscleGroup = MuscleGroup.CARDIO, isWeighted = false, metric = ExerciseMetric.DURATION, category = "Cardio")
    private val exercises = listOf(bench, run).associateBy { it.id }

    private fun row(
        id: Long,
        startedAgoH: Long,
        completedAgoH: Long? = startedAgoH - 1,
        imported: Boolean = false,
        vararg sets: SessionSet,
    ) = WorkoutSession(
        id = id, label = "Fictional", startedAtMs = NOW - startedAgoH * H,
        completedAtMs = completedAgoH?.let { NOW - it * H }, imported = imported,
    ) to sets.toList()

    private fun benchSet(rpe: Int? = 9, done: Boolean = true, kind: SetKind = SetKind.WORKING, index: Int = 0) = SessionSet(
        exerciseId = 1, exerciseName = "Bench Press", setIndex = index, reps = 5, weightKg = 100.0, done = done, kind = kind, rpe = rpe,
    )

    @Test
    fun `no history gives an empty report and a new lifter`() {
        assertTrue(FatigueInput.sessions(emptyList(), null, emptyMap(), NOW).isEmpty())
        assertNull(FatigueInput.firstWorkoutMs(emptyList(), null))
        val report = FatigueModel.estimate(emptyList(), NOW, null)
        assertTrue(report.newLifter)
        Muscle.entries.forEach { assertEquals(0.0, report.residual(it), 0.0) }
    }

    @Test
    fun `a live workout counts at now`() {
        val live = WorkoutSession(id = 9, label = "Live", startedAtMs = NOW - 40 * 60_000L) to listOf(benchSet(index = 0), benchSet(index = 1))
        val sessions = FatigueInput.sessions(emptyList(), live, exercises, NOW)
        assertEquals(NOW, sessions.single().atMs)
        val report = FatigueModel.estimate(sessions, NOW, NOW - 400 * DAY)
        assertEquals(1.8 * 1.4, report.residual(Muscle.MID_CHEST), 1e-9)
        assertEquals("decays from now, not from the start", 1.8 * 1.4 / 2, report.residual(Muscle.MID_CHEST, NOW + 24 * H), 1e-9)
    }

    @Test
    fun `live undone and warm-up sets are ignored`() {
        val live = WorkoutSession(id = 9, label = "Live", startedAtMs = NOW) to listOf(
            benchSet(), benchSet(done = false, index = 1), benchSet(kind = SetKind.WARMUP, index = 2),
        )
        val report = FatigueModel.estimate(FatigueInput.sessions(emptyList(), live, exercises, NOW), NOW, NOW)
        assertEquals(0.9 * 1.4, report.residual(Muscle.MID_CHEST), 1e-9)
    }

    @Test
    fun `a live only lifter is new`() {
        val live = WorkoutSession(id = 9, label = "Live", startedAtMs = NOW - H)
        assertEquals(live.startedAtMs, FatigueInput.firstWorkoutMs(emptyList(), live))
        val report = FatigueModel.estimate(FatigueInput.sessions(emptyList(), live to listOf(benchSet()), exercises, NOW), NOW, live.startedAtMs)
        assertTrue(report.newLifter)
    }

    @Test
    fun `history uses completion time else start time`() {
        val completed = row(1, startedAgoH = 11, completedAgoH = 10, sets = arrayOf(benchSet()))
        val open = row(2, startedAgoH = 30, completedAgoH = null, sets = arrayOf(benchSet()))
        val sessions = FatigueInput.sessions(listOf(completed, open), null, exercises, NOW).associateBy { it.sessionId }
        assertEquals(NOW - 10 * H, sessions.getValue(1).atMs)
        assertEquals(NOW - 30 * H, sessions.getValue(2).atMs)
    }

    @Test
    fun `activities are left out`() {
        val running = SessionSet(exerciseId = 2, exerciseName = "Running", setIndex = 0, reps = 0, durationSec = 1800, done = true)
        val sessions = FatigueInput.sessions(listOf(row(1, 5, 4, sets = arrayOf(running, benchSet()))), null, exercises, NOW)
        assertEquals(listOf("Bench Press"), sessions.single().sets.map { it.exerciseName })
    }

    @Test
    fun `three years of history only reads the window and is fast`() {
        val history = (0 until 600).map { i ->
            row(i + 1L, startedAgoH = 24L * (1 + i * 1095 / 600), completedAgoH = 24L * (1 + i * 1095 / 600) - 1, sets = Array(20) { benchSet(index = it) })
        }
        val started = System.nanoTime()
        val sessions = FatigueInput.sessions(history, null, exercises, NOW)
        val report = FatigueModel.estimate(sessions, NOW, FatigueInput.firstWorkoutMs(history, null))
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $ms ms", ms < 500)
        assertTrue(sessions.all { it.atMs >= NOW - 28 * DAY })
        assertEquals(history.count { it.first.completedAtMs!! >= NOW - 28 * DAY }, sessions.size)
        assertFalse(report.newLifter)
    }

    @Test
    fun `first workout is the earliest of any origin`() {
        val history = listOf(row(1, 100), row(2, 24 * 400, imported = true), row(3, 500))
        assertEquals(NOW - 24 * 400 * H, FatigueInput.firstWorkoutMs(history, null))
        assertEquals(NOW - 24 * 400 * H, FatigueInput.firstWorkoutMs(history.reversed(), WorkoutSession(id = 9, label = "L", startedAtMs = NOW)))
    }
}
