package com.ironvellum.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Search matches at word starts, in any word order. */
class ExercisePickerSearchTest {

    @Test
    fun `a term matches the start of a word, not the inside of one`() {
        assertTrue(matchesSearch("Chin-up", "chin"))
        assertTrue(matchesSearch("Weighted Chin-up", "CHIN"))
        assertFalse(matchesSearch("Machine Row", "chin"))
        assertFalse(matchesSearch("Machine Chest Press", "chin"))
        // "up" starts the word after the hyphen.
        assertTrue(matchesSearch("Pull-up", "up"))
    }

    @Test
    fun `every term must match, in any order`() {
        assertTrue(matchesSearch("Leg Extension", "ext leg"))
        assertTrue(matchesSearch("Lat Pulldown", "pulldown lat"))
        assertFalse(matchesSearch("Leg Extension", "leg curl"))
        assertTrue(matchesSearch("Leg Extension", "   "))
    }
}
