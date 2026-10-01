package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Strength Rank from the lift boards: unranked, every band edge, lifts combined, the window. */
class RankTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day
    private val bodyweight = 100.0

    private fun set(name: String, reps: Int, weightKg: Double?) =
        SessionSet(exerciseId = 1, exerciseName = name, setIndex = 0, reps = reps, weightKg = weightKg, done = true)

    private fun trial(daysAgo: Int, vararg sets: SessionSet): Pair<WorkoutSession, List<SessionSet>> {
        val at = now - daysAgo * day
        return WorkoutSession(id = 1, label = "t", startedAtMs = at - 3_600_000, completedAtMs = at) to sets.toList()
    }

    /** A one-rep set whose Epley e1RM is [ratio] x bodyweight. */
    private fun at(name: String, ratio: Double) =
        set(name, reps = 1, weightKg = ratio * bodyweight / (1.0 + 1 / 30.0))

    private fun rank(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        sex: Sex = Sex.MALE,
        bw: (Long) -> Double = { bodyweight },
    ) = Rank.current(history, bw, sex, now)

    @Test
    fun `no lifts is unranked`() {
        assertNull(rank(emptyList()))
    }

    @Test
    fun `no bodyweight reading is unranked`() {
        assertNull(rank(listOf(trial(1, at("Bench Press", 1.5))), bw = { 0.0 }))
    }

    @Test
    fun `skill rungs alone are unranked`() {
        val history = listOf(trial(1, set("Push-up", reps = 50, weightKg = null)))
        assertTrue(LiftBoards.marks(history, { bodyweight }, Sex.MALE, now).isNotEmpty())
        assertNull(rank(history))
    }

    @Test
    fun `a lift below the first floor is Untrained, not unranked`() {
        assertEquals(Rank.UNTRAINED, rank(listOf(trial(1, at("Bench Press", 0.3)))))
    }

    @Test
    fun `steps map two to a band`() {
        val expected = listOf(
            Rank.UNTRAINED, Rank.UNTRAINED, Rank.UNTRAINED,
            Rank.NOVICE, Rank.NOVICE,
            Rank.INTERMEDIATE, Rank.INTERMEDIATE,
            Rank.ADVANCED, Rank.ADVANCED,
            Rank.ELITE, Rank.ELITE,
        )
        assertEquals(expected, (0..LiftBoards.MAX_STEP).map { Rank.forStep(it) })
    }

    @Test
    fun `each band starts at its strength standard floor`() {
        // Male bench floors: Novice 1.0, Intermediate 1.25, Advanced 1.5, Elite 2.0 x bodyweight.
        val edges = listOf(1.0 to Rank.NOVICE, 1.25 to Rank.INTERMEDIATE, 1.5 to Rank.ADVANCED, 2.0 to Rank.ELITE)
        val below = listOf(Rank.UNTRAINED, Rank.NOVICE, Rank.INTERMEDIATE, Rank.ADVANCED)
        edges.forEachIndexed { i, (floor, band) ->
            assertEquals("at $floor", band, rank(listOf(trial(1, at("Bench Press", floor + 1e-6)))))
            assertEquals("under $floor", below[i], rank(listOf(trial(1, at("Bench Press", floor - 1e-3)))))
        }
    }

    @Test
    fun `the floors follow sex`() {
        // 0.75 x bodyweight is a woman's Intermediate bench floor and a man's Ash.
        val history = listOf(trial(1, at("Bench Press", 0.76)))
        assertEquals(Rank.INTERMEDIATE, rank(history, sex = Sex.FEMALE))
        assertEquals(Rank.UNTRAINED, rank(history, sex = Sex.MALE))
    }

    @Test
    fun `lifts combine as the mean step, rounded down`() {
        // Squat 2.3 = Gold I (7), bench 1.0 = Bone I (3): mean 5 = Intermediate.
        assertEquals(
            Rank.INTERMEDIATE,
            rank(listOf(trial(1, at("Back Squat", 2.3), at("Bench Press", 1.0)))),
        )
        // Squat 2.3 (7), bench 0.9 (Ash II, 2): mean 4.5 rounds down to Novice.
        assertEquals(
            Rank.NOVICE,
            rank(listOf(trial(1, at("Back Squat", 2.3), at("Bench Press", 0.9)))),
        )
    }

    @Test
    fun `one specialty lift does not carry the rank`() {
        // Deadlift Umbral II (10) beside an Ash I bench (1) and squat (1): mean 4.
        val history = listOf(trial(1, at("Deadlift", 4.0), at("Bench Press", 0.6), at("Back Squat", 0.8)))
        assertEquals(Rank.NOVICE, rank(history))
    }

    @Test
    fun `each lift counts its best set in the window`() {
        val history = listOf(
            trial(3, at("Bench Press", 1.0)),
            trial(10, at("Bench Press", 1.5)),
        )
        assertEquals(Rank.ADVANCED, rank(history))
    }

    @Test
    fun `strength older than the window no longer counts`() {
        val old = trial(Rank.WINDOW_DAYS + 1, at("Bench Press", 2.0))
        assertNull(rank(listOf(old)))
        assertEquals(Rank.NOVICE, rank(listOf(old, trial(5, at("Bench Press", 1.0)))))
        assertEquals(Rank.ELITE, rank(listOf(trial(Rank.WINDOW_DAYS - 1, at("Bench Press", 2.0)))))
    }

    @Test
    fun `an intermediate-looking lifter at 85 kg reads Novice`() {
        // 100 kg bench, 140 kg squat, 180 kg deadlift as singles at 85 kg.
        val history = listOf(
            trial(
                2,
                set("Bench Press", reps = 1, weightKg = 100.0),
                set("Back Squat", reps = 1, weightKg = 140.0),
                set("Deadlift", reps = 1, weightKg = 180.0),
            ),
        )
        val steps = LiftBoards.marks(history, { 85.0 }, Sex.MALE, now).associate { it.lift to it.step }
        assertEquals(mapOf(Lift.BENCH to 4, Lift.SQUAT to 4, Lift.DEADLIFT to 5), steps)
        assertEquals(Rank.NOVICE, Rank.current(history, { 85.0 }, Sex.MALE, now))
    }
}
