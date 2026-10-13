package com.ironvellum.app.ui.program

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saving one rite asks for a day; the first free one is ticked, and Weekly coverage says what it leaves out. */
class RiteDayTest {

    @Test
    fun `the first free day is ticked when the proposal's own day is taken`() {
        assertEquals(2, defaultRiteDay(preferred = 1, taken = setOf(1)))
        assertEquals(4, defaultRiteDay(preferred = 1, taken = setOf(1, 2, 3, 5)))
        assertEquals(7, defaultRiteDay(preferred = null, taken = setOf(1, 2, 3, 4, 5, 6)))
    }

    @Test
    fun `a free preferred day is kept`() {
        assertEquals(3, defaultRiteDay(preferred = 3, taken = setOf(1, 2)))
    }

    @Test
    fun `a full week ticks no day`() {
        assertNull(defaultRiteDay(preferred = 1, taken = (1..7).toSet()))
    }

    @Test
    fun `rites without a day are uncounted only while another rite has one`() {
        val days = listOf<Int?>(1, null, 3, null)
        assertEquals(listOf(null, null), uncountedRoutine(days) { it })
        // Nothing scheduled: the whole board is counted, so nothing is left out.
        assertTrue(uncountedRoutine(listOf<Int?>(null, null)) { it }.isEmpty())
        assertTrue(uncountedRoutine(emptyList<Int?>()) { it }.isEmpty())
        // The two views are exact complements whenever any rite has a day.
        assertEquals(days.size, scheduledRoutine(days) { it }.size + uncountedRoutine(days) { it }.size)
    }

    @Test
    fun `the quiet line counts the rites it leaves out`() {
        assertEquals("1 rite has no day, so it isn't counted", uncountedRitesLine(1))
        assertEquals("3 rites have no day, so they aren't counted", uncountedRitesLine(3))
    }

    @Test
    fun `the sheet names what the ticked day costs`() {
        assertEquals("A rite with no day is not counted in Weekly coverage.", daySheetNote(null, emptyList()))
        assertEquals("Wednesday is free.", daySheetNote(3, emptyList()))
    }
}
