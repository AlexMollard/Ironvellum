package com.ironvellum.app.domain

/**
 * The clock on a timed set's row, as plain state on the monotonic clock
 * (SystemClock.elapsedRealtime), so a wall clock change cannot move it and nothing
 * ticks in the data: the time on the face is always computed from [accumulatedMs]
 * and [startedAtMs]. With a [targetSec] it counts down toward it (a hold, a
 * duration); without one it is a stopwatch counting up (a run's time).
 */
data class SetClock(
    val targetSec: Int?,
    /** Time already banked by earlier runs, before a pause. */
    val accumulatedMs: Long = 0,
    /** When the current run began, or null while idle or paused. */
    val startedAtMs: Long? = null,
) {
    /** What a cue on the last seconds of a countdown asks for. */
    enum class Cue { NONE, TICK, DONE }

    val running: Boolean get() = startedAtMs != null

    /** True once it has been started and then paused (or can be resumed). */
    val paused: Boolean get() = !running && accumulatedMs > 0

    fun elapsedMs(nowMs: Long): Long = accumulatedMs + (startedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)

    fun start(nowMs: Long): SetClock = if (running) this else copy(startedAtMs = nowMs)

    fun pause(nowMs: Long): SetClock = if (!running) this else copy(accumulatedMs = elapsedMs(nowMs), startedAtMs = null)

    /** Milliseconds to go to the target; null for a stopwatch. Never below zero. */
    fun remainingMs(nowMs: Long): Long? = targetSec?.let { (it * 1000L - elapsedMs(nowMs)).coerceAtLeast(0L) }

    /** A countdown that has run out. A stopwatch is never done: the lifter stops it. */
    fun isDone(nowMs: Long): Boolean = remainingMs(nowMs) == 0L

    /**
     * The seconds on the face: what is left, rounded up so the last second reads 1 and 0 is
     * only ever shown at the end, for a countdown; whole seconds elapsed for a stopwatch.
     */
    fun displaySeconds(nowMs: Long): Int =
        remainingMs(nowMs)?.let { ((it + 999) / 1000).toInt() } ?: (elapsedMs(nowMs) / 1000).toInt()

    /**
     * The seconds to log if the lifter finishes now: the target when the countdown ran out,
     * otherwise the time actually held, to the nearest second and never past the target.
     */
    fun loggedSeconds(nowMs: Long): Int {
        val actual = ((elapsedMs(nowMs) + 500) / 1000).toInt()
        return if (targetSec == null) actual else if (isDone(nowMs)) targetSec else actual.coerceAtMost(targetSec)
    }

    companion object {
        /** The last seconds that get a cue. */
        const val CUE_SECONDS = 3

        fun countdown(targetSec: Int): SetClock = SetClock(targetSec.coerceAtLeast(1))

        fun stopwatch(): SetClock = SetClock(null)

        /**
         * The cue for the [displaySeconds] a countdown just showed: a tick on 3, 2 and 1 and the
         * long one at 0. Called once per change of the displayed second.
         */
        fun cueAt(displaySeconds: Int): Cue = when {
            displaySeconds == 0 -> Cue.DONE
            displaySeconds in 1..CUE_SECONDS -> Cue.TICK
            else -> Cue.NONE
        }

        /** "1:05", the face of either clock. */
        fun face(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

        /** Whether a metric is timed by a countdown toward its figure (a hold, a duration). */
        fun countsDown(metric: ExerciseMetric): Boolean =
            metric == ExerciseMetric.HOLD || metric == ExerciseMetric.DURATION

        /** Whether a metric gets a stopwatch that fills its time (a distance run). */
        fun countsUp(metric: ExerciseMetric): Boolean = metric == ExerciseMetric.DISTANCE_TIME
    }
}
