package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** A ticked set's load flows to the later sets that have none, and only those. */
class CarryLoadTest {

    private fun set(i: Int, kg: Double?, done: Boolean = false) = CarrySet(id = i.toLong(), setIndex = i, weightKg = kg, done = done)

    @Test
    fun `a ticked load reaches the later undone sets that have none`() {
        val sets = listOf(set(0, 60.0, done = true), set(1, null), set(2, 0.0), set(3, null))
        assertEquals(listOf(1L, 2L, 3L), loadsToCarry(sets[0], sets))
    }

    @Test
    fun `a load already set is never overwritten`() {
        val sets = listOf(set(0, 60.0, done = true), set(1, 62.5), set(2, null))
        assertEquals(listOf(2L), loadsToCarry(sets[0], sets))
    }

    @Test
    fun `earlier and done sets are left alone`() {
        val sets = listOf(set(0, null), set(1, 60.0, done = true), set(2, null, done = true), set(3, null))
        assertEquals(listOf(3L), loadsToCarry(sets[1], sets))
    }

    @Test
    fun `a tick with no load carries nothing`() {
        val sets = listOf(set(0, null, done = true), set(1, null))
        assertEquals(emptyList<Long>(), loadsToCarry(sets[0], sets))
        assertEquals(emptyList<Long>(), loadsToCarry(set(0, 0.0, done = true), sets))
    }
}
