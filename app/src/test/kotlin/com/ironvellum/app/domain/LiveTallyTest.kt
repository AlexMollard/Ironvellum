package com.ironvellum.app.domain

import com.ironvellum.app.ui.train.restEffortChips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live tally and the seal read one pricer: the running total is the seal's set-driven XP for the same sets. */
class LiveTallyTest {
    private val pullUp = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
    private val dip = Exercise(id = 2, name = "Dip", muscleGroup = MuscleGroup.PUSH, isWeighted = false)
    private val run = Exercise(
        id = 3, name = "Run", muscleGroup = MuscleGroup.CARDIO, isWeighted = false,
        metric = ExerciseMetric.DISTANCE_TIME, category = "Cardio",
    )
    private val exercises = listOf(pullUp, dip, run).associateBy { it.id }
    private val bodyweight = 79.3

    private fun session(id: Long, start: Long, vararg sets: SessionSet) =
        WorkoutSession(id = id, label = "t", startedAtMs = start, completedAtMs = start + 3_600_000) to sets.toList()

    private var nextId = 1L
    private fun set(ex: Exercise, pos: Int, idx: Int, reps: Int, weight: Double? = null, rpe: Int? = null, seconds: Int? = null, metres: Double? = null) =
        SessionSet(
            id = nextId++, exerciseId = ex.id, exerciseName = ex.name, exercisePosition = pos, setIndex = idx,
            reps = reps, weightKg = weight, done = true, rpe = rpe, durationSec = seconds, distanceM = metres,
        )

    private val past = session(
        1, 1_000_000,
        set(pullUp, 0, 0, 5, 15.0), set(pullUp, 0, 1, 5, 15.0), set(dip, 1, 0, 8),
    )
    private val book = SessionXp(exercises, listOf(past), { bodyweight })

    private val trial = listOf(
        set(pullUp, 0, 0, 5, 17.0, rpe = 9),
        set(pullUp, 0, 1, 5, 17.0, rpe = 9),
        set(pullUp, 0, 2, 4, 17.0, rpe = 9),
        set(dip, 1, 0, 12),
        set(run, 2, 0, 0, seconds = 1_500, metres = 5_000.0),
    )

    @Test
    fun theGainsOfTheSetsAddUpToTheRunningTotal() {
        val tally = book.tally(trial, bodyweight)
        assertEquals(tally.total, tally.gains.values.sum())
        assertEquals(trial.size, tally.gains.size)
        assertTrue(tally.total > 0)
    }

    @Test
    fun theRunningTotalIsWhatTheSealAwardsForTheSetsDoneSoFar() {
        for (done in 0..trial.size) {
            val soFar = trial.take(done)
            val live = book.tally(soFar, bodyweight).total
            val facts = Xp.TrialFacts(quest = true, weekCovered = true, firstOfWeek = true)
            val sealed = book.breakdown(soFar, bodyweight, facts, doneSetCount = soFar.size)
            assertEquals("after $done sets", live, sealed.setDriven)
            val trialLevel = sealed.lines.filter { !it.kind.setDriven }.sumOf { it.xp }
            assertEquals(sealed.total, live + trialLevel)
        }
    }

    @Test
    fun anUntickedSetAndAWarmupAreNotInTheTally() {
        val extra = trial + set(dip, 1, 1, 12).copy(done = false) + set(dip, 1, 2, 12).copy(kind = SetKind.WARMUP)
        assertEquals(book.tally(trial, bodyweight), book.tally(extra, bodyweight))
    }

    @Test
    fun theTallyIsAFunctionOfTheDoneSetsNotOfTheOrderTheyArrivedIn() {
        assertEquals(book.tally(trial, bodyweight).total, book.tally(trial.reversed(), bodyweight).total)
    }

    @Test
    fun theTallyJudgesAgainstHistoryBeforeThisTrialOnly() {
        // The same sets, with the trial itself left out of its own history.
        val self = WorkoutSession(id = 9, label = "now", startedAtMs = 2_000_000, completedAtMs = 2_100_000) to trial
        val withSelf = SessionXp(exercises, listOf(past, self), { bodyweight }, excludeSessionId = 9)
        assertEquals(book.tally(trial, bodyweight), withSelf.tally(trial, bodyweight))
    }

    @Test
    fun anOlderWorkoutIsPricedOnlyAgainstHistoryBeforeIt() {
        // An import dated before the lifter's heavy log: that log must not make its sets read as easy.
        val later = session(2, 5_000_000, set(pullUp, 0, 0, 5, 40.0), set(pullUp, 0, 1, 5, 40.0))
        val older = listOf(set(pullUp, 0, 0, 5, 15.0), set(pullUp, 0, 1, 5, 15.0))
        val judgedAtItsDate = SessionXp(exercises, listOf(past, later), { bodyweight }, beforeMs = 2_000_000)
        val judgedAgainstEverything = SessionXp(exercises, listOf(past, later), { bodyweight })
        val againstPastOnly = SessionXp(exercises, listOf(past), { bodyweight })
        assertEquals(againstPastOnly.tally(older, bodyweight), judgedAtItsDate.tally(older, bodyweight))
        assertTrue(
            judgedAgainstEverything.tally(older, bodyweight).total < judgedAtItsDate.tally(older, bodyweight).total,
        )
    }

    @Test
    fun theRestBarChipsAreFiveOnEitherScale() {
        assertEquals(listOf("0", "1", "2", "3", "4+"), restEffortChips(EffortScale.RIR).map { it.second })
        assertEquals(listOf(10, 9, 8, 7, 6), restEffortChips(EffortScale.RIR).map { it.first })
        assertEquals(listOf("≤6", "7", "8", "9", "10"), restEffortChips(EffortScale.RPE).map { it.second })
        assertEquals(listOf(6, 7, 8, 9, 10), restEffortChips(EffortScale.RPE).map { it.first })
        // A chip's stored value is the same RPE either way: RIR 2 and RPE 8 are one rating.
        assertEquals(restEffortChips(EffortScale.RIR).first { it.second == "2" }.first, 8)
    }
}
