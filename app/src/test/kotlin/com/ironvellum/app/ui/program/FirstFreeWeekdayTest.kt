package com.ironvellum.app.ui.program

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Generate one workout" defaults to the first weekday the routine leaves free, not always Monday. */
class FirstFreeWeekdayTest {

    @Test
    fun `the first weekday no workout holds is the default`() {
        assertEquals(1, firstFreeWeekday(emptySet()))
        assertEquals(2, firstFreeWeekday(setOf(1, 3, 5)))
        assertEquals(7, firstFreeWeekday(setOf(1, 2, 3, 4, 5, 6)))
    }

    @Test
    fun `a full week leaves the workout unscheduled`() {
        assertNull(firstFreeWeekday((1..7).toSet()))
    }
}
