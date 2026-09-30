package com.ironvellum.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weekly band-goal payout must land exactly once per band+week: a
 * re-render or re-read must never pay twice, and a new week must pay again.
 * The once-per-week behaviour lives entirely in the paid-key set keyed through
 * [WarbandGoalPayout.payoutKey] and [WarbandGoalPayout.isoWeekKey] — so the
 * week-key tests are load-bearing: collapse the key to a constant (the
 * regression this guards) and the second-week assertions fail.
 */
class WarbandGoalPayoutTest {

    // Mondays of two consecutive ISO weeks: 2026 week 34 starts Aug 17.
    private val week1 = LocalDate.of(2026, 8, 17)
    private val week2 = LocalDate.of(2026, 8, 24)
    private val band = "wb-123"

    @Test
    fun `owes when the band crosses the goal and this lifter contributed`() {
        assertTrue(WarbandGoalPayout.owes(total = 12, goal = 12, contributed = 3, alreadyPaid = false))
        assertTrue(WarbandGoalPayout.owes(total = 15, goal = 12, contributed = 1, alreadyPaid = false))
    }

    @Test
    fun `never owes below the goal or with no contribution`() {
        assertFalse(WarbandGoalPayout.owes(total = 11, goal = 12, contributed = 4, alreadyPaid = false))
        assertFalse(WarbandGoalPayout.owes(total = 15, goal = 12, contributed = 0, alreadyPaid = false))
    }

    @Test
    fun `a paid band week never owes again`() {
        assertFalse(WarbandGoalPayout.owes(total = 12, goal = 12, contributed = 3, alreadyPaid = true))
    }

    @Test
    fun `iso week key is stable within a week and rolls over on Monday`() {
        assertEquals(WarbandGoalPayout.isoWeekKey(week1), WarbandGoalPayout.isoWeekKey(week1.plusDays(6)))
        assertNotEquals(WarbandGoalPayout.isoWeekKey(week1), WarbandGoalPayout.isoWeekKey(week2))
    }

    @Test
    fun `pays once this week and again next week — a collapsed week key breaks this`() {
        fun paidKeys(vararg weeks: String) = weeks.map { WarbandGoalPayout.payoutKey(band, it) }.toSet()

        // Week 1, goal crossed, this lifter contributed: owed.
        val key1 = WarbandGoalPayout.isoWeekKey(week1)
        assertTrue(WarbandGoalPayout.owes(band, key1, total = 12, goal = 12, contributed = 2, paidKeys = emptySet()))

        // Same week, same numbers, after the flag was written: not owed again.
        assertFalse(WarbandGoalPayout.owes(band, key1, total = 12, goal = 12, contributed = 2, paidKeys = paidKeys(key1)))

        // Next week the counts reset to the new week's numbers: owed again.
        val key2 = WarbandGoalPayout.isoWeekKey(week2)
        assertTrue(WarbandGoalPayout.owes(band, key2, total = 5, goal = 5, contributed = 1, paidKeys = paidKeys(key1)))
    }

    @Test
    fun `payout keys separate bands and weeks`() {
        assertNotEquals(WarbandGoalPayout.payoutKey(band, "a"), WarbandGoalPayout.payoutKey("other", "a"))
        assertNotEquals(WarbandGoalPayout.payoutKey(band, "a"), WarbandGoalPayout.payoutKey(band, "b"))
    }

    @Test
    fun `the stated bonus matches the Xp constant the payout uses`() {
        assertEquals(40, WarbandGoalPayout.BONUS_XP)
        assertEquals(40, com.ironvellum.app.domain.Xp.BAND_GOAL_BONUS)
    }
}
