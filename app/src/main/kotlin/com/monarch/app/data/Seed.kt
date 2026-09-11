package com.monarch.app.data

import com.monarch.app.data.db.ExerciseEntity
import com.monarch.app.data.db.PresetEntity
import com.monarch.app.domain.MuscleGroup
import com.monarch.app.domain.ExerciseMetric
import com.monarch.app.domain.Skills

/** Static catalog and preset seeds; runs once on an empty database. */
object Seed {

    private val baseExercises: List<ExerciseEntity> = listOf(
        // Pull
        ExerciseEntity(name = "Pull-up", muscleGroup = MuscleGroup.PULL.name, isWeighted = false),
        ExerciseEntity(name = "Chin-up", muscleGroup = MuscleGroup.PULL.name, isWeighted = false),
        ExerciseEntity(name = "Archer Pull-up", muscleGroup = MuscleGroup.PULL.name, isWeighted = false),
        ExerciseEntity(name = "Inverted Row", muscleGroup = MuscleGroup.PULL.name, isWeighted = false),
        ExerciseEntity(name = "Door Sheet Row", muscleGroup = MuscleGroup.PULL.name, isWeighted = false),
        ExerciseEntity(name = "Active Bar Hang", muscleGroup = MuscleGroup.PULL.name, isWeighted = false),
        ExerciseEntity(name = "Wrist Curl", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Bicep Curl", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        // Push
        ExerciseEntity(name = "Dip", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Push-up", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Archer Push-up", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Diamond Push-up", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Pike Push-up", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Handstand Push-up", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Overhead Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        // Legs
        ExerciseEntity(name = "Pistol Squat", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        ExerciseEntity(name = "Back Squat", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Bulgarian Split Squat", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Single-Leg Glute Bridge", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        ExerciseEntity(name = "Single-Leg Calf Raise", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        ExerciseEntity(name = "Nordic Curl", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        ExerciseEntity(name = "Knee-to-Wall Dorsiflexion", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        ExerciseEntity(name = "Glute Bridge", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        // Core
        ExerciseEntity(name = "Hanging Leg Raise", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),
        ExerciseEntity(name = "Hanging Knee Raise", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),
        ExerciseEntity(name = "Ab Wheel Rollout", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),
        ExerciseEntity(name = "L-sit", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),
        ExerciseEntity(name = "Dragon Flag", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),
        ExerciseEntity(name = "Weighted Plank", muscleGroup = MuscleGroup.CORE.name, isWeighted = true),
    )

    /**
     * Every skill-tree movement is also a loggable exercise, derived from the
     * single source of truth so the catalog and the tree can never diverge.
     */
    private fun skillGroup(def: Skills.SkillDef): MuscleGroup = when {
        def.line == "Pull" || def.line == "Lever" ||
            def.line == "Rings" || def.line == "Movement" -> MuscleGroup.PULL
        def.line == "Push" || def.line == "Handstand" || def.line == "Planche" -> MuscleGroup.PUSH
        def.line == "Legs" -> MuscleGroup.LEGS
        def.line == "Core" || def.line == "Mobility" -> MuscleGroup.CORE
        def.name.contains("Squat") || def.name.contains("Curl") -> MuscleGroup.LEGS
        else -> MuscleGroup.CORE
    }


    /**
     * Activity/sport catalogue. Metric picks how a set is measured; muscleGroup
     * carries a coarse bucket so the existing ORDER BY and journal grouping still
     * work. Repository.ensureSeeded inserts any missing by name, so existing v12
     * installs pick these up on upgrade without touching their history.
     */
    private fun activity(
        name: String,
        group: String,
        metric: ExerciseMetric,
        category: String,
        weighted: Boolean = false,
    ) = ExerciseEntity(
        name = name,
        muscleGroup = group,
        isWeighted = weighted,
        metric = metric.name,
        category = category,
    )

    val activities: List<ExerciseEntity> = listOf(
        // Cardio — distance/timed
        activity("Running", "CARDIO", ExerciseMetric.DISTANCE_TIME, "Cardio"),
        activity("Trail Running", "CARDIO", ExerciseMetric.DISTANCE_TIME, "Cardio"),
        activity("Cycling", "CARDIO", ExerciseMetric.DISTANCE_TIME, "Cardio"),
        activity("Rowing", "CARDIO", ExerciseMetric.DISTANCE_TIME, "Cardio"),
        activity("Hiking", "CARDIO", ExerciseMetric.DISTANCE_TIME, "Cardio"),
        activity("Walking", "CARDIO", ExerciseMetric.DISTANCE_TIME, "Cardio"),
        // Cardio — duration only
        activity("Skipping", "CARDIO", ExerciseMetric.DURATION, "Cardio"),
        activity("Weighted Skipping", "CARDIO", ExerciseMetric.DURATION, "Cardio", weighted = true),
        activity("Jump Rope Intervals", "CARDIO", ExerciseMetric.DURATION, "Cardio"),
        activity("Stair Climbing", "CARDIO", ExerciseMetric.DURATION, "Cardio"),
        activity("Elliptical", "CARDIO", ExerciseMetric.DURATION, "Cardio"),
        activity("Assault Bike", "CARDIO", ExerciseMetric.DURATION, "Cardio"),
        // Water
        activity("Swimming", "WATER", ExerciseMetric.DISTANCE_TIME, "Water"),
        activity("Water Polo", "WATER", ExerciseMetric.DURATION, "Water"),
        // Climbing — attempts + grade text
        activity("Bouldering", "CLIMBING", ExerciseMetric.ATTEMPTS_GRADE, "Climbing"),
        activity("Sport Climbing", "CLIMBING", ExerciseMetric.ATTEMPTS_GRADE, "Climbing"),
        activity("Top Rope", "CLIMBING", ExerciseMetric.ATTEMPTS_GRADE, "Climbing"),
        // Sports
        activity("Football (Soccer)", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Basketball", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Tennis", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Badminton", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Squash", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Cricket", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Rugby", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Volleyball", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Table Tennis", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Golf", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Boxing", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Kickboxing", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Brazilian Jiu-Jitsu", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Wrestling", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Judo", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Karate", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Skateboarding", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Surfing", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Snowboarding", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Skiing", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Dancing", "SPORT", ExerciseMetric.DURATION, "Sport"),
        activity("Martial Arts Class", "SPORT", ExerciseMetric.DURATION, "Sport"),
        // Mobility
        activity("Yoga", "MOBILITY", ExerciseMetric.DURATION, "Mobility"),
        activity("Pilates", "MOBILITY", ExerciseMetric.DURATION, "Mobility"),
        activity("Stretching", "MOBILITY", ExerciseMetric.DURATION, "Mobility"),
        activity("Mobility Flow", "MOBILITY", ExerciseMetric.DURATION, "Mobility"),
    )

    val exercises: List<ExerciseEntity>
        get() = allExercises

    private val allExercises: List<ExerciseEntity> = baseExercises +
        Skills.ALL
            .filterNot { skill -> baseExercises.any { it.name == skill.name } }
            .map { skill ->
                ExerciseEntity(
                    name = skill.name,
                    muscleGroup = skillGroup(skill).name,
                    isWeighted = skill.name.contains("Weighted"),
                )
            } +
        activities

    data class SeedEntry(
        val exercise: String,
        val sets: Int,
        val reps: Int,
        val weightKg: Double? = null,
        val modifiers: String = "",
    )

    data class PresetSpec(
        val name: String,
        val note: String,
        val scheduledDay: Int?,
        val entries: List<SeedEntry>,
    )

    /** The user's real four-day split, pre-loaded as the quest board. ISO days: Mon=1 .. Sun=7. */
    val presets: List<PresetSpec> = listOf(
        PresetSpec(
            name = "Heavy Pull",
            note = "Monday. Low reps, maximal load. Rise.",
            scheduledDay = 1,
            entries = listOf(
                SeedEntry("Pull-up", 5, 5, 10.0, "weighted"),
                SeedEntry("Chin-up", 4, 5, 10.0, "weighted"),
                SeedEntry("Door Sheet Row", 4, 10),
                SeedEntry("Active Bar Hang", 3, 30, null, "hold seconds"),
                SeedEntry("Ab Wheel Rollout", 3, 10),
                SeedEntry("Wrist Curl", 3, 15, 10.0, "weighted"),
            ),
        ),
        PresetSpec(
            name = "Legs",
            note = "Tuesday. Single-leg strength and durable joints.",
            scheduledDay = 2,
            entries = listOf(
                SeedEntry("Knee-to-Wall Dorsiflexion", 3, 15),
                SeedEntry("Pistol Squat", 4, 6, 8.0, "weighted"),
                SeedEntry("Single-Leg Glute Bridge", 3, 10, 10.0, "weighted"),
                SeedEntry("Single-Leg Calf Raise", 4, 12, 16.0, "weighted"),
                SeedEntry("Hanging Leg Raise", 3, 12),
            ),
        ),
        PresetSpec(
            name = "Volume Pull",
            note = "Thursday. Higher volume, lower load. Endure.",
            scheduledDay = 4,
            entries = listOf(
                SeedEntry("Pull-up", 5, 10),
                SeedEntry("Chin-up", 4, 10),
                SeedEntry("Door Sheet Row", 4, 15),
                SeedEntry("Active Bar Hang", 3, 20, null, "hold seconds"),
                SeedEntry("Inverted Row", 3, 15),
            ),
        ),
        PresetSpec(
            name = "Push",
            note = "Friday. Vertical power first, then the arrows.",
            scheduledDay = 5,
            entries = listOf(
                SeedEntry("Handstand Push-up", 4, 6),
                SeedEntry("Archer Push-up", 4, 8, null, "weighted, deficit, elevated"),
                SeedEntry("Push-up", 3, 15, null, "deficit, elevated"),
                SeedEntry("Dip", 4, 8, 10.0, "weighted"),
                SeedEntry("Overhead Press", 3, 10, 25.0),
            ),
        ),
    )

    fun presetEntities(): List<PresetEntity> =
        presets.map { PresetEntity(name = it.name, note = it.note, scheduledDay = it.scheduledDay) }
}
