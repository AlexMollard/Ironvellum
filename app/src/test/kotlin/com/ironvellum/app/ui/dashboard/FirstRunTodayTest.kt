package com.ironvellum.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunTodayTest {

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
