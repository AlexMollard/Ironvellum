package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sender must never hand out a code the app itself refuses: every cycle
 * the generator can produce, shared the way Repository.sharedRoutine shares
 * it, has to import back unchanged.
 */
class RoutineCodeRoundTripTest {

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    private val strength = ProgramRules.strengthProfile(
        listOf(LoggedLift("Bench Press", 75.0, 10), LoggedLift("Back Squat", 80.0, 10)),
    )

    private fun shared(plan: RoutinePlan) = plan.presets.map { preset ->
        RoutineCode.SharedWorkout(
            name = preset.name,
            note = Evidence.split(preset.note).first,
            scheduledDay = preset.scheduledDay,
            entries = preset.entries.map {
                RoutineCode.SharedEntry(it.exerciseName, it.sets, it.reps, it.targetWeightKg, it.modifiers)
            },
        )
    }

    @Test
    fun `every cycle the generator produces imports back unchanged`() {
        val equipment = listOf(Equipment.NOTHING, Equipment(fullGym = false, gear = Gear.entries.toSet()), Equipment.FULL_GYM)
        var checked = 0
        for (eq in equipment) for (focus in TrainingFocus.entries) for (volume in VolumeLevel.entries) for (days in 1..6) {
            val plan = ProgramGenerator.week(ProgramRequest(focus, volume, eq, days), catalogue, strength)
            if (plan.presets.isEmpty()) continue
            val workouts = shared(plan)
            val decoded = RoutineCode.decode(RoutineCode.encode(workouts))
            assertTrue("$focus/$volume/$days/$eq: ${decoded.exceptionOrNull()?.message}", decoded.isSuccess)
            assertEquals("$focus/$volume/$days", workouts, decoded.getOrThrow())
            checked++
        }
        assertTrue("the sweep ran", checked > 50)
    }
}