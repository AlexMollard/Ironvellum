package com.ironvellum.app.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueTraitsTest {

    @After
    fun forget() = CustomExercises.reset()

    private fun profile(name: String) = MuscleMap.profile(name)!!

    @Test
    fun `every muscle has a recovery class`() {
        val classes = Muscle.entries.map { it.recoveryClass() }
        assertEquals(9, classes.count { it == RecoveryClass.FAST })
        assertEquals(11, classes.count { it == RecoveryClass.MEDIUM })
        assertEquals(7, classes.count { it == RecoveryClass.SLOW })
        assertEquals(RecoveryClass.FAST, Muscle.CALVES.recoveryClass())
        assertEquals(18.0, Muscle.CALVES.recoveryClass().halfLifeHours, 0.0)
        assertEquals(RecoveryClass.MEDIUM, Muscle.LATS.recoveryClass())
        assertEquals(24.0, Muscle.LATS.recoveryClass().halfLifeHours, 0.0)
        assertEquals(RecoveryClass.SLOW, Muscle.QUADS.recoveryClass())
        assertEquals(36.0, Muscle.QUADS.recoveryClass().halfLifeHours, 0.0)
        assertEquals(RecoveryClass.SLOW, Muscle.LOWER_BACK.recoveryClass())
        assertEquals(RecoveryClass.FAST, Muscle.NECK.recoveryClass())
        assertEquals(RecoveryClass.MEDIUM, Muscle.SERRATUS.recoveryClass())
    }

    @Test
    fun `trait keys are catalogue names`() {
        val missing = (FatigueTraits.STRETCH_TABLE.keys + FatigueTraits.HEAVY_WHOLE_BODY).filter { it !in MuscleMap.keys }
        assertTrue("not in the catalogue: $missing", missing.isEmpty())
    }

    @Test
    fun `romanian deadlift is stretch heavy on the hamstrings only`() {
        val p = profile("Romanian Deadlift")
        assertEquals(setOf(Muscle.HAMSTRINGS), FatigueTraits.stretchedMuscles("Romanian Deadlift", "", p))
    }

    @Test
    fun `deficit stretches main muscles only`() {
        val p = profile("Push-up")
        val stretched = FatigueTraits.stretchedMuscles("Push-up", "deficit", p)
        assertTrue(Muscle.MID_CHEST in stretched)
        assertFalse("share 0.6 is not main", Muscle.TRICEPS in stretched)
    }

    @Test
    fun `plain bench has no stretch trait and only squats and hinges are heavy whole body`() {
        assertTrue(FatigueTraits.stretchedMuscles("Bench Press", "", profile("Bench Press")).isEmpty())
        assertTrue(FatigueTraits.isHeavyWholeBody("Back Squat"))
        assertFalse(FatigueTraits.isHeavyWholeBody("Bench Press"))
    }

    @Test
    fun `the plan generator stretch flag is not the source`() {
        val cable = profile("Cable Pull-Through")
        assertTrue(cable.stretchBias)
        assertTrue(FatigueTraits.stretchedMuscles("Cable Pull-Through", "", cable).isEmpty())
    }

    @Test
    fun `a custom exercise has no traits`() {
        val shares = CustomExercises.shares(listOf(Muscle.QUADS), emptyList())
        CustomExercises.sync(listOf(CustomExercises.Row("My Squat", MuscleGroup.LEGS, "", emptySet(), shares)))
        val p = MuscleMap.profile("My Squat")!!
        assertTrue(FatigueTraits.stretchedMuscles("My Squat", "deficit", p).isEmpty())
        assertFalse(FatigueTraits.isHeavyWholeBody("My Squat"))
    }
}
