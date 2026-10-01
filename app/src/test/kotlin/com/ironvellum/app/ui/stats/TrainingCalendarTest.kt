package com.ironvellum.app.ui.stats

import com.ironvellum.app.domain.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingCalendarTest {
    private val zone = ZoneId.of("UTC")

    private fun trial(id: Long, day: LocalDate, hour: Int = 9) = WorkoutSession(
        id = id,
        label = "t",
        startedAtMs = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        completedAtMs = day.atTime(hour + 1, 0).atZone(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun `weeks follow the locale's first day and pad to whole weeks`() {
        // 1 Oct 2026 is a Thursday.
        val monday = calendarWeeks(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(5, monday.size)
        assertEquals(3, monday.first().takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 10, 1), monday.first()[3])
        assertEquals(LocalDate.of(2026, 10, 31), monday.last().filterNotNull().last())
        assertNull(monday.last().last())

        val sunday = calendarWeeks(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(4, sunday.first().takeWhile { it == null }.size)
        assertEquals(true, sunday.all { it.size == 7 })
    }

    @Test
    fun `a month that starts on the week's first day has no leading blanks`() {
        // 1 Jun 2026 is a Monday.
        val weeks = calendarWeeks(YearMonth.of(2026, 6), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 6, 1), weeks.first().first())
    }

    @Test
    fun `two trials on one day count twice and the later one is last`() {
        val day = LocalDate.of(2026, 10, 1)
        val byDay = trialsByDay(listOf(trial(2, day, 18), trial(1, day, 7)), zone)
        assertEquals(listOf(1L, 2L), byDay[day])
        assertEquals(2, trialsInMonth(byDay, YearMonth.of(2026, 10), day))
    }

    @Test
    fun `the month count ignores other months and days after today`() {
        val today = LocalDate.of(2026, 10, 10)
        val byDay = trialsByDay(
            listOf(
                trial(1, LocalDate.of(2026, 9, 30)),
                trial(2, LocalDate.of(2026, 10, 2)),
                trial(3, today),
                // clock skew can leave a trial dated ahead of today
                trial(4, LocalDate.of(2026, 10, 20)),
            ),
            zone,
        )
        assertEquals(2, trialsInMonth(byDay, YearMonth.of(2026, 10), today))
        assertEquals(1, trialsInMonth(byDay, YearMonth.of(2026, 9), today))
        assertEquals(0, trialsInMonth(emptyMap(), YearMonth.of(2026, 10), today))
    }

    @Test
    fun `a trial without a finish time is filed under its start day`() {
        val day = LocalDate.of(2026, 10, 3)
        val session = trial(1, day).copy(completedAtMs = null)
        assertEquals(day, trialDay(session, zone))
    }

    @Test
    fun `screen reader speech names the trial, the rite and today`() {
        assertEquals("3 October, trial sealed", daySpeech("3 October", 1, rite = true, isToday = false, future = false))
        assertEquals("3 October, 2 trials sealed, today", daySpeech("3 October", 2, rite = false, isToday = true, future = false))
        assertEquals("4 October, rite day", daySpeech("4 October", 0, rite = true, isToday = false, future = true))
        assertEquals("2 October, rite day, no trial sealed", daySpeech("2 October", 0, rite = true, isToday = false, future = false))
        assertEquals("5 October", daySpeech("5 October", 0, rite = false, isToday = false, future = true))
    }
}
