package com.ironvellum.app.data

import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.domain.CustomExercises
import com.ironvellum.app.domain.MuscleGroup

/**
 * Refreshes [CustomExercises]' registry from a catalogue read, so the name-keyed tables (MuscleMap,
 * GearRequirements, MovementDifficulty) can price and profile the lifter's own movements. Idempotent and cheap;
 * every path that reads the catalogue to score or draw calls it, so scoring never depends on which screen ran first.
 */
internal fun List<ExerciseEntity>.syncCustomExercises(): List<ExerciseEntity> {
    CustomExercises.sync(
        filter { it.custom }.map { row ->
            CustomExercises.Row(
                name = row.name,
                group = MuscleGroup.entries.firstOrNull { it.name == row.muscleGroup } ?: MuscleGroup.CORE,
                category = row.category,
                needs = CustomExercises.decodeNeeds(row.gear),
                shares = CustomExercises.decodeShares(row.muscles),
            )
        },
    )
    return this
}
