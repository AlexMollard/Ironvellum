package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The rite editor's dedicated-exercise prompt: when a modifier or load asks it, and what Switch writes. */
class TwinPromptTest {

    private val push = Exercise(1, "Push-up", MuscleGroup.PUSH, isWeighted = false)
    private val archer = Exercise(2, "Archer Push-up", MuscleGroup.PUSH, isWeighted = false)
    private val incline = Exercise(3, "Incline Push-up", MuscleGroup.PUSH, isWeighted = false)
    private val weightedPush = Exercise(4, "Weighted Push-up", MuscleGroup.PUSH, isWeighted = true)
    private val bench = Exercise(5, "Bench Press", MuscleGroup.PUSH, isWeighted = true)
    private val catalogue = listOf(push, archer, incline, weightedPush, bench)

    private fun entry(exercise: Exercise, weight: String = "", modifiers: String = "") =
        EditorEntry(exercise.id, exercise.name, sets = "3", reps = "8", weight = weight, modifiers = modifiers)

    private fun prompt(before: EditorEntry, edited: EditorEntry, kept: Set<String> = emptySet()) =
        twinPromptFor(before, edited.followingLoad(before, catalogue), catalogue, kept)

    @Test
    fun `adding archer to a push-up asks about the archer push-up`() {
        val before = entry(push)
        assertEquals("Archer Push-up", prompt(before, before.copy(modifiers = "archer"))!!.twin.dedicated)
    }

    @Test
    fun `a load that tags weighted asks about the weighted twin`() {
        val before = entry(push)
        assertEquals("Weighted Push-up", prompt(before, before.copy(weight = "10"))!!.twin.dedicated)
    }

    @Test
    fun `weighted and incline together ask about the incline push-up and keep weighted`() {
        val before = entry(push, weight = "10", modifiers = "weighted")
        val res = prompt(before, before.copy(modifiers = "weighted, incline"))!!
        assertEquals("Incline Push-up", res.twin.dedicated)
        assertEquals("weighted", res.modifiers)
    }

    @Test
    fun `keeping it is remembered for that base and modifier only`() {
        val before = entry(push)
        val kept = setOf("push-up|archer")
        assertNull(prompt(before, before.copy(modifiers = "archer"), kept))
        assertNotNull(prompt(before, before.copy(weight = "10"), kept))
    }

    @Test
    fun `no prompt for a standing modifier, a pick of another exercise or a barbell lift`() {
        val has = entry(push, modifiers = "archer")
        assertNull(prompt(has, has.copy(sets = "4")))
        assertNull(prompt(entry(archer), entry(archer).copy(exerciseId = push.id, exerciseName = push.name, modifiers = "archer")))
        val barbell = entry(bench)
        assertNull(prompt(barbell, barbell.copy(weight = "60", modifiers = "paused")))
    }

    @Test
    fun `switching replaces the exercise and drops what the name carries`() {
        val before = entry(push, weight = "10", modifiers = "weighted")
        val edited = before.copy(modifiers = "weighted, tempo, incline")
        val res = prompt(before, edited)!!
        val switched = edited.switchedTo(incline, res)
        assertEquals("Incline Push-up", switched.exerciseName)
        assertEquals(incline.id, switched.exerciseId)
        assertEquals("weighted, tempo", switched.modifiers)
    }
}
