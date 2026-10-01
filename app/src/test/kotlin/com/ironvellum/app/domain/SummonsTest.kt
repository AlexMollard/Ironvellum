package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SummonsTest {

    private val london = ZoneId.of("Europe/London")
    private val at = LocalTime.of(19, 30)

    /** The first DST transition after [from] in [zone], read from the zone rules, not a hard-coded date. */
    private fun nextTransition(zone: ZoneId, from: ZonedDateTime) =
        zone.rules.nextTransition(from.toInstant())

    @Test
    fun `before the hour it fires tonight, after it tomorrow`() {
        val afternoon = ZonedDateTime.of(LocalDateTime.of(2026, 7, 1, 15, 0), london)
        assertEquals(ZonedDateTime.of(LocalDateTime.of(2026, 7, 1, 19, 30), london), Summons.nextFireAt(afternoon, at))
        val evening = ZonedDateTime.of(LocalDateTime.of(2026, 7, 1, 21, 0), london)
        assertEquals(ZonedDateTime.of(LocalDateTime.of(2026, 7, 2, 19, 30), london), Summons.nextFireAt(evening, at))
    }

    @Test
    fun `exactly on the hour books tomorrow, never now`() {
        val onTheHour = ZonedDateTime.of(LocalDateTime.of(2026, 7, 1, 19, 30), london)
        assertEquals(LocalDate.of(2026, 7, 2), Summons.nextFireAt(onTheHour, at).toLocalDate())
    }

    @Test
    fun `a run started seconds early does not book tonight again`() {
        // WorkManager woke the worker 20 s before 19:30. Without the lead the
        // next fire would be 20 s away: a second Summons the same evening.
        val early = ZonedDateTime.of(LocalDateTime.of(2026, 7, 1, 19, 29, 40), london)
        assertEquals(LocalDate.of(2026, 7, 1), Summons.nextFireAt(early, at).toLocalDate())
        val booked = Summons.nextFireAt(early, at, Summons.MIN_LEAD)
        assertEquals(ZonedDateTime.of(LocalDateTime.of(2026, 7, 2, 19, 30), london), booked)
    }

    @Test
    fun `spring forward the delay is 23 hours and the clock still reads 19 30`() {
        val transition = nextTransition(london, ZonedDateTime.of(LocalDateTime.of(2026, 1, 1, 0, 0), london))
        assertTrue("expected the spring transition", transition.isGap)
        // 19:30 on the evening before the clocks go forward.
        val eve = ZonedDateTime.of(transition.dateTimeBefore.toLocalDate().minusDays(1), at, london)
        val next = Summons.nextFireAt(eve, at, Summons.MIN_LEAD)
        assertEquals(at, next.toLocalTime())
        assertEquals(eve.toLocalDate().plusDays(1), next.toLocalDate())
        assertEquals(Duration.ofHours(23), Summons.delayUntilNext(eve, at, Summons.MIN_LEAD))
    }

    @Test
    fun `fall back the delay is 25 hours and the clock still reads 19 30`() {
        val transition = nextTransition(london, ZonedDateTime.of(LocalDateTime.of(2026, 7, 1, 0, 0), london))
        assertTrue("expected the autumn transition", transition.isOverlap)
        val eve = ZonedDateTime.of(transition.dateTimeBefore.toLocalDate().minusDays(1), at, london)
        val next = Summons.nextFireAt(eve, at, Summons.MIN_LEAD)
        assertEquals(at, next.toLocalTime())
        assertEquals(Duration.ofHours(25), Summons.delayUntilNext(eve, at, Summons.MIN_LEAD))
    }

    @Test
    fun `a new timezone keeps the local hour`() {
        val sameInstant = ZonedDateTime.of(LocalDateTime.of(2026, 7, 1, 12, 0), london)
            .withZoneSameInstant(ZoneId.of("America/New_York"))
        val next = Summons.nextFireAt(sameInstant, at)
        assertEquals(at, next.toLocalTime())
        assertEquals(ZoneId.of("America/New_York"), next.zone)
    }

    @Test
    fun `a morning catch up run is out of its evening`() {
        assertFalse(Summons.isSummonsHour(LocalTime.of(8, 0), at))
        assertTrue(Summons.isSummonsHour(LocalTime.of(19, 27), at))
        assertTrue(Summons.isSummonsHour(LocalTime.of(23, 50), at))
    }

    @Test
    fun `the rite keeps its own case after THE SUMMONS`() {
        val copy = Summons.copy("Heavy Pull", exercises = 5, sets = 18, oathDays = 4, nextDeedDays = 7, oathAtRisk = false)
        assertEquals("THE SUMMONS · Heavy Pull", copy.title)
        assertEquals("5 exercises · 18 sets waiting.", copy.text)
        assertTrue(copy.bigText.endsWith("Oath · 4 days kept. Next deed at 7 days."))
    }

    @Test
    fun `an oath at risk is said in the Summons itself`() {
        val copy = Summons.copy("Heavy Pull", exercises = 1, sets = 1, oathDays = 12, nextDeedDays = 14, oathAtRisk = true)
        assertEquals("1 exercise · 1 set · your oath ends tonight.", copy.text)
        assertTrue(copy.bigText.endsWith("Your oath of 12 days ends tonight unless you seal a trial."))
    }

    @Test
    fun `the Summons is wanted only on an unsealed rite day with nothing live and a way to post`() {
        assertTrue(Summons.wanted(riteScheduledToday = true, sealedToday = false, liveToday = false, canPost = true))
        // A respite or an unscheduled cycle.
        assertFalse(Summons.wanted(riteScheduledToday = false, sealedToday = false, liveToday = false, canPost = true))
        // Already sealed today: silence.
        assertFalse(Summons.wanted(riteScheduledToday = true, sealedToday = true, liveToday = false, canPost = true))
        // Mid-trial.
        assertFalse(Summons.wanted(riteScheduledToday = true, sealedToday = false, liveToday = true, canPost = true))
        // Blocked notifications.
        assertFalse(Summons.wanted(riteScheduledToday = true, sealedToday = false, liveToday = false, canPost = false))
    }
}
