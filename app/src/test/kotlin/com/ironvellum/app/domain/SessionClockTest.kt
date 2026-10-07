package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The workout clock: elapsed figure, set costs and the estimate lines. */
class SessionClockTest {

    private val metrics = mapOf(1L to ExerciseMetric.REPS, 2L to ExerciseMetric.REPS, 3L to ExerciseMetric.HOLD)
    private val metricOf: (Long) -> ExerciseMetric? = { metrics[it] }

    private fun deadlift(done: Boolean = false) =
        SessionSet(exerciseId = 1, exerciseName = "Deadlift", setIndex = 0, reps = 5, done = done)

    private fun curl(done: Boolean = false) =
        SessionSet(exerciseId = 2, exerciseName = "Bicep Curl", setIndex = 0, reps = 12, done = done)

    private fun hold(seconds: Int, done: Boolean = false) =
        SessionSet(exerciseId = 3, exerciseName = "Plank", setIndex = 0, reps = 0, durationSec = seconds, done = done)

    @Test
    fun `the profiles the costs below rely on`() {
        assertTrue(MuscleMap.profile("Deadlift")!!.compound)
        assertFalse(MuscleMap.profile("Bicep Curl")!!.compound)
    }

    @Test
    fun `remaining time counts only the sets not yet done`() {
        val sets = listOf(deadlift(done = true), deadlift(), curl(done = true), curl())
        // Strength: a deadlift is priced at its 180 s ready rest + 40 s work, a curl at 60 + 40.
        assertEquals(220 + 100, SessionClock.remainingSeconds(sets, metricOf, TrainingFocus.STRENGTH))
        assertEquals(2 * 220 + 2 * 100, SessionClock.totalSeconds(sets, metricOf, TrainingFocus.STRENGTH))
        // Muscle focus shortens them to 120 and 45.
        assertEquals(160 + 85, SessionClock.remainingSeconds(sets, metricOf, TrainingFocus.MUSCLE))
    }

    @Test
    fun `a finished session has nothing left`() {
        val sets = listOf(deadlift(done = true), curl(done = true))
        assertEquals(0, SessionClock.remainingSeconds(sets, metricOf, TrainingFocus.STRENGTH))
        assertEquals("EST 6 MIN", SessionClock.estimateLine(220 + 100, 0))
    }

    @Test
    fun `a hold costs its own seconds plus rest, not the counted-set work time`() {
        val plankRest = RestRules.readySeconds("Plank", TrainingFocus.MUSCLE)
        assertEquals(plankRest + 90, SessionClock.setSeconds(hold(90), ExerciseMetric.HOLD, TrainingFocus.MUSCLE))
        assertEquals(plankRest + 20, SessionClock.setSeconds(hold(20), ExerciseMetric.HOLD, TrainingFocus.MUSCLE))
        // A legacy hold keeps its seconds in reps.
        val legacy = SessionSet(exerciseId = 3, exerciseName = "Plank", setIndex = 0, reps = 45)
        assertEquals(plankRest + 45, SessionClock.setSeconds(legacy, ExerciseMetric.REPS, TrainingFocus.MUSCLE))
        // Done holds drop out of what is left.
        assertEquals(
            plankRest + 60,
            SessionClock.remainingSeconds(listOf(hold(60), hold(60, done = true)), metricOf, TrainingFocus.MUSCLE),
        )
    }

    @Test
    fun `minutes round up so a set still ahead never reads as zero`() {
        assertEquals(0, SessionClock.minutes(0))
        assertEquals(1, SessionClock.minutes(1))
        assertEquals(1, SessionClock.minutes(60))
        assertEquals(2, SessionClock.minutes(61))
        assertEquals("EST 58 MIN · ~31 LEFT", SessionClock.estimateLine(58 * 60, 30 * 60 + 5))
    }

    @Test
    fun `the plan line uses the generator's session estimate`() {
        val entries = listOf(
            PlannedEntry(exerciseName = "Deadlift", sets = 3, reps = 5, targetWeightKg = null),
            PlannedEntry(exerciseName = "Bicep Curl", sets = 2, reps = 12, targetWeightKg = null),
        )
        // 3 × 220 + 2 × 100 = 860 s → 15 min.
        assertEquals("2 EXERCISES · 5 SETS · ~15 MIN", SessionClock.planLine(entries, TrainingFocus.STRENGTH))
    }

    @Test
    fun `elapsed reads m ss under an hour and h mm ss past it`() {
        assertEquals("0:00", SessionClock.elapsedLabel(0))
        assertEquals("0:59", SessionClock.elapsedLabel(59_999))
        assertEquals("4:07", SessionClock.elapsedLabel(247_000))
        assertEquals("59:59", SessionClock.elapsedLabel(3_599_000))
        assertEquals("1:00:00", SessionClock.elapsedLabel(3_600_000))
        assertEquals("1:04:07", SessionClock.elapsedLabel(3_847_000))
        assertEquals("0:00", SessionClock.elapsedLabel(-5_000))
    }

    @Test
    fun `the training mode outranks the saved focus, which only decides without one`() {
        // A hypertrophy lifter whose last program was a strength one still rests like one.
        assertEquals(TrainingFocus.MUSCLE, SessionClock.focusFor(TrainingFocus.STRENGTH, TrainingMode.HYPERTROPHY))
        assertEquals(TrainingFocus.STRENGTH, SessionClock.focusFor(TrainingFocus.MUSCLE, TrainingMode.STRENGTH))
        assertEquals(TrainingFocus.STRENGTH, SessionClock.focusFor(null, TrainingMode.STRENGTH))
        assertEquals(TrainingFocus.MUSCLE, SessionClock.focusFor(null, TrainingMode.HYPERTROPHY))
        assertEquals(TrainingFocus.SKILL, SessionClock.focusFor(TrainingFocus.SKILL, null))
        assertEquals(TrainingFocus.MUSCLE, SessionClock.focusFor(null, null))
    }

    /** A finished session [seconds] long with [done] ticked sets (plus one skipped). */
    private fun logged(
        startMs: Long,
        seconds: Long,
        done: Int,
        presetId: Long? = null,
        imported: Boolean = false,
        completed: Boolean = true,
    ): Pair<WorkoutSession, List<SessionSet>> =
        WorkoutSession(
            presetId = presetId,
            label = "Pull",
            startedAtMs = startMs,
            completedAtMs = if (completed) startMs + seconds * 1000 else null,
            imported = imported,
        ) to (List(done) { deadlift(done = true) } + deadlift())

    @Test
    fun `pace is the median seconds per done set, and times the plan`() {
        // The owner's own days: Push 11 sets in 48.6 min, Pull 17 in 66.8 min.
        val history = listOf(
            logged(startMs = 3_000_000_000, seconds = 2916, done = 11),
            logged(startMs = 2_000_000_000, seconds = 4008, done = 17),
            logged(startMs = 1_000_000_000, seconds = 3000, done = 12),
        )
        assertEquals(250, SessionClock.medianPace(history))
        // An even count takes the mean of the middle pair.
        assertEquals((235 + 265) / 2, SessionClock.medianPace(history.take(2)))
        val day = listOf(PlannedEntry(exerciseName = "Deadlift", sets = 18, reps = 5, targetWeightKg = null))
        assertEquals("1 EXERCISE · 18 SETS · ~75 MIN", SessionClock.planLine(day, TrainingFocus.STRENGTH, 250))
    }

    @Test
    fun `sessions that say nothing about pace are dropped, and one is not enough`() {
        val usable = logged(startMs = 5_000_000_000, seconds = 2400, done = 10)
        val noise = listOf(
            logged(startMs = 4_000_000_000, seconds = 12 * 3600, done = 10), // left open overnight
            logged(startMs = 3_000_000_000, seconds = 300, done = 10), // ticked off after the fact
            logged(startMs = 2_000_000_000, seconds = 1000, done = 5, imported = true),
            logged(startMs = 1_000_000_000, seconds = 1000, done = 5, completed = false),
            logged(startMs = 500_000_000, seconds = 1000, done = 0),
        )
        assertEquals(null, SessionClock.medianPace(listOf(usable) + noise))
        val second = logged(startMs = 100_000_000, seconds = 2000, done = 10)
        assertEquals((240 + 200) / 2, SessionClock.medianPace(listOf(usable) + noise + second))
    }

    @Test
    fun `only the newest sessions set the pace`() {
        val recent = List(SessionClock.PACE_WINDOW) { logged(startMs = 10_000_000_000 - it * 1000L, seconds = 2000, done = 10) }
        val older = List(SessionClock.PACE_WINDOW + 1) { logged(startMs = 1_000_000_000 - it * 1000L, seconds = 5000, done = 10) }
        assertEquals(200, SessionClock.medianPace(older + recent))
    }

    @Test
    fun `a preset's own pace outranks the lifter's, which outranks the rules`() {
        val history = listOf(
            logged(startMs = 6_000_000_000, seconds = 3000, done = 10, presetId = 1),
            logged(startMs = 5_000_000_000, seconds = 3000, done = 10, presetId = 1),
            logged(startMs = 4_000_000_000, seconds = 1200, done = 10, presetId = 2),
            logged(startMs = 3_000_000_000, seconds = 1500, done = 10),
        )
        val pace = SessionClock.pace(history)
        // Preset 1: two sessions at 300 s/set.
        assertEquals(300, pace.secondsPerSet(1))
        // Preset 2 has one session: the lifter's median over all four (300, 300, 120, 150).
        assertEquals(225, pace.secondsPerSet(2))
        assertEquals(225, pace.secondsPerSet(null))
        // No history: null, and the estimate falls back to the rule figure.
        assertEquals(null, SessionClock.pace(emptyList()).secondsPerSet(1))
        val sets = listOf(deadlift(done = true), deadlift(), curl())
        assertEquals(220 + 100, SessionClock.remainingSeconds(sets, metricOf, TrainingFocus.STRENGTH, null))
        assertEquals(2 * 225, SessionClock.remainingSeconds(sets, metricOf, TrainingFocus.STRENGTH, 225))
        assertEquals(3 * 225, SessionClock.totalSeconds(sets, metricOf, TrainingFocus.STRENGTH, 225))
    }

    @Test
    fun `with a measured pace a hold costs its seconds plus the lifter's rest`() {
        // 225 s a set is 40 s of work and 185 s of rest.
        assertEquals(185 + 60, SessionClock.setSeconds(hold(60), ExerciseMetric.HOLD, TrainingFocus.STRENGTH, 225))
    }

    @Test
    fun `a timed activity costs its duration plus rest, not a pace set`() {
        val run = SessionSet(exerciseId = 4, exerciseName = "Running", setIndex = 0, reps = 0, durationSec = 2700)
        // An activity has no rest between sets: it costs its logged duration.
        val rest = RestRules.readySeconds("Running", TrainingFocus.MUSCLE)
        assertEquals(0, rest)
        assertEquals(rest + 2700, SessionClock.setSeconds(run, ExerciseMetric.DURATION, TrainingFocus.MUSCLE))
        val row = run.copy(distanceM = 5000.0, durationSec = 1500)
        assertEquals(185 + 1500, SessionClock.setSeconds(row, ExerciseMetric.DISTANCE_TIME, TrainingFocus.MUSCLE, 225))
        // No duration logged yet: nothing to price but the pace.
        assertEquals(225, SessionClock.setSeconds(run.copy(durationSec = null), ExerciseMetric.DURATION, TrainingFocus.MUSCLE, 225))
    }

    @Test
    fun `the plan line prices a hold entry as its seconds plus rest`() {
        val hang = listOf(PlannedEntry(exerciseName = "Plank", sets = 3, reps = 60, targetWeightKg = null))
        // Pace 225 s: rest 185 + 60 s held = 245 s a set, 735 s → 13 min (not 3 × 225 = 675 s → 12).
        assertEquals(
            "1 EXERCISE · 3 SETS · ~13 MIN",
            SessionClock.planLine(hang, TrainingFocus.MUSCLE, 225) { ExerciseMetric.HOLD },
        )
        // Without a metric the name rule still recognises the hold.
        assertEquals("1 EXERCISE · 3 SETS · ~13 MIN", SessionClock.planLine(hang, TrainingFocus.MUSCLE, 225))
    }
}
