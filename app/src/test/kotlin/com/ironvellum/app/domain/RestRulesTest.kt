package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The per-movement rest windows: category derivation, the overrides, and how a mode picks a column. */
class RestRulesTest {

    private fun w(ready: Int, max: Int) = RestRules.Window(ready, max)

    @Test
    fun `pinned windows for the movements the owner named`() {
        // Ab wheel: loaded anti-extension core, not a dynamic core move.
        assertEquals(w(60, 90), RestRules.window("Ab Wheel Rollout", TrainingFocus.MUSCLE))
        assertEquals(w(75, 120), RestRules.window("Ab Wheel Rollout", TrainingFocus.STRENGTH))
        // Back squat in both modes.
        assertEquals(w(120, 180), RestRules.window("Back Squat", TrainingFocus.MUSCLE))
        assertEquals(w(180, 300), RestRules.window("Back Squat", TrainingFocus.STRENGTH))
        // Lateral raise: small-muscle isolation.
        assertEquals(w(45, 75), RestRules.window("Lateral Raise", TrainingFocus.MUSCLE))
        assertEquals(w(60, 90), RestRules.window("Lateral Raise", TrainingFocus.STRENGTH))
        // Dorsiflexion: mobility, the same either way.
        assertEquals(w(15, 30), RestRules.window("Knee-to-Wall Dorsiflexion", TrainingFocus.MUSCLE))
        assertEquals(w(15, 30), RestRules.window("Knee-to-Wall Dorsiflexion", TrainingFocus.STRENGTH))
        // Plank: a static hold, not the dynamic core its pattern says (hold is checked before compound).
        assertEquals(w(45, 75), RestRules.window("Plank", TrainingFocus.MUSCLE))
        assertEquals(w(60, 90), RestRules.window("Plank", TrainingFocus.STRENGTH))
    }

    @Test
    fun `a hypertrophy rite on a strength profile rests like a hypertrophy day`() {
        // The lifter's profile mode is HYPERTROPHY though the last program asked for strength.
        val focus = SessionClock.focusFor(TrainingFocus.STRENGTH, TrainingMode.HYPERTROPHY)
        assertEquals(RestRules.window("Back Squat", TrainingFocus.MUSCLE), RestRules.window("Back Squat", focus))
        assertEquals(w(120, 180), RestRules.window("Back Squat", focus))
        // And the estimate prices the same ready time.
        assertEquals(120 + ProgramRules.SET_WORK_SECONDS, ProgramRules.setSeconds(focus, "Back Squat"))
    }

    @Test
    fun `focus without a mode picks the column, GENERAL the midpoint and SKILL by category`() {
        assertEquals(w(150, 240), RestRules.window("Back Squat", TrainingFocus.GENERAL))
        // SKILL: the strength column for a skill static, the muscle column for anything else.
        assertEquals(RestRules.SKILL_STATIC.strength, RestRules.window("Front Lever", TrainingFocus.SKILL))
        assertEquals(RestRules.SKILL_DYNAMIC.strength, RestRules.window("Muscle-up", TrainingFocus.SKILL))
        assertEquals(RestRules.LOADED_COMPOUND.muscle, RestRules.window("Dumbbell Bench Press", TrainingFocus.SKILL))
    }

    @Test
    fun `categories derive from the catalogue fields`() {
        assertSame(RestRules.HEAVY_LOWER, RestRules.windowsFor("Deadlift"))
        assertSame(RestRules.HEAVY_UPPER, RestRules.windowsFor("Bench Press"))
        assertSame(RestRules.HEAVY_UPPER, RestRules.windowsFor("Weighted Pull-up"))
        assertSame(RestRules.LOADED_COMPOUND, RestRules.windowsFor("Dumbbell Bench Press"))
        assertSame(RestRules.LOADED_COMPOUND, RestRules.windowsFor("Lat Pulldown"))
        assertSame(RestRules.LOADED_COMPOUND, RestRules.windowsFor("Assisted Pull-up"))
        assertSame(RestRules.BODYWEIGHT_COMPOUND, RestRules.windowsFor("Push-up"))
        assertSame(RestRules.BODYWEIGHT_COMPOUND, RestRules.windowsFor("Pull-up"))
        assertSame(RestRules.UNILATERAL_COMPOUND, RestRules.windowsFor("Bulgarian Split Squat"))
        assertSame(RestRules.ISOLATION_LOWER, RestRules.windowsFor("Leg Extension"))
        assertSame(RestRules.CORE_DYNAMIC, RestRules.windowsFor("Hanging Leg Raise"))
        assertSame(RestRules.SKILL_STATIC, RestRules.windowsFor("L-Sit"))
        assertSame(RestRules.SKILL_DYNAMIC, RestRules.windowsFor("Muscle-up"))
        assertSame(RestRules.MOBILITY, RestRules.windowsFor("Front Split"))
        assertSame(RestRules.NONE, RestRules.windowsFor("Running"))
        assertTrue(RestRules.window("Running", TrainingFocus.STRENGTH).isNone)
    }

    @Test
    fun `an unprofiled movement is a loaded compound unless known to carry no load`() {
        assertSame(RestRules.LOADED_COMPOUND, RestRules.windowsFor("My Own Lift"))
        assertSame(RestRules.LOADED_COMPOUND, RestRules.windowsFor("My Own Lift", weighted = true))
        assertSame(RestRules.BODYWEIGHT_COMPOUND, RestRules.windowsFor("My Own Lift", weighted = false))
        assertSame(RestRules.HOLD_STATIC, RestRules.windowsFor("My Own Hang", ExerciseMetric.HOLD))
        assertSame(RestRules.NONE, RestRules.windowsFor("My Own Run", ExerciseMetric.DURATION))
        // No longer the old 300 s heavy-compound rest.
        assertEquals(120, RestRules.window("My Own Lift", TrainingFocus.STRENGTH).ready)
    }

    @Test
    fun `every profiled movement has a window and the overrides name real movements`() {
        for (name in MuscleMap.keys) {
            for (focus in TrainingFocus.entries) {
                val window = RestRules.window(name, focus)
                assertTrue("$name $focus", window.ready >= 0 && window.ready <= window.max)
            }
        }
        for (name in RestRules.overrideNames) assertTrue("$name is not profiled", name in MuscleMap.keys)
    }
}
