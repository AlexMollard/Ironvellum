package com.ironvellum.app.domain

import com.ironvellum.app.domain.SchemePlan.DropRow
import org.junit.Assert.assertEquals
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
}
