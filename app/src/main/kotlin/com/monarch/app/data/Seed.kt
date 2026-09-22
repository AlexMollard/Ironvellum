package com.monarch.app.data

import com.monarch.app.data.db.ExerciseEntity
import com.monarch.app.domain.MuscleGroup
import com.monarch.app.domain.ExerciseMetric
import com.monarch.app.domain.MovementDifficulty
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
        ExerciseEntity(name = "Barbell Row", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Dumbbell Row", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Lat Pulldown", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Face Pull", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        // Push
        ExerciseEntity(name = "Dip", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Push-up", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Archer Push-up", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Diamond Push-up", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Pike Push-up", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Handstand Push-up", muscleGroup = MuscleGroup.PUSH.name, isWeighted = false),
        ExerciseEntity(name = "Overhead Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Bench Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Incline Bench Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        // Legs
        ExerciseEntity(name = "Pistol Squat", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        ExerciseEntity(name = "Back Squat", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Bulgarian Split Squat", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Single-Leg Glute Bridge", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        ExerciseEntity(name = "Single-Leg Calf Raise", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        ExerciseEntity(name = "Nordic Curl", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        ExerciseEntity(name = "Knee-to-Wall Dorsiflexion", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        ExerciseEntity(name = "Glute Bridge", muscleGroup = MuscleGroup.LEGS.name, isWeighted = false),
        ExerciseEntity(name = "Deadlift", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Romanian Deadlift", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Front Squat", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Hip Thrust", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        // Core
        ExerciseEntity(name = "Hanging Leg Raise", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),
        ExerciseEntity(name = "Hanging Knee Raise", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),
        ExerciseEntity(name = "Ab Wheel Rollout", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),
        ExerciseEntity(name = "L-sit", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),
        ExerciseEntity(name = "Dragon Flag", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),
        ExerciseEntity(name = "Weighted Plank", muscleGroup = MuscleGroup.CORE.name, isWeighted = true),
        ExerciseEntity(name = "Plank", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),
        ExerciseEntity(name = "Side Plank", muscleGroup = MuscleGroup.CORE.name, isWeighted = false),

        // Gym floor - barbell. Free-weight band (no loadFactor entry).
        ExerciseEntity(name = "Close-Grip Bench Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Push Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Sumo Deadlift", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Rack Pull", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Pendlay Row", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "T-Bar Row", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Good Morning", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Barbell Lunge", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Barbell Step-Up", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Barbell Shrug", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        // Gym floor - dumbbell. Free-weight band.
        ExerciseEntity(name = "Dumbbell Bench Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Incline Dumbbell Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Dumbbell Shoulder Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Arnold Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Lateral Raise", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Front Raise", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Reverse Fly", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Dumbbell Fly", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Hammer Curl", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Preacher Curl", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Dumbbell Shrug", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Goblet Squat", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Walking Lunge", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Dumbbell Step-Up", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Triceps Kickback", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Dumbbell Pullover", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        // Gym floor - cable stations. Stack band (0.85); the fly/crossover is the
        // dual-pulley band and gets its factor in MovementDifficulty.loadFactors.
        ExerciseEntity(name = "Seated Cable Row", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Triceps Pushdown", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Overhead Cable Extension", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Cable Fly", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Cable Lateral Raise", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Cable Curl", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Cable Pull-Through", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Woodchop", muscleGroup = MuscleGroup.CORE.name, isWeighted = true),
        // Gym floor - plate-loaded lever machines. Sled band (0.70): the plates
        // ride an angled lever, so a marked kilo imposes less than a vertical one.
        ExerciseEntity(name = "Leg Press", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Hack Squat", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Chest-Supported Row", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        // Gym floor - selectorised pin machines. Stack band (0.85).
        ExerciseEntity(name = "Leg Extension", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Seated Leg Curl", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Lying Leg Curl", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Pec Deck", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Machine Chest Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Machine Shoulder Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Machine Row", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Hip Adduction", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Hip Abduction", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Seated Calf Raise", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Standing Calf Raise", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        // Gym floor - Smith machine. Smith band (0.90).
        ExerciseEntity(name = "Smith Machine Squat", muscleGroup = MuscleGroup.LEGS.name, isWeighted = true),
        ExerciseEntity(name = "Smith Machine Bench Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Smith Machine Overhead Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(name = "Smith Machine Row", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        // Gym floor - assisted machines. The marked weight SUBTRACTS load; the
        // existing "assisted" modifier factor models that, so no loadFactor here.
        ExerciseEntity(name = "Assisted Pull-up", muscleGroup = MuscleGroup.PULL.name, isWeighted = true),
        ExerciseEntity(name = "Assisted Dip", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
    )

    /**
     * Every skill-tree movement is also a loggable exercise, derived from the
     * single source of truth so the catalog and the tree can never diverge.
     * Two lines are mixed-purpose, so the line alone misfiles some movements:
     * "Ring Dip" logged as pulling volume while the identical bar Dip logs as
     * pushing. The line rule still decides every single-purpose line; only the
     * known mixed cases are overridden by name.
     */
    private val skillGroupOverrides: Map<String, MuscleGroup> = mapOf(
        // Rings mixes straight-arm holds/presses with rows; these push, Ring Row
        // and Ring Muscle-up genuinely pull and stay on the line rule.
        "Ring Support Hold" to MuscleGroup.PUSH,
        "Ring Dip" to MuscleGroup.PUSH,
        "Iron Cross" to MuscleGroup.PUSH,
        // Movement mixes hand-balance/core tricks with muscle-up pulls.
        "Kip-up" to MuscleGroup.CORE,
        "Handstand-to-Bridge" to MuscleGroup.CORE,
        "Human Flag" to MuscleGroup.CORE,
    )

    private fun skillGroup(def: Skills.SkillDef): MuscleGroup =
        skillGroupOverrides[def.name] ?: when {
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
        activity("Treadmill", "CARDIO", ExerciseMetric.DISTANCE_TIME, "Cardio"),
        activity("Ski Erg", "CARDIO", ExerciseMetric.DISTANCE_TIME, "Cardio"),
        activity("Indoor Cycling", "CARDIO", ExerciseMetric.DISTANCE_TIME, "Cardio"),
        activity("Versaclimber", "CARDIO", ExerciseMetric.DURATION, "Cardio"),
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

    /**
     * Static holds carry [ExerciseMetric.HOLD] so their figure is seconds in
     * `durationSec`, never repetitions. The skill tree's claim standard is the
     * source of truth for tree movements; [MovementDifficulty] adds the
     * catalogue-only ones. Stamped here rather than written out per row so a
     * new hold cannot be added without its metric.
     */
    private fun withMetric(entity: ExerciseEntity): ExerciseEntity =
        if (entity.metric == ExerciseMetric.REPS.name && MovementDifficulty.isHoldByName(entity.name)) {
            entity.copy(metric = ExerciseMetric.HOLD.name)
        } else {
            entity
        }

    private val allExercises: List<ExerciseEntity> = (
        baseExercises +
            Skills.ALL
                .filterNot { skill -> baseExercises.any { it.name == skill.name } }
                .map { skill ->
                    ExerciseEntity(
                        name = skill.name,
                        muscleGroup = skillGroup(skill).name,
                        isWeighted = skill.name.contains("Weighted"),
                    )
                }
        ).map(::withMetric) + activities

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
                SeedEntry("Active Bar Hang", 3, 30),
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
                SeedEntry("Active Bar Hang", 3, 20),
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

}
