package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekDoneDaysTest {

    private val zone = ZoneId.of("UTC")
    private val thursday = LocalDate.of(2026, 10, 1)
    private val presets = listOf(
        WorkoutPreset(id = 1, name = "Push", scheduledDay = 1),
        WorkoutPreset(id = 2, name = "Pull", scheduledDay = 2),
        WorkoutPreset(id = 3, name = "Legs", scheduledDay = 4),
    )

    private fun at(date: LocalDate, hour: Int = 10) =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun trial(preset: Long, date: LocalDate, sealed: Boolean = true) = WorkoutSession(
        presetId = preset,
        label = "t",
        startedAtMs = at(date),
        completedAtMs = if (sealed) at(date, 11) else null,
    )

    @Test
    fun `a rite done on another day is still found as sealed on the day it was done`() {
        val monday = thursday.minusDays(3)
        val sessions = listOf(trial(1, thursday), trial(2, monday), trial(3, thursday, sealed = false))
        // Monday's rite ticks Monday's own day: a rite marks its scheduled day...
        assertEquals(setOf(1, 2), weekDoneDays(sessions.take(2), presets, thursday, zone).keys)
        // ...and Thursday's card can still say a trial was sealed that day.
        assertEquals(sessions[0], sealedOnDay(sessions, thursday, zone))
        assertEquals(null, sealedOnDay(sessions, thursday.plusDays(1), zone))
        assertEquals(sessions[1], sealedOnDay(sessions, monday, zone))
    }

    @Test
    fun `only sealed trials of the day's own rite light a tick, however many trials followed`() {
        val monday = thursday.minusDays(3)
        val sessions = listOf(
            trial(1, monday),
            trial(2, monday.plusDays(1)),
            trial(3, thursday, sealed = false),
        ) + (1..8).map { trial(99, thursday) }
        assertEquals(setOf(1, 2), weekDoneDays(sessions, presets, thursday, zone).keys)
    }

    @Test
    fun `a sealed trial of another rite or another week lights nothing`() {
        val sessions = listOf(trial(99, thursday.minusDays(3)), trial(1, thursday.minusDays(10)))
        assertEquals(emptySet<Int>(), weekDoneDays(sessions, presets, thursday, zone).keys)
    }

    @Test
    fun `a rite made up late or taken early in its week lights its own day`() {
        // Push, Legs and Pull scheduled Mon, Wed, Fri; Friday's Push made up on Sunday.
        val cycle = listOf(
            WorkoutPreset(id = 1, name = "Pull", scheduledDay = 1),
            WorkoutPreset(id = 2, name = "Legs", scheduledDay = 3),
            WorkoutPreset(id = 4, name = "Push", scheduledDay = 5),
        )
        val sunday = LocalDate.of(2026, 10, 4)
        val makeUp = trial(4, sunday)
        val sessions = listOf(trial(1, sunday.minusDays(6)), trial(2, sunday.minusDays(4)), makeUp)
        val done = weekDoneDays(sessions, cycle, sunday, zone)
        assertEquals(setOf(1, 3, 5), done.keys)
        assertEquals(makeUp, done[5])
        // Taken early on Tuesday, Wednesday's rite is already done.
        assertEquals(setOf(3), weekDoneDays(listOf(trial(2, sunday.minusDays(5))), cycle, sunday, zone).keys)
    }
}