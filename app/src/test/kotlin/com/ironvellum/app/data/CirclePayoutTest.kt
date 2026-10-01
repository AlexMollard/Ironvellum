package com.ironvellum.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weekly circle-goal payout must land exactly once per LIFTER and week: a
 * re-render or re-read must never pay twice, a new week must pay again, and a
 * lifter who changes circles inside a week must not be paid again for it. The
 * once-per-week behaviour lives entirely in the paid-key set keyed through
 * [CirclePayout.payoutKey] and [CirclePayout.weekKey] — so the week-key tests
 * are load-bearing: collapse the key to a constant (the regression this
 * guards) and the second-week assertions fail.
 */
class CirclePayoutTest {

    // Mondays of two consecutive weeks.
    private val week1 = LocalDate.of(2026, 8, 17)
    private val week2 = LocalDate.of(2026, 8, 24)
    private val lifter = "lifter-123"

    @Test
    fun `owes when the circle crosses the goal and this lifter contributed`() {
        assertTrue(CirclePayout.owes(total = 12, goal = 12, contributed = 3, alreadyPaid = false))
        assertTrue(CirclePayout.owes(total = 15, goal = 12, contributed = 1, alreadyPaid = false))
    }

    @Test
    fun `never owes below the goal or with no contribution`() {
        assertFalse(CirclePayout.owes(total = 11, goal = 12, contributed = 4, alreadyPaid = false))
        assertFalse(CirclePayout.owes(total = 15, goal = 12, contributed = 0, alreadyPaid = false))
    }

    @Test
    fun `a paid lifter week never owes again`() {
        assertFalse(CirclePayout.owes(total = 12, goal = 12, contributed = 3, alreadyPaid = true))
    }

    @Test
    fun `the week key is the UTC Monday, stable within a week and rolling over on Monday`() {
        assertEquals("2026-08-17", CirclePayout.weekKey(week1))
        assertEquals(CirclePayout.weekKey(week1), CirclePayout.weekKey(week1.plusDays(6)))
        assertEquals("2026-08-24", CirclePayout.weekKey(week1.plusDays(7)))
        assertNotEquals(CirclePayout.weekKey(week1), CirclePayout.weekKey(week2))
    }

    @Test
    fun `pays once this week and again next week — a collapsed week key breaks this`() {
        fun paidKeys(vararg weeks: String) = weeks.map { CirclePayout.payoutKey(lifter, it) }.toSet()

        // Week 1, goal crossed, this lifter contributed: owed.
        val key1 = CirclePayout.weekKey(week1)
        assertTrue(CirclePayout.owes(lifter, key1, total = 12, goal = 12, contributed = 2, paidKeys = emptySet()))

        // Same week, same numbers, after the flag was written: not owed again.
        assertFalse(CirclePayout.owes(lifter, key1, total = 12, goal = 12, contributed = 2, paidKeys = paidKeys(key1)))

        // Next week the counts reset to the new week's numbers: owed again.
        val key2 = CirclePayout.weekKey(week2)
        assertTrue(CirclePayout.owes(lifter, key2, total = 5, goal = 5, contributed = 1, paidKeys = paidKeys(key1)))
    }

    @Test
    fun `the flag is the lifter's and the week's, not the circle's`() {
        // The farm this closes: form a circle, meet the goal, leave, form another
        // in the same week. Nothing in the key names a circle, so the second
        // circle finds the lifter already paid.
        val paid = setOf(CirclePayout.payoutKey(lifter, CirclePayout.weekKey(week1)))
        assertFalse(
            CirclePayout.owes(lifter, CirclePayout.weekKey(week1), total = 6, goal = 5, contributed = 6, paidKeys = paid),
        )
        // A different lifter in the same week is a different flag.
        assertTrue(
            CirclePayout.owes("someone-else", CirclePayout.weekKey(week1), total = 6, goal = 5, contributed = 6, paidKeys = paid),
        )
    }

    @Test
    fun `payout keys separate lifters and weeks`() {
        assertNotEquals(CirclePayout.payoutKey(lifter, "a"), CirclePayout.payoutKey("other", "a"))
        assertNotEquals(CirclePayout.payoutKey(lifter, "a"), CirclePayout.payoutKey(lifter, "b"))
    }

    @Test
    fun `a flag written by the band-keyed store maps to that week's Monday`() {
        // 2026 ISO week 34 opens on Monday 17 August.
        assertEquals("2026-08-17", CirclePayout.legacyWeekKey("band-uuid:2026-W34"))
        // An ISO year that begins in the previous calendar year.
        assertEquals("2024-12-30", CirclePayout.legacyWeekKey("band-uuid:2025-W01"))
        assertNull(CirclePayout.legacyWeekKey("not a flag"))
        assertNull(CirclePayout.legacyWeekKey("lifter:2026-08-17"))
    }

    @Test
    fun `the stated bonus matches the Xp constant the payout uses`() {
        assertEquals(40, CirclePayout.BONUS_XP)
        assertEquals(40, com.ironvellum.app.domain.Xp.BAND_GOAL_BONUS)
    }
}
