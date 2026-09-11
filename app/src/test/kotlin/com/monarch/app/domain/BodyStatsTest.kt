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
}
