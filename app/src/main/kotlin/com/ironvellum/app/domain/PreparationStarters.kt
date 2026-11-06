package com.ironvellum.app.domain

import com.ironvellum.app.R
import com.ironvellum.app.data.Words

object PreparationStarters {
    fun suggest(kind: PreparationKind, working: List<Exercise>, catalogue: List<Exercise>): PreparationSnapshot {
        val groups = working.distinctBy { it.id }.map { it.muscleGroup }.toSet()
        val legs = MuscleGroup.LEGS in groups
        val upper = groups.any { it == MuscleGroup.PUSH || it == MuscleGroup.PULL || it == MuscleGroup.CLIMBING }
        return templates(kind, catalogue)[when { legs && !upper -> 2; upper && !legs -> 1; else -> 0 }]
    }

    fun templates(kind: PreparationKind, catalogue: List<Exercise>): List<PreparationSnapshot> {
        fun step(name: String, target: Int = 30, repeat: PreparationRepeat = PreparationRepeat.ONCE, reps: Boolean = false): PreparationStep {
            val exercise = catalogue.singleOrNull { it.name.equals(name, ignoreCase = true) }
            requireNotNull(exercise) { "Preparation catalogue movement missing or ambiguous: $name" }
            return PreparationStep(exercise.id, exercise.name, if (reps) PreparationMeasure.REPS else PreparationMeasure.SECONDS, target, repeat = repeat)
        }
        fun title(plain: Int, themed: Int) = Words.text(UiText.themed(plain, themed))
        val sides = PreparationRepeat.SIDES
        val directions = PreparationRepeat.DIRECTIONS
        return if (kind == PreparationKind.WARMUP) listOf(
            PreparationSnapshot(kind, title(R.string.preparation_full_warmup, R.string.preparation_full_warmup__t), listOf(
                step("Arm Circles", repeat = directions), step("Bodyweight Squat", 10, reps = true), step("Cat-Cow Stretch", 45), step("World's Greatest Stretch", repeat = sides))),
            PreparationSnapshot(kind, title(R.string.preparation_upper_warmup, R.string.preparation_upper_warmup__t), listOf(
                step("Arm Circles", repeat = directions), step("Cat-Cow Stretch", 45), step("World's Greatest Stretch", repeat = sides))),
            PreparationSnapshot(kind, title(R.string.preparation_lower_warmup, R.string.preparation_lower_warmup__t), listOf(
                step("Leg Swing Stretch", repeat = sides), step("Bodyweight Squat", 10, reps = true), step("Ankle Circles", repeat = sides))),
        ) else listOf(
            PreparationSnapshot(kind, title(R.string.preparation_full_cooldown, R.string.preparation_full_cooldown__t), listOf(
                step("Standing Quad Stretch", repeat = sides), step("Doorway Chest Stretch"), step("Child's Pose", 60))),
            PreparationSnapshot(kind, title(R.string.preparation_upper_cooldown, R.string.preparation_upper_cooldown__t), listOf(
                step("Doorway Chest Stretch"), step("Cross-Body Shoulder Stretch", repeat = sides), step("Child's Pose", 60))),
            PreparationSnapshot(kind, title(R.string.preparation_lower_cooldown, R.string.preparation_lower_cooldown__t), listOf(
                step("Kneeling Hip Flexor Stretch", repeat = sides), step("Hamstring Stretch", repeat = sides), step("Standing Quad Stretch", repeat = sides), step("Wall Calf Stretch", repeat = sides))),
        )
    }
}
