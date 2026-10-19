package com.ironvellum.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadNoteTest {

    @Test
    fun `the 1-rep max note is told apart from the other load notes without reading its words`() {
        val note = ProgramRules.workingLoadKg(
            "bench press", "PUSH", StrengthProfile(mapOf("bench press" to 100.0)), reps = 8, rir = 2,
        )!!.second
        assertTrue(note, ProgramRules.isMaxNote(note))
        assertFalse(ProgramRules.isMaxNote("estimate from Bench Press"))
        assertFalse(ProgramRules.isMaxNote("capped at your 24 kg dumbbells"))
    }
}
