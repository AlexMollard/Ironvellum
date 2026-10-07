package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import com.ironvellum.app.domain.Progression.Lever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        val sets = List(4) { Progression.Attempt(25.0, 6) }
        val rec = Progression.fromSets(
            TrainingMode.STRENGTH, targetReps = 6, minSets = 4, sets,
            muscleGroup = "LEGS", exerciseName = "Weighted Pistol Squat",
        )
        assertEquals(30.0, rec.weightKg!!, 0.0001)
    }

    @Test
    fun `bodyweight work climbs reps instead of taking on load`() {
        val sets = List(5) { Progression.Attempt(null, 6) }
        val strength = Progression.fromSets(TrainingMode.STRENGTH, targetReps = 5, minSets = 5, sets)
        assertEquals(null, strength.weightKg)
        assertEquals(8, strength.reps)
        val hypertrophy = Progression.fromSets(TrainingMode.HYPERTROPHY, targetReps = 5, minSets = 5, List(5) { Progression.Attempt(null, 9) })
        assertEquals(null, hypertrophy.weightKg)
        assertEquals(11, hypertrophy.reps)
    }

    @Test
    fun `an off-grid load steps onto a weight a rack actually holds`() {
        val sets = List(3) { Progression.Attempt(16.0, 5) }
        val rec = Progression.fromSets(
            TrainingMode.STRENGTH, targetReps = 5, minSets = 3, sets,
            muscleGroup = "PUSH", exerciseName = "Dumbbell Shoulder Press",
        )
        assertEquals(17.5, rec.weightKg!!, 0.0001)
        assertEquals("Target cleared — add 1.5 kg, drop to 3 reps", rec.reason)
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

    @Test
    fun `only a moved prescription is marked changed`() {
        val failed = List(3) { Progression.Attempt(80.0, 3) }
        // First attempt and a repeat have nothing to explain.
        assertEquals(false, Progression.next(TrainingMode.STRENGTH, 5, 0, 3, false, 20.0, null).changed)
        assertEquals(false, Progression.fromSessions(TrainingMode.STRENGTH, 5, 3, listOf(failed)).changed)
        // Cleared work adds load; three failures at one load back it off.
        assertEquals(true, Progression.fromSets(TrainingMode.STRENGTH, 5, 5, List(5) { Progression.Attempt(20.0, 5) }).changed)
        val deload = Progression.fromSessions(TrainingMode.STRENGTH, 5, 3, listOf(failed, failed, failed))
        assertEquals(true, deload.changed)
        assertEquals(true, deload.deload)
    }

    // ------------------------------------------------------------ levers

    private fun cleared(times: Int, weight: Double? = null, reps: Int = 10, sets: Int = 3): List<List<Progression.Attempt>> =
        List(times) { List(sets) { Progression.Attempt(weight, reps) } }

    @Test
    fun `an ab wheel cleared three times cycles range, tempo and reps and never grows reps or load`() {
        val reasons = (1..3).map { n ->
            val rec = Progression.fromSessions(
                TrainingMode.HYPERTROPHY, targetReps = 10, minSets = 3, sessions = cleared(n),
                exerciseName = "Ab Wheel Rollout", lever = Lever.CONTROL,
            )
            assertEquals(null, rec.weightKg)
            assertEquals(10, rec.reps)
            assertTrue(rec.changed)
            rec.reason
        }
        assertEquals(
            listOf(
                "Trial cleared — reach a little further before adding reps",
                "Trial cleared — slow the way out to 3 s",
                "Trial cleared — add 2 reps, same range and pace",
            ),
            reasons,
        )
        // The cycle starts over.
        val fourth = Progression.fromSessions(
            TrainingMode.HYPERTROPHY, 10, 3, cleared(4), exerciseName = "Ab Wheel Rollout", lever = Lever.CONTROL,
        )
        assertEquals(reasons[0], fourth.reason)
    }

    @Test
    fun `dorsiflexion never asks for more reps or load`() {
        for (n in 1..3) {
            val rec = Progression.fromSessions(
                TrainingMode.HYPERTROPHY, 10, 3, cleared(n), exerciseName = "Knee-to-Wall Dorsiflexion", lever = Lever.MOBILITY,
            )
            assertEquals(null, rec.weightKg)
            assertEquals(10, rec.reps)
            assertFalse(rec.reason, rec.reason.contains("reps") || rec.reason.contains("kg"))
        }
    }

    @Test
    fun `an unloaded hold progresses on seconds, and a weighted plank deload never quotes reps`() {
        // 3 sets of 45 s, logged as durationSec with reps 0.
        val held = List(3) { Progression.attemptOf(null, reps = 0, durationSec = 45, hold = true) }
        val rec = Progression.fromSessions(
            TrainingMode.STRENGTH, targetReps = 45, minSets = 3, sessions = listOf(held),
            exerciseName = "Plank", lever = Lever.HOLD,
        )
        assertEquals(50, rec.reps)
        assertEquals(null, rec.weightKg)
        assertEquals("Trial cleared — hold 5 s longer next trial", rec.reason)
        // Read as reps (the old defect) the same sets never cleared.
        val asReps = List(3) { Progression.attemptOf(null, reps = 0, durationSec = 45, hold = false) }
        assertFalse(Progression.fromSessions(TrainingMode.STRENGTH, 45, 3, listOf(asReps), lever = Lever.HOLD).changed)
        // A legacy archive keeps the seconds in reps.
        assertEquals(45, Progression.attemptOf(null, reps = 45, durationSec = null, hold = true).reps)
        // A loaded hold that keeps failing: the deload text quotes seconds as seconds.
        val failed = List(3) { Progression.attemptOf(10.0, reps = 0, durationSec = 30, hold = true) }
        val deload = Progression.fromSessions(
            TrainingMode.STRENGTH, 45, 3, listOf(failed, failed, failed), exerciseName = "Weighted Plank", lever = Lever.HOLD,
        )
        assertTrue(deload.deload)
        assertTrue(deload.reason, deload.reason.contains("45 s"))
        assertFalse(deload.reason, deload.reason.contains("reps"))
    }

    @Test
    fun `levers resolve from the movement`() {
        fun lever(name: String, weighted: Boolean = false, metric: ExerciseMetric = ExerciseMetric.REPS) =
            Progression.leverFor(name, weighted, metric)
        assertEquals(Lever.CONTROL, lever("Ab Wheel Rollout"))
        assertEquals(Lever.MOBILITY, lever("Knee-to-Wall Dorsiflexion"))
        assertEquals(Lever.HOLD, lever("Plank", metric = ExerciseMetric.HOLD))
        assertEquals(Lever.LOAD, lever("Bench Press", weighted = true))
        assertEquals(Lever.REPS_THEN_LOAD, lever("Pull-up"))
        assertEquals(Lever.SKILL, lever("Handstand Push-up"))
        // The owner's edge cases.
        assertEquals(Lever.REPS_THEN_LOAD, lever("Hanging Leg Raise"))
        assertEquals(Lever.CONTROL, lever("Cossack Squat"))
        assertEquals(Lever.CONTROL, lever("Nordic Curl"))
        // An unmapped movement follows its weighted flag.
        assertEquals(Lever.LOAD, lever("My Own Lift", weighted = true))
        assertEquals(Lever.REPS_THEN_LOAD, lever("My Own Lift"))
    }

    @Test
    fun `the default lever keeps today's bodyweight copy`() {
        val rec = Progression.fromSets(TrainingMode.STRENGTH, 5, 5, List(5) { Progression.Attempt(null, 6) })
        assertEquals("Target cleared — climb to 8 reps", rec.reason)
        assertEquals(8, rec.reps)
        // The same sets with no lever named climb past the cap; REPS_THEN_LOAD stops there.
        val high = List(5) { Progression.Attempt(null, 14) }
        assertEquals(16, Progression.fromSets(TrainingMode.STRENGTH, 5, 5, high).reps)
        assertEquals(15, Progression.fromSets(TrainingMode.STRENGTH, 5, 5, high, lever = Lever.REPS_THEN_LOAD).reps)
    }

    @Test
    fun `bodyweight reps stop at the ceiling and point to load or a harder variation`() {
        val rec = Progression.fromSets(
            TrainingMode.HYPERTROPHY, 10, 3, List(3) { Progression.Attempt(null, 15) }, lever = Lever.REPS_THEN_LOAD,
        )
        assertEquals(null, rec.weightKg)
        assertEquals(15, rec.reps)
        assertEquals("Reps are high enough — add 2.5 kg or take a harder variation", rec.reason)
        assertTrue(rec.changed)
    }

    @Test
    fun `every catalogue row resolves to a lever and the lever name sets are real movements`() {
        for (e in Seed.exercises) {
            val metric = ExerciseMetric.valueOf(e.metric)
            val lever = Progression.leverFor(e.name, e.isWeighted, metric)
            if (metric == ExerciseMetric.HOLD) assertEquals(e.name, Lever.HOLD, lever)
        }
        assertTrue(MuscleMap.controlNames.all { it in MuscleMap.keys })
        assertTrue(MuscleMap.mobilityNames.all { it in MuscleMap.keys })
        assertTrue(MuscleMap.controlNames.intersect(MuscleMap.mobilityNames).isEmpty())
    }
}
