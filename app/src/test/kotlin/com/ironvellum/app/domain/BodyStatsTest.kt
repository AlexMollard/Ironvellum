package com.ironvellum.app.domain

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
        // + 0.15456*log10(178)) - 450 = 16.43 -> whole percent, 16
        assertEquals(
            16.0,
            BodyStats.estimateBodyFatNavy(Sex.MALE, heightCm = 178.0, neckCm = 38.0, waistCm = 85.0, hipCm = null)!!,
            0.05,
        )
    }

    @Test
    fun `navy estimate of female reference body uses hips`() {
        // 495 / (1.29579 - 0.35004*log10(72+96-32) + 0.221*log10(165)) - 450 = 26.4 -> 26
        assertEquals(
            26.0,
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

    // ---- the one band table

    @Test
    fun `bmi bands switch exactly on their boundaries`() {
        assertEquals("Underweight", BodyStats.bmiCategory(18.4))
        assertEquals("Healthy range", BodyStats.bmiCategory(18.5))
        assertEquals("Healthy range", BodyStats.bmiCategory(24.9))
        assertEquals("Overweight", BodyStats.bmiCategory(25.0))
        assertEquals("Overweight", BodyStats.bmiCategory(29.9))
        assertEquals("Obese", BodyStats.bmiCategory(30.0))
        assertEquals("Obese", BodyStats.bmiCategory(55.0))
    }

    @Test
    fun `range text prints one decimal and reads in order`() {
        val texts = Bands.BMI.bands.indices.map { Bands.rangeText(Bands.BMI, it) }
        assertEquals(
            listOf("below 18.5", "18.5 to under 25.0", "25.0 to under 30.0", "30.0 and over"),
            texts,
        )
    }

    @Test
    fun `the category text and the band the dialog marks always agree`() {
        for (sex in Sex.entries) {
            val table = Bands.ffmi(sex)
            var v = 8.0
            while (v < 30.0) {
                assertEquals(BodyStats.ffmiCategory(v, sex), Bands.bandOf(table, v).label)
                v += 0.1
            }
        }
        var b = 10.0
        while (b < 45.0) {
            assertEquals(BodyStats.bmiCategory(b), Bands.bandOf(Bands.BMI, b).label)
            b += 0.1
        }
    }

    @Test
    fun `female ffmi bands sit lower than male and say so`() {
        val male = Bands.ffmi(Sex.MALE).bands.map { it.upTo }.dropLast(1)
        val female = Bands.ffmi(Sex.FEMALE).bands.map { it.upTo }.dropLast(1)
        male.zip(female).forEach { (m, f) -> assertEquals(m - 3.5, f, 1e-9) }
        assertEquals("Average (active female)", BodyStats.ffmiCategory(15.0, Sex.FEMALE))
        assertEquals("Below average", BodyStats.ffmiCategory(15.0, Sex.MALE))
        assertEquals(true, Bands.ffmi(Sex.FEMALE).note!!.contains("not a published"))
    }
}
