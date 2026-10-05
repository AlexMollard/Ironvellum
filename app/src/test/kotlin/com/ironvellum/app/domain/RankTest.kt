package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Strength Rank by pattern: both routes, the mean, the window, and the breakdown. */
class RankTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day
    private val bodyweight = 100.0

    private fun set(name: String, reps: Int, weightKg: Double? = null, mods: String = "", seconds: Int? = null) =
        SessionSet(
            exerciseId = 1, exerciseName = name, setIndex = 0, reps = reps, weightKg = weightKg,
            modifiers = mods, done = true, durationSec = seconds,
        )

    private fun hold(name: String, seconds: Int) = set(name, reps = 0, seconds = seconds)

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
        practices: List<SkillPractice> = emptyList(),
    ) = Rank.current(history, bw, sex, now, practices)

    private fun breakdown(history: List<Pair<WorkoutSession, List<SessionSet>>>, bw: (Long) -> Double = { bodyweight }) =
        Rank.breakdown(history, bw, Sex.MALE, now)

    /** The owner's phone log, 2026-10-05, at 78.9 kg. */
    private val ownerLog = listOf(
        trial(
            16,
            set("Handstand Push-up", 7),
            set("Archer Push-up", 7, 7.5, "weighted, deficit, elevated"),
            set("Push-up", 10, 7.5, "deficit, elevated, weighted"),
            hold("Hollow Hold", 45),
        ),
        trial(
            7,
            set("Pull-up", 5, 15.2, "weighted"),
            set("Pull-up", 3, 15.2, "weighted"),
            set("Chin-up", 8),
            set("Dumbbell Row", 8, 24.0),
        ),
        trial(
            5,
            set("Pistol Squat", 8, 7.7, "weighted"),
            set("Shrimp Squat", 7, 7.7, "weighted"),
            set("Hanging Leg Raise", 10),
        ),
        trial(
            1,
            set("Wall HSPU", 5),
            set("Archer Push-up", 7, 7.7, "weighted, deficit, elevated"),
            hold("Tuck Planche", 5),
            set("Push-up", 12, 7.7, "weighted, deficit, elevated"),
        ),
    )
    private val ownerBw: (Long) -> Double = { 78.9 }

    @Test
    fun `no work is unranked`() {
        assertNull(rank(emptyList()))
    }

    @Test
    fun `a lift without a bodyweight reading is unranked`() {
        assertNull(rank(listOf(trial(1, at("Bench Press", 1.5))), bw = { 0.0 }))
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
        val edges = listOf(1.0 to Rank.NOVICE, 1.25 to Rank.INTERMEDIATE, 1.5 to Rank.ADVANCED, 2.0 to Rank.ELITE)
        val below = listOf(Rank.UNTRAINED, Rank.NOVICE, Rank.INTERMEDIATE, Rank.ADVANCED)
        edges.forEachIndexed { i, (floor, band) ->
            assertEquals("at $floor", band, rank(listOf(trial(1, at("Bench Press", floor + 1e-6)))))
            assertEquals("under $floor", below[i], rank(listOf(trial(1, at("Bench Press", floor - 1e-3)))))
        }
    }

    @Test
    fun `the floors follow sex`() {
        val history = listOf(trial(1, at("Bench Press", 0.76)))
        assertEquals(Rank.INTERMEDIATE, rank(history, sex = Sex.FEMALE))
        assertEquals(Rank.UNTRAINED, rank(history, sex = Sex.MALE))
    }

    @Test
    fun `patterns combine as the mean step, rounded down`() {
        // Squat 2.3 = Legs 7, bench 1.0 = Push 3: mean 5.
        assertEquals(Rank.INTERMEDIATE, rank(listOf(trial(1, at("Back Squat", 2.3), at("Bench Press", 1.0)))))
        // Squat 2.3 (7), bench 0.9 (2): mean 4.5 rounds down to Novice.
        assertEquals(Rank.NOVICE, rank(listOf(trial(1, at("Back Squat", 2.3), at("Bench Press", 0.9)))))
    }

    @Test
    fun `a weak lift in a trained pattern does not lower it`() {
        val strong = listOf(trial(1, at("Deadlift", 4.0), at("Bench Press", 0.6)))
        val withWeakSquat = listOf(trial(1, at("Deadlift", 4.0), at("Bench Press", 0.6), at("Back Squat", 0.8)))
        assertEquals(Rank.INTERMEDIATE, rank(strong))
        assertEquals(rank(strong), rank(withWeakSquat))
    }

    @Test
    fun `each pattern counts its best in the window`() {
        val history = listOf(trial(3, at("Bench Press", 1.0)), trial(10, at("Bench Press", 1.5)))
        assertEquals(Rank.ADVANCED, rank(history))
    }

    @Test
    fun `work older than the window no longer counts`() {
        val old = trial(Rank.WINDOW_DAYS + 1, at("Bench Press", 2.0), set("Pistol Squat", 8))
        assertNull(rank(listOf(old)))
        assertEquals(Rank.NOVICE, rank(listOf(old, trial(5, at("Bench Press", 1.0)))))
        assertEquals(Rank.ELITE, rank(listOf(trial(Rank.WINDOW_DAYS - 1, at("Bench Press", 2.0)))))
    }

    @Test
    fun `the owner's log reads Intermediate`() {
        val b = breakdown(ownerLog, ownerBw)!!
        assertEquals(mapOf(Pattern.PULL to 4, Pattern.PUSH to 7, Pattern.LEGS to 5), b.patterns.associate { it.pattern to it.step })
        assertEquals(5, b.step)
        assertEquals(Rank.INTERMEDIATE, b.band)
    }

    @Test
    fun `core work never changes the rank`() {
        val withCore = ownerLog + trial(0, hold("Hollow Hold", 60), set("Hanging Leg Raise", 10), set("Hanging Knee Raise", 10))
        assertEquals(breakdown(ownerLog, ownerBw)!!.patterns, breakdown(withCore, ownerBw)!!.patterns)
    }

    @Test
    fun `a specialist is ranked on the patterns they train`() {
        val b = breakdown(listOf(trial(1, at("Bench Press", 1.3))))!!
        assertEquals(listOf(Pattern.PUSH), b.patterns.map { it.pattern })
        assertEquals(Rank.INTERMEDIATE, b.band)
    }

    @Test
    fun `skills rank without a weigh-in`() {
        assertEquals(Rank.INTERMEDIATE, rank(listOf(trial(1, set("Pistol Squat", 8))), bw = { 0.0 }))
        assertEquals(Rank.NOVICE, rank(listOf(trial(1, set("Push-up", 50))), bw = { 0.0 }))
    }

    @Test
    fun `a skill clears only at its standard`() {
        assertNull(rank(listOf(trial(1, set("Pistol Squat", 7)))))
        assertEquals(Rank.INTERMEDIATE, rank(listOf(trial(1, set("Pistol Squat", 8, 7.7, "weighted")))))
        assertNull(rank(listOf(trial(1, hold("Tuck Planche", 14)))))
        assertEquals(Rank.INTERMEDIATE, rank(listOf(trial(1, hold("Tuck Planche", 15)))))
    }

    @Test
    fun `assisted sets and claims never clear a skill`() {
        assertNull(rank(listOf(trial(1, set("Pistol Squat", 8, mods = "assisted")))))
        val claim = SkillPractice("Pistol Squat", now - day, claimed = true, value = 8)
        assertNull(rank(emptyList(), practices = listOf(claim)))
        val practice = SkillPractice("Pistol Squat", now - day, claimed = false, value = 8)
        assertEquals(Rank.INTERMEDIATE, rank(emptyList(), practices = listOf(practice)))
        val oldPractice = practice.copy(practicedAtMs = now - (Rank.WINDOW_DAYS + 1) * day)
        assertNull(rank(emptyList(), practices = listOf(oldPractice)))
    }

    @Test
    fun `the breakdown names the source and the next targets`() {
        val b = breakdown(ownerLog, ownerBw)!!
        val pull = b.patterns.single { it.pattern == Pattern.PULL }
        assertEquals("Pull-up 5 × +15.2 kg", pull.source)
        assertEquals("+0.6 kg on Pull-up × 5", pull.liftTarget)
        assertTrue(pull.skillTarget!!, pull.skillTarget!!.startsWith("Pull-up: "))
        val push = b.patterns.single { it.pattern == Pattern.PUSH }
        assertNull(push.liftTarget)
        assertTrue(push.skillTarget!!, push.skillTarget!!.startsWith("90-Degree Push-up: "))
        val legs = b.patterns.single { it.pattern == Pattern.LEGS }
        assertEquals("Pistol Squat", legs.source)
        assertTrue(legs.skillTarget!!, legs.skillTarget!!.startsWith("Dragon Squat: "))
    }

    @Test
    fun `the lift target is enough to clear the boundary`() {
        // 94.1 kg moved needs 0.58 kg more; 0.6 kg must reach Silver I.
        val bumped = LiftBoards.tieredScore(set("Pull-up", 5, 15.8, "weighted"), 78.9, Sex.MALE)!!
        assertEquals(5, bumped.step)
    }

    @Test
    fun `the headline counts steps to the next band`() {
        val b = breakdown(ownerLog, ownerBw)!!
        assertEquals(Rank.ADVANCED, b.nextBand)
        assertEquals(5, b.stepsToNext) // 7 x 3 patterns - 16
        assertEquals(1f / 6f, b.progress, 1e-6f) // (16 - 15) / (2 x 3)
    }

    @Test
    fun `elite has no next band and no lift target`() {
        val b = breakdown(listOf(trial(1, at("Bench Press", 2.6))))!!
        assertEquals(10, b.step)
        assertEquals(Rank.ELITE, b.band)
        assertNull(b.nextBand)
        assertNull(b.stepsToNext)
        assertEquals(1f, b.progress, 0f)
        assertNull(b.patterns.single().liftTarget)
    }

    @Test
    fun `an intermediate-looking lifter at 85 kg reads Novice`() {
        val history = listOf(
            trial(
                2,
                set("Bench Press", reps = 1, weightKg = 100.0),
                set("Back Squat", reps = 1, weightKg = 140.0),
                set("Deadlift", reps = 1, weightKg = 180.0),
            ),
        )
        // Push 4, Legs max(4, 5) = 5: mean 4.5 rounds down.
        assertEquals(Rank.NOVICE, Rank.current(history, { 85.0 }, Sex.MALE, now))
    }
}
