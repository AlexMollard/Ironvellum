package com.ironvellum.app.ui.train

import org.junit.Assert.assertEquals
import org.junit.Test

class EffortAskTest {

    @Test
    fun `the rest bar asks a rep set while the rest runs`() {
        assertEquals(EffortAsk.REST_BAR, effortAsk(restRunning = true, allLogged = false, reps = true, settingOn = false))
    }

    @Test
    fun `the last rep set is asked above slide to seal`() {
        assertEquals(EffortAsk.SEAL_BAR, effortAsk(restRunning = true, allLogged = true, reps = true, settingOn = false))
        assertEquals(EffortAsk.SEAL_BAR, effortAsk(restRunning = false, allLogged = true, reps = true, settingOn = true))
    }

    @Test
    fun `a finished rest still asks a rep set under its row`() {
        assertEquals(EffortAsk.UNDER_ROW, effortAsk(restRunning = false, allLogged = false, reps = true, settingOn = false))
    }

    @Test
    fun `a last hold stays under its row`() {
        assertEquals(EffortAsk.UNDER_ROW, effortAsk(restRunning = false, allLogged = true, reps = false, settingOn = true))
    }

    @Test
    fun `a hold is asked only when settings asks`() {
        assertEquals(EffortAsk.NONE, effortAsk(restRunning = true, allLogged = false, reps = false, settingOn = false))
        assertEquals(EffortAsk.UNDER_ROW, effortAsk(restRunning = true, allLogged = false, reps = false, settingOn = true))
    }
    // ---- which set the question is about

    @Test
    fun `a set logged another way takes the question over from the older one`() {
        // Set 1 was asked; the lock screen then logged set 2: the question moves on, it does not linger on set 1.
        assertEquals(2L, effortTarget(seen = setOf(1L), now = setOf(1L, 2L), current = 1L, order = listOf(1L, 2L, 3L)))
    }

    @Test
    fun `a set taken back loses its question and a trial coming back keeps it`() {
        assertEquals(null, effortTarget(seen = setOf(1L), now = emptySet(), current = 1L, order = listOf(1L)))
        assertEquals(1L, effortTarget(seen = null, now = setOf(1L, 2L), current = 1L, order = listOf(1L, 2L)))
        assertEquals(1L, effortTarget(seen = setOf(1L), now = setOf(1L), current = 1L, order = listOf(1L)))
    }
}
