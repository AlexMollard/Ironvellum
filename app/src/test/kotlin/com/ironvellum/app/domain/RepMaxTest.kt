package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one peak rule: heavier (at 3+ reps) or more reps, judged against history before the trial, paid by how big the jump is. */
class RepMaxTest {

    private val bodyweight = 79.0

    private fun m(figure: Int, kg: Double = 0.0) = RepMax.Mark(figure, kg)

    private fun judge(figure: Int, kg: Double, vararg history: RepMax.Mark, hold: Boolean = false) =
        RepMax.judge(figure, kg, hold, history.toList())

    private fun set(name: String, id: Long, index: Int, reps: Int, kg: Double? = null, seconds: Int? = null, position: Int = 0) =
        SessionSet(
            exerciseId = id, exerciseName = name, exercisePosition = position, setIndex = index,
            reps = reps, weightKg = kg, done = true, durationSec = seconds,
        )

    private val pullUp = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false, metric = ExerciseMetric.REPS)
    private val bench = Exercise(id = 2, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true, metric = ExerciseMetric.REPS)
    private val lateral = Exercise(id = 3, name = "Lateral Raise", muscleGroup = MuscleGroup.PUSH, isWeighted = true, metric = ExerciseMetric.REPS)
    private val plank = Exercise(id = 4, name = "Plank", muscleGroup = MuscleGroup.CORE, isWeighted = false, metric = ExerciseMetric.HOLD)
    private val catalogue = listOf(pullUp, bench, lateral, plank).associateBy { it.id }

    private fun peaksOf(history: Map<String, List<RepMax.Mark>>, vararg today: SessionSet) =
        SessionPeaks.of(today.toList(), history, bodyweight) { catalogue[it.exerciseId] }

    // ------------------------------------------------------------ the rule

    @Test
    fun aHeavierSetForTheSameOrMoreRepsIsAHeavierPeak() {
        // 17.7 kg x 4 beats the best load for 4 or more reps (15 x 5), though 4 x 17.7 is less than 5 x 15.
        val (kind, was) = judge(4, 17.7, m(5, 15.0), m(8, 10.0))!!
        assertEquals(RepMax.Kind.HEAVIER, kind)
        assertEquals(m(5, 15.0), was)
    }

    @Test
    fun aHeavySingleOrDoubleNeverCounts() {
        assertNull(judge(2, 25.0, m(5, 15.0)))
        assertNull(judge(1, 40.0, m(5, 15.0)))
        // Three reps is the floor.
        assertEquals(RepMax.Kind.HEAVIER, judge(RepMax.MIN_PEAK_REPS, 25.0, m(5, 15.0))!!.first)
    }

    @Test
    fun aLighterSetThatBeatsNoRepMaxIsNotAPeak() {
        assertNull(judge(6, 10.0, m(8, 12.0)))
    }

    @Test
    fun anEqualSetIsNotAPeak() {
        assertNull(judge(5, 15.0, m(5, 15.0)))
        assertNull(judge(4, 15.0, m(5, 15.0)))
    }

    @Test
    fun moreRepsAtTheSameLoadIsAMoreRepsPeak() {
        val (kind, was) = judge(12, 10.0, m(10, 10.0), m(8, 12.0))!!
        assertEquals(RepMax.Kind.MORE_REPS, kind)
        assertEquals(m(10, 10.0), was)
    }

    @Test
    fun moreRepsAtALighterLoadBeatsTheHeavierSetsRepsOnly() {
        // 12 kg x 8 after 15.2 kg x 5: more reps than ever at 12 kg or more.
        assertEquals(RepMax.Kind.MORE_REPS, judge(8, 12.0, m(5, 15.2))!!.first)
    }

    @Test
    fun aHoldNeedsMoreSecondsAtTheSameOrGreaterLoad() {
        assertEquals(RepMax.Kind.MORE_REPS, judge(45, 0.0, m(30), hold = true)!!.first)
        assertNull(judge(45, 0.0, m(60), hold = true))
        // A hold is never a "heavier" peak, even with load on it.
        assertEquals(RepMax.Kind.MORE_REPS, judge(30, 10.0, m(25, 10.0), hold = true)!!.first)
        assertNull(judge(20, 12.0, m(25, 10.0), hold = true))
    }

    @Test
    fun aBodyweightOnlyLiftIsJudgedOnReps() {
        assertEquals(RepMax.Kind.MORE_REPS, judge(12, 0.0, m(10))!!.first)
        assertNull(judge(10, 0.0, m(10)))
    }

    @Test
    fun theFirstTimeIsHistoryStartingNotAPeak() {
        assertNull(judge(10, 50.0))
    }

    @Test
    fun heavierAndMoreRepsThanEverIsAHeavierPeakAgainstTheHeaviestSet() {
        val (kind, was) = judge(8, 20.0, m(5, 15.0))!!
        assertEquals(RepMax.Kind.HEAVIER, kind)
        assertEquals(m(5, 15.0), was)
    }

    // ------------------------------------------------------------ the trial

    @Test
    fun aTrialIsJudgedAgainstHistoryBeforeItAndNeverAgainstItsOwnEarlierSets() {
        val history = mapOf("pull-up" to listOf(m(5, 15.0)))
        val peaks = peaksOf(history, set("Pull-up", 1, 0, 4, 17.7), set("Pull-up", 1, 1, 4, 17.7), set("Pull-up", 1, 2, 3, 17.7))
        val peak = peaks.single()
        // Every set beat the history, so all three count; one lift, one segment.
        assertEquals(3, peak.count)
        assertEquals(setOf(0, 1, 2), peak.setIndexes)
        assertEquals(RepMax.Kind.HEAVIER, peak.kind)
        assertEquals(m(5, 15.0), peak.was)
    }

    @Test
    fun unloggedSetsWarmUpsAndActivitiesNeverPeak() {
        val history = mapOf("pull-up" to listOf(m(5, 15.0)))
        val unticked = set("Pull-up", 1, 0, 4, 17.7).copy(done = false)
        assertTrue(peaksOf(history, unticked).isEmpty())
        assertTrue(peaksOf(emptyMap(), set("Pull-up", 1, 0, 4, 17.7)).isEmpty())
    }

    @Test
    fun theBookKeepsWorkingStrengthSetsFromTheTrialsBeforeOnly() {
        val old = WorkoutSession(id = 1, label = "old", startedAtMs = 1_000, completedAtMs = 2_000)
        val this_ = WorkoutSession(id = 2, label = "now", startedAtMs = 5_000, completedAtMs = 6_000)
        val warm = set("Pull-up", 1, 0, 8, 5.0).copy(kind = SetKind.WARMUP)
        val book = RepMax.book(
            listOf(old to listOf(set("Pull-up", 1, 1, 5, 15.0), warm), this_ to listOf(set("Pull-up", 1, 0, 9, 30.0))),
            excludeSessionId = 2,
        )
        assertEquals(listOf(m(5, 15.0)), book["pull-up"])
    }

    // ------------------------------------------------------------ what it pays

    private val history = mapOf(
        "pull-up" to listOf(m(5, 15.0)),
        "bench press" to listOf(m(5, 100.0)),
        "lateral raise" to listOf(m(10, 10.0)),
        "plank" to listOf(m(30)),
    )

    @Test
    fun aPullUpAtPlusTwoPointSevenKgPaysTheFullBonus() {
        // 79 + 15 = 94 kg to 79 + 17.7 = 96.7 kg is +2.9%, over the 2.5% a compound lift needs.
        val peak = peaksOf(history, set("Pull-up", 1, 0, 4, 17.7)).single()
        assertEquals(RepMax.Kind.HEAVIER, peak.kind)
        assertEquals(Xp.PEAK_BONUS, peak.xp)
    }

    @Test
    fun creepingTwoTenthsOfAKiloOnACompoundPaysAtMostOne() {
        val peak = peaksOf(history, set("Pull-up", 1, 0, 5, 15.2)).single()
        assertEquals(RepMax.Kind.HEAVIER, peak.kind)
        assertTrue("paid ${peak.xp}", peak.xp <= 1)
    }

    @Test
    fun aHalfKiloOnABenchPressPaysAboutThree() {
        // 100 to 100.5 is +0.5% of the bar, a fifth of the 2.5% that pays in full.
        assertEquals(3, peaksOf(history, set("Bench Press", 2, 0, 5, 100.5)).single().xp)
    }

    @Test
    fun aHalfKiloOnALateralRaisePaysInFullBecauseItIsIsolation() {
        // 10 to 10.5 kg is +5%, the full-size jump for an isolation lift.
        val peak = peaksOf(history, set("Lateral Raise", 3, 0, 10, 10.5)).single()
        assertEquals(Xp.PEAK_BONUS, peak.xp)
    }

    @Test
    fun oneMoreRepPaysHalfAndTwoPayInFull() {
        assertEquals(8, peaksOf(history, set("Lateral Raise", 3, 0, 11, 10.0)).single().xp)
        assertEquals(Xp.PEAK_BONUS, peaksOf(history, set("Lateral Raise", 3, 0, 12, 10.0)).single().xp)
        assertEquals(RepMax.Kind.MORE_REPS, peaksOf(history, set("Lateral Raise", 3, 0, 12, 10.0)).single().kind)
    }

    @Test
    fun aHoldPaysByTheShareOfSecondsItGained() {
        // 30 to 31 s is 3%, a third of the 10% that pays in full.
        assertEquals(5, peaksOf(history, set("Plank", 4, 0, 0, seconds = 31)).single().xp)
        assertEquals(Xp.PEAK_BONUS, peaksOf(history, set("Plank", 4, 0, 0, seconds = 45)).single().xp)
    }

    @Test
    fun aPeakThatPaysNothingIsStillShown() {
        // +0.01 kg on a bench press rounds to no XP, but the set is still a heavier peak.
        val peak = peaksOf(history, set("Bench Press", 2, 0, 5, 100.01)).single()
        assertEquals(0, peak.xp)
        assertEquals(RepMax.Kind.HEAVIER, peak.kind)
    }

    @Test
    fun theHeadlineIsTheSetThatPaysMost() {
        val peak = peaksOf(history, set("Lateral Raise", 3, 0, 10, 10.1), set("Lateral Raise", 3, 1, 10, 10.5)).single()
        assertEquals(1, peak.setIndex)
        assertEquals(2, peak.count)
    }

    // ------------------------------------------------------------ Today

    @Test
    fun todayNamesOnlyAFreshPeakAndJudgesEachTrialByWhatCameBefore() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        fun trial(id: Long, daysAgo: Long, kg: Double) =
            WorkoutSession(id = id, label = "t$id", startedAtMs = now - daysAgo * day, completedAtMs = now - daysAgo * day + 1) to
                listOf(set("Bench Press", 2, 0, 5, kg))
        val ex = mapOf(2L to bench)
        // Heavier than its predecessor and recent: a peak. A later, better trial does not take it away from the earlier one.
        val recent = RepMax.recent(listOf(trial(1, 20, 100.0), trial(2, 12, 110.0), trial(3, 2, 120.0)), ex, now, withinDays = 14, limit = 2)
        assertEquals(listOf("Bench Press"), recent.map { it.name })
        assertEquals(120.0, recent.single().hit.set.weightKg!!, 1e-9)
        // First trial of a lift, a tie, or a peak long ago is not news.
        assertTrue(RepMax.recent(listOf(trial(1, 2, 100.0)), ex, now, 14, 2).isEmpty())
        assertTrue(RepMax.recent(listOf(trial(1, 20, 100.0), trial(2, 2, 100.0)), ex, now, 14, 2).isEmpty())
        assertTrue(RepMax.recent(listOf(trial(1, 60, 100.0), trial(2, 40, 120.0), trial(3, 2, 90.0)), ex, now, 14, 2).isEmpty())
    }
}
