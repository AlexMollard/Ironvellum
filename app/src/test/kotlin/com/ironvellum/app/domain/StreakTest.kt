package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private fun d(vararg iso: String) = iso.map { LocalDate.parse(it) }.toSet()

    @Test
    fun `no records no streak`() {
        assertEquals(0, Streak.current(emptySet(), LocalDate.parse("2026-09-10")))
    }

    @Test
    fun `today trained counts today itself`() {
        assertEquals(1, Streak.current(d("2026-09-10"), LocalDate.parse("2026-09-10")))
    }

    @Test
    fun `the chain spans from the oldest linked workout through today`() {
        // Mon + Thu, today Thursday: the chain opened Monday, so it reads 4.
        assertEquals(4, Streak.current(d("2026-09-07", "2026-09-10"), LocalDate.parse("2026-09-10")))
    }

    @Test
    fun `a seven day gap holds the chain together`() {
        // Trained Mon Sep 7, then Mon Sep 14: exactly a week, still linked.
        assertEquals(8, Streak.current(d("2026-09-07", "2026-09-14"), LocalDate.parse("2026-09-14")))
    }

    @Test
    fun `a gap past the week closes the chain`() {
        // Mon Sep 7 then Thu Sep 17: ten days apart, the chain broke.
        assertEquals(1, Streak.current(d("2026-09-07", "2026-09-17"), LocalDate.parse("2026-09-17")))
    }

    @Test
    fun `letting a whole week slip since the last workout reads zero`() {
        // Trained Mon Sep 14, nothing since, today Tue Sep 22: eight days.
        assertEquals(0, Streak.current(d("2026-09-14"), LocalDate.parse("2026-09-22")))
    }

    @Test
    fun `only the current chain counts - an old block before a break is gone`() {
        // A week of training, a ten-day hole, then one workout today: the old
        // block must not leak into the new chain.
        assertEquals(
            1,
            Streak.current(
                d("2026-08-24", "2026-08-25", "2026-08-26", "2026-08-27", "2026-08-28", "2026-09-11"),
                LocalDate.parse("2026-09-11"),
            ),
        )
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
