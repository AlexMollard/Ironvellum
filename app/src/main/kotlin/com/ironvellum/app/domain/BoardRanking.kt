package com.ironvellum.app.domain

import com.ironvellum.app.data.cloud.LeaderboardRow
import com.ironvellum.app.data.cloud.LiftBoardRow

/**
 * How the boards order their rows, moved out of `LeaderboardScreen` unchanged so the web viewer's port can be pinned against the same
 * rules: the training board's metric value and sort (XP breaks ties, then name), and the lift boards' tier ranking with shared ranks.
 */
object BoardRanking {
    /** The training board's ranking metrics; the screen owns their labels. */
    enum class Metric { Xp, Level, Streak, Titles, Strength, Last7 }

    /** The raw value a metric ranks by. */
    fun value(metric: Metric, row: LeaderboardRow): Long = when (metric) {
        Metric.Xp -> row.totalXp
        Metric.Level -> row.level.toLong()
        Metric.Streak -> row.streakDays.toLong()
        Metric.Titles -> row.titlesCount.toLong()
        Metric.Strength -> row.lifetimeStrength
        Metric.Last7 -> row.sessionsLast7d.toLong()
    }

    /** Local re-sort for the selected metric; XP breaks ties, then name for stability. No network call. */
    fun sort(rows: List<LeaderboardRow>, metric: Metric): List<LeaderboardRow> = rows.sortedWith(
        compareByDescending<LeaderboardRow> { value(metric, it) }
            .thenByDescending { it.totalXp }
            .thenBy { it.displayName },
    )

    /** One lifter's place on one lift board; [step] is the tier step that ranked them, never a ratio. */
    data class LiftStanding(val rank: Int, val row: LiftBoardRow, val step: Int)

    /**
     * Ranks one lift for a window, best tier first. Ties share a rank (1, 1, 3) so
     * two lifters on the same tier are never ordered by an arbitrary name sort
     * that reads as one beating the other. The week window ([recent]) drops lifters with no qualifying
     * set in the last 7 days rather than ranking their stale tier.
     */
    fun liftStandings(rows: List<LiftBoardRow>, lift: Lift, recent: Boolean): List<LiftStanding> {
        val ranked = rows
            .filter { it.lift == lift }
            .mapNotNull { row ->
                val step = if (recent) row.recentStep else row.step
                step?.let { row to it }
            }
            .sortedWith(
                compareByDescending<Pair<LiftBoardRow, Int>> { it.second }
                    .thenBy { it.first.displayName.lowercase() },
            )
        var rank = 0
        return ranked.mapIndexed { index, (row, step) ->
            if (index == 0 || step != ranked[index - 1].second) rank = index + 1
            LiftStanding(rank, row, step)
        }
    }
}
