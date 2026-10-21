package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecapScheduleTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0, zone: ZoneId = berlin) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    // Week 42 of 2026: Mon 12 - Sun 18 October.
    private val week42 = LocalDate.of(2026, 10, 12)

    @Test
    fun `the next delivery is the coming Sunday at 19 00 local time`() {
        assertEquals(at(2026, 10, 18, 19), RecapSchedule.nextDelivery(at(2026, 10, 14, 9, 30)))
        assertEquals(at(2026, 10, 18, 19), RecapSchedule.nextDelivery(at(2026, 10, 18, 18, 59)))
    }

    @Test
    fun `at 19 00 sharp and after it the next delivery is a week on`() {
        assertEquals(at(2026, 10, 25, 19), RecapSchedule.nextDelivery(at(2026, 10, 18, 19, 0)))
        assertEquals(at(2026, 10, 25, 19), RecapSchedule.nextDelivery(at(2026, 10, 18, 23, 59)))
        assertEquals(at(2026, 10, 25, 19), RecapSchedule.nextDelivery(at(2026, 10, 19, 7)))
    }

    @Test
    fun `the delay follows the wall clock across a clock change`() {
        // Sunday 25 Oct 2026 clocks go back an hour at 03:00: Saturday noon to Sunday 19:00 is 31 hours on the wall, 32 elapsed.
        assertEquals(Duration.ofHours(32), RecapSchedule.delayUntilNext(at(2026, 10, 24, 12)))
        // Sunday 29 Mar 2026 clocks go forward: the same wall span is 30 hours elapsed.
        assertEquals(Duration.ofHours(30), RecapSchedule.delayUntilNext(at(2026, 3, 28, 12)))
    }

    @Test
    fun `the delivery time is local to the zone the phone is in`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals(at(2026, 10, 18, 19, zone = tokyo), RecapSchedule.nextDelivery(at(2026, 10, 18, 10, zone = tokyo)))
    }

    @Test
    fun `the due week is the one the latest Sunday 19 00 ended`() {
        assertEquals(week42, RecapSchedule.dueWeek(at(2026, 10, 18, 19, 0)))
        assertEquals(week42, RecapSchedule.dueWeek(at(2026, 10, 18, 21)))
    }

    @Test
    fun `a run held into Monday morning is still the Sunday recap, never the new empty week`() {
        assertEquals(week42, RecapSchedule.dueWeek(at(2026, 10, 19, 7)))
        assertEquals(week42, RecapSchedule.dueWeek(at(2026, 10, 19, 18, 59)))
    }

    @Test
    fun `nothing is due before 19 00 on Sunday, nor after the grace window`() {
        assertNull(RecapSchedule.dueWeek(at(2026, 10, 18, 18, 59).minusHours(24).minusMinutes(1)))
        assertNull(RecapSchedule.dueWeek(at(2026, 10, 19, 19, 1)))
        assertNull(RecapSchedule.dueWeek(at(2026, 10, 22, 12)))
        // Just before Sunday 19:00 the previous week's moment is six days old.
        assertNull(RecapSchedule.dueWeek(at(2026, 10, 18, 18, 59)))
    }

    @Test
    fun `a week with workouts is delivered`() {
        val verdict = RecapSchedule.decide(at(2026, 10, 18, 19, 2), lastDelivered = null) { 4 }
        assertEquals(RecapSchedule.Verdict.Deliver(week42), verdict)
    }

    @Test
    fun `a week with no workouts sends nothing`() {
        val verdict = RecapSchedule.decide(at(2026, 10, 18, 19, 2), lastDelivered = null) { 0 }
        assertEquals(RecapSchedule.Verdict.EmptyWeek(week42), verdict)
    }

    @Test
    fun `the same week is never delivered twice`() {
        val verdict = RecapSchedule.decide(at(2026, 10, 18, 19, 2), lastDelivered = week42) { 4 }
        assertEquals(RecapSchedule.Verdict.AlreadyDelivered(week42), verdict)
        // A worker run twice (a retry, the Monday catch-up after a delivered Sunday) stays quiet.
        assertEquals(RecapSchedule.Verdict.AlreadyDelivered(week42), RecapSchedule.decide(at(2026, 10, 19, 6), week42) { 4 })
    }

    @Test
    fun `the week after a delivered one is delivered`() {
        val verdict = RecapSchedule.decide(at(2026, 10, 25, 19, 0), lastDelivered = week42) { 3 }
        assertEquals(RecapSchedule.Verdict.Deliver(week42.plusDays(7)), verdict)
    }

    @Test
    fun `a run outside the window is not due, whatever was logged`() {
        assertEquals(RecapSchedule.Verdict.NotDue, RecapSchedule.decide(at(2026, 10, 21, 12), null) { 4 })
    }

    @Test
    fun `the week counted is the due week, asked once`() {
        val asked = mutableListOf<LocalDate>()
        RecapSchedule.decide(at(2026, 10, 18, 20), null) { asked += it; 1 }
        assertEquals(listOf(week42), asked)
    }

    @Test
    fun `Today offers last week from Monday through Wednesday when it had workouts`() {
        assertTrue(RecapSchedule.showsOnToday(LocalDate.of(2026, 10, 19), 4)) // Monday
        assertTrue(RecapSchedule.showsOnToday(LocalDate.of(2026, 10, 21), 4)) // Wednesday
        assertFalse(RecapSchedule.showsOnToday(LocalDate.of(2026, 10, 22), 4)) // Thursday
        assertFalse(RecapSchedule.showsOnToday(LocalDate.of(2026, 10, 18), 4)) // Sunday
        assertFalse(RecapSchedule.showsOnToday(LocalDate.of(2026, 10, 20), 0)) // Tuesday, an empty week
    }
}
