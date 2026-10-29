package com.ironvellum.app.domain

import com.ironvellum.app.domain.ModifierEffect.Effect
import org.junit.Assert.assertEquals
import org.junit.Test

/** The arrow on a modifier chip: harder, easier or nothing, per exercise, from the existing scoring. */
class ModifierEffectTest {

    @Test
    fun `elevated on an inverted row is harder`() = assertEquals(Effect.HARDER, ModifierEffect.of("Inverted Row", "elevated"))

    @Test
    fun `incline on an inverted row is easier`() = assertEquals(Effect.EASIER, ModifierEffect.of("Inverted Row", "incline"))

    @Test
    fun `elevated on an australian pull-up is harder`() = assertEquals(Effect.HARDER, ModifierEffect.of("Australian Pull-up", "elevated"))

    @Test
    fun `the inverted row picker shows the up arrow on elevated`() {
        val row = Exercise(id = 6, name = "Inverted Row", muscleGroup = MuscleGroup.PULL, isWeighted = false)
        val chips = modifierChips(applicableModifiers(row), "elevated", "Inverted Row").associate { it.token to it.effect }
        assertEquals(Effect.HARDER, chips["elevated"])
        assertEquals(Effect.EASIER, chips["incline"])
    }

    @Test
    fun `incline on a push-up is easier`() = assertEquals(Effect.EASIER, ModifierEffect.of("Push-up", "incline"))

    @Test
    fun `elevated on a push-up is harder`() = assertEquals(Effect.HARDER, ModifierEffect.of("Push-up", "elevated"))

    @Test
    fun `assisted on a pull-up is easier`() = assertEquals(Effect.EASIER, ModifierEffect.of("Pull-up", "assisted"))

    @Test
    fun `archer on a pull-up is harder`() = assertEquals(Effect.HARDER, ModifierEffect.of("Pull-up", "archer"))

    @Test
    fun `elevated on a bulgarian split squat is harder`() = assertEquals(Effect.HARDER, ModifierEffect.of("Bulgarian Split Squat", "elevated"))

    @Test
    fun `weighted shows nothing because the load already counts`() = assertEquals(Effect.NONE, ModifierEffect.of("Pull-up", "weighted"))

    @Test
    fun `banded shows nothing on any exercise`() {
        listOf("Pull-up", "Push-up", "Bench Press", "Inverted Row").forEach { assertEquals(Effect.NONE, ModifierEffect.of(it, "banded")) }
    }

    @Test
    fun `an unknown token shows nothing`() {
        listOf("Pull-up", "Push-up", "Bench Press").forEach { assertEquals(Effect.NONE, ModifierEffect.of(it, "defecit")) }
    }

    @Test
    fun `case and spacing do not matter`() = assertEquals(Effect.HARDER, ModifierEffect.of("push-up", " Elevated "))

    @Test
    fun `chips carry the effect only when the exercise is named`() {
        val pushUp = Exercise(id = 5, name = "Push-up", muscleGroup = MuscleGroup.PUSH, isWeighted = false)
        val named = modifierChips(applicableModifiers(pushUp), "", "Push-up").associate { it.token to it.effect }
        assertEquals(Effect.EASIER, named["incline"])
        assertEquals(Effect.HARDER, named["elevated"])
        assertEquals(setOf(Effect.NONE), modifierChips(applicableModifiers(pushUp), "").map { it.effect }.toSet())
    }
}
