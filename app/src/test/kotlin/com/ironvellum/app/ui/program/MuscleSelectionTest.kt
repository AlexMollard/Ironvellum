package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.VolumeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one line under a figure on each screen, and what the coverage screen narrows to. */
class MuscleSelectionTest {

    @Test fun nameAloneWhenThereIsNoFact() {
        assertEquals("Lats", muscleLine(Muscle.LATS))
        assertEquals("Lats", muscleLine(Muscle.LATS, ""))
    }

    @Test fun exerciseSheetAndTechniqueSheetWording() {
        assertEquals("Triceps · assist in Bench Press", exerciseMuscleLine(Muscle.TRICEPS, 0.6, "Bench Press"))
        assertEquals("Lats · main in Pull-up", exerciseMuscleLine(Muscle.LATS, 1.0, "Pull-up"))
        assertEquals("Forearms · main in Dead Hang", exerciseMuscleLine(Muscle.FOREARMS, 0.7, "Dead Hang"))
        assertEquals("Glutes · not worked in Bench Press", exerciseMuscleLine(Muscle.GLUTES, null, "Bench Press"))
        assertEquals("Glutes · not worked in Bench Press", exerciseMuscleLine(Muscle.GLUTES, 0.0, "Bench Press"))
    }

    @Test fun riteSheetWording() {
        assertEquals("Lats · 4.5 sets in Pull A", riteMuscleLine(Muscle.LATS, 4.5, "Pull A"))
        assertEquals("Lats · 6 sets in Pull A", riteMuscleLine(Muscle.LATS, 6.0, "Pull A"))
        assertEquals("Lats · 1 set in Pull A", riteMuscleLine(Muscle.LATS, 1.0, "Pull A"))
        assertEquals("Quads · not worked in Pull A", riteMuscleLine(Muscle.QUADS, 0.0, "Pull A"))
    }

    @Test fun coverageWordingShowsTheTargetRange() {
        val goal = CoverageGoal(VolumeLevel.STANDARD, TrainingFocus.MUSCLE)
        assertEquals("Hamstrings · 6 sets this week · target 12–18", coverageMuscleLine(Muscle.HAMSTRINGS, 6.0, goal))
        assertEquals("Lats · 7.5 sets this week · target 12–18", coverageMuscleLine(Muscle.LATS, 7.5, goal))
        // Past the range it still reads as a count against a target, never "20 / 12".
        assertEquals("Lats · 20 sets this week · target 12–18", coverageMuscleLine(Muscle.LATS, 20.0, goal))
        // A helper reads against its own range.
        assertEquals("Forearms · 2 sets this week · target 3+", coverageMuscleLine(Muscle.FOREARMS, 2.0, goal))
    }

    @Test fun theNeckHasNoTargetSoItShowsJustItsSets() {
        val goal = CoverageGoal(VolumeLevel.STANDARD, TrainingFocus.MUSCLE)
        assertTrue(Muscle.NECK !in JUDGED)
        assertEquals("Neck · 3 sets this week", coverageMuscleLine(Muscle.NECK, 3.0, goal))
        assertEquals("Neck · 1 set this week", coverageMuscleLine(Muscle.NECK, 1.0, goal))
        assertEquals("Neck · 0 sets this week", coverageMuscleLine(Muscle.NECK, 0.0, goal))
    }

    // ------------------------------------------------------------------ filter

    private fun entry(name: String, sets: Int, modifiers: String = "") = PlannedEntry(name, sets, 8, null, modifiers)

    private val push = PlannedPreset("Push A", "", 1, listOf(entry("Bench Press", 4), entry("Pull-up", 3)))
    private val pull = PlannedPreset("Pull A", "", 3, listOf(entry("Pull-up", 5), entry("Bench Press", 2), entry("Bench Press", 1)))
    private val legs = PlannedPreset("Legs", "", 5, listOf(entry("Pull-up", 2)))

    private fun share(exercise: String, muscle: Muscle) = MuscleMap.profile(exercise)!!.muscles[muscle] ?: 0.0

    @Test fun theFilterKeepsOnlyRitesThatTrainTheMuscle() {
        assertEquals(0.0, share("Bench Press", Muscle.LATS), 0.0)
        assertTrue(share("Pull-up", Muscle.LATS) > 0.0)
        val lats = ritesTraining(listOf(push, pull, legs), Muscle.LATS)
        // Pull-ups: 5 sets in Pull A, 3 in Push A, 2 in Legs - the same share, so by sets.
        assertEquals(listOf("Pull A", "Push A", "Legs"), lats.map { it.rite })
        // Bench Press does not work the lats at all, so no rite lists it for them.
        assertTrue(lats.flatMap { it.exercises }.none { it.exerciseName == "Bench Press" })
        val benchOnly = ritesTraining(listOf(PlannedPreset("Bench day", "", 1, listOf(entry("Bench Press", 3)))), Muscle.LATS)
        assertTrue(benchOnly.isEmpty())
    }

    @Test fun eachRiteCreditsSetsTimesShareAndExercisesMergeWithinIt() {
        val triceps = ritesTraining(listOf(push, pull), Muscle.TRICEPS)
        val s = share("Bench Press", Muscle.TRICEPS)
        assertTrue(s > 0.0)
        val pullRite = triceps.single { it.rite == "Pull A" }
        // Two Bench Press entries in one rite are one credit of three sets.
        val bench = pullRite.exercises.single { it.exerciseName == "Bench Press" }
        assertEquals(3, bench.sets)
        assertEquals(3 * s, bench.credited, 1e-9)
        assertEquals(3 * s, pullRite.credited - pullRite.exercises.filter { it.exerciseName != "Bench Press" }.sumOf { it.credited }, 1e-9)
    }

    @Test fun ritesAndExercisesAreLargestFirstAndAddUpToTheWeeklyVolume() {
        val presets = listOf(push, pull, legs)
        for (muscle in listOf(Muscle.TRICEPS, Muscle.LATS, Muscle.BICEPS)) {
            val rites = ritesTraining(presets, muscle)
            assertEquals(rites.sortedByDescending { it.credited }, rites)
            rites.forEach { r -> assertEquals(r.exercises.sortedByDescending { it.credited }, r.exercises) }
            assertEquals(
                "$muscle total",
                ProgramRules.weeklyVolume(presets)[muscle] ?: 0.0,
                rites.sumOf { it.credited },
                1e-9,
            )
        }
    }

    @Test fun aMuscleNothingTrainsFiltersToNothing() {
        assertTrue(ritesTraining(listOf(push), Muscle.NECK).isEmpty())
        assertTrue(ritesTraining(emptyList(), Muscle.LATS).isEmpty())
    }
}
