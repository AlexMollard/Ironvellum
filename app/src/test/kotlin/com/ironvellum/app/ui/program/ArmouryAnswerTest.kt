package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Gear
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The armoury has no default: unanswered lights no cell, "Nothing" is only an explicit answer. */
class ArmouryAnswerTest {

    @Test
    fun `unanswered does not light the Nothing cell`() {
        assertFalse(nothingSelected(null))
    }

    @Test
    fun `an explicit empty armoury lights the Nothing cell`() {
        assertTrue(nothingSelected(Equipment.NOTHING))
    }

    @Test
    fun `full gym and partial kit do not light the Nothing cell`() {
        assertFalse(nothingSelected(Equipment.FULL_GYM))
        assertFalse(nothingSelected(Equipment(fullGym = false, gear = setOf(Gear.entries.first()))))
    }
}
