package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stand-in Today offers for a tired exercise: always below High, never one the routine holds, ties to recent lifts. */
class FatigueSwapTest {

    private val now = 1_000_000_000_000L

    private val catalogue: List<Exercise> = Seed.exercises.mapIndexed { i, it ->
        Exercise(
            id = i + 1L,
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    private val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, maxExercises = 8)

    private fun report(vararg residuals: Pair<Muscle, Double>) = FatigueReport(
        nowMs = now,
        newLifter = false,
        contributions = residuals.associate { (muscle, residual) ->
            muscle to listOf(FatigueContribution(muscle, 1, now, "Test lift", "", "test lift", residual, 0.0, false, false, 24.0))
        },
    )

    private fun entry(name: String, position: Int, id: Long = position + 1L) =
        PresetEntry(id = id, exerciseId = id, exerciseName = name, targetSets = 3, targetReps = 8, position = position)

    private val dip = entry("Weighted Dip", 0)
    private val routine = listOf(dip, entry("Overhead Press", 1))

    /** The lower chest, which the dips lean on, is High. */
    private val tired = report(Muscle.LOWER_CHEST to 4.0)

    @Test
    fun `the stand-in works no muscle at High, and is neither the lift nor one the routine already holds`() {
        val swap = FatigueSwap.choose(dip, routine, tired, now, catalogue, request, StrengthProfile(), recent = emptySet())
        assertNotNull("a push day with tired chest has a fresher push", swap)
        swap!!
        val main = FatigueAdvice.mainMuscles(swap.exerciseName)
        assertTrue(main.isNotEmpty())
        assertTrue("${swap.exerciseName} must be below High", main.none { FatigueAdvice.triggers(tired.levelAt(it, now)) })
        assertFalse(swap.exerciseName.equals("Weighted Dip", ignoreCase = true))
        assertFalse(swap.exerciseName.equals("Overhead Press", ignoreCase = true))
        assertTrue(swap.sets == 3 && swap.reps > 0)
        assertEquals("a stand-in starts clean of the old lift's modifiers", "", swap.modifiers)
        assertEquals(catalogue.first { it.name == swap.exerciseName }.id, swap.exerciseId)
    }

    @Test
    fun `every candidate the ranking offers is below High`() {
        val candidates = FatigueSwap.standIns(dip, routine, tired, now, catalogue, request)
        assertTrue(candidates.isNotEmpty())
        candidates.forEach { c ->
            assertTrue(FatigueAdvice.mainMuscles(c.name).none { FatigueAdvice.triggers(tired.levelAt(it, now)) })
        }
        assertTrue(candidates.size <= 3)
    }

    @Test
    fun `when everything of the pattern is tired there is no swap`() {
        val everything = report(*Muscle.entries.map { it to 5.0 }.toTypedArray())
        assertNull(FatigueSwap.choose(dip, routine, everything, now, catalogue, request, StrengthProfile(), emptySet()))
    }

    @Test
    fun `a lift the report cannot place has no swap`() {
        val mystery = entry("An exercise nobody profiled", 0)
        assertNull(FatigueSwap.choose(mystery, listOf(mystery), tired, now, catalogue, request, StrengthProfile(), emptySet()))
    }

    @Test
    fun `a recent lift among the best candidates wins a tie, otherwise the closest does`() {
        val candidates = FatigueSwap.standIns(dip, routine, tired, now, catalogue, request)
        assertTrue("the case needs a few candidates", candidates.size >= 2)
        val closest = FatigueSwap.choose(dip, routine, tired, now, catalogue, request, StrengthProfile(), recent = emptySet())!!
        assertEquals(candidates.first().name, closest.exerciseName)

        val lately = candidates[1].name.lowercase()
        val picked = FatigueSwap.choose(dip, routine, tired, now, catalogue, request, StrengthProfile(), recent = setOf(lately))!!
        assertEquals(candidates[1].name, picked.exerciseName)

        // A lift done lately that is not among the best is no reason to go further down.
        val farAway = catalogue.first { it.name !in candidates.map(Exercise::name) }.name.lowercase()
        val unchanged = FatigueSwap.choose(dip, routine, tired, now, catalogue, request, StrengthProfile(), recent = setOf(farAway))!!
        assertEquals(candidates.first().name, unchanged.exerciseName)
    }
}
