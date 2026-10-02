package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The arithmetic and boundary rules of ProgramRules, each citing its evidence. */
class ProgramRulesTest {

    // ------------------------------------------------------------- volume

    @Test
    fun `weekly set targets follow the evidence tiers`() {
        fun bounds(r: ClosedFloatingPointRange<Double>) = Pair(r.start, r.endInclusive)
        assertEquals(Pair(8.0, 12.0), bounds(ProgramRules.weeklySetTarget(VolumeLevel.LOW, TrainingFocus.MUSCLE)))
        assertEquals(Pair(12.0, 18.0), bounds(ProgramRules.weeklySetTarget(VolumeLevel.STANDARD, TrainingFocus.MUSCLE)))
        assertEquals(Pair(15.0, 22.0), bounds(ProgramRules.weeklySetTarget(VolumeLevel.HIGH, TrainingFocus.MUSCLE)))
        // Strength saturates early (Pelland 2026; Ralston 2017): 5-15 direct.
        assertEquals(Pair(5.0, 15.0), bounds(ProgramRules.weeklySetTarget(VolumeLevel.HIGH, TrainingFocus.STRENGTH)))
    }

    @Test
    fun `a prioritised muscle on a strength week is judged up to the growth range top`() {
        val back = MuscleArea.BACK.muscles
        // Strength flattens at 15 sets (Pelland 2026), size keeps coming: a
        // prioritised lat is not "over" until the muscle range's top.
        assertEquals(5.0..18.0, ProgramRules.judgedRange(Muscle.LATS, VolumeLevel.STANDARD, TrainingFocus.STRENGTH, back))
        assertEquals(5.0..22.0, ProgramRules.judgedRange(Muscle.LATS, VolumeLevel.HIGH, TrainingFocus.STRENGTH, back))
        // Unprioritised muscles, and a muscle-focus week, keep their own range.
        assertEquals(5.0..15.0, ProgramRules.judgedRange(Muscle.QUADS, VolumeLevel.STANDARD, TrainingFocus.STRENGTH, back))
        assertEquals(12.0..18.0, ProgramRules.judgedRange(Muscle.LATS, VolumeLevel.STANDARD, TrainingFocus.MUSCLE, back))
        // A LOW muscle top (12) never lowers the strength ceiling.
        assertEquals(5.0..15.0, ProgramRules.judgedRange(Muscle.LATS, VolumeLevel.LOW, TrainingFocus.STRENGTH, back))
        // Helpers keep their floor, prioritised or not.
        assertEquals(ProgramRules.HELPER_RANGE, ProgramRules.judgedRange(Muscle.TRAPS, VolumeLevel.STANDARD, TrainingFocus.STRENGTH, back))
    }

    @Test
    fun `tracked muscles are the major set and front delts are absent`() {
        // Every press already serves the front delts (Lanza 2024); a target
        // of their own would just over-press people.
        assertFalse(Muscle.FRONT_DELTS in ProgramRules.TRACKED)
        assertTrue(Muscle.MID_CHEST in ProgramRules.TRACKED)
        assertTrue(Muscle.HAMSTRINGS in ProgramRules.TRACKED)
        assertTrue(Muscle.SIDE_DELTS in ProgramRules.TRACKED)
    }

    @Test
    fun `the neck is a muscle with no weekly target`() {
        // It is on the figure, but no catalogue movement trains it and a target would have the
        // generator chase it. Not persisted, so the label is all there is to round-trip.
        assertFalse(Muscle.NECK in ProgramRules.TRACKED)
        assertFalse(Muscle.NECK in ProgramRules.HELPERS)
        assertEquals("Neck", Muscle.NECK.label)
        assertEquals(Muscle.NECK, Muscle.valueOf(Muscle.NECK.name))
    }

    @Test
    fun `weekly volume counts indirect sets fractionally`() {
        // Bench Press: chest 1.0, front delts 0.7, triceps 0.6, side delts 0.3.
        val week = listOf(
            PlannedPreset("Push", "", 1, listOf(
                PlannedEntry("Bench Press", 3, 8, null),
                PlannedEntry("Seated Leg Curl", 4, 12, null),
            )),
        )
        val volume = ProgramRules.weeklyVolume(week)
        assertEquals(3.0, volume[Muscle.MID_CHEST]!!, 1e-9)
        assertEquals(2.1, volume[Muscle.FRONT_DELTS]!!, 1e-9)
        assertEquals(1.8, volume[Muscle.TRICEPS]!!, 1e-9)
        assertEquals(0.9, volume[Muscle.SIDE_DELTS]!!, 1e-9)
        assertEquals(4.0, volume[Muscle.HAMSTRINGS]!!, 1e-9)
    }

    @Test
    fun `muscle credits name each exercise once and add up to the weekly volume`() {
        val week = listOf(
            PlannedPreset("Push A", "", 1, listOf(
                PlannedEntry("Bench Press", 3, 8, null),
                PlannedEntry("Push-up", 3, 12, null, modifiers = "Elevated"),
            )),
            PlannedPreset("Push B", "", 4, listOf(
                PlannedEntry("Bench Press", 2, 8, null),
                PlannedEntry("Push-up", 2, 12, null),
                PlannedEntry("Unprofiled Mystery Lift", 5, 5, null),
            )),
        )
        val volume = ProgramRules.weeklyVolume(week)
        val credits = ProgramRules.muscleCredits(week)
        // The breakdown is what the lifter reads under a muscle's total: it
        // must say the same number, muscle by muscle.
        assertEquals(volume.keys, credits.keys)
        volume.forEach { (muscle, sets) ->
            assertEquals(muscle.name, sets, credits.getValue(muscle).sumOf { it.credited }, 1e-9)
        }
        // Bench on two days is one line of 5 sets; the feet-up push-up is its
        // own line, because it works the upper chest in full.
        val upper = credits.getValue(Muscle.UPPER_CHEST)
        assertEquals(ProgramRules.MuscleCredit("Bench Press", "", 5, 0.5), upper.single { it.exerciseName == "Bench Press" })
        assertEquals(1.0, upper.single { it.modifiers == "Elevated" }.share, 1e-9)
        assertEquals(0.5, upper.single { it.exerciseName == "Push-up" && it.modifiers == "" }.share, 1e-9)
        // Largest contribution first.
        assertEquals(upper.sortedByDescending { it.credited }, upper)
    }

    // --------------------------------------------------------------- tier

    @Test
    fun `suggest tier boundaries sit at 365 and 1095 days`() {
        assertEquals(VolumeLevel.LOW, ProgramRules.suggestVolume(null, 1000L))
        assertEquals(VolumeLevel.LOW, ProgramRules.suggestVolume(0L, 364L))
        assertEquals(VolumeLevel.STANDARD, ProgramRules.suggestVolume(0L, 365L))
        assertEquals(VolumeLevel.STANDARD, ProgramRules.suggestVolume(0L, 1094L))
        assertEquals(VolumeLevel.HIGH, ProgramRules.suggestVolume(0L, 1095L))
    }

    // -------------------------------------------------------------- loads

    @Test
    fun `strength profile keeps the best capped e1rm per lift`() {
        val profile = ProgramRules.strengthProfile(
            listOf(
                LoggedLift("Bench Press", 75.0, 10), // e1RM 100.0
                LoggedLift("Bench Press", 60.0, 8), // e1RM 76 - loses
                LoggedLift("Bench Press", 200.0, 20), // 20 reps: beyond Epley validity
                LoggedLift("Back Squat", 0.0, 5), // no load: not a lift
                LoggedLift("back squat", 100.0, 5), // same lift, case-insensitive
            ),
        )
        assertEquals(100.0, profile.bestE1rmKg["bench press"]!!, 1e-9)
        assertEquals(116.67, profile.bestE1rmKg["back squat"]!!, 0.01)
    }

    @Test
    fun `working load applies the epley inversion and rounds DOWN to the step`() {
        val strength = ProgramRules.strengthProfile(listOf(LoggedLift("Bench Press", 75.0, 10)))
        // 100 * (1 - 10/30) = 66.67 -> floor to the 2.5 kg step, not round.
        val (load, note) = ProgramRules.workingLoadKg("Bench Press", "PUSH", strength, 8, 2)!!
        assertEquals(65.0, load, 1e-9)
        assertTrue(note.contains("100"))
        // Another one that would round UP if mis-rounded: 76.67 -> 75.0.
        val (load2, _) = ProgramRules.workingLoadKg("Bench Press", "PUSH", strength, 6, 1)!!
        assertEquals(75.0, load2, 1e-9)
    }

    @Test
    fun `an unlogged lift has no working load`() {
        val strength = ProgramRules.strengthProfile(listOf(LoggedLift("Bench Press", 75.0, 10)))
        assertNull(ProgramRules.workingLoadKg("Lat Pulldown", "PULL", strength, 10, 2))
    }

    @Test
    fun `related-lift estimates run in force units and say where they came from`() {
        // Barbell Row 60 kg x 10 -> e1RM 80 kg in FORCE units (free weight).
        val strength = ProgramRules.strengthProfile(listOf(LoggedLift("Barbell Row", 60.0, 10)))
        val (load, note) = ProgramRules.estimatedLoadKg("Seated Cable Row", "PULL", strength, 10, 2)!!
        // force: 80 * 0.90 ratio * 0.90 safety = 64.8; marked: / 0.85 stack = 76.2;
        // 10 reps at 2 RIR: 76.2 * (1 - 12/30) = 45.7 -> floor to the 5 kg stack step.
        assertEquals(45.0, load, 1e-9)
        assertEquals("estimate from Barbell Row", note)
    }

    @Test
    fun `a lift with no logged relative gets no estimate`() {
        val strength = ProgramRules.strengthProfile(listOf(LoggedLift("Bench Press", 75.0, 10)))
        assertNull(ProgramRules.estimatedLoadKg("Seated Leg Curl", "LEGS", strength, 12, 2))
    }

    // ---------------------------------------------------------------- rest

    @Test
    fun `rest respects the evidence thresholds`() {
        // Strength mains 3-5 min (Schoenfeld 2016), accessories never under
        // 90 s (Singer 2024).
        assertEquals(300, ProgramRules.restSeconds(TrainingFocus.STRENGTH, true))
        assertTrue(ProgramRules.restSeconds(TrainingFocus.MUSCLE, true) >= 90)
        assertTrue(ProgramRules.restSeconds(TrainingFocus.MUSCLE, false) >= 90)
    }

    // ----------------------------------------------------------------- sex

    @Test
    fun `the sex note cites the evidence and promises no structural change`() {
        assertTrue(ProgramRules.SEX_NOTE.contains("Roberts 2020"))
    }
}
