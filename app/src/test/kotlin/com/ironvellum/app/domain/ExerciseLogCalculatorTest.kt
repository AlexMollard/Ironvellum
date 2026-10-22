package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The History tab reports a lifter's own past: grouping, order, best sets and records must each mean what they say. */
class ExerciseLogCalculatorTest {

    private val pullUp = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
    private val plank = Exercise(id = 2, name = "Plank", muscleGroup = MuscleGroup.CORE, isWeighted = false, metric = ExerciseMetric.HOLD)

    private fun row(
        session: Long,
        day: Int,
        position: Int = 0,
        set: Int,
        reps: Int,
        kg: Double? = null,
        modifiers: String = "",
        kind: String = "WORKING",
        seconds: Int? = null,
        label: String = "Pull day",
        title: String = "",
    ) = ExerciseLogRow(
        sessionId = session, startedAtMs = day * DAY, label = label, title = title, exercisePosition = position,
        setIndex = set, reps = reps, weightKg = kg, modifiers = modifiers, durationSec = seconds,
        distanceM = null, grade = null, kind = kind,
    )

    @Test
    fun `nothing logged is empty with no summary`() {
        val log = ExerciseLogCalculator.build(pullUp, emptyList())
        assertTrue(log.isEmpty)
        assertNull(log.lastAtMs)
        assertNull(log.best)
        assertNull(log.bestE1rmKg)
        assertTrue(log.trend.isEmpty())
    }

    @Test
    fun `trials come newest first with their sets in order`() {
        val log = ExerciseLogCalculator.build(
            pullUp,
            listOf(
                row(1, day = 1, set = 2, reps = 8, kg = 10.0), row(1, day = 1, set = 1, reps = 10, kg = 10.0),
                row(2, day = 5, set = 1, reps = 6, kg = 15.0),
            ),
        )
        assertEquals(listOf(2L, 1L), log.sessions.map { it.sessionId })
        assertEquals(listOf(10, 8), log.sessions[1].blocks.single().sets.map { it.figure })
        assertEquals(5 * DAY, log.lastAtMs)
    }

    @Test
    fun `the same movement twice in one trial is two blocks each with its own modifiers`() {
        val log = ExerciseLogCalculator.build(
            pullUp,
            listOf(
                row(1, day = 1, position = 0, set = 1, reps = 5, kg = 10.0, modifiers = "weighted"),
                row(1, day = 1, position = 3, set = 1, reps = 12, modifiers = "incline"),
            ),
        )
        val blocks = log.sessions.single().blocks
        assertEquals(listOf(0, 3), blocks.map { it.position })
        assertEquals(listOf("weighted", "incline"), blocks.map { it.modifiers })
    }

    @Test
    fun `warm-ups never count and a trial with only warm-ups is not listed`() {
        val log = ExerciseLogCalculator.build(
            pullUp,
            listOf(
                row(1, day = 1, set = 1, reps = 5, kg = 100.0, kind = "WARMUP"),
                row(2, day = 2, set = 1, reps = 5, kg = 20.0),
                row(2, day = 2, set = 2, reps = 3, kg = 90.0, kind = "WARMUP"),
            ),
        )
        assertEquals(listOf(2L), log.sessions.map { it.sessionId })
        assertEquals(1, log.sessions.single().blocks.single().sets.size)
        assertEquals(20.0 * (1 + 5 / 30.0), log.bestE1rmKg!!, 1e-9)
    }

    @Test
    fun `a loaded movement is judged by estimated 1RM not by reps`() {
        val log = ExerciseLogCalculator.build(
            pullUp,
            listOf(
                row(1, day = 1, set = 1, reps = 12, kg = 10.0),
                row(1, day = 1, set = 2, reps = 4, kg = 20.0),
            ),
        )
        assertTrue(log.byE1rm)
        assertEquals(1, log.sessions.single().blocks.single().bestIndex)
        assertEquals(4, log.best!!.figure)
    }

    @Test
    fun `a top set alone stands for its block`() {
        val log = ExerciseLogCalculator.build(
            pullUp,
            listOf(
                row(1, day = 1, set = 1, reps = 3, kg = 20.0, kind = "TOP"),
                row(1, day = 1, set = 2, reps = 12, kg = 19.0, kind = "BACKOFF"),
            ),
        )
        assertEquals(0, log.sessions.single().blocks.single().bestIndex)
    }

    @Test
    fun `bodyweight work is judged by reps and has no 1RM`() {
        val log = ExerciseLogCalculator.build(
            pullUp,
            listOf(row(1, day = 1, set = 1, reps = 8), row(1, day = 1, set = 2, reps = 11), row(2, day = 3, set = 1, reps = 9)),
        )
        assertFalse(log.byE1rm)
        assertNull(log.bestE1rmKg)
        assertEquals(11, log.best!!.figure)
        assertEquals(listOf(11.0, 9.0), log.trend.map { it.value })
    }

    @Test
    fun `a hold is judged by seconds`() {
        val log = ExerciseLogCalculator.build(
            plank,
            listOf(row(1, day = 1, set = 1, reps = 1, seconds = 30), row(2, day = 2, set = 1, reps = 1, seconds = 45)),
        )
        assertEquals(45, log.best!!.figure)
        assertEquals(listOf(30.0, 45.0), log.trend.map { it.value })
    }

    @Test
    fun `a record beats every earlier trial of the same variation and the first log is not one`() {
        val log = ExerciseLogCalculator.build(
            pullUp,
            listOf(
                row(1, day = 1, set = 1, reps = 5, kg = 10.0),
                row(2, day = 2, set = 1, reps = 5, kg = 12.5),
                row(3, day = 3, set = 1, reps = 5, kg = 12.5),
                // Another variation starts its own record book: this is its first log.
                row(4, day = 4, set = 1, reps = 5, kg = 30.0, modifiers = "deficit"),
            ),
        )
        val records = log.sessions.associate { it.sessionId to it.blocks.single().record }
        assertEquals(mapOf(1L to false, 2L to true, 3L to false, 4L to false), records)
    }

    @Test
    fun `two blocks tying a trial's peak mark only the first`() {
        val log = ExerciseLogCalculator.build(
            pullUp,
            listOf(
                row(1, day = 1, set = 1, reps = 5, kg = 10.0),
                row(2, day = 2, position = 0, set = 1, reps = 5, kg = 15.0),
                row(2, day = 2, position = 1, set = 1, reps = 5, kg = 15.0),
            ),
        )
        assertEquals(listOf(true, false), log.sessions.first().blocks.map { it.record })
    }

    @Test
    fun `a trial's title wins over its label and the trend is one point per trial`() {
        val log = ExerciseLogCalculator.build(
            pullUp,
            listOf(
                row(1, day = 1, set = 1, reps = 5, kg = 10.0, title = "Heavy Tuesday"),
                row(2, day = 2, set = 1, reps = 5, kg = 12.0, label = "Pull B"),
                row(2, day = 2, set = 2, reps = 5, kg = 11.0, label = "Pull B"),
            ),
        )
        assertEquals(listOf("Pull B", "Heavy Tuesday"), log.sessions.map { it.label })
        assertEquals(2, log.trend.size)
    }

    @Test
    fun `visible caps the list newest first and the whole history stays summarised`() {
        val rows = (1..120).map { row(it.toLong(), day = it, set = 1, reps = 5, kg = 10.0 + it) }
        val log = ExerciseLogCalculator.build(pullUp, rows)
        assertEquals(120, log.sessions.size)
        assertEquals((120 downTo 71).map { it.toLong() }, log.visible(ExerciseLog.PAGE).map { it.sessionId })
        assertEquals(130.0 * (1 + 5 / 30.0), log.bestE1rmKg!!, 1e-9)
        assertEquals(120, log.trend.size)
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
