package com.ironvellum.app.domain

import com.ironvellum.app.domain.PlateRack.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatesTest {

    private val kg = PlateSizes.defaults(LoadUnit.KG)
    private val lb = PlateSizes.defaults(LoadUnit.LB)

    private fun sides(rack: PlateRack, total: Double) = rack.build(total)?.perSide

    @Test
    fun `the default kg set reaches 297_5 and steps by 2_5`() {
        assertEquals(297.5, kg.heaviest, 0.0)
        assertEquals(2.5, kg.step!!, 0.0)
        assertEquals(1.25, kg.smallest!!, 0.0)
    }

    @Test
    fun `an exact load is the greedy build per side`() {
        assertEquals(listOf(25.0, 15.0), sides(kg, 100.0))
        assertEquals(listOf<Double>(), sides(kg, 20.0))
        assertEquals(listOf(25.0, 25.0, 20.0, 20.0, 15.0, 10.0, 10.0, 5.0, 5.0, 2.5, 1.25), sides(kg, 297.5))
        val read = kg.read(100.0)
        assertEquals(Kind.EXACT, read.kind)
        assertEquals(100.0, read.build.total, 0.0)
        assertEquals(40.0, read.build.sideTotal, 0.0)
        assertNull(read.above)
    }

    @Test
    fun `a load between plates shows the closest build below and the nearest above`() {
        val read = kg.read(101.0)
        assertEquals(Kind.BETWEEN, read.kind)
        assertEquals(100.0, read.build.total, 0.0)
        assertEquals(listOf(25.0, 15.0), read.build.perSide)
        assertEquals(102.5, read.above!!.total, 0.0)
        assertEquals(100.0, kg.nearestBelow(101.0)!!, 0.0)
        assertEquals(102.5, kg.nearestAbove(101.0)!!, 0.0)
    }

    @Test
    fun `a load over the rack is the heaviest build`() {
        val read = kg.read(300.0)
        assertEquals(Kind.OVER, read.kind)
        assertEquals(297.5, read.build.total, 0.0)
        assertNull(read.above)
        assertNull(kg.nearestAbove(300.0))
    }

    @Test
    fun `a load under the bar is the bar alone, and the next step up is offered`() {
        val read = kg.read(15.0)
        assertEquals(Kind.UNDER_BAR, read.kind)
        assertEquals(20.0, read.build.total, 0.0)
        assertTrue(read.build.perSide.isEmpty())
        assertEquals(22.5, read.above!!.total, 0.0)
    }

    @Test
    fun `1_25 kg plates make 2_5 kg total steps`() {
        assertEquals(listOf(1.25), sides(kg, 22.5))
        assertEquals(listOf(2.5), sides(kg, 25.0))
        assertEquals(listOf(2.5, 1.25), sides(kg, 27.5))
        assertNull(kg.build(21.0))
        assertNull(kg.build(23.75))
        assertEquals(22.5, kg.stepFrom(20.0, up = true)!!, 0.0)
        assertEquals(20.0, kg.stepFrom(22.5, up = false)!!, 0.0)
        assertNull(kg.stepFrom(20.0, up = false))
        assertNull(kg.stepFrom(297.5, up = true))
        // Without the small plates the step doubles.
        val noFine = PlateRack(LoadUnit.KG, 20.0, kg.pairs - 1.25)
        assertEquals(5.0, noFine.step!!, 0.0)
        assertNull(noFine.build(22.5))
    }

    @Test
    fun `the lb set swaps to a 45 lb bar and its own plates`() {
        assertEquals(45.0, lb.bar, 0.0)
        assertEquals(490.0, lb.heaviest, 0.0)
        assertEquals(5.0, lb.step!!, 0.0)
        assertEquals(listOf(45.0, 45.0), sides(lb, 225.0))
        assertEquals(listOf(35.0, 5.0, 2.5), sides(lb, 130.0))
        assertEquals(Kind.BETWEEN, lb.read(226.0).kind)
        assertEquals(225.0, lb.read(226.0).build.total, 0.0)
        assertEquals(230.0, lb.read(226.0).above!!.total, 0.0)
        // A kg load is read in lb: 100 kg shows as 220.5 lb, which falls between 220 and 225.
        val shown = Loads.snap(100.0, LoadUnit.LB)
        assertEquals(Kind.BETWEEN, lb.read(shown).kind)
        assertEquals(220.0, lb.read(shown).build.total, 0.0)
    }

    @Test
    fun `a build greedy would miss is still found, and every offered load can be drawn`() {
        // 25 x1 and 15 x2 make 30 a side only as 15 + 15: greedy would take the 25 and strand 5.
        val rack = PlateRack(LoadUnit.KG, 20.0, mapOf(25.0 to 1, 15.0 to 2))
        assertEquals(listOf(15.0, 15.0), sides(rack, 80.0))
        assertTrue(80.0 in rack.loadable)
        assertEquals(Kind.EXACT, rack.read(80.0).kind)
        // 35 a side is out of reach (25 + 15 = 40, 15 + 15 = 30), so 90 sits between 80 and 100.
        assertNull(rack.build(90.0))
        assertEquals(80.0, rack.nearestBelow(90.0)!!, 0.0)
        assertEquals(100.0, rack.nearestAbove(90.0)!!, 0.0)
        rack.loadable.forEach { assertTrue("$it", rack.build(it) != null) }
    }

    @Test
    fun `pairs owned cap how many of a size go on`() {
        val rack = PlateRack(LoadUnit.KG, 20.0, mapOf(20.0 to 1, 10.0 to 1))
        assertEquals(listOf(20.0, 10.0), sides(rack, 80.0))
        assertNull(rack.build(100.0))
        assertEquals(80.0, rack.heaviest, 0.0)
    }

    @Test
    fun `a rack with no plates loads the bar only`() {
        val rack = PlateRack(LoadUnit.KG, 20.0, emptyMap())
        assertEquals(20.0, rack.heaviest, 0.0)
        assertNull(rack.step)
        assertEquals(listOf(20.0), rack.loadable)
        assertEquals(Kind.OVER, rack.read(30.0).kind)
    }
}
