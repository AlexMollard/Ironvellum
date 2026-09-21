package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetRecordsTest {

    private val bw = 80.0

    // Constant time-aware lookup: every session scored with `bw`.
    private val bwAt: (Long) -> Double = { _ -> bw }

    private fun session(id: Long, startedAtMs: Long) = WorkoutSession(
        id = id,
        label = "s$id",
        startedAtMs = startedAtMs,
        completedAtMs = startedAtMs + 3_600_000,
    )

    private fun set(
        name: String = "Pull-up",
        setIndex: Int,
        reps: Int,
        weightKg: Double? = null,
        done: Boolean = true,
    ) = SessionSet(
        exerciseId = 1L,
        exerciseName = name,
        setIndex = setIndex,
        reps = reps,
        weightKg = weightKg,
        done = done,
    )

    @Test
    fun set3IsJudgedAgainstSet3RecordNotSet1() {
        // Set 1 history is far stronger; set 3 history is weak.
        val history = listOf(
            session(1, 1_000) to listOf(
                set(setIndex = 1, reps = 12),
                set(setIndex = 3, reps = 4),
            ),
        )
        val records = SetRecords.records(history, bwAt)
        val delta = SetRecords.delta(records, "Pull-up", setIndex = 3, reps = 5, weightKg = null, bodyweightKg = bw)
        // 5 bodyweight reps beats the 4-rep set-3 record even though set 1 did 12.
        assertTrue("5 reps should beat the 4-rep set-3 record", delta.isRecord)
        assertEquals(records[("pull-up" to 3)]!!.score, delta.record!!.score, 1e-9)
        assertTrue(delta.deltaScore > 0.0)
    }

    @Test
    fun untickedSetsNeverBecomeRecords() {
        val history = listOf(
            session(1, 1_000) to listOf(set(setIndex = 1, reps = 20, done = false)),
        )
        val records = SetRecords.records(history, bwAt)
        assertNull("done=false sets must not count", records["pull-up" to 1])
        val delta = SetRecords.delta(records, "pull-up", setIndex = 1, reps = 1, weightKg = null, bodyweightKg = bw)
        assertTrue(delta.isRecord)
        assertNull(delta.record)
    }

    @Test
    fun excludeSessionIdDropsThatSessionBeforeComputing() {
        val history = listOf(
            session(1, 1_000) to listOf(set(setIndex = 1, reps = 10)),
        )
        val records = SetRecords.records(history, bwAt, excludeSessionId = 1L)
        assertNull(records["pull-up" to 1])
        // The session being logged is not the record it is judged against.
        val delta = SetRecords.delta(records, "pull-up", setIndex = 1, reps = 10, weightKg = null, bodyweightKg = bw)
        assertTrue(delta.isRecord)
        assertNull(delta.record)
    }

    @Test
    fun firstEverAttemptAtSetPositionIsARecordWithNullRecord() {
        val delta = SetRecords.delta(emptyMap(), "Deadlift", setIndex = 3, reps = 5, weightKg = 100.0, bodyweightKg = bw)
        assertNull(delta.record)
        assertEquals(0.0, delta.deltaScore, 1e-9)
        assertEquals(0.0, delta.deltaFraction, 1e-9)
        assertTrue(delta.isRecord)
        assertTrue(delta.score > 0.0)
    }

    @Test
    fun exactTieIsNotANewRecord() {
        val history = listOf(
            session(1, 1_000) to listOf(set(setIndex = 2, reps = 8)),
        )
        val records = SetRecords.records(history, bwAt)
        val delta = SetRecords.delta(records, "pull-up", setIndex = 2, reps = 8, weightKg = null, bodyweightKg = bw)
        assertFalse("exact tie must not be a record", delta.isRecord)
        assertEquals(0.0, delta.deltaScore, 1e-9)
        assertEquals(0.0, delta.deltaFraction, 1e-9)
    }

    @Test
    fun caseAndWhitespaceInExerciseNameResolveToSameRecord() {
        val history = listOf(
            session(1, 1_000) to listOf(set(name = "  PULL-UP ", setIndex = 1, reps = 10)),
        )
        val records = SetRecords.records(history, bwAt)
        // Attempt uses different casing/whitespace; must still hit the record.
        val delta = SetRecords.delta(records, "pull-Up", setIndex = 1, reps = 9, weightKg = null, bodyweightKg = bw)
        assertEquals(records["pull-up" to 1]!!.score, delta.record!!.score, 1e-9)
        assertFalse(delta.isRecord)
    }

    @Test
    fun earlierSessionWinsAScoreTie() {
        val history = listOf(
            session(1, 1_000) to listOf(set(setIndex = 1, reps = 10)),
            session(2, 2_000) to listOf(set(setIndex = 1, reps = 10)),
        )
        val records = SetRecords.records(history, bwAt)
        val record = records["pull-up" to 1]!!
        assertEquals("tie on score keeps the earlier session", 1L, record.sessionId)
        assertEquals(1_000, record.achievedAtMs)
    }

    @Test
    fun bodyweightSetCanOutscoreLightlyWeightedSet() {
        // Heavier weighted set with fewer reps vs a bodyweight set with more reps.
        val history = listOf(
            session(1, 1_000) to listOf(set(setIndex = 1, reps = 3, weightKg = 2.5)),
        )
        val records = SetRecords.records(history, bwAt)
        // repScore = reps * (bw + added) / bw^0.67, so 10 BW reps > 3 reps @ +2.5kg.
        val delta = SetRecords.delta(records, "pull-up", setIndex = 1, reps = 10, weightKg = null, bodyweightKg = bw)
        assertTrue("10 bodyweight reps must outscore 3 reps at +2.5kg", delta.isRecord)
        assertTrue(delta.deltaScore > 0.0)
        assertTrue(delta.deltaFraction > 0.0)
    }

    @Test
    fun zeroRecordScoreYieldsZeroFractionWithoutNaN() {
        // A record with score 0 can only come from a degenerate bodyweight; force
        // it via the map directly — deltaFraction must be 0.0, never NaN.
        val record = SetRecords.Record(
            exerciseName = "pull-up",
            setIndex = 1,
            score = 0.0,
            reps = 5,
            weightKg = null,
            achievedAtMs = 1_000,
            sessionId = 1L,
        )
        val delta = SetRecords.delta(mapOf(("pull-up" to 1) to record), "pull-up", setIndex = 1, reps = 5, weightKg = null, bodyweightKg = bw)
        assertEquals(0.0, delta.deltaFraction, 1e-9)
        assertFalse(delta.deltaFraction.isNaN())
    }

    @Test
    fun outOfOrderHistoryStillKeepsEarliestTie() {
        // History list given newest-first must not flip which tie wins.
        val history = listOf(
            session(2, 2_000) to listOf(set(setIndex = 1, reps = 10)),
            session(1, 1_000) to listOf(set(setIndex = 1, reps = 10)),
        )
        val records = SetRecords.records(history, bwAt)
        assertEquals(1L, records["pull-up" to 1]!!.sessionId)
    }
    private fun stat(takenAtMs: Long, weightKg: Double) =
        StatEntry(id = 0, takenAtMs = takenAtMs, weightKg = weightKg, heightCm = 175.0, bodyFatPct = null)
    @Test
    fun recordKeepsItsEarnedScoreAfterLaterWeightLoss() {
        // Set earned at 80 kg; the user then drops to 70 kg. The record's score
        // must stay the 80 kg score — otherwise it becomes trivially beatable.
        val earned = StrengthIndex.repScore(10, null, 80.0)
        val stats = listOf(stat(500, 80.0), stat(5_000, 70.0))
        val history = listOf(
            session(1, 1_000) to listOf(set(setIndex = 1, reps = 10)),
        )
        val records = SetRecords.records(history, SetRecords.bodyweightLookup(stats))
        assertEquals(earned, records["pull-up" to 1]!!.score, 1e-9)
        // Heavier athletes score more, so the same reps at today's lighter 70 kg
        // scores BELOW the 80 kg record — proof the record kept its earned value.
        val delta = SetRecords.delta(records, "pull-up", setIndex = 1, reps = 10, weightKg = null, bodyweightKg = 70.0)
        assertFalse(delta.isRecord)
        assertTrue(delta.deltaScore < 0.0)
        assertEquals(earned, delta.record!!.score, 1e-9)
    }

    @Test
    fun setBeforeAnyWeighInUsesEarliestReading() {
        val stats = listOf(stat(1_000, 75.0), stat(2_000, 80.0))
        val lookup = SetRecords.bodyweightLookup(stats)
        assertEquals(75.0, lookup(500), 1e-9)
        val history = listOf(session(1, 500) to listOf(set(setIndex = 1, reps = 10)))
        val records = SetRecords.records(history, SetRecords.bodyweightLookup(stats))
        assertEquals(StrengthIndex.repScore(10, null, 75.0), records["pull-up" to 1]!!.score, 1e-9)
    }

    @Test
    fun noStatReadingsFallsBackWithoutThrowing() {
        val lookup = SetRecords.bodyweightLookup(emptyList(), fallbackKg = 80.0)
        assertEquals(80.0, lookup(1_000), 1e-9)
        val history = listOf(session(1, 1_000) to listOf(set(setIndex = 1, reps = 10)))
        val records = SetRecords.records(history, lookup)
        assertEquals(StrengthIndex.repScore(10, null, 80.0), records["pull-up" to 1]!!.score, 1e-9)
        // Default fallback 0.0 must not throw either (score is 0, delta pins fraction).
        val zero = SetRecords.records(history, SetRecords.bodyweightLookup(emptyList()))
        val delta = SetRecords.delta(zero, "pull-up", setIndex = 1, reps = 10, weightKg = null, bodyweightKg = bw)
        assertEquals(0.0, delta.deltaFraction, 1e-9)
        assertFalse(delta.deltaFraction.isNaN())
    }

    @Test
    fun unsortedStatsStillResolveCorrectReading() {
        val stats = listOf(stat(4_000, 70.0), stat(1_000, 85.0), stat(2_500, 80.0))
        val lookup = SetRecords.bodyweightLookup(stats)
        assertEquals(85.0, lookup(1_000), 1e-9)
        assertEquals(80.0, lookup(2_500), 1e-9)
        assertEquals(80.0, lookup(3_999), 1e-9)
        assertEquals(70.0, lookup(9_999), 1e-9)
    }

    @Test
    fun liveAttemptIsScoredWithTodaysBodyweight() {
        // Documented asymmetry: delta() takes the concrete current bodyweight,
        // so a 5-rep attempt today at 70 kg scores at 70 kg even though the
        // record was earned at 80 kg.
        val stats = listOf(stat(1_000, 80.0), stat(5_000, 70.0))
        val history = listOf(
            session(1, 1_000) to listOf(set(setIndex = 1, reps = 5)),
        )
        val records = SetRecords.records(history, SetRecords.bodyweightLookup(stats))
        val delta = SetRecords.delta(records, "pull-up", setIndex = 1, reps = 5, weightKg = null, bodyweightKg = 70.0)
        assertEquals(StrengthIndex.repScore(5, null, 70.0), delta.score, 1e-9)
    }

    /**
     * A climbing set carries its ATTEMPT COUNT in the reps column, so scoring
     * it through the strength path minted a bodyweight-rep record: seven
     * attempts read as seven pull-ups. Lifting in the same history must still
     * record normally, or the guard has simply turned records off.
     */
    @Test
    fun `an activity set sets no strength record while lifting in the same history still does`() {
        val history = listOf(
            session(1, 1_000) to listOf(
                set(name = "Bouldering", setIndex = 0, reps = 7),
                set(name = "Pull-up", setIndex = 0, reps = 5),
            ),
        )
        val records = SetRecords.records(history, bwAt) { s ->
            if (s.exerciseName == "Bouldering") ExerciseMetric.ATTEMPTS_GRADE else ExerciseMetric.REPS
        }
        assertNull("attempts are not a strength record", records["bouldering" to 0])
        assertEquals(
            StrengthIndex.repScore(5, null, bw),
            records.getValue("pull-up" to 0).score,
            1e-9,
        )
    }
}
