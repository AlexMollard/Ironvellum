package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyStatsTest {

    @Test
    fun `bmi of classic reference body`() {
        assertEquals(22.9, BodyStats.bmi(weightKg = 70.0, heightCm = 175.0)!!, 0.0001)
    }

    @Test
    fun `bmi rejects nonpositive inputs`() {
        assertNull(BodyStats.bmi(0.0, 175.0))
        assertNull(BodyStats.bmi(70.0, 0.0))
    }

    @Test
    fun `bmi categories follow WHO bands`() {
        assertEquals("Underweight", BodyStats.bmiCategory(17.0))
        assertEquals("Healthy range", BodyStats.bmiCategory(22.9))
        assertEquals("Overweight", BodyStats.bmiCategory(27.5))
        assertEquals("Obese", BodyStats.bmiCategory(31.0))
    }

    @Test
    fun `ffmi of 70kg 175cm at 15 percent body fat`() {
        // lean = 70 * 0.85 = 59.5; height^2 = 1.75^2 = 3.0625; 59.5 / 3.0625 = 19.4285... -> 19.4
        assertEquals(19.4, BodyStats.ffmi(70.0, 175.0, 15.0)!!, 0.0001)
    }

    @Test
    fun `ffmi rejects implausible body fat`() {
        assertNull(BodyStats.ffmi(70.0, 175.0, 2.0))
        assertNull(BodyStats.ffmi(70.0, 175.0, 61.0))
    }

    @Test
    fun `ffmi rejects nonpositive inputs`() {
        assertNull(BodyStats.ffmi(-1.0, 175.0, 15.0))
    }

    @Test
    fun `ffmi categories follow Kouri 1995 norms`() {
        assertEquals("Below average", BodyStats.ffmiCategory(17.9))
        assertEquals("Average (active male)", BodyStats.ffmiCategory(19.0))
        assertEquals("Above average (1-3 yrs training)", BodyStats.ffmiCategory(20.9))
        assertEquals("Excellent (3-5 yrs training)", BodyStats.ffmiCategory(22.5))
        assertEquals("Approaching natural ceiling", BodyStats.ffmiCategory(24.0))
    }

    @Test
    fun `navy estimate of male reference body`() {
        // Hodgdon & Beckett male form: 495 / (1.0324 - 0.19077*log10(85-38)
        // + 0.15456*log10(178)) - 450 = 16.43 -> 16.4
        assertEquals(
            16.4,
            BodyStats.estimateBodyFatNavy(Sex.MALE, heightCm = 178.0, neckCm = 38.0, waistCm = 85.0, hipCm = null)!!,
            0.05,
        )
    }

    @Test
    fun `navy estimate of female reference body uses hips`() {
        // 495 / (1.29579 - 0.35004*log10(72+96-32) + 0.221*log10(165)) - 450 = 26.4
        assertEquals(
            26.4,
            BodyStats.estimateBodyFatNavy(Sex.FEMALE, heightCm = 165.0, neckCm = 32.0, waistCm = 72.0, hipCm = 96.0)!!,
            0.05,
        )
    }

    @Test
    fun `navy estimate is null on missing or nonpositive inputs`() {
        assertNull(BodyStats.estimateBodyFatNavy(Sex.MALE, 0.0, 38.0, 85.0, null))
        assertNull(BodyStats.estimateBodyFatNavy(Sex.MALE, 178.0, 0.0, 85.0, null))
        assertNull(BodyStats.estimateBodyFatNavy(Sex.MALE, 178.0, 38.0, -1.0, null))
        assertNull(BodyStats.estimateBodyFatNavy(Sex.FEMALE, 165.0, 32.0, 72.0, null)) // hip required
        assertNull(BodyStats.estimateBodyFatNavy(Sex.FEMALE, 165.0, 32.0, 72.0, 0.0))
    }

    @Test
    fun `navy estimate rejects geometrically impossible tape sets`() {
        // Male waist must exceed neck, or the log argument goes non-positive.
        assertNull(BodyStats.estimateBodyFatNavy(Sex.MALE, 178.0, 40.0, 38.0, null))
    }
}
