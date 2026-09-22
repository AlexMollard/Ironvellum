package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private fun day(date: String, scheduled: Int?, completed: Boolean) =
        Streak.DayRecord(LocalDate.parse(date), scheduled, completed)

    @Test
    fun `no records no streak`() {
        assertEquals(0, Streak.current(emptyList(), LocalDate.parse("2026-09-10")))
    }

    @Test
    fun `today completed counts immediately`() {
        val records = listOf(day("2026-09-10", 4, true))
        assertEquals(1, Streak.current(records, LocalDate.parse("2026-09-10")))
    }

    @Test
    fun `consecutive scheduled days accumulate across rest days`() {
        val records = listOf(
            day("2026-09-10", 4, true), // Thu
            day("2026-09-09", null, false), // Wed rest
            day("2026-09-08", 2, true), // Tue
            day("2026-09-07", 1, true), // Mon
        )
        assertEquals(3, Streak.current(records, LocalDate.parse("2026-09-10")))
    }

    @Test
    fun `missed scheduled day in the past breaks the streak`() {
        val records = listOf(
            day("2026-09-10", 4, true),
            day("2026-09-08", 2, false), // skipped Tuesday
            day("2026-09-07", 1, true),
        )
        assertEquals(1, Streak.current(records, LocalDate.parse("2026-09-10")))
    }

    @Test
    fun `today pending does not break the streak yet`() {
        val records = listOf(
            day("2026-09-09", 3, true),
            day("2026-09-10", 4, false), // today, not trained yet
        )
        assertEquals(1, Streak.current(records, LocalDate.parse("2026-09-10")))
    }

    @Test
    fun `freeform completions count`() {
        val records = listOf(
            day("2026-09-10", null, true),
            day("2026-09-09", null, true),
        )
        assertEquals(2, Streak.current(records, LocalDate.parse("2026-09-10")))
    }
}

class ArmyClassTest {

    @Test
    fun `class ladder thresholds`() {
        assertEquals("Recruit", ArmyClass.forLevel(1).title)
        assertEquals("Soldier", ArmyClass.forLevel(5).title)
        assertEquals("Knight", ArmyClass.forLevel(15).title)
        assertEquals("Elite Knight", ArmyClass.forLevel(25).title)
        assertEquals("Commander", ArmyClass.forLevel(40).title)
        assertEquals("Marshal", ArmyClass.forLevel(55).title)
        assertEquals("Grand Marshal", ArmyClass.forLevel(70).title)
        assertEquals("Grand Marshal", ArmyClass.forLevel(99).title)
    }

    @Test
    fun `next tier reveals the road ahead`() {
        assertEquals("Soldier", ArmyClass.nextFor(1)?.title)
        assertEquals(null, ArmyClass.nextFor(70))
    }
}

class RankTest {

    @Test
    fun `rank bands match the strength ladder`() {
        assertEquals(Rank.UNTRAINED, Rank.forLevel(1))
        assertEquals(Rank.UNTRAINED, Rank.forLevel(9))
        assertEquals(Rank.NOVICE, Rank.forLevel(10))
        assertEquals(Rank.NOVICE, Rank.forLevel(29))
        assertEquals(Rank.INTERMEDIATE, Rank.forLevel(30))
        assertEquals(Rank.INTERMEDIATE, Rank.forLevel(49))
        assertEquals(Rank.ADVANCED, Rank.forLevel(50))
        assertEquals(Rank.ADVANCED, Rank.forLevel(79))
        assertEquals(Rank.ELITE, Rank.forLevel(80))
    }
}
