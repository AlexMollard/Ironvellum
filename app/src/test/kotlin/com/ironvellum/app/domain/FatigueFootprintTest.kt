package com.ironvellum.app.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueFootprintTest {

    @After
    fun forget() = CustomExercises.reset()

    private fun footprint(name: String, mods: String = "", pool: Collection<String> = MuscleMap.keys) =
        FatigueFootprints.of(name, mods, pool)!!

    private fun FatigueFootprint.dose(muscle: Muscle) = muscles.first { it.muscle == muscle }

    @Test
    fun `bench press bands`() {
        val f = footprint("Bench Press")
        assertEquals(2.7, f.dose(Muscle.MID_CHEST).dose, 1e-9)
        assertEquals(FootprintBand.STRONG, f.dose(Muscle.MID_CHEST).band)
        assertEquals(1.89, f.dose(Muscle.FRONT_DELTS).dose, 1e-9)
        assertEquals(FootprintBand.MEDIUM, f.dose(Muscle.FRONT_DELTS).band)
        assertEquals(1.62, f.dose(Muscle.TRICEPS).dose, 1e-9)
        assertEquals(FootprintBand.MEDIUM, f.dose(Muscle.TRICEPS).band)
        assertEquals(1.35, f.dose(Muscle.UPPER_CHEST).dose, 1e-9)
        assertEquals(1.35, f.dose(Muscle.LOWER_CHEST).dose, 1e-9)
        assertEquals(0.81, f.dose(Muscle.SIDE_DELTS).dose, 1e-9)
        assertEquals(FootprintBand.LIGHT, f.dose(Muscle.SIDE_DELTS).band)
        assertEquals(f.muscles.sortedByDescending { it.dose }, f.muscles)
    }

    @Test
    fun `bands break at one and two`() {
        assertEquals(FootprintBand.STRONG, FatigueFootprints.band(2.0))
        assertEquals(FootprintBand.MEDIUM, FatigueFootprints.band(1.99))
        assertEquals(FootprintBand.MEDIUM, FatigueFootprints.band(1.0))
        assertEquals(FootprintBand.LIGHT, FatigueFootprints.band(0.99))
    }

    @Test
    fun `a muscle under a third of a set is omitted and none is dropped that reaches it`() {
        val shares = CustomExercises.shares(listOf(Muscle.QUADS), listOf(Muscle.CALVES))
        CustomExercises.sync(listOf(CustomExercises.Row("My Press", MuscleGroup.LEGS, "", emptySet(), shares)))
        val f = footprint("My Press")
        assertTrue(f.muscles.all { it.dose >= FatigueTuning.BAND_OMIT_BELOW - 1e-9 })
        // Every muscle of the lifted set reaches 0.3 or has a dose below it and is left out.
        val all = MuscleMap.profile("My Press")!!.muscles.filterValues { it > 0.0 }.keys
        assertEquals(all.size, f.muscles.size + all.count { m -> f.muscles.none { it.muscle == m } })
        assertTrue(FatigueTuning.BAND_OMIT_BELOW > 0.0)
    }

    @Test
    fun `romanian deadlift carries both tags on the hamstrings`() {
        val f = footprint("Romanian Deadlift")
        assertTrue(f.stretchHeavy)
        assertTrue(f.heavyWholeBody)
        assertEquals(Muscle.HAMSTRINGS, f.lead)
        assertEquals(4.212, f.dose(Muscle.HAMSTRINGS).dose, 1e-9)
        assertEquals(FootprintBand.STRONG, f.dose(Muscle.HAMSTRINGS).band)
    }

    @Test
    fun `typical level and light in come from the lead muscle alone`() {
        val bench = footprint("Bench Press")
        assertEquals(Muscle.MID_CHEST, bench.lead)
        assertEquals(FatigueLevel.MODERATE, bench.typicalLevel)
        assertEquals(Math.round(24 * Math.log(2.7 / 2.1) / Math.log(2.0)).toInt(), bench.lightInHours)
        // 0.9 x 3 = 2.7 for a plain lift; a calf-sized dose that starts below the threshold has no light-in.
        val shares = CustomExercises.shares(emptyList(), listOf(Muscle.CALVES))
        CustomExercises.sync(listOf(CustomExercises.Row("My Calf", MuscleGroup.LEGS, "", emptySet(), shares)))
        val f = footprint("My Calf")
        assertTrue(f.typicalLevel <= FatigueLevel.MODERATE)
        if (f.typicalLevel <= FatigueLevel.LIGHT) assertNull(f.lightInHours)
    }

    @Test
    fun `a custom exercise has its own shares and no tags or comparison`() {
        val shares = CustomExercises.shares(listOf(Muscle.QUADS), emptyList())
        CustomExercises.sync(listOf(CustomExercises.Row("My Squat", MuscleGroup.LEGS, "", emptySet(), shares)))
        val f = footprint("My Squat")
        assertFalse(f.stretchHeavy)
        assertFalse(f.heavyWholeBody)
        assertNull(f.comparison)
        assertEquals(Muscle.QUADS, f.muscles.first().muscle)
    }

    @Test
    fun `the comparison is a same-lead lift without the trait`() {
        val f = footprint("Romanian Deadlift", pool = listOf("romanian deadlift", "hamstring bridge", "lying leg curl", "seated leg curl", "bench press"))
        // The stretch table lists both curls and the RDL itself, so the bridge is the only hamstring lead without it.
        assertEquals(Comparison("hamstring bridge", FootprintTrait.LOADS_LONG), f.comparison)
        assertNotNull(footprint("Romanian Deadlift").comparison)
        // A deadlift shares the heavy whole-body trait, so it is no fair foil for the RDL.
        assertNull(footprint("Romanian Deadlift", pool = listOf("deadlift")).comparison)
    }

    @Test
    fun `no trait or an empty pool gives no comparison`() {
        assertNull(footprint("Bench Press").comparison)
        assertNull(footprint("Romanian Deadlift", pool = emptyList()).comparison)
    }

    @Test
    fun `a heavy lift that does not load long compares on the whole body`() {
        val f = footprint("Back Squat", pool = listOf("back squat", "leg extension", "hack squat"))
        assertTrue(f.heavyWholeBody)
        assertEquals(FootprintTrait.HEAVY_WHOLE_BODY, f.comparison?.trait)
        assertNotNull(f.comparison)
    }

    @Test
    fun `every catalogue movement has a derived footprint`() {
        for (key in MuscleMap.keys) {
            val f = FatigueFootprints.of(key, "")
            assertNotNull(key, f)
            assertTrue(key, f!!.muscles.all { it.dose >= FatigueTuning.BAND_OMIT_BELOW - 1e-9 })
        }
    }

    @Test
    fun `no profile gives null`() {
        assertNull(FatigueFootprints.of("Zzz Not A Lift", ""))
    }

    @Test
    fun `the page copy says three sets and one rep left`() {
        // The words on the Fatigue page spell these out; change a constant and the copy must follow.
        assertEquals(3, FatigueTuning.FOOTPRINT_SETS)
        assertEquals(1, FatigueTuning.FOOTPRINT_RIR)
    }
}
