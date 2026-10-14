package com.ironvellum.app.ui.train

import org.junit.Assert.assertEquals
import org.junit.Test

class EffortAskTest {

    @Test
    fun `the rest bar asks a rep set while the rest runs`() {
        assertEquals(EffortAsk.REST_BAR, effortAsk(restRunning = true, allLogged = false, reps = true, settingOn = false))
    }

    @Test
    fun `the last set and a finished rest still ask a rep set under its row`() {
        assertEquals(EffortAsk.UNDER_ROW, effortAsk(restRunning = true, allLogged = true, reps = true, settingOn = false))
        assertEquals(EffortAsk.UNDER_ROW, effortAsk(restRunning = false, allLogged = false, reps = true, settingOn = false))
    }

    @Test
    fun `a hold is asked only when settings asks`() {
        assertEquals(EffortAsk.NONE, effortAsk(restRunning = true, allLogged = false, reps = false, settingOn = false))
        assertEquals(EffortAsk.UNDER_ROW, effortAsk(restRunning = true, allLogged = false, reps = false, settingOn = true))
    }
}
