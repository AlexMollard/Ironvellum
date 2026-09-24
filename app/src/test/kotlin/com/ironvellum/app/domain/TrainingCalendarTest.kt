package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The streak and the best-week count are headline numbers, and both are pure
 * calendar arithmetic over a set of dates — the place where an off-by-one lives
 * silently because nothing crashes, the number is just wrong.
 *
 * Neither had a test of its own: the title rules were covered by setting the
 * ledger field directly, which never exercises the arithmetic that produces it.
 */
class TrainingCalendarTest {

    private fun days(vararg iso: String) = iso.map { LocalDate.parse(it) }.toSet()

    @Test
    fun `streak counts today when today was trained`() {
        val today = LocalDate.parse("2026-03-10")
        assertEquals(
            3,
            Titles.trainingStreakDays(days("2026-03-08", "2026-03-09", "2026-03-10"), today = today),
        )
    }

    @Test
    fun `a rest stretch so far today does not break the streak`() {
        // Opening the app in the morning before training must not show 0: the
        // chain is alive, and today sits inside its span.
        val today = LocalDate.parse("2026-03-10")
        assertEquals(
            3,
            Titles.trainingStreakDays(days("2026-03-08", "2026-03-09"), today = today),
        )
    }

    @Test
    fun `two empty days inside the current week stay open`() {
        // Fri–Sun trained, Monday and Tuesday empty: the chain is still alive,
        // and its span runs through today.
        val today = LocalDate.parse("2026-03-10")
        assertEquals(
            5,
            Titles.trainingStreakDays(days("2026-03-06", "2026-03-07", "2026-03-08"), today = today),
        )
    }

    @Test
    fun `a washed previous week breaks the streak`() {
        // Same training, one week later and nothing since: the empty week has
        // closed untrained, so the streak dies at its boundary.
        val today = LocalDate.parse("2026-03-17")
        assertEquals(
            0,
            Titles.trainingStreakDays(days("2026-03-06", "2026-03-07", "2026-03-08"), today = today),
        )
    }

    @Test
    fun `streak stops at the first gap rather than counting every date`() {
        val today = LocalDate.parse("2026-03-10")
        val dates = days("2026-01-01", "2026-01-02", "2026-03-09", "2026-03-10")
        assertEquals(2, Titles.trainingStreakDays(dates, today = today))
    }

    @Test
    fun `streak spans a month boundary`() {
        val today = LocalDate.parse("2026-03-01")
        assertEquals(
            3,
            Titles.trainingStreakDays(days("2026-02-27", "2026-02-28", "2026-03-01"), today = today),
        )
    }

    @Test
    fun `streak spans a leap day`() {
        // 2028 is a leap year: 29 February exists and must be walked over.
        val today = LocalDate.parse("2028-03-01")
        assertEquals(
            3,
            Titles.trainingStreakDays(days("2028-02-28", "2028-02-29", "2028-03-01"), today = today),
        )
    }

    @Test
    fun `streak spans a year boundary`() {
        val today = LocalDate.parse("2027-01-01")
        assertEquals(
            3,
            Titles.trainingStreakDays(days("2026-12-30", "2026-12-31", "2027-01-01"), today = today),
        )
    }

    @Test
    fun `no training at all is not a streak`() {
        assertEquals(0, Titles.trainingStreakDays(emptySet(), today = LocalDate.parse("2026-03-10")))
    }

    @Test
    fun `future dates do not inflate the streak`() {
        // A device clock moved backwards leaves dates ahead of today; they must
        // not be counted, and must not be treated as the start of the walk.
        val today = LocalDate.parse("2026-03-10")
        val dates = days("2026-03-10", "2026-03-11", "2026-03-12")
        assertEquals(1, Titles.trainingStreakDays(dates, today = today))
    }

    @Test
    fun `best week is the densest rolling window, not the calendar week`() {
        // Mon-Wed then Sat-Sun of the same week is 5, but Thu-Sun plus the
        // following Mon-Tue is 6 across a weekend — a fixed Mon-Sun window
        // would report 5 and the title would never unlock.
        val dates = days(
            "2026-03-05", "2026-03-06", "2026-03-07", "2026-03-08", // Thu-Sun
            "2026-03-09", "2026-03-10", // Mon-Tue
        )
        assertEquals(6, Titles.bestWeekWorkouts(dates))
    }

    @Test
    fun `best week window is seven days inclusive of the start`() {
        // Exactly seven days apart must NOT both count: day 0 and day 7 are
        // eight calendar days, so the window is [start, start+7).
        assertEquals(1, Titles.bestWeekWorkouts(days("2026-03-01", "2026-03-08")))
        assertEquals(2, Titles.bestWeekWorkouts(days("2026-03-01", "2026-03-07")))
    }

    @Test
    fun `best week of no training is zero`() {
        assertEquals(0, Titles.bestWeekWorkouts(emptySet()))
    }
}
