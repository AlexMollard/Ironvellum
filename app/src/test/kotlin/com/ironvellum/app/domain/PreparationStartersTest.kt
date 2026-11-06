package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.*
import org.junit.Test

class PreparationStartersTest {
    private val catalogue = Seed.exercises.mapIndexed { index, e ->
        Exercise(index + 1L, e.name, MuscleGroup.valueOf(e.muscleGroup), e.isWeighted, ExerciseMetric.valueOf(e.metric))
    }
    @Test fun allThirteenCanonicalMovementsResolveExactlyOnceWithArtAndGuides() {
        val names = PreparationKind.entries.flatMap { PreparationStarters.templates(it, catalogue) }.flatMap { it.steps }.map { it.exerciseName }.toSet()
        assertEquals(13, names.size)
        names.forEach { name ->
            assertEquals(name, 1, catalogue.count { it.name.equals(name, true) })
            assertNotNull(name, ExerciseArtIndex.forName(name))
            assertNotNull(name, ExerciseGuides.forName(name))
        }
    }
    @Test fun startersHaveExactOrderTargetsAndRepetitions() {
        val warm = PreparationStarters.templates(PreparationKind.WARMUP, catalogue)
        val cool = PreparationStarters.templates(PreparationKind.COOLDOWN, catalogue)
        assertEquals(listOf("Arm Circles", "Bodyweight Squat", "Cat-Cow Stretch", "World's Greatest Stretch"), warm[0].steps.map { it.exerciseName })
        assertEquals(listOf(30, 10, 45, 30), warm[0].steps.map { it.target })
        assertEquals(listOf(PreparationRepeat.DIRECTIONS, PreparationRepeat.ONCE, PreparationRepeat.ONCE, PreparationRepeat.SIDES), warm[0].steps.map { it.repeat })
        assertEquals(PreparationMeasure.REPS, warm[0].steps[1].measure)
        assertEquals(listOf("Arm Circles", "Cat-Cow Stretch", "World's Greatest Stretch"), warm[1].steps.map { it.exerciseName })
        assertEquals(listOf("Leg Swing Stretch", "Bodyweight Squat", "Ankle Circles"), warm[2].steps.map { it.exerciseName })
        assertEquals(listOf(30, 10, 30), warm[2].steps.map { it.target })
        assertEquals(listOf(PreparationRepeat.SIDES, PreparationRepeat.ONCE, PreparationRepeat.SIDES), warm[2].steps.map { it.repeat })
        assertEquals(listOf("Standing Quad Stretch", "Doorway Chest Stretch", "Child's Pose"), cool[0].steps.map { it.exerciseName })
        assertEquals(listOf("Doorway Chest Stretch", "Cross-Body Shoulder Stretch", "Child's Pose"), cool[1].steps.map { it.exerciseName })
        assertEquals(listOf(30, 30, 60), cool[0].steps.map { it.target })
        assertEquals(listOf(30, 30, 60), cool[1].steps.map { it.target })
        assertEquals(listOf(PreparationRepeat.SIDES, PreparationRepeat.ONCE, PreparationRepeat.ONCE), cool[0].steps.map { it.repeat })
        assertEquals(listOf(PreparationRepeat.ONCE, PreparationRepeat.SIDES, PreparationRepeat.ONCE), cool[1].steps.map { it.repeat })
        assertTrue(cool.flatMap { it.steps }.all { it.measure == PreparationMeasure.SECONDS })
        assertEquals(listOf("Kneeling Hip Flexor Stretch", "Hamstring Stretch", "Standing Quad Stretch", "Wall Calf Stretch"), cool[2].steps.map { it.exerciseName })
        assertTrue(cool[2].steps.all { it.target == 30 && it.repeat == PreparationRepeat.SIDES })
        assertTrue((warm + cool).flatMap { it.steps }.all { it.rounds == 1 && it.weightKg == null && it.validForEditor() })
    }
    @Test fun allGroupsAndDuplicateWorkIdsClassifyDeterministically() {
        PreparationKind.entries.forEach { kind ->
            val templates = PreparationStarters.templates(kind, catalogue)
            MuscleGroup.entries.forEach { group ->
                val e = Exercise(999, "work", group, false)
                val index = when (group) { MuscleGroup.LEGS -> 2; MuscleGroup.PUSH, MuscleGroup.PULL, MuscleGroup.CLIMBING -> 1; else -> 0 }
                assertEquals(group.name, templates[index], PreparationStarters.suggest(kind, listOf(e, e), catalogue))
            }
            assertEquals(templates[0], PreparationStarters.suggest(kind, emptyList(), catalogue))
            assertEquals(templates[0], PreparationStarters.suggest(kind, listOf(Exercise(998, "upper", MuscleGroup.PUSH, false), Exercise(999, "legs", MuscleGroup.LEGS, false)), catalogue))
        }
    }
    @Test fun missingCanonicalMovementIsAnError() {
        assertThrows(IllegalArgumentException::class.java) { PreparationStarters.templates(PreparationKind.WARMUP, emptyList()) }
    }
}
