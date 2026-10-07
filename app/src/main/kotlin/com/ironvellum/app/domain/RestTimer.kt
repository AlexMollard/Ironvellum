package com.ironvellum.app.domain

/**
 * The rest between sets, as plain state: which trial it belongs to and when
 * it ends, on the monotonic clock (SystemClock.elapsedRealtime), so a wall
 * clock change mid-rest cannot shorten or stretch it. Pure, so the rules are
 * tested here; data/RestClock.kt holds the one live instance and the trial
 * service turns its end into a buzz.
 */
data class RestTimer(
    val sessionId: Long,
    /** When the rest ends, in elapsed-realtime milliseconds. */
    val endsAtMs: Long,
    /** The whole rest as it now stands, extensions included. */
    val totalMs: Long,
    /** The most rest worth taking after this set ([RestRules.Window.max]); 0 shows no ceiling. */
    val maxSeconds: Int = 0,
) {
    fun remainingMs(nowMs: Long): Long = (endsAtMs - nowMs).coerceAtLeast(0L)

    fun isOver(nowMs: Long): Boolean = nowMs >= endsAtMs

    /** "1:05": whole seconds rounded up, so the last second reads 0:01, never 0:00 while it runs. */
    fun label(nowMs: Long): String {
        val seconds = (remainingMs(nowMs) + 999) / 1000
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    /** +[seconds] on the time left; a rest that already ran out restarts from now. */
    fun extended(seconds: Int, nowMs: Long): RestTimer {
        val ends = maxOf(endsAtMs, nowMs) + seconds * 1000L
        return copy(endsAtMs = ends, totalMs = totalMs + (ends - endsAtMs))
    }

    /**
     * -[seconds] off the time left, or null when that leaves nothing: the rest
     * is over as if skipped, so the caller drops it and no end is announced.
     * The whole shrinks by what was cut, so the sweep keeps its proportion.
     */
    fun shortened(seconds: Int, nowMs: Long): RestTimer? {
        val ends = endsAtMs - seconds * 1000L
        if (ends <= nowMs) return null
        return copy(endsAtMs = ends, totalMs = (totalMs - seconds * 1000L).coerceAtLeast(ends - nowMs))
    }

    companion object {
        const val EXTEND_SECONDS = 15
        const val SHORTEN_SECONDS = 15

        fun start(sessionId: Long, seconds: Int, nowMs: Long, maxSeconds: Int = 0): RestTimer =
            RestTimer(sessionId, nowMs + seconds * 1000L, seconds * 1000L, maxSeconds)

        /**
         * The rest after a set of [exerciseName]: the movement's [RestRules]
         * window. The timer counts down to its ready time, which the trial's
         * time estimate prices too; its max is shown as the ceiling.
         * [metric] and [weighted] are the catalogue's, when known.
         */
        fun window(
            exerciseName: String,
            focus: TrainingFocus,
            metric: ExerciseMetric? = null,
            weighted: Boolean? = null,
        ): RestRules.Window = RestRules.window(exerciseName, focus, metric, weighted)

        /** "1:30" for [seconds], the same shape as [label]. */
        fun clock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

        /**
         * True when ticking [setId] to [nowDone] begins a rest: the set goes
         * from not done to done and another set is still waiting (a warm-up never waits). Unticking,
         * re-saving a done set, or ticking the last set starts nothing.
         */
        fun startsRest(before: List<SessionSet>, setId: Long, nowDone: Boolean): Boolean {
            val set = before.firstOrNull { it.id == setId } ?: return false
            if (set.done || !nowDone) return false
            return before.any { it.isPending && it.id != setId }
        }
    }
}
