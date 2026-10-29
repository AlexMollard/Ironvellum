package com.ironvellum.app.domain

/**
 * The figures the lifter screen derives from the sessions it already loaded (no extra server read): the XP sum, the
 * per-week rate over the last four weeks, how long ago the last workout was and the strength series with its peak.
 * Moved out of `LifterScreen` unchanged so the web viewer's port can be pinned against the same rules.
 */
object LifterSummary {
    /** One shared workout as the summary reads it. */
    data class Entry(val completedAtMs: Long?, val xpAwarded: Int, val strengthScore: Int)

    data class Figures(
        val count: Int,
        val xp: Long,
        /** Workouts in the last 28 days over 4: the screen prints one decimal. */
        val perWeek: Double,
        /** Whole days since the newest completed workout; null when none is completed. */
        val daysSince: Int?,
        /** Strength score per workout, oldest first (workouts without a time sort last). */
        val strengthSeries: List<Double>,
    ) {
        val peak: Int? get() = strengthSeries.maxOrNull()?.toInt()
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun of(sessions: List<Entry>, nowMs: Long): Figures {
        val times = sessions.mapNotNull { it.completedAtMs }.sorted()
        val chrono = sessions.sortedBy { it.completedAtMs ?: Long.MAX_VALUE }
        return Figures(
            count = sessions.size,
            xp = sessions.sumOf { it.xpAwarded }.toLong(),
            perWeek = times.count { nowMs - it <= 28 * DAY_MS } / 4.0,
            daysSince = times.lastOrNull()?.let { ((nowMs - it) / DAY_MS).toInt() },
            strengthSeries = chrono.map { it.strengthScore.toDouble() },
        )
    }
}
