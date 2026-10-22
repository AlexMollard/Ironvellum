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
    private val pullUp = Exercise(id = 3, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
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
        listOf(bench, pullUp, run).associateBy { it.id },
        { 0.0 },
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
    fun `a lift is new only when its latest session beat every earlier one`() {
        // Best came two weeks of sessions ago, then a lighter session: still inside the window, not a new PR.
        val slipped = board(
            session(1, 20) to listOf(set(bench, 90.0, 5)),
            session(2, 8) to listOf(set(bench, 100.0, 5)),
            session(3, 2) to listOf(set(bench, 95.0, 5)),
        ).single()
        assertFalse(LiftRecords.isFresh(slipped, now))
        // The first session of a lift is history starting, not a PR.
        val first = board(session(1, 2) to listOf(set(bench, 100.0, 5))).single()
        assertFalse(LiftRecords.isFresh(first, now))
        // A session that beats the earlier ones, inside the window, is.
        val up = board(
            session(1, 20) to listOf(set(bench, 90.0, 5)),
            session(2, 2) to listOf(set(bench, 100.0, 5)),
        ).single()
        assertTrue(LiftRecords.isFresh(up, now))
    }

    @Test
    fun `a lift with no history that old has no trend yet and an old best is not fresh`() {
        val lift = board(session(1, 30) to listOf(set(bench, 100.0, 1))).single()
        assertNull(lift.deltaKg)
        assertFalse(LiftRecords.isFresh(lift, now))
    }

    @Test
    fun `a record carries the actual set that set it, and whether its load is added to the body`() {
        val rows = board(
            session(1, 20) to listOf(set(pullUp, 5.0, 8)),
            session(2, 3) to listOf(set(pullUp, 11.0, 8), set(pullUp, 8.0, 6), set(bench, 100.0, 5)),
        )
        val pull = rows.first { it.name == "Pull-up" }
        assertEquals(11.0, pull.bestSet!!.weightKg!!, 0.0)
        assertEquals(8, pull.bestSet!!.reps)
        assertTrue(pull.bestSetAddsToBody)
        val squat = rows.first { it.name == "Bench Press" }
        assertEquals(100.0, squat.bestSet!!.weightKg!!, 0.0)
        assertFalse(squat.bestSetAddsToBody)
    }

    // ---- a bodyweight lift counts the body ----

    private val pushUp = Exercise(id = 5, name = "Push-up", muscleGroup = MuscleGroup.PUSH, isWeighted = false)

    private fun withBody(kg: Double, vararg trials: Pair<WorkoutSession, List<SessionSet>>) = LiftRecords.board(
        trials.map { it.first },
        trials.associate { it.first.id to it.second },
        listOf(bench, pullUp, pushUp).associateBy { it.id },
        { kg },
        now,
    )

    @Test
    fun `a weighted push-up record is the total-load estimate shown as added load`() {
        val rows = withBody(79.8, session(1, 5) to listOf(set(pushUp, 18.0, 12)))
        // (0.65 x 79.8 + 18) x (1 + 12/30) - 0.65 x 79.8 = 45.95, not the plates' 25.2
        assertEquals(45.95, rows.single().bestE1rmKg, 0.01)
        assertTrue(rows.single().bestSetAddsToBody)
    }

    @Test
    fun `a heavier set at fewer reps is the better record once the body counts`() {
        val rows = withBody(
            79.8,
            session(1, 20) to listOf(set(pushUp, 18.0, 12)),
            session(2, 3) to listOf(set(pushUp, 55.0, 3)),
        )
        val row = rows.single()
        assertEquals(55.0, row.bestSet!!.weightKg!!, 0.0)
        assertEquals(listOf(45.95, 65.69), row.series.map { Math.round(it * 100) / 100.0 })
        assertTrue(LiftRecords.isFresh(row, now))
    }

    @Test
    fun `a barbell lift ignores the body weight`() {
        val rows = withBody(79.8, session(1, 5) to listOf(set(bench, 100.0, 5)))
        assertEquals(ProgramRules.epley(100.0, 5), rows.single().bestE1rmKg, 1e-9)
    }

    @Test
    fun `gains and new lifts read the same total-load estimate`() {
        val history = listOf(
            session(1, 40) to listOf(set(pushUp, 18.0, 12), set(pullUp, 10.0, 8)),
            session(2, 3) to listOf(set(pushUp, 55.0, 3), set(pullUp, 10.0, 8)),
        )
        val catalogue = listOf(pushUp, pullUp).associateBy { it.id }
        val gains = LiftRecords.gains(history, catalogue, { 79.8 }, now - 10 * day, now)
        // The push-up improved (45.95 -> 65.69); the pull-up repeated itself and is no gain.
        assertEquals(listOf("Push-up"), gains.map { it.name })
        assertEquals(65.69 - 45.95, gains.single().gainKg, 0.01)
        assertTrue(gains.single().addsToBody)
        assertTrue(LiftRecords.newLifts(history, catalogue, { 79.8 }, now - 10 * day, now).isEmpty())
    }
}
