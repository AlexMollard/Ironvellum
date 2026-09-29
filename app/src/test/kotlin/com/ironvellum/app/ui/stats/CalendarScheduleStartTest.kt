package com.ironvellum.app.ui.stats

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarScheduleStartTest {

    private val today = LocalDate.of(2026, 9, 29)

    @Test
    fun `before any workout scheduled dots start today, not in the past`() {
        assertEquals(today, calendarScheduleStart(emptySet(), today))
    }

    @Test
    fun `scheduled dots start at the first workout`() {
        val first = LocalDate.of(2026, 8, 3)
        val done = setOf(LocalDate.of(2026, 9, 1), first, LocalDate.of(2026, 8, 20))
        assertEquals(first, calendarScheduleStart(done, today))
    }

    @Test
    fun `a future-dated workout never pushes the start past today`() {
        // Clock skew or a restored archive can carry a date after today; the
        // start must not hide today's own scheduled dot.
        assertEquals(today, calendarScheduleStart(setOf(today.plusDays(3)), today))
    }
}
