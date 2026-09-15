package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These bounds exist because every entry point used to accept anything
 * positive, and the consequence was not a crash but a lie: a body fat of 500
 * reaches Katch-McArdle as **negative lean mass** and the app reports a
 * negative resting burn as a fact about the hunter's body.
 */
class BodyLimitsTest {

    @Test
    fun `a typo that used to produce a negative resting burn is refused`() {
        // The original defect: 500 read as a percentage.
        assertFalse(BodyLimits.validBodyFat(500.0))
        // And the arithmetic it would have reached, for the record.
        assertEquals(-320.0, Energy.leanMassKg(80.0, 500.0)!!, 1e-9)
        assertTrue(
            "an out-of-range body fat still yields a negative resting burn, so the gate is the defence",
            Energy.restingKcalPerDay(80.0, 500.0)!!.kcal < 0,
        )
    }

    @Test
    fun `real bodies are accepted at both extremes`() {
        assertTrue(BodyLimits.validWeight(45.0))
        assertTrue(BodyLimits.validWeight(180.0))
        assertTrue(BodyLimits.validHeight(150.0))
        assertTrue(BodyLimits.validHeight(210.0))
        assertTrue(BodyLimits.validBodyFat(4.0))
        assertTrue(BodyLimits.validBodyFat(45.0))
    }

    @Test
    fun `zero and negative figures are refused where positivity was the only check`() {
        assertFalse(BodyLimits.validWeight(0.0))
        assertFalse(BodyLimits.validWeight(-80.0))
        assertFalse(BodyLimits.validHeight(0.0))
        assertFalse(BodyLimits.validBodyFat(-5.0))
    }

    @Test
    fun `absent figures stay valid where the field is optional`() {
        // Body fat is optional — refusing null would block every weigh-in that
        // does not have a caliper reading.
        assertTrue(BodyLimits.validBodyFat(null))
        // Weight and height are not optional.
        assertFalse(BodyLimits.validWeight(null))
        assertFalse(BodyLimits.validHeight(null))
    }

    @Test
    fun `the bounds are wide enough for records and narrow enough to catch a slipped decimal`() {
        // 272 cm is the tallest recorded human; a slipped decimal gives 1800.
        assertTrue(BodyLimits.validHeight(272.0))
        assertFalse(BodyLimits.validHeight(1800.0))
        // A weight typed in grams, or in pounds mistaken for kilos twice over.
        assertFalse(BodyLimits.validWeight(80000.0))
        // Body fat as a fraction rather than a percentage is the other slip.
        assertFalse(BodyLimits.validBodyFat(0.18))
    }
}
