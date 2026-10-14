package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** kg stays the stored truth; lb is what is shown (nearest 0.5) and typed (exact pound). */
class LoadUnitsTest {

    private val lb = LoadUnit.LB
    private val kg = LoadUnit.KG

    @Test
    fun `lb displays to the nearest half pound`() {
        // The approved mock's own figures.
        assertEquals("220.5", Loads.format(100.0, lb))
        assertEquals("231.5", Loads.format(105.0, lb))
        assertEquals("176.5", Loads.format(80.0, lb))
        assertEquals("308.5", Loads.format(140.0, lb))
        assertEquals("132.5", Loads.format(60.0, lb))
        // 102.5 kg is 225.97 lb: a whole pound, not 225.5.
        assertEquals("226", Loads.format(102.5, lb))
        assertEquals("225 lb", Loads.withUnit(Loads.toKg(225.0, lb), lb))
    }

    @Test
    fun `kg display is unchanged`() {
        assertEquals("100", Loads.format(100.0, kg))
        assertEquals("8.75", Loads.format(8.75, kg))
        assertEquals("102.5 kg", Loads.withUnit(102.5, kg))
        assertEquals("BW", Loads.label(null, kg))
        assertEquals("BW", Loads.label(0.0, lb))
    }

    @Test
    fun `typed lb converts to kg at the exact pound, kept to the gram`() {
        assertEquals(102.058, Loads.parse("225", lb, 500.0).getOrThrow(), 1e-9)
        assertEquals(45.359, Loads.parse("100", lb, 500.0).getOrThrow(), 1e-9)
        assertEquals(0.0, Loads.parse("", lb, 500.0).getOrThrow(), 0.0)
        assertEquals(15.2, Loads.parse(" 15,2 ", kg, 500.0).getOrThrow(), 1e-9)
    }

    @Test
    fun `typed loads over the ceiling or not numbers fail in either unit`() {
        assertTrue(Loads.parse("1103", lb, 500.0).isFailure)
        assertTrue(Loads.parse("1102", lb, 500.0).isSuccess)
        assertTrue(Loads.parse("501", kg, 500.0).isFailure)
        assertTrue(Loads.parse("abc", lb, 500.0).isFailure)
        assertEquals("1102", Loads.ceilingText(500.0, lb))
        assertEquals("500", Loads.ceilingText(500.0, kg))
    }

    @Test
    fun `every half pound survives typing, the gram, and display`() {
        for (half in 0..2200) {
            val typed = half / 2.0
            val stored = Loads.parse(Loads.fieldText(Loads.toKg(typed, lb), lb), lb, 1000.0).getOrThrow()
            assertEquals("at $typed lb", typed, Loads.snap(stored, lb), 0.0)
        }
    }

    @Test
    fun `lb stepper grids follow the movement kg step`() {
        assertEquals(5.0, Loads.lbGrid(5.0), 0.0)
        assertEquals(5.0, Loads.lbGrid(2.5), 0.0)
        assertEquals(2.5, Loads.lbGrid(1.25), 0.0)
        assertEquals(10.0, Loads.lbGrid(10.0), 0.0)
        assertEquals(5.0, Loads.lbGrid(Progression.weightStepKg("LEGS", "Back Squat")), 0.0)
        assertEquals(2.5, Loads.lbGrid(Progression.weightStepKg("ARMS", "Dumbbell Curl")), 0.0)
    }

    @Test
    fun `a typed lb load steps up and down the grid, never sticking on a near miss`() {
        // 225 lb is stored as 102.058 kg, which reads back as 224.9993: "+" must still reach 230.
        val stored = Loads.parse("225", lb, 500.0).getOrThrow()
        assertEquals("230", Loads.format(Loads.stepUp(stored, lb, 5.0, 2.5), lb))
        assertEquals("220", Loads.format(Loads.stepDown(stored, lb, 5.0, 2.5)!!, lb))
        // Off-grid snaps onto it, as the kg stepper does.
        val off = Loads.toKg(222.0, lb)
        assertEquals("225", Loads.format(Loads.stepUp(off, lb, 5.0, 2.5), lb))
        assertEquals("220", Loads.format(Loads.stepDown(off, lb, 5.0, 2.5)!!, lb))
        // Small work moves 2.5 lb.
        val curl = Loads.toKg(25.0, lb)
        assertEquals("27.5", Loads.format(Loads.stepUp(curl, lb, 2.5, 2.5), lb))
        assertEquals("22.5", Loads.format(Loads.stepDown(curl, lb, 2.5, 2.5)!!, lb))
        // Bodyweight starts at the first step, and the first step down returns to it.
        assertEquals("5", Loads.format(Loads.stepUp(null, lb, 5.0, 2.5), lb))
        assertNull(Loads.stepDown(Loads.toKg(5.0, lb), lb, 5.0, 2.5))
        assertNull(Loads.stepDown(null, lb, 5.0, 2.5))
    }

    @Test
    fun `the kg stepper keeps the 2_5 kg plate grid`() {
        assertEquals(17.5, Loads.stepUp(15.2, kg, 5.0, 2.5), 1e-9)
        assertEquals(15.0, Loads.stepDown(15.2, kg, 5.0, 2.5)!!, 1e-9)
        assertEquals(2.5, Loads.stepUp(null, kg, 5.0, 2.5), 1e-9)
        assertNull(Loads.stepDown(2.5, kg, 5.0, 2.5))
    }

    @Test
    fun `fixed rounds to places in kg and to the half pound in lb`() {
        assertEquals("102.3", Loads.fixed(102.34, kg, 1))
        assertEquals("231", Loads.fixed(105.0, lb, 0))
        assertEquals("231.5", Loads.fixed(105.0, lb, 1))
    }

    @Test
    fun `progression in lb adds on the lb grid and words the advice in lb`() {
        val sets = List(5) { Progression.Attempt(Loads.toKg(220.0, lb), 5) }
        val rec = Progression.fromSets(TrainingMode.STRENGTH, targetReps = 5, minSets = 5, sets, "LEGS", "Back Squat", unit = lb)
        assertEquals("225", Loads.format(rec.weightKg!!, lb))
        assertEquals("Target cleared — add 5 lb, drop to 3 reps", rec.reason)
        val kgRec = Progression.fromSets(TrainingMode.STRENGTH, targetReps = 5, minSets = 5, List(5) { Progression.Attempt(20.0, 5) })
        assertEquals("Target cleared — add 2.5 kg, drop to 3 reps", kgRec.reason)
    }

    @Test
    fun `a barbell lift is one whose every way of doing it uses the bar`() {
        assertTrue(GearRequirements.isBarbellLift("Back Squat"))
        assertTrue(GearRequirements.isBarbellLift("deadlift"))
        assertTrue(!GearRequirements.isBarbellLift("Push-Up"))
        assertTrue(!GearRequirements.isBarbellLift("Goblet Squat"))
        assertTrue(!GearRequirements.isBarbellLift("Not A Real Lift"))
    }
    @Test
    fun `a loaded lift with nothing on it reads zero, never bodyweight`() {
        assertEquals("BW", Loads.label(null, kg))
        assertEquals("BW", Loads.label(0.0, kg, weighted = false))
        assertEquals("0 kg", Loads.label(null, kg, weighted = true))
        assertEquals("0 lb", Loads.label(0.0, lb, weighted = true))
        assertEquals("60 kg", Loads.label(60.0, kg, weighted = true))
    }
}
