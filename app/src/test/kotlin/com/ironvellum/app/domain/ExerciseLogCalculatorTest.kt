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

    // ---- a bodyweight lift counts the body ----

    private val pushUp = Exercise(id = 3, name = "Push-up", muscleGroup = MuscleGroup.PUSH, isWeighted = false)

    /** The owner's weigh-ins: 79.0 kg on day 0, 80.0 kg on day 20. */
    private val weighIns = SetRecords.bodyweightLookup(
        listOf(StatEntry(1, 0, 79.0, 180.0), StatEntry(2, 20 * DAY, 80.0, 180.0)),
    )

    @Test
    fun `a weighted push-up is estimated on the total load and shown as added load`() {
        val log = ExerciseLogCalculator.build(pushUp, listOf(row(1, day = 25, set = 1, reps = 12, kg = 18.0)), weighIns)
        // 80 kg on day 25 (the day-20 reading), share 0.65: (52 + 18) x 1.4 - 52 = 46.0
        assertEquals(46.0, log.bestE1rmKg!!, 0.01)
        assertTrue(log.addsToBody)
        assertFalse(log.bodyMissing)
    }

    @Test
    fun `the body weight is the reading in force on the day of the trial`() {
        val rows = listOf(row(1, day = 5, set = 1, reps = 10, kg = 20.0), row(2, day = 25, set = 1, reps = 10, kg = 20.0))
        val log = ExerciseLogCalculator.build(pushUp, rows, weighIns)
        val share = BodyShare.of(pushUp)
        // Newest first: day 25 uses 80 kg, day 5 uses the 79 kg reading.
        assertEquals(BodyShare.e1rmAddedKg(20.0, 10, share, 80.0), log.sessions[0].blocks.single().sets.single().e1rmKg!!, 1e-9)
        assertEquals(BodyShare.e1rmAddedKg(20.0, 10, share, 79.0), log.sessions[1].blocks.single().sets.single().e1rmKg!!, 1e-9)
    }

    @Test
    fun `a trial before any weigh-in uses the earliest reading and no readings falls back to added load with a caveat`() {
        val early = SetRecords.bodyweightLookup(listOf(StatEntry(1, 10 * DAY, 78.0, 180.0)))
        val before = ExerciseLogCalculator.build(pushUp, listOf(row(1, day = 1, set = 1, reps = 10, kg = 20.0)), early)
        assertEquals(BodyShare.e1rmAddedKg(20.0, 10, 0.65, 78.0), before.bestE1rmKg!!, 1e-9)
        assertFalse(before.bodyMissing)

        val none = ExerciseLogCalculator.build(pushUp, listOf(row(1, day = 1, set = 1, reps = 10, kg = 20.0)), SetRecords.bodyweightLookup(emptyList()))
        assertEquals(ProgramRules.epley(20.0, 10), none.bestE1rmKg!!, 1e-9)
        assertTrue(none.bodyMissing)
    }

    @Test
    fun `a barbell lift is never adjusted by body weight`() {
        val bench = Exercise(id = 4, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
        val log = ExerciseLogCalculator.build(bench, listOf(row(1, day = 25, set = 1, reps = 5, kg = 100.0)), weighIns)
        assertEquals(ProgramRules.epley(100.0, 5), log.bestE1rmKg!!, 1e-9)
        assertFalse(log.addsToBody)
        assertFalse(log.bodyMissing)
    }

    // ---- variations ----

    private fun twoVariations() = listOf(
        row(1, day = 1, position = 0, set = 1, reps = 10, kg = 10.0, modifiers = "weighted, incline"),
        row(2, day = 5, position = 0, set = 1, reps = 10, kg = 20.0, modifiers = "weighted, deficit, elevated"),
        row(3, day = 9, position = 0, set = 1, reps = 10, kg = 12.0, modifiers = "weighted, incline"),
        row(3, day = 9, position = 1, set = 1, reps = 8, kg = 30.0, modifiers = "weighted, deficit, elevated"),
    )

    @Test
    fun `the variations the lifter used are listed most recent first and weighted alone is not one`() {
        val log = ExerciseLogCalculator.build(pushUp, twoVariations() + row(4, day = 2, set = 1, reps = 5, kg = 30.0, modifiers = "weighted"), weighIns)
        assertEquals(listOf("deficit,elevated", "incline", ""), log.variations.map { it.key })
        assertEquals(listOf(2, 2, 1), log.variations.map { it.sessions })
        // Without the plain trial, "weighted" never made a third.
        assertEquals(2, ExerciseLogCalculator.build(pushUp, twoVariations(), weighIns).variations.size)
    }

    @Test
    fun `it opens on the variation of the most recent block`() {
        // Trial 3 did incline first, then deficit and elevated: the latest block is the deficit one.
        assertEquals("deficit,elevated", ExerciseLogCalculator.build(pushUp, twoVariations(), weighIns).defaultVariation)
        // One variation only: nothing to pick.
        val single = ExerciseLogCalculator.build(pushUp, listOf(row(1, day = 1, set = 1, reps = 5, kg = 10.0, modifiers = "incline")), weighIns)
        assertNull(single.defaultVariation)
        assertEquals(1, single.variations.size)
    }

    @Test
    fun `choosing a variation filters the list the trend and the summary`() {
        val log = ExerciseLogCalculator.build(pushUp, twoVariations(), weighIns, variation = "incline")
        assertEquals(listOf(3L, 1L), log.sessions.map { it.sessionId })
        // Trial 3's deficit block is not in the incline list.
        assertEquals(listOf("weighted, incline", "weighted, incline"), log.sessions.flatMap { s -> s.blocks.map { it.modifiers } })
        assertEquals(2, log.trend.size)
        assertEquals(12, log.best!!.weightKg!!.toInt())
        assertEquals("incline", log.selected)
        // The list of chips is still everything the lifter has done.
        assertEquals(2, log.variations.size)
    }

    @Test
    fun `all hides the trend when the variations differ and keeps it for one`() {
        val all = ExerciseLogCalculator.build(pushUp, twoVariations(), weighIns)
        assertTrue(all.trend.isEmpty())
        assertEquals(3, all.sessions.size)
        val one = ExerciseLogCalculator.build(pushUp, twoVariations().filter { it.modifiers.contains("incline") }, weighIns)
        assertEquals(2, one.trend.size)
    }

    @Test
    fun `a record is judged inside its own variation`() {
        val log = ExerciseLogCalculator.build(pushUp, twoVariations(), weighIns, variation = "deficit,elevated")
        // 30 kg x 8 on day 9 beats 20 kg x 10 on day 5 within the deficit variation, whatever the incline did.
        assertEquals(listOf(true), log.sessions.first().blocks.map { it.record })
    }

    @Test
    fun `chip labels read as the lifter's own modifiers`() {
        assertEquals("Deficit · elevated", variationLabel("deficit,elevated", "Standard"))
        assertEquals("Incline", variationLabel("incline", "Standard"))
        assertEquals("Standard", variationLabel("", "Standard"))
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
