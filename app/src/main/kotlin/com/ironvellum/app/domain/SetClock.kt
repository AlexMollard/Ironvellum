package com.ironvellum.app.domain

/**
 * The clock on a timed set's row, as plain state on the monotonic clock
 * (SystemClock.elapsedRealtime), so a wall clock change cannot move it and nothing
 * ticks in the data: the time on the face is always computed from [startedAtMs].
 *
 * A hold or a duration (it has a [targetSec]) opens with a [GET_READY_MS] get-ready
 * countdown, then counts UP from zero. The target is a reference only: reaching it cues
 * once and changes the face, and the clock keeps counting. Stopping moves it to the adjust
 * step, which holds the stopped seconds in [stoppedSec] for the lifter to nudge and log.
 * Without a target it is a plain stopwatch counting up (a run's time), with no get-ready.
 */
data class SetClock(
    val targetSec: Int?,
    /** When the run began, or null while idle or in the adjust step. */
    val startedAtMs: Long? = null,
    /** The seconds being adjusted after a stop; non-null means the adjust step. */
    val stoppedSec: Int? = null,
    /** The cue at the end of the get-ready has played. Kept here so a rotation cannot repeat it. */
    val goCued: Boolean = false,
    /** The cue at the target has played. Kept here so a rotation cannot repeat it. */
    val targetCued: Boolean = false,
) {
    /** What a cue asks for. */
    enum class Cue { NONE, TICK, DONE }

    enum class Phase { IDLE, GET_READY, COUNTING, ADJUST }

    private val readyMs: Long get() = if (targetSec == null) 0L else GET_READY_MS

    fun phase(nowMs: Long): Phase = when {
        stoppedSec != null -> Phase.ADJUST
        startedAtMs == null -> Phase.IDLE
        nowMs - startedAtMs < readyMs -> Phase.GET_READY
        else -> Phase.COUNTING
    }

    val running: Boolean get() = startedAtMs != null

    /** Milliseconds counted so far, after the get-ready; zero before it ends. */
    fun elapsedMs(nowMs: Long): Long = startedAtMs?.let { (nowMs - it - readyMs).coerceAtLeast(0L) } ?: 0L

    fun start(nowMs: Long): SetClock = if (running || stoppedSec != null) this else copy(startedAtMs = nowMs)

    /** The seconds left of the get-ready, 5 down to 1; 0 outside it. */
    fun getReadySeconds(nowMs: Long): Int {
        if (phase(nowMs) != Phase.GET_READY) return 0
        val left = readyMs - (nowMs - (startedAtMs ?: nowMs))
        return ((left + 999) / 1000).toInt()
    }

    /** The whole seconds counted, for the face. */
    fun displaySeconds(nowMs: Long): Int = (elapsedMs(nowMs) / 1000).toInt()

    /** The count has reached the target. Always false for a stopwatch. */
    fun targetReached(nowMs: Long): Boolean =
        targetSec != null && phase(nowMs) == Phase.COUNTING && elapsedMs(nowMs) >= targetSec * 1000L

    /**
     * Stop: nothing during the get-ready (null, back to idle), otherwise the adjust step at
     * the whole seconds counted, never below 1.
     */
    fun stop(nowMs: Long): SetClock? =
        if (phase(nowMs) == Phase.GET_READY) null else copy(startedAtMs = null, stoppedSec = displaySeconds(nowMs).coerceAtLeast(MIN_SECONDS))

    /** One press of -1 s or +1 s in the adjust step: never below 1, no upper limit. */
    fun adjust(deltaSec: Int): SetClock =
        if (stoppedSec == null) this else copy(stoppedSec = (stoppedSec + deltaSec).coerceAtLeast(MIN_SECONDS))

    /**
     * The seconds a stopwatch logs if stopped now, to the nearest second. Only the stopwatch
     * uses it; a timed set logs [stoppedSec] after the adjust step.
     */
    fun stopwatchSeconds(nowMs: Long): Int = ((elapsedMs(nowMs) + 500) / 1000).toInt()

    /**
     * The cue due now and the clock to keep once it has played: a short one at the go, a long
     * one when the target is first reached. At most one, once each; call again after storing
     * the returned clock to get the next.
     */
    fun cueDue(nowMs: Long): Pair<Cue, SetClock> = when {
        targetSec != null && phase(nowMs) == Phase.COUNTING && !goCued -> Cue.TICK to copy(goCued = true)
        targetReached(nowMs) && !targetCued -> Cue.DONE to copy(targetCued = true)
        else -> Cue.NONE to this
    }

    /** What ticking the row (the footer's log action) does with this clock. */
    data class RowLog(
        /** The clock to keep: null drops it. */
        val clock: SetClock?,
        /** Log the set now. */
        val log: Boolean,
        /** The seconds to log, or null for the row's own figure (the target). */
        val seconds: Int? = null,
    )

    fun onRowLog(nowMs: Long): RowLog = when {
        // The adjust step is open: log what was adjusted.
        stoppedSec != null -> RowLog(null, true, stoppedSec)
        // A hold being timed: stop it into the adjust step instead of logging an unseen figure.
        targetSec != null && phase(nowMs) == Phase.COUNTING -> RowLog(stop(nowMs), false)
        // Idle, in the get-ready, or a stopwatch: the lifter chose the plain log.
        else -> RowLog(null, true)
    }

    companion object {
        /** The get-ready before a hold counts. */
        const val GET_READY_MS = 5_000L

        /** The last seconds of the get-ready that tick. */
        const val CUE_SECONDS = 3

        const val MIN_SECONDS = 1

        fun countdown(targetSec: Int): SetClock = SetClock(targetSec.coerceAtLeast(1))

        fun stopwatch(): SetClock = SetClock(null)

        /** The cue for the get-ready second just shown: a tick on 3, 2 and 1. */
        fun readyCue(seconds: Int): Cue = if (seconds in 1..CUE_SECONDS) Cue.TICK else Cue.NONE

        /** "1:05", the face of either clock. */
        fun face(seconds: Int): String = ClockText.minutesSeconds(seconds.toLong())

        /** Whether a metric is timed against its target figure (a hold, a duration). */
        fun countsDown(metric: ExerciseMetric): Boolean =
            metric == ExerciseMetric.HOLD || metric == ExerciseMetric.DURATION

        /** Whether a metric gets a stopwatch that fills its time (a distance run). */
        fun countsUp(metric: ExerciseMetric): Boolean = metric == ExerciseMetric.DISTANCE_TIME
    }
}
