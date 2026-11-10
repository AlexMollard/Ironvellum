package com.ironvellum.app.domain

import com.ironvellum.app.domain.SchemePlan.DropRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A drop follows the load actually on the set before it, not the load the trial began with. */
class SchemePlanDropTest {

    private val lift = LiftLoading(stepKg = 2.5)

    private fun work(kg: Double?, done: Boolean = false) = DropRow(isDrop = false, done = done, weightKg = kg, edited = false)
    private fun drop(kg: Double? = null, done: Boolean = false, edited: Boolean = false) = DropRow(true, done, kg, edited)

    @Test
    fun `a drop that began at nothing takes 20 percent off the load the set before it was logged at`() {
        // The trial began with no load (a generated rite), the lifter logged 60 kg: the drop is 20% lighter, 48 rounded down to the 2.5 kg grid: 47.5, not 0.
        val loads = SchemePlan.dropLoads(listOf(work(60.0, done = true), drop()), 20, lift)
        assertEquals(listOf(60.0, 47.5), loads)
    }

    @Test
    fun `drops step down from one another and round down onto the kit`() {
        val loads = SchemePlan.dropLoads(listOf(work(62.5, done = true), drop(), drop()), 20, lift)
        // 62.5 * 0.8 = 50, then 40.
        assertEquals(listOf(62.5, 50.0, 40.0), loads)
        // 57.5 * 0.8 = 46 rounds down to 45; 45 * 0.8 = 36 rounds down to 35.
        assertEquals(listOf(57.5, 45.0, 35.0), SchemePlan.dropLoads(listOf(work(57.5), drop(), drop()), 20, lift))
    }

    @Test
    fun `a load the lifter typed on a drop, and a drop already done, stay as they are`() {
        val typed = SchemePlan.dropLoads(listOf(work(60.0), drop(kg = 40.0, edited = true), drop()), 20, lift)
        // 40 * 0.8 = 32 rounds down to 30.
        assertEquals(listOf(60.0, 40.0, 30.0), typed)
        val done = SchemePlan.dropLoads(listOf(work(60.0), drop(kg = 45.0, done = true)), 20, lift)
        assertEquals(listOf(60.0, 45.0), done)
    }

    @Test
    fun `no load before it means a bodyweight drop, never a made up one`() {
        assertEquals(listOf(null, null), SchemePlan.dropLoads(listOf(work(null), drop()), 20, lift))
    }
    @Test
    fun `assisted backoffs and drops increase known support and preserve unknowns`() {
        val assisted = LiftLoading(stepKg = 2.5, bodyweightKg = 80.0, assisted = true)
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF, backoffPercent = 10)
        assertEquals(27.5, SchemePlan.backoff(20.0, 8, scheme, assisted).weightKg!!, 0.0)
        assertEquals(27.5, SchemePlan.seedFixedBackoff(20.0, scheme, assisted)!!, 0.0)
        assertEquals(30.0, SchemePlan.drop(20.0, 10, assisted.copy(stepKg = 5.0))!!, 0.0)
        assertEquals(10.0, SchemePlan.seedFixedBackoff(0.0, scheme, assisted)!!, 0.0)
        assertEquals(8.0, SchemePlan.drop(0.0, 10, assisted.copy(stepKg = 1.0))!!, 0.0)
        assertEquals(null, SchemePlan.drop(null, 10, assisted))
        assertEquals(null, SchemePlan.drop(20.0, 10, assisted.copy(bodyweightKg = null)))
        assertEquals(null, SchemePlan.backoff(null, 8, scheme, assisted).weightKg)
        assertEquals(null, SchemePlan.seedFixedBackoff(null, scheme, assisted))
        assertEquals(null, SchemePlan.seedFixedBackoff(20.0, scheme, assisted.copy(bodyweightKg = null)))
        assertEquals(12.0, SchemePlan.backoff(null, 8, scheme.copy(backoffFixedKg = 12.0), assisted.copy(bodyweightKg = null)).weightKg!!, 0.0)
        assertEquals(listOf(20.0, 27.5, 35.0), SchemePlan.dropLoads(listOf(work(20.0), drop(), drop()), 10, assisted))
        assertEquals(listOf(20.0, 35.0, 40.0), SchemePlan.dropLoads(listOf(work(20.0), drop(35.0, edited = true), drop()), 10, assisted))
        assertEquals(listOf(20.0, 30.0, 35.0), SchemePlan.dropLoads(listOf(work(20.0), drop(30.0, done = true), drop()), 10, assisted))
        assertEquals(listOf(20.0, null, null), SchemePlan.dropLoads(listOf(work(20.0), drop(), drop()), 10, assisted.copy(bodyweightKg = null)))
        assertEquals(17.5, SchemePlan.drop(20.0, 10, lift)!!, 0.0)
    }

    @Test
    fun `assisted support rounds up on the pound grid`() {
        val assisted = LiftLoading(stepKg = 2.5, unit = LoadUnit.LB, bodyweightKg = 80.0, assisted = true)
        val result = SchemePlan.drop(20.0, 10, assisted)!!
        assertEquals(60.0, Loads.toUnit(result, LoadUnit.LB), 0.002)
        assertTrue(result >= 26.0)
    }

    @Test
    fun `stored pound inputs round at the boundary without hiding genuine extra bodyweight`() {
        val support = Loads.toKg(50.0, LoadUnit.LB)
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF, backoffPercent = 10)
        for ((bodyLb, expectedLb) in listOf(200.0 to 65.0, 201.0 to 70.0)) {
            val assisted = LiftLoading(stepKg = 2.5, unit = LoadUnit.LB,
                bodyweightKg = Loads.toKg(bodyLb, LoadUnit.LB), assisted = true)
            val drop = SchemePlan.drop(support, 10, assisted)!!
            val backoff = SchemePlan.backoff(support, 8, scheme, assisted).weightKg!!
            val seed = SchemePlan.seedFixedBackoff(support, scheme, assisted)!!
            assertEquals(expectedLb, Loads.toUnit(drop, LoadUnit.LB), 0.002)
            assertEquals(expectedLb, Loads.toUnit(backoff, LoadUnit.LB), 0.002)
            assertEquals(expectedLb, Loads.toUnit(seed, LoadUnit.LB), 0.002)
        }
    }

}
