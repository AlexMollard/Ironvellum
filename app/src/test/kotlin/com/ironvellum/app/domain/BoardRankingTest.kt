package com.ironvellum.app.domain

import com.ironvellum.app.data.cloud.LeaderboardRow
import com.ironvellum.app.data.cloud.LiftBoardRow
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardRankingTest {
    private fun row(id: String, name: String, xp: Long = 0, level: Int = 1, streak: Int = 0, titles: Int = 0, strength: Long = 0, last7: Int = 0) =
        LeaderboardRow(id, name, level, xp, streak, titles, strength, last7, null)

    private val rows = listOf(
        row("a", "Mara", xp = 9000, level = 31, streak = 5, titles = 12, strength = 14000, last7 = 4),
        row("b", "Jonah", xp = 9000, level = 31, streak = 9, titles = 8, strength = 15000, last7 = 2),
        row("c", "Ines", xp = 4000, level = 18, streak = 9, titles = 8, strength = 15000, last7 = 6),
    )

    @Test fun eachMetricReadsItsOwnValue() {
        val mara = rows[0]
        assertEquals(9000L, BoardRanking.value(BoardRanking.Metric.Xp, mara)); assertEquals(31L, BoardRanking.value(BoardRanking.Metric.Level, mara))
        assertEquals(5L, BoardRanking.value(BoardRanking.Metric.Streak, mara)); assertEquals(12L, BoardRanking.value(BoardRanking.Metric.Titles, mara))
        assertEquals(14000L, BoardRanking.value(BoardRanking.Metric.Strength, mara)); assertEquals(4L, BoardRanking.value(BoardRanking.Metric.Last7, mara))
    }

    @Test fun xpBreaksTiesThenNameDecides() {
        // Level ties at 31: equal XP too, so the name decides (Jonah before Mara).
        assertEquals(listOf("b", "a", "c"), BoardRanking.sort(rows, BoardRanking.Metric.Level).map { it.userId })
        // Streak ties at 9 between Jonah (9000 XP) and Ines (4000 XP): the higher XP first.
        assertEquals(listOf("b", "c", "a"), BoardRanking.sort(rows, BoardRanking.Metric.Streak).map { it.userId })
    }

    @Test fun anEmptyBoardSortsToNothing() {
        assertEquals(emptyList<LeaderboardRow>(), BoardRanking.sort(emptyList(), BoardRanking.Metric.Xp))
    }

    private fun lift(id: String, name: String, step: Int, recent: Int?, which: Lift = Lift.SQUAT) = LiftBoardRow(id, name, 10, null, which, step, recent)

    @Test fun tiesShareARankThenSkip() {
        val standings = BoardRanking.liftStandings(
            listOf(lift("a", "Mara", 7, 6), lift("b", "jonah", 7, null), lift("c", "Ines", 5, 5), lift("d", "Odin", 7, 6), lift("e", "Eve", 2, 1)),
            Lift.SQUAT, recent = false,
        )
        assertEquals(listOf("b", "a", "d", "c", "e"), standings.map { it.row.userId })
        assertEquals(listOf(1, 1, 1, 4, 5), standings.map { it.rank })
    }

    @Test fun theWeekWindowDropsLiftersWithoutARecentStep() {
        val standings = BoardRanking.liftStandings(
            listOf(lift("a", "Mara", 7, 6), lift("b", "jonah", 7, null), lift("c", "Ines", 5, 5)),
            Lift.SQUAT, recent = true,
        )
        assertEquals(listOf("a", "c"), standings.map { it.row.userId }); assertEquals(listOf(6, 5), standings.map { it.step })
    }

    @Test fun otherLiftsDoNotRankOnThisBoard() {
        assertEquals(emptyList<BoardRanking.LiftStanding>(), BoardRanking.liftStandings(listOf(lift("a", "Mara", 4, null, Lift.BENCH)), Lift.SQUAT, recent = false))
    }
}
