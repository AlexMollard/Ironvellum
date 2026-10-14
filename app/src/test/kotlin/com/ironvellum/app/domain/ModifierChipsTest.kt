package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one modifier picker's state, shared by the live trial's dialog and the rite editor: what is offered, what is set,
 * what is flagged, and how "weighted" follows the load.
 */
class ModifierChipsTest {

    private val pullUp = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
    private val bench = Exercise(id = 2, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
    private val plank = Exercise(id = 3, name = "Plank", muscleGroup = MuscleGroup.CORE, isWeighted = false, metric = ExerciseMetric.HOLD)
    private val run = Exercise(id = 4, name = "Running", muscleGroup = MuscleGroup.CORE, isWeighted = false, metric = ExerciseMetric.DISTANCE_TIME, category = "Cardio")

    @Test
    fun `the chips are what the movement offers, set or not`() {
        val chips = modifierChips(applicableModifiers(pullUp), "archer")
        assertEquals(applicableModifiers(pullUp), chips.map { it.token })
        assertEquals(listOf("archer"), chips.filter { it.selected }.map { it.token })
        assertTrue(chips.all { it.flag == ModifierFlag.NONE })
    }

    @Test
    fun `weighted has no chip and is never flagged`() {
        val chips = modifierChips(applicableModifiers(pullUp), "weighted, tempo")
        assertFalse(chips.any { it.token == "weighted" })
        assertEquals(listOf("tempo"), chips.filter { it.selected }.map { it.token })
    }

    @Test
    fun `a legacy or mistyped token is shown set, flagged unknown, and removable`() {
        val chips = modifierChips(applicableModifiers(bench), "tempo, defecit, banded")
        val unknown = chips.filter { it.flag == ModifierFlag.UNKNOWN }
        assertEquals(listOf("defecit", "banded"), unknown.map { it.token })
        assertTrue(unknown.all { it.selected })
        // Never dropped: still on the entry until the lifter taps it off.
        assertEquals("tempo, banded", toggleModifier("tempo, defecit, banded", "defecit"))
        assertEquals("tempo", toggleModifier(toggleModifier("tempo, defecit, banded", "defecit"), "banded"))
    }

    @Test
    fun `a known modifier that does not fit the movement is flagged as not applicable`() {
        val chips = modifierChips(applicableModifiers(bench), "deficit, paused")
        val flagged = chips.single { it.flag != ModifierFlag.NONE }
        assertEquals("deficit", flagged.token)
        assertEquals(ModifierFlag.NOT_APPLICABLE, flagged.flag)
        assertTrue(flagged.selected)
        assertEquals(ModifierFlag.NONE, chips.single { it.token == "paused" }.flag)
    }

    @Test
    fun `a movement that offers nothing shows only what is set on it`() {
        assertEquals(emptyList<ModifierChip>(), modifierChips(applicableModifiers(plank), ""))
        assertEquals(emptyList<ModifierChip>(), modifierChips(applicableModifiers(run), "weighted"))
        assertEquals(listOf(ModifierChip("deficit", true, ModifierFlag.NOT_APPLICABLE)), modifierChips(applicableModifiers(plank), "deficit"))
    }

    @Test
    fun `toggling sets and clears one token and keeps the rest in order`() {
        assertEquals("tempo", toggleModifier("", "tempo"))
        assertEquals("weighted, tempo", toggleModifier("weighted", "tempo"))
        assertEquals("weighted", toggleModifier("weighted, tempo", "tempo"))
        assertEquals("weighted", toggleModifier("weighted, Tempo", "tempo"))
        assertEquals("", toggleModifier("tempo", "TEMPO"))
    }

    @Test
    fun `tokens are trimmed in order with a repeat dropped`() {
        assertEquals(listOf("weighted", "tempo"), modifierTokens(" weighted ,, tempo , Weighted"))
        assertEquals(emptyList<String>(), modifierTokens(null))
    }

    // ------------------------------------------------------------ weighted follows the load

    @Test
    fun `weighted joins a bodyweight lift that gains a load and leaves when it is cleared`() {
        assertEquals("weighted", modifiersFollowingLoad("", pullUp, loaded = true))
        assertEquals("tempo, weighted", modifiersFollowingLoad("tempo", pullUp, loaded = true))
        assertEquals("tempo", modifiersFollowingLoad("tempo, weighted", pullUp, loaded = false))
        assertEquals("", modifiersFollowingLoad("weighted", pullUp, loaded = false))
        // Already right: untouched.
        assertEquals("weighted, archer", modifiersFollowingLoad("weighted, archer", pullUp, loaded = true))
    }

    @Test
    fun `a barbell lift is never given weighted and a stale one goes when the load is empty`() {
        assertEquals("", modifiersFollowingLoad("", bench, loaded = true))
        assertEquals("paused", modifiersFollowingLoad("paused", bench, loaded = true))
        assertEquals("paused", modifiersFollowingLoad("weighted, paused", bench, loaded = false))
    }

    @Test
    fun `an activity carries no weighted tag`() {
        assertEquals("", modifiersFollowingLoad("weighted", run, loaded = true))
        assertEquals("", modifiersFollowingLoad("", run, loaded = true))
    }

    @Test
    fun `an unknown exercise is not given weighted`() {
        assertEquals("", modifiersFollowingLoad("", null, loaded = true))
    }
}
