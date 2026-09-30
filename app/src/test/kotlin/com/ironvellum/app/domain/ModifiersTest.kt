package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifiersTest {

    private val catalogue: Map<String, Exercise> = Seed.exercises.associate { e ->
        e.name to Exercise(
            name = e.name,
            muscleGroup = MuscleGroup.entries.firstOrNull { it.name == e.muscleGroup } ?: MuscleGroup.CORE,
            isWeighted = e.isWeighted,
            metric = ExerciseMetric.valueOf(e.metric),
            category = e.category,
        )
    }

    private fun of(name: String) = applicableModifiers(catalogue.getValue(name))

    @Test
    fun `a barbell press gets only tempo and paused`() {
        assertEquals(listOf("tempo", "paused"), of("Bench Press"))
    }

    @Test
    fun `a pull-up gets the bodyweight pull modifiers and no angle`() {
        val m = of("Pull-up")
        assertTrue(m.containsAll(listOf("assisted", "archer", "one-arm", "tempo", "paused")))
        listOf("weighted", "banded", "incline", "decline", "elevated").forEach { assertFalse(it, it in m) }
    }

    @Test
    fun `a deadlift gets deficit tempo and paused`() {
        assertEquals(listOf("deficit", "tempo", "paused"), of("Deadlift"))
    }

    @Test
    fun `a push-up gets angles deficit and the one-arm line`() {
        assertTrue(of("Push-up").containsAll(listOf("incline", "decline", "elevated", "deficit", "archer", "one-arm")))
    }

    @Test
    fun `a word already in the name is not offered again`() {
        assertFalse("paused" in of("Paused Bench Press"))
        assertFalse("archer" in of("Archer Push-up"))
        assertFalse("one-arm" in of("One-Arm Push-up"))
        assertFalse("incline" in of("Incline Push-up"))
    }

    @Test
    fun `a hold gets nothing`() {
        assertEquals(emptyList<String>(), of("Plank"))
    }

    @Test
    fun `an unknown movement gets only what fits anything`() {
        val own = Exercise(name = "Towel Thing", muscleGroup = MuscleGroup.PULL, isWeighted = false)
        assertEquals(listOf("assisted", "tempo", "paused"), applicableModifiers(own))
        assertEquals(listOf("tempo", "paused"), applicableModifiers(own.copy(isWeighted = true)))
    }

    @Test
    fun `every offered token is one the scoring or muscle code reads`() {
        val bad = catalogue.values.flatMap { e ->
            applicableModifiers(e).filter { MovementDifficulty.modifierFactor(it) == 1.0 }.map { "${e.name}: $it" }
        }
        assertEquals(emptyList<String>(), bad)
    }
}
