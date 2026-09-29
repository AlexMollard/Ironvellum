package com.ironvellum.app.domain

import java.util.Locale

/**
 * Clock arithmetic for a workout: the live elapsed figure on the session
 * screen, and the estimate shown before and during it. The per-set cost is
 * [ProgramRules.setSeconds] - the same figure the generator budgets with - so
 * a card, the Today panel and the running session never disagree about what
 * a set costs.
 */
object SessionClock {

    /**
     * The focus timing is judged at: what the lifter last told the generator,
     * else the profile's training mode.
     */
    fun focusFor(saved: TrainingFocus?, mode: TrainingMode?): TrainingFocus =
        saved ?: if (mode == TrainingMode.STRENGTH) TrainingFocus.STRENGTH else TrainingFocus.MUSCLE

    /**
     * Clock time one session set costs. A hold costs its own seconds plus the
     * rest after it, not the 40 s a counted set is charged; unprofiled
     * movements count as compounds, as [ProgramRules.sessionSeconds] does.
     */
    fun setSeconds(set: SessionSet, metric: ExerciseMetric?, focus: TrainingFocus): Int {
        val compound = MuscleMap.profile(set.exerciseName)?.compound ?: true
        return if (MovementDifficulty.isHoldSet(metric, set.exerciseName, set.modifiers)) {
            // An archive from before HOLD existed keeps the seconds in reps.
            ProgramRules.restSeconds(focus, compound) + (set.durationSec ?: set.reps).coerceAtLeast(0)
        } else {
            ProgramRules.setSeconds(focus, compound)
        }
    }

    /** Estimated clock time of every set in the session, done or not. */
    fun totalSeconds(sets: List<SessionSet>, metricOf: (Long) -> ExerciseMetric?, focus: TrainingFocus): Int =
        sets.sumOf { setSeconds(it, metricOf(it.exerciseId), focus) }

    /** Estimated clock time still ahead: only the sets not yet done. */
    fun remainingSeconds(sets: List<SessionSet>, metricOf: (Long) -> ExerciseMetric?, focus: TrainingFocus): Int =
        sets.filterNot { it.done }.sumOf { setSeconds(it, metricOf(it.exerciseId), focus) }

    /** Whole minutes, rounded up: a set still ahead never reads as zero. */
    fun minutes(seconds: Int): Int = (seconds.coerceAtLeast(0) + 59) / 60

    /** "EST 58 MIN · ~31 LEFT"; the tail drops once nothing is left. */
    fun estimateLine(totalSeconds: Int, remainingSeconds: Int): String =
        if (remainingSeconds <= 0) "EST ${minutes(totalSeconds)} MIN"
        else "EST ${minutes(totalSeconds)} MIN · ~${minutes(remainingSeconds)} LEFT"

    /** "5 MOVES · 20 SETS · ~55 MIN" for a preset before it starts. */
    fun planLine(entries: List<PlannedEntry>, focus: TrainingFocus): String =
        "${entries.size} MOVES · ${entries.sumOf { it.sets }} SETS · ~${minutes(ProgramRules.sessionSeconds(entries, focus))} MIN"

    /** "4:07" under an hour, "1:04:07" past it. A clock set backwards reads 0:00. */
    fun elapsedLabel(elapsedMs: Long): String {
        val total = elapsedMs.coerceAtLeast(0) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}
