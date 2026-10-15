package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.domain.WorkoutPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunTodayTest {

    @Test
    fun `a new cycle opens with its earliest rite whatever weekday it is taken on`() {
        // Taken on a Thursday: Monday's A and Wednesday's B are past, the calendar's next rite is Friday's C.
        val cycle = listOf(
            WorkoutPreset(id = 3, name = "Full Body C", scheduledDay = 5),
            WorkoutPreset(id = 1, name = "Full Body A", scheduledDay = 1),
            WorkoutPreset(id = 2, name = "Full Body B", scheduledDay = 3),
            WorkoutPreset(id = 4, name = "Unscheduled"),
        )
        assertEquals("Full Body A", firstCycleRite(cycle)?.name)
        assertNull(firstCycleRite(listOf(WorkoutPreset(id = 4, name = "Unscheduled"))))
        assertNull(firstCycleRite(emptyList()))
    }

    @Test
    fun `a first run is a profile with nothing sealed`() {
        assertTrue(isFirstRun(0L))
        assertFalse(isFirstRun(1L))
        // Still loading: not a first run, so nothing first-run flashes in.
        assertFalse(isFirstRun(null))
    }

    @Test
    fun `a free first day offers the next rite and an open trial`() {
        assertEquals(
            listOf(FirstBegin.RITE, FirstBegin.OPEN),
            firstBegins(DayKind.RESPITE, isToday = true, firstRun = true, hasNextRite = true),
        )
    }

    @Test
    fun `a free first day with no rite on any day still offers an open trial`() {
        assertEquals(
            listOf(FirstBegin.OPEN),
            firstBegins(DayKind.RESPITE, isToday = true, firstRun = true, hasNextRite = false),
        )
    }

    @Test
    fun `nothing is added once a trial has been sealed, on another day, or when the day has its own begin`() {
        assertTrue(firstBegins(DayKind.RESPITE, isToday = true, firstRun = false, hasNextRite = true).isEmpty())
        assertTrue(firstBegins(DayKind.RESPITE, isToday = false, firstRun = true, hasNextRite = true).isEmpty())
        assertTrue(firstBegins(DayKind.BEGIN, isToday = true, firstRun = true, hasNextRite = true).isEmpty())
        assertTrue(firstBegins(DayKind.NO_CYCLE, isToday = true, firstRun = true, hasNextRite = false).isEmpty())
    }
}
