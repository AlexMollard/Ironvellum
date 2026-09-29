package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Muscle
import org.junit.Assert.assertEquals
import org.junit.Test

/** What TalkBack reads for one exercise's figure. */
class ExerciseMuscleSummaryTest {

    @Test
    fun `main work and assisting work are named apart`() {
        val shares = linkedMapOf(Muscle.LATS to 1.0, Muscle.BICEPS to 0.7, Muscle.REAR_DELTS to 0.5, Muscle.ABS to 0.0)
        assertEquals("Main: Lats, Biceps. Assists: Rear delts.", exerciseMuscleSummary(shares))
    }

    @Test
    fun `an empty half is left out`() {
        assertEquals("Assists: Forearms.", exerciseMuscleSummary(mapOf(Muscle.FOREARMS to 0.3)))
        assertEquals("No muscle data for this exercise.", exerciseMuscleSummary(emptyMap()))
    }
}
