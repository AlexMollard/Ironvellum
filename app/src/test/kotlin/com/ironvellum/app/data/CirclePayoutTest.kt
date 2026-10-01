package com.ironvellum.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The weekly circle bonus must land exactly once per LIFTER and week: a
 * re-read must never pay twice, a new week must pay again, and a lifter who
 * changes circles inside a week must not be paid again for it. The server
 * decides which weeks are owed; this guards the client turning that list into
 * payments — the paid-key set keyed through [CirclePayout.payoutKey] is
 * load-bearing: collapse the key to a constant and the second-week
 * assertions fail.
 */
class CirclePayoutTest {

    private val lifter = "lifter-123"

    private fun week(week: String, days: Int = 2) = CircleBonusWeek(week, days, "North Gate")

    @Test
    fun `owes each unpaid week the server lists, oldest first`() {
        val owed = CirclePayout.owedWeeks(listOf(week("2026-08-24"), week("2026-08-17"))) { false }
        assertEquals(listOf("2026-08-17", "2026-08-24"), owed)
    }

    @Test
    fun `a paid week is never owed again but the next one is`() {
        val paid = setOf(CirclePayout.payoutKey(lifter, "2026-08-17"))
        val owed = CirclePayout.owedWeeks(listOf(week("2026-08-17"), week("2026-08-24"))) {
            CirclePayout.payoutKey(lifter, it) in paid
        }
        assertEquals(listOf("2026-08-24"), owed)
    }

    @Test
    fun `a week without a day of the lifter's own is never owed`() {
        assertEquals(emptyList<String>(), CirclePayout.owedWeeks(listOf(week("2026-08-17", days = 0))) { false })
    }

    @Test
    fun `the same week listed for two circles is owed once`() {
        // The farm this closes: meet the goal, leave, join another in the same
        // week. The flag names no circle, so the week is one payment.
        val owed = CirclePayout.owedWeeks(
            listOf(week("2026-08-17"), CircleBonusWeek("2026-08-17", 2, "Other Circle")),
        ) { false }
        assertEquals(listOf("2026-08-17"), owed)
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
        assertEquals(40, com.ironvellum.app.domain.Xp.CIRCLE_GOAL_BONUS)
    }
}
