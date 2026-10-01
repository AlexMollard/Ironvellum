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
    fun `only sealed trials of the day's own rite light a tick, however many trials followed`() {
        val monday = thursday.minusDays(3)
        val sessions = listOf(
            trial(1, monday),
            trial(2, monday.plusDays(1)),
            trial(3, thursday, sealed = false),
        ) + (1..8).map { trial(99, thursday) }
        assertEquals(setOf(1, 2), weekDoneDays(sessions, presets, thursday, zone))
    }

    @Test
    fun `a sealed trial of another rite or another week lights nothing`() {
        val sessions = listOf(trial(2, thursday.minusDays(3)), trial(1, thursday.minusDays(10)))
        assertEquals(emptySet<Int>(), weekDoneDays(sessions, presets, thursday, zone))
    }
}