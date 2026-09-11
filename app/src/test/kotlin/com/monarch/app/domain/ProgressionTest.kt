package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionTest {

    @Test
    fun `no history returns target prescription`() {
        val rec = Progression.next(TrainingMode.HYPERTROPHY, targetReps = 10, setsDone = 0, minSets = 3, allSetsAtTarget = false, lastWeightKg = 20.0, lastReps = null)
        assertEquals(10, rec.reps)
        assertEquals("First attempt — hit 10 reps every set", rec.reason)
    }

    @Test
    fun `strength mode adds load and drops reps when cleared`() {
        val sets = List(5) { Progression.Attempt(20.0, 5) }
        val rec = Progression.fromSets(TrainingMode.STRENGTH, targetReps = 5, minSets = 5, sets)
        assertEquals(22.5, rec.weightKg!!, 0.0001)
        assertEquals(3, rec.reps)
        assertEquals("Target cleared — add 2.5 kg, drop to 3 reps", rec.reason)
    }

    @Test
    fun `strength mode floors reps at three`() {
        val sets = List(5) { Progression.Attempt(20.0, 3) }
        val rec = Progression.fromSets(TrainingMode.STRENGTH, targetReps = 3, minSets = 5, sets)
        assertEquals(3, rec.reps)
    }

    @Test
    fun `hypertrophy mode climbs reps before load`() {
        val sets = List(3) { Progression.Attempt(20.0, 10) }
        val rec = Progression.fromSets(TrainingMode.HYPERTROPHY, targetReps = 10, minSets = 3, sets)
        assertEquals(20.0, rec.weightKg!!, 0.0001)
        assertEquals(12, rec.reps)
        assertEquals("Same load — climb to 12 reps", rec.reason)
    }

    @Test
    fun `hypertrophy mode resets reps after ceiling`() {
        val sets = List(3) { Progression.Attempt(20.0, 14) }
        val rec = Progression.fromSets(TrainingMode.HYPERTROPHY, targetReps = 10, minSets = 3, sets)
        assertEquals(22.5, rec.weightKg!!, 0.0001)
        assertEquals(10, rec.reps)
        assertEquals("Rep ceiling reached — add 2.5 kg, back to 10 reps", rec.reason)
    }

    @Test
    fun `missed targets repeat the same prescription`() {
        val sets = listOf(Progression.Attempt(20.0, 10), Progression.Attempt(20.0, 6))
        val rec = Progression.fromSets(TrainingMode.STRENGTH, targetReps = 5, minSets = 5, sets)
        assertEquals(20.0, rec.weightKg!!, 0.0001)
        assertEquals(5, rec.reps)
    }

    @Test
    fun `too few sets repeats too`() {
        val sets = List(2) { Progression.Attempt(20.0, 5) }
        val rec = Progression.fromSets(TrainingMode.STRENGTH, targetReps = 5, minSets = 5, sets)
        assertEquals(20.0, rec.weightKg!!, 0.0001)
    }

    @Test
    fun `leg movements take the five kilo step`() {
        val sets = List(4) { Progression.Attempt(24.0, 6) }
        val rec = Progression.fromSets(
            TrainingMode.STRENGTH, targetReps = 6, minSets = 4, sets,
            muscleGroup = "LEGS", exerciseName = "Weighted Pistol Squat",
        )
        assertEquals(29.0, rec.weightKg!!, 0.0001)
    }

    @Test
    fun `isolation movements climb before load`() {
        val sets = List(3) { Progression.Attempt(10.0, 15) }
        val rec = Progression.fromSets(
            TrainingMode.HYPERTROPHY, targetReps = 12, minSets = 3, sets,
            muscleGroup = "PULL", exerciseName = "Wrist Curl",
        )
        assertEquals(10.0, rec.weightKg!!, 0.0001)
        assertEquals(16, rec.reps)
    }

    @Test
    fun `isolation ceiling reset uses small step`() {
        val sets = List(3) { Progression.Attempt(10.0, 16) }
        val rec = Progression.fromSets(
            TrainingMode.HYPERTROPHY, targetReps = 12, minSets = 3, sets,
            muscleGroup = "PULL", exerciseName = "Wrist Curl",
        )
        assertEquals(11.25, rec.weightKg!!, 0.0001)
        assertEquals(12, rec.reps)
    }

    @Test
    fun `load step scale rules`() {
        assertEquals(5.0, Progression.weightStepKg("LEGS", "Back Squat"), 0.0001)
        assertEquals(2.5, Progression.weightStepKg("PUSH", "Overhead Press"), 0.0001)
        assertEquals(2.5, Progression.weightStepKg("PULL", "Weighted Pull-up"), 0.0001)
        assertEquals(1.25, Progression.weightStepKg("PULL", "Wrist Curl"), 0.0001)
        assertEquals(1.25, Progression.weightStepKg("LEGS", "Single-Leg Calf Raise"), 0.0001)
    }
}
