package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPeaksTest {

    private val bw = 80.0
    private val bwAt: (Long) -> Double = { _ -> bw }

    private fun set(
        name: String = "Pull-up",
        exerciseId: Long = 1L,
        setIndex: Int,
        reps: Int,
        weightKg: Double? = null,
        done: Boolean = true,
        durationSec: Int? = null,
        position: Int = 0,
    ) = SessionSet(
        exerciseId = exerciseId,
        exerciseName = name,
        exercisePosition = position,
        setIndex = setIndex,
        reps = reps,
        weightKg = weightKg,
        done = done,
        durationSec = durationSec,
    )

    private fun recordsOf(vararg sets: SessionSet, metricOf: (SessionSet) -> ExerciseMetric? = { null }) =
        SetRecords.records(
            listOf(WorkoutSession(id = 1, label = "old", startedAtMs = 1_000, completedAtMs = 2_000) to sets.toList()),
            bwAt,
            metricOf = metricOf,
        )

    private val reps: (SessionSet) -> ExerciseMetric = { ExerciseMetric.REPS }

    @Test
    fun peaksRollUpPerExerciseWithTheBiggestGainAsTheHeadline() {
        val records = recordsOf(set(setIndex = 0, reps = 8), set(setIndex = 1, reps = 6))
        val today = listOf(set(setIndex = 0, reps = 10), set(setIndex = 1, reps = 11))

        val peaks = SessionPeaks.of(today, records, bw, reps)

        assertEquals(1, peaks.size)
        val peak = peaks.single()
        assertEquals(2, peak.count)
        // Set 2 gained more over its own record than set 1 did over its.
        assertEquals(1, peak.setIndex)
        assertEquals(11, peak.figure)
        assertEquals(6, peak.was.reps)
        assertTrue(peak.deltaScore > 0.0)
    }

    @Test
    fun aRepeatOfAnEarlierSetTodayIsNotASecondPeak() {
        // Set 2's record is weak, but today's set 2 only matches today's set 1.
        val records = recordsOf(set(setIndex = 0, reps = 8), set(setIndex = 1, reps = 4))
        val today = listOf(set(setIndex = 0, reps = 10), set(setIndex = 1, reps = 10))

        val peak = SessionPeaks.of(today, records, bw, reps).single()

        assertEquals(1, peak.count)
        assertEquals(0, peak.setIndex)
    }

    @Test
    fun aFirstEverSetIsHistoryStartingNotAPeak() {
        val today = listOf(set(name = "Muscle-up", setIndex = 0, reps = 5))
        assertTrue(SessionPeaks.of(today, emptyMap(), bw, reps).isEmpty())
    }

    @Test
    fun noBodyweightMeansNoPeaks() {
        val records = recordsOf(set(setIndex = 0, reps = 4))
        val today = listOf(set(setIndex = 0, reps = 10))
        assertTrue(SessionPeaks.of(today, records, null, reps).isEmpty())
        assertTrue(SessionPeaks.of(today, records, 0.0, reps).isEmpty())
    }

    @Test
    fun untickedSetsAndActivitiesNeverPeak() {
        val records = recordsOf(set(setIndex = 0, reps = 4))
        val unticked = listOf(set(setIndex = 0, reps = 10, done = false))
        assertTrue(SessionPeaks.of(unticked, records, bw, reps).isEmpty())
        val activity = listOf(set(setIndex = 0, reps = 10))
        assertTrue(SessionPeaks.of(activity, records, bw) { ExerciseMetric.ATTEMPTS_GRADE }.isEmpty())
    }

    @Test
    fun aHoldPeaksOnItsSeconds() {
        val hold: (SessionSet) -> ExerciseMetric = { ExerciseMetric.HOLD }
        val records = recordsOf(set(name = "Plank", setIndex = 0, reps = 0, durationSec = 30), metricOf = hold)
        val today = listOf(set(name = "Plank", setIndex = 0, reps = 0, durationSec = 45))

        val peak = SessionPeaks.of(today, records, bw, hold).single()

        assertTrue(peak.isHold)
        assertEquals(45, peak.figure)
        assertEquals(30, peak.was.reps)
    }

    @Test
    fun peaksFollowTheSessionsExerciseOrder() {
        val records = recordsOf(
            set(name = "Pull-up", exerciseId = 1, setIndex = 0, reps = 4),
            set(name = "Dip", exerciseId = 2, setIndex = 0, reps = 4),
        )
        val today = listOf(
            set(name = "Pull-up", exerciseId = 1, setIndex = 0, reps = 10, position = 1),
            set(name = "Dip", exerciseId = 2, setIndex = 0, reps = 10, position = 0),
        )

        val names = SessionPeaks.of(today, records, bw, reps).map { it.exerciseName }

        assertEquals(listOf("Dip", "Pull-up"), names)
    }

    private fun trial(id: Long, startedAt: Long, reps: Int) =
        WorkoutSession(id = id, label = "t", startedAtMs = startedAt, completedAtMs = startedAt + 1_000) to
            listOf(set(setIndex = 0, reps = reps))

    /** A trial seen from history is judged as history stood when it was done. */
    private fun peaksAsOfThen(thisReps: Int, earlierReps: Int, laterReps: Int): List<SessionPeaks.Peak> {
        val mine = trial(2, 5_000, thisReps)
        val history = listOf(trial(1, 1_000, earlierReps), mine, trial(3, 9_000, laterReps))
        val records = SetRecords.records(
            history,
            bwAt,
            excludeSessionId = mine.first.id,
            beforeMs = mine.first.completedAtMs,
        )
        return SessionPeaks.of(mine.second, records, bw, reps)
    }

    @Test
    fun aLaterBetterTrialDoesNotTakeThePeakAway() {
        assertEquals(1, peaksAsOfThen(thisReps = 10, earlierReps = 8, laterReps = 14).size)
    }

    @Test
    fun anEarlierBetterTrialPreventsThePeak() {
        assertTrue(peaksAsOfThen(thisReps = 10, earlierReps = 12, laterReps = 14).isEmpty())
    }
}
