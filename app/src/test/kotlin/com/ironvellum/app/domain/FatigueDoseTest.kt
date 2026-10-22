package com.ironvellum.app.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueDoseTest {

    @After
    fun forget() = CustomExercises.reset()

    private fun set(
        name: String,
        rpe: Int? = 9,
        kind: SetKind = SetKind.WORKING,
        mods: String = "",
        reps: Int = 5,
        weight: Double? = 100.0,
        done: Boolean = true,
    ) = LoggedSet(name, mods, weight, reps, rpe, kind, done, 0L)

    private fun List<MuscleDose>.of(muscle: Muscle) = firstOrNull { it.muscle == muscle }

    // ---------------------------------------------------------------- the factors

    @Test
    fun `effort table`() {
        assertEquals(1.0, FatigueDose.effort(set("Bench Press", rpe = 10)), 0.0)
        assertEquals(0.9, FatigueDose.effort(set("Bench Press", rpe = 9)), 0.0)
        assertEquals(0.8, FatigueDose.effort(set("Bench Press", rpe = 8)), 0.0)
        assertEquals(0.65, FatigueDose.effort(set("Bench Press", rpe = 7)), 0.0)
        assertEquals(1.15, FatigueDose.effort(set("Bench Press", rpe = 6, kind = SetKind.FAILURE)), 0.0)
        assertEquals(1.15, FatigueDose.effort(set("Bench Press", rpe = 6, kind = SetKind.REST_PAUSE)), 0.0)
        assertEquals(0.8, FatigueDose.effort(set("Bench Press", rpe = null)), 0.0)
        assertEquals(0.8, FatigueDose.effort(set("Bench Press", rpe = null, kind = SetKind.DROP)), 0.0)
        assertEquals(0.8, FatigueDose.effort(set("Bench Press", rpe = 8, kind = SetKind.DROP)), 0.0)
    }

    @Test
    fun `novelty table`() {
        assertEquals(1.4, FatigueDose.novelty(0), 0.0)
        assertEquals(1.2, FatigueDose.novelty(1), 0.0)
        assertEquals(1.0, FatigueDose.novelty(2), 0.0)
        assertEquals(1.0, FatigueDose.novelty(5), 0.0)
    }

    @Test
    fun `dose is the plain product`() {
        assertEquals(0.5 * 0.9 * 1.3 * 1.2 * 1.4, FatigueDose.dose(0.5, 0.9, 1.3, 1.2, 1.4), 1e-12)
    }

    // ---------------------------------------------------------------- which sets count

    @Test
    fun `only hard sets produce dose`() {
        assertTrue(FatigueDose.perSet(set("Bench Press", kind = SetKind.WARMUP), 1.0).isEmpty())
        assertTrue(FatigueDose.perSet(set("Bench Press", done = false), 1.0).isEmpty())
        assertTrue(FatigueDose.perSet(set("Bench Press", rpe = 6), 1.0).isEmpty())
        assertFalse(FatigueDose.perSet(set("Bench Press", rpe = 7), 1.0).isEmpty())
    }

    @Test
    fun `unknown exercise contributes nothing`() {
        assertTrue(FatigueDose.perSet(set("Zzz Not A Lift"), 1.0).isEmpty())
    }

    @Test
    fun `unrated imported set counts at point eight`() {
        val doses = FatigueDose.perSet(set("Bench Press", rpe = null, weight = 60.0, reps = 8), 1.0)
        assertEquals(0.8, doses.of(Muscle.MID_CHEST)!!.dose, 1e-9)
        assertTrue("an endurance-length unrated set is not hard", FatigueDose.perSet(set("Bench Press", rpe = null, weight = 60.0, reps = 20), 1.0).isEmpty())
    }

    // ---------------------------------------------------------------- shares and traits

    @Test
    fun `bench set at one rep left`() {
        val doses = FatigueDose.perSet(set("Bench Press", rpe = 9), 1.0)
        assertEquals(0.9, doses.of(Muscle.MID_CHEST)!!.dose, 1e-9)
        assertEquals(0.63, doses.of(Muscle.FRONT_DELTS)!!.dose, 1e-9)
        assertEquals(0.54, doses.of(Muscle.TRICEPS)!!.dose, 1e-9)
        assertEquals(0.27, doses.of(Muscle.SIDE_DELTS)!!.dose, 1e-9)
        assertTrue(doses.none { it.stretched })
    }

    @Test
    fun `romanian deadlift combines stretch and systemic load`() {
        val doses = FatigueDose.perSet(set("Romanian Deadlift", rpe = 9), 1.0)
        val hamstrings = doses.of(Muscle.HAMSTRINGS)!!
        assertEquals(1.0 * 0.9 * 1.3 * 1.2, hamstrings.dose, 1e-9)
        assertTrue(hamstrings.stretched)
        assertEquals(1.08, doses.of(Muscle.GLUTES)!!.dose, 1e-9)
        assertEquals("already credited at 0.5, so no bonus", 0.54, doses.of(Muscle.LOWER_BACK)!!.dose, 1e-9)
        assertEquals(1, doses.count { it.muscle == Muscle.LOWER_BACK })
    }

    @Test
    fun `squat gets the lower back bonus`() {
        val doses = FatigueDose.perSet(set("Back Squat", rpe = 9), 1.0)
        assertEquals(1.0 * 0.9 * 1.2, doses.of(Muscle.QUADS)!!.dose, 1e-9)
        assertEquals("bonus has effort and novelty only", 0.25 * 0.9, doses.of(Muscle.LOWER_BACK)!!.dose, 1e-9)
        assertFalse(doses.of(Muscle.LOWER_BACK)!!.stretched)
        assertNull("share 0 is not credited", doses.of(Muscle.HAMSTRINGS))
    }

    @Test
    fun `novelty scales everything`() {
        assertEquals(1.26, FatigueDose.perSet(set("Bench Press", rpe = 9), 1.4).of(Muscle.MID_CHEST)!!.dose, 1e-9)
    }

    @Test
    fun `incline deficit bench moves chest credit and stretches the main muscles`() {
        val doses = FatigueDose.perSet(set("Bench Press", rpe = 10, mods = "incline, deficit"), 1.0)
        val upper = doses.of(Muscle.UPPER_CHEST)!!
        assertEquals("incline raises the clavicular chest to 1.0, stretched by the deficit", 1.3, upper.dose, 1e-9)
        assertTrue(upper.stretched)
        // MuscleMap keeps the sternal chest at 1.0 when an incline raises the clavicular one, so it is main work too.
        assertTrue(doses.of(Muscle.MID_CHEST)!!.stretched)
        assertTrue(doses.of(Muscle.FRONT_DELTS)!!.stretched)
        assertFalse("0.6 is not main", doses.of(Muscle.TRICEPS)!!.stretched)
        assertFalse("0.5 is not main", doses.of(Muscle.LOWER_CHEST)!!.stretched)
    }

    @Test
    fun `modifier order does not split novelty`() {
        assertEquals(Variation.liftKey("Bench Press", "deficit,incline"), Variation.liftKey("Bench Press", "incline,deficit"))
        assertEquals(Variation.liftKey("Bench Press", ""), Variation.liftKey("Bench Press", "tempo"))
    }

    @Test
    fun `custom exercise uses its own shares and no traits`() {
        val shares = CustomExercises.shares(listOf(Muscle.MID_CHEST), listOf(Muscle.TRICEPS))
        CustomExercises.sync(listOf(CustomExercises.Row("My Press", MuscleGroup.PUSH, "", emptySet(), shares)))
        val doses = FatigueDose.perSet(set("My Press", rpe = 9, mods = "deficit"), 1.0)
        assertEquals(0.9, doses.of(Muscle.MID_CHEST)!!.dose, 1e-9)
        assertEquals(0.45, doses.of(Muscle.TRICEPS)!!.dose, 1e-9)
        assertTrue(doses.none { it.stretched })
        assertEquals(2, doses.size)
    }

    @Test
    fun `custom squat gets no systemic factor or lower back bonus`() {
        val shares = CustomExercises.shares(listOf(Muscle.QUADS), emptyList())
        CustomExercises.sync(listOf(CustomExercises.Row("My Squat", MuscleGroup.LEGS, "", emptySet(), shares)))
        val doses = FatigueDose.perSet(set("My Squat", rpe = 9), 1.0)
        assertEquals(0.9, doses.of(Muscle.QUADS)!!.dose, 1e-9)
        assertNull(doses.of(Muscle.LOWER_BACK))
    }
}
