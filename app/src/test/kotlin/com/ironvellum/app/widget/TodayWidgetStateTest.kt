package com.ironvellum.app.widget

import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.Summons
import com.ironvellum.app.ui.dashboard.DayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TodayWidgetStateTest {
    private val zone = ZoneId.of("UTC")

    /** A Wednesday; the week opened on Monday the 12th. */
    private val wednesday = LocalDate.of(2026, 10, 14)

    private fun ms(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun entry(exerciseId: Long, sets: Int, warmup: Boolean = false) =
        PresetEntry(exerciseId = exerciseId, targetSets = sets, targetReps = 5, warmup = warmup)

    private val upperA = WorkoutPreset(
        id = 1, name = "Upper A", scheduledDay = 1,
        entries = listOf(entry(10, 3), entry(11, 4), entry(12, 2, warmup = true)),
    )
    private val lowerB = WorkoutPreset(
        id = 2, name = "Lower B", scheduledDay = 3,
        entries = listOf(entry(20, 5), entry(21, 3)),
    )

    private fun done(preset: WorkoutPreset, date: LocalDate) =
        WorkoutSession(id = 100L + date.toEpochDay(), presetId = preset.id, label = preset.name, startedAtMs = ms(date, 11), completedAtMs = ms(date))

    private fun state(
        presets: List<WorkoutPreset>,
        completed: List<WorkoutSession> = emptyList(),
        live: WorkoutSession? = null,
    ) = widgetState(presets, completed, live, wednesday, zone)

    @Test
    fun `a rite scheduled today and not yet sealed offers to begin`() {
        val s = state(listOf(upperA, lowerB))
        assertEquals(DayKind.BEGIN, s.kind)
        assertEquals("Lower B", s.title)
        assertEquals(2L, s.presetId)
        assertNull(s.sessionId)
        // Working exercises and sets only: a warm-up is not counted.
        assertEquals(2, state(listOf(lowerB)).exercises)
        assertEquals(8, s.sets)
        assertEquals(2, state(listOf(upperA, lowerB)).exercises)
        assertTrue(s.canStart)
    }

    @Test
    fun `a rite sealed this week reads as done and offers nothing to start`() {
        val s = state(listOf(upperA, lowerB), completed = listOf(done(lowerB, wednesday)))
        assertEquals(DayKind.SEALED, s.kind)
        assertEquals("Lower B", s.title)
        assertNull(s.presetId)
        assertFalse(s.canStart)
    }

    @Test
    fun `a rite taken early this week is not offered again on its day`() {
        // Today's Lower B was done on Tuesday.
        val s = state(listOf(upperA, lowerB), completed = listOf(done(lowerB, wednesday.minusDays(1))))
        assertEquals(DayKind.SEALED, s.kind)
    }

    @Test
    fun `a day the plan leaves free is a rest day`() {
        val s = state(listOf(upperA))
        assertEquals(DayKind.RESPITE, s.kind)
        assertNull(s.title)
        assertFalse(s.canStart)
    }

    @Test
    fun `no plan at all is its own face`() {
        val s = state(emptyList())
        assertEquals(DayKind.NO_CYCLE, s.kind)
        assertFalse(s.canStart)
        assertEquals(0, s.streak)
    }

    @Test
    fun `a trial under way wins over today's rite and is continued, not begun again`() {
        val live = WorkoutSession(id = 7, presetId = 1, label = "Upper A", startedAtMs = ms(wednesday, 9))
        val s = state(listOf(upperA, lowerB), live = live)
        assertEquals(DayKind.LIVE, s.kind)
        assertEquals("Upper A", s.title)
        assertEquals(7L, s.sessionId)
        assertNull(s.presetId)
        assertEquals(2, s.exercises)
        assertTrue(s.canStart)
        // A live trial with no plan at all is still continued.
        assertEquals(DayKind.LIVE, state(emptyList(), live = live).kind)
    }

    @Test
    fun `a live trial's own title is what the widget names`() {
        val live = WorkoutSession(id = 7, label = "Empty workout", title = "Leg day", startedAtMs = ms(wednesday, 9))
        assertEquals("Leg day", state(listOf(lowerB), live = live).title)
    }

    @Test
    fun `the streak is the app's own chain, counted to today`() {
        // Trained Monday and Tuesday: the chain spans Monday to Wednesday, three days.
        val s = state(listOf(upperA, lowerB), completed = listOf(done(upperA, wednesday.minusDays(2)), done(lowerB, wednesday.minusDays(1))))
        assertEquals(3, s.streak)
        // A week untrained breaks it.
        assertEquals(0, state(listOf(upperA), completed = listOf(done(upperA, wednesday.minusDays(9)))).streak)
        assertEquals(0, state(listOf(upperA)).streak)
    }

    @Test
    fun `the midnight run lands a minute after the local day turns, across a clock change`() {
        val at = LocalTime.of(0, 1)
        val evening = ZonedDateTime.of(LocalDateTime.of(2026, 10, 14, 22, 0), zone)
        assertEquals(Duration.ofHours(2).plusMinutes(1), Summons.delayUntilNext(evening, at = at))
        // Just after midnight but before the run's minute: the same night's run, not tomorrow's.
        val justAfter = ZonedDateTime.of(LocalDateTime.of(2026, 10, 15, 0, 0, 30), zone)
        assertEquals(Duration.ofSeconds(30), Summons.delayUntilNext(justAfter, at = at))
        // The night the clocks go back in New York is 25 hours long: the delay is real elapsed time.
        val night = ZonedDateTime.of(LocalDateTime.of(2026, 11, 1, 0, 30), ZoneId.of("America/New_York"))
        assertEquals(Duration.ofHours(24).plusMinutes(31), Summons.delayUntilNext(night, at = at))
    }
}
