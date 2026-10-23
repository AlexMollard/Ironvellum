package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hundred Risings and Two Hundred Suns count the movement itself, not an easier version of it (Variation.eases). */
class SessionRepsVariationTest {

    private val pullUp = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
    private val pushUp = Exercise(id = 4, name = "Push-up", muscleGroup = MuscleGroup.PUSH, isWeighted = false)
    private val catalogue = listOf(pullUp, pushUp).associateBy { it.id }

    private fun set(exercise: Exercise, reps: Int, modifiers: String = "", index: Int = 0) =
        SessionSet(exerciseId = exercise.id, setIndex = index, reps = reps, modifiers = modifiers, done = true)

    private fun ledger(sets: List<SessionSet>) = Titles.ledgerOf(
        totalXp = 0,
        history = listOf(WorkoutSession(id = 1, label = "s", startedAtMs = 1_000L) to sets),
        healthDays = emptyList(),
        practices = emptyList(),
        exercises = catalogue,
    )

    private fun earned(id: String, ledger: Titles.Ledger) = Titles.satisfied(Titles.byId(id)!!.rule, ledger)

    @Test
    fun `hundred risings ignores assisted reps`() {
        val standard = (0 until 10).map { set(pullUp, 10, index = it) }
        assertTrue(earned("century_of_rungs", ledger(standard)))
        val assistedHalf = (0 until 5).map { set(pullUp, 10, index = it) } + (5 until 10).map { set(pullUp, 10, "assisted", index = it) }
        assertFalse("50 assisted reps are not pull-ups", earned("century_of_rungs", ledger(assistedHalf)))
        val withMore = assistedHalf + (10 until 15).map { set(pullUp, 10, index = it) }
        assertTrue("100 standard reps beside assisted ones", earned("century_of_rungs", ledger(withMore)))
        val harder = (0 until 10).map { set(pullUp, 10, "archer", index = it) }
        assertTrue("a harder variation still counts as the lift", earned("century_of_rungs", ledger(harder)))
    }

    @Test
    fun `two hundred suns ignores incline reps`() {
        val flat = (0 until 10).map { set(pushUp, 20, index = it) }
        assertTrue(earned("two_hundred_suns", ledger(flat)))
        val inclineHalf = (0 until 5).map { set(pushUp, 20, index = it) } + (5 until 10).map { set(pushUp, 20, "incline", index = it) }
        assertFalse(earned("two_hundred_suns", ledger(inclineHalf)))
        val deficit = (0 until 10).map { set(pushUp, 20, "deficit", index = it) }
        assertTrue(earned("two_hundred_suns", ledger(deficit)))
        assertEquals(100, ledger(inclineHalf).bestSessionReps["push-up"])
    }
}
