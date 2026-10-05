package com.ironvellum.app.domain

/**
 * What the Train screen's lead card offers right now. One rule, in one place,
 * so the card never disagrees with Today about whether today's rite is done.
 */
sealed interface TrainFocus {
    /** A trial is under way: it is continued, never offered fresh. */
    data class Live(val session: WorkoutSession, val preset: WorkoutPreset?) : TrainFocus

    /** Today's scheduled rite, not yet sealed this week. */
    data class Begin(val preset: WorkoutPreset) : TrainFocus

    /** Today's rite is sealed this week; [session] is the trial that sealed it. */
    data class Sealed(val preset: WorkoutPreset, val session: WorkoutSession) : TrainFocus

    /**
     * A rest day. [next] is the next scheduled rite, offered early only when
     * [canTakeEarly]: once a trial is sealed today the offer is spent.
     */
    data class Respite(val next: WorkoutPreset?, val nextDay: Int?, val canTakeEarly: Boolean) : TrainFocus

    /** No rites at all. */
    data object NoCycle : TrainFocus

    companion object {
        /**
         * The trial that seals [preset] for the Mon-Sun week opened at
         * [weekStartMs]: a rite counts on any day of its own week, taken early
         * or made up late, so a Friday rite done on Sunday still seals Friday.
         * The latest such trial, or null.
         */
        fun sealing(preset: WorkoutPreset, sessions: List<WorkoutSession>, weekStartMs: Long): WorkoutSession? =
            sessions
                .filter { it.presetId == preset.id && (it.completedAtMs ?: 0L) >= weekStartMs }
                .maxByOrNull { it.completedAtMs ?: 0L }

        /**
         * [today] is the ISO day of week; [todayStartMs] the local midnight that
         * opened it, and [weekStartMs] the Monday midnight that opened its week.
         * [sessions] may be in any order and include unfinished ones.
         */
        fun resolve(
            presets: List<WorkoutPreset>,
            sessions: List<WorkoutSession>,
            live: WorkoutSession?,
            today: Int,
            todayStartMs: Long,
            weekStartMs: Long,
        ): TrainFocus {
            if (live != null) return Live(live, presets.firstOrNull { it.id == live.presetId })
            if (presets.isEmpty()) return NoCycle
            val sealedToday = sessions.filter { (it.completedAtMs ?: 0L) >= todayStartMs }
            val scheduled = presets.firstOrNull { it.scheduledDay == today }
            if (scheduled != null) {
                val sealing = sealing(scheduled, sessions, weekStartMs)
                return if (sealing != null) Sealed(scheduled, sealing) else Begin(scheduled)
            }
            // The next scheduled day after today, wrapping the week.
            val nextDay = (1..7).map { (today - 1 + it) % 7 + 1 }
                .firstOrNull { day -> presets.any { it.scheduledDay == day } }
            return Respite(
                next = nextDay?.let { day -> presets.first { it.scheduledDay == day } },
                nextDay = nextDay,
                canTakeEarly = nextDay != null && sealedToday.isEmpty(),
            )
        }
    }
}
