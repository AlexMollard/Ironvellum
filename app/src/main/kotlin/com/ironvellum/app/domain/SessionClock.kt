package com.ironvellum.app.domain

import java.util.Locale

/**
 * Clock arithmetic for a workout: the live elapsed figure on the session
 * screen, and the estimate shown before and during it. A set costs the
 * lifter's own measured pace once he has one ([pace]); until then it costs
 * [ProgramRules.setSeconds] - the figure the generator budgets with - so a
 * card, the Today panel and the running session never disagree about what a
 * set costs.
 */
object SessionClock {

    /**
     * The focus timing is judged at: what the lifter last told the generator,
     * else the profile's training mode.
     */
    fun focusFor(saved: TrainingFocus?, mode: TrainingMode?): TrainingFocus =
        saved ?: if (mode == TrainingMode.STRENGTH) TrainingFocus.STRENGTH else TrainingFocus.MUSCLE

    /** Most recent usable sessions a pace is read from: it tracks the lifter as he changes. */
    const val PACE_WINDOW = 10

    /** Fewer usable sessions than this and the pace falls back a level. */
    const val PACE_MIN_SESSIONS = 2

    /**
     * Plausible seconds per done set. Below, sets were ticked off after the
     * fact; above, the session was left open (overnight, forgotten).
     */
    val PLAUSIBLE_SET_SECONDS = 60..600

    /**
     * One completed session's seconds per done set, or null when it says
     * nothing about pace: live, imported (its clock is the source app's), no
     * done sets, or outside [PLAUSIBLE_SET_SECONDS].
     */
    fun sessionPace(session: WorkoutSession, sets: List<SessionSet>): Int? {
        val completed = session.completedAtMs ?: return null
        if (session.imported) return null
        val done = sets.count { it.done }
        if (done == 0) return null
        val perSet = ((completed - session.startedAtMs) / 1000 / done).toInt()
        return perSet.takeIf { it in PLAUSIBLE_SET_SECONDS }
    }

    /**
     * Median seconds per set over the newest [PACE_WINDOW] usable sessions, or
     * null below [PACE_MIN_SESSIONS]. The median, so one slow day (a long
     * chat between sets) does not drag every estimate.
     */
    fun medianPace(history: List<Pair<WorkoutSession, List<SessionSet>>>): Int? {
        val paces = history
            .sortedByDescending { it.first.startedAtMs }
            .mapNotNull { (session, sets) -> sessionPace(session, sets) }
            .take(PACE_WINDOW)
        if (paces.size < PACE_MIN_SESSIONS) return null
        val sorted = paces.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** The lifter's measured pace: overall, and per preset where that preset has enough sessions. */
    data class Pace(val overall: Int? = null, val byPreset: Map<Long, Int> = emptyMap()) {
        /**
         * Seconds per set for a session of [presetId]: that preset's own pace,
         * else the lifter's overall pace, else null (the caller's rule-based
         * figure).
         */
        fun secondsPerSet(presetId: Long?): Int? = presetId?.let { byPreset[it] } ?: overall
    }

    fun pace(history: List<Pair<WorkoutSession, List<SessionSet>>>): Pace = Pace(
        overall = medianPace(history),
        byPreset = history
            .filter { it.first.presetId != null }
            .groupBy { it.first.presetId!! }
            .mapNotNull { (id, sessions) -> medianPace(sessions)?.let { id to it } }
            .toMap(),
    )

    /**
     * Clock time one session set costs. With a measured [paceSeconds] a
     * counted set costs exactly that. A hold costs its own seconds plus the
     * rest after it - the lifter's rest (his pace less [ProgramRules.SET_WORK_SECONDS])
     * when measured, the prescribed rest otherwise. Unprofiled movements count
     * as compounds, as [ProgramRules.sessionSeconds] does.
     */
    fun setSeconds(set: SessionSet, metric: ExerciseMetric?, focus: TrainingFocus, paceSeconds: Int? = null): Int {
        val compound = MuscleMap.profile(set.exerciseName)?.compound ?: true
        return if (MovementDifficulty.isHoldSet(metric, set.exerciseName, set.modifiers)) {
            val rest = paceSeconds?.let { (it - ProgramRules.SET_WORK_SECONDS).coerceAtLeast(0) }
                ?: ProgramRules.restSeconds(focus, compound)
            // An archive from before HOLD existed keeps the seconds in reps.
            rest + (set.durationSec ?: set.reps).coerceAtLeast(0)
        } else {
            paceSeconds ?: ProgramRules.setSeconds(focus, compound)
        }
    }

    /** Estimated clock time of every set in the session, done or not. */
    fun totalSeconds(
        sets: List<SessionSet>,
        metricOf: (Long) -> ExerciseMetric?,
        focus: TrainingFocus,
        paceSeconds: Int? = null,
    ): Int = sets.sumOf { setSeconds(it, metricOf(it.exerciseId), focus, paceSeconds) }

    /** Estimated clock time still ahead: only the sets not yet done. */
    fun remainingSeconds(
        sets: List<SessionSet>,
        metricOf: (Long) -> ExerciseMetric?,
        focus: TrainingFocus,
        paceSeconds: Int? = null,
    ): Int = sets.filterNot { it.done }.sumOf { setSeconds(it, metricOf(it.exerciseId), focus, paceSeconds) }

    /** Whole minutes, rounded up: a set still ahead never reads as zero. */
    fun minutes(seconds: Int): Int = (seconds.coerceAtLeast(0) + 59) / 60

    /** "EST 58 MIN · ~31 LEFT"; the tail drops once nothing is left. */
    fun estimateLine(totalSeconds: Int, remainingSeconds: Int): String =
        if (remainingSeconds <= 0) "EST ${minutes(totalSeconds)} MIN"
        else "EST ${minutes(totalSeconds)} MIN · ~${minutes(remainingSeconds)} LEFT"

    /** "5 MOVES · 20 SETS · ~55 MIN" for a preset before it starts; [paceSeconds] as in [setSeconds]. */
    fun planLine(entries: List<PlannedEntry>, focus: TrainingFocus, paceSeconds: Int? = null): String {
        val sets = entries.sumOf { it.sets }
        val seconds = paceSeconds?.let { sets * it } ?: ProgramRules.sessionSeconds(entries, focus)
        return "${entries.size} MOVES · $sets SETS · ~${minutes(seconds)} MIN"
    }

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
