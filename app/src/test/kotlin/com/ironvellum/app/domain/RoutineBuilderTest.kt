package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Run against the REAL seed catalogue - the 214 rows that actually ship - so
 * a rename or an unseeded movement breaks these tests instead of a stranger's
 * first week.
 */
class RoutineBuilderTest {

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    private fun byName(name: String): Exercise =
        catalogue.first { it.name == name }

    private fun allPlans(): List<RoutinePlan> =
        EquipmentAccess.entries.flatMap { eq ->
            TrainingFocus.entries.map { focus ->
                RoutineBuilder.plan(4, eq, focus, catalogue)
            }
        } + (2..6).map { RoutineBuilder.plan(it, EquipmentAccess.FULL_GYM, TrainingFocus.GENERAL, catalogue) }

    @Test
    fun `bodyweight plan never prescribes a loaded movement`() {
        for (days in 2..6) for (focus in TrainingFocus.entries) {
            val plan = RoutineBuilder.plan(days, EquipmentAccess.BODYWEIGHT, focus, catalogue)
            val names = plan.presets.flatMap { it.entries }.map { it.exerciseName }
            assertTrue("plan was empty for $focus/$days", names.isNotEmpty())
            names.forEach { name ->
                assertFalse("loaded movement $name in a BODYWEIGHT plan", byName(name).isWeighted)
            }
        }
    }

    @Test
    fun `home weights plan never prescribes a machine`() {
        val isMachine = { name: String ->
            name.trim().lowercase().startsWith("assisted") ||
                MovementDifficulty.loadFactor(name) < MovementDifficulty.FREE_WEIGHT_LOAD
        }
        for (days in 2..6) for (focus in TrainingFocus.entries) {
            val plan = RoutineBuilder.plan(days, EquipmentAccess.HOME_WEIGHTS, focus, catalogue)
            val names = plan.presets.flatMap { it.entries }.map { it.exerciseName }
            assertTrue("plan was empty for $focus/$days", names.isNotEmpty())
            names.forEach { name ->
                assertFalse("machine movement $name in a HOME_WEIGHTS plan", isMachine(name))
            }
        }
    }

    @Test
    fun `full gym unlocks machine movements that home weights forbids`() {
        // Same fixture as the shrink test: a gym whose only pressing is a
        // pin-stack machine. FULL_GYM must be able to prescribe it.
        val fixture = listOf(
            Exercise(name = "Machine Chest Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
            Exercise(name = "Inverted Row", muscleGroup = MuscleGroup.PULL, isWeighted = false),
            Exercise(name = "Glute Bridge", muscleGroup = MuscleGroup.LEGS, isWeighted = false),
            Exercise(name = "Hanging Knee Raise", muscleGroup = MuscleGroup.CORE, isWeighted = false),
        )
        val plan = RoutineBuilder.plan(6, EquipmentAccess.FULL_GYM, TrainingFocus.GENERAL, fixture)
        val names = plan.presets.flatMap { it.entries }.map { it.exerciseName }
        assertTrue("FULL_GYM refused a machine movement", "Machine Chest Press" in names)
    }

    @Test
    fun `every plan covers push pull and legs and balances push against pull`() {
        for (plan in allPlans()) {
            val groups = plan.presets
                .flatMap { it.entries }
                .mapNotNull { byName(it.exerciseName).muscleGroup }
            listOf(MuscleGroup.PUSH, MuscleGroup.PULL, MuscleGroup.LEGS).forEach { group ->
                assertTrue("plan missed $group: ${plan.presets.map { it.name }}", group in groups)
            }
            assertEquals(
                "push and pull volume unbalanced",
                groups.count { it == MuscleGroup.PUSH },
                groups.count { it == MuscleGroup.PULL },
            )
        }
    }

    @Test
    fun `day count matches the request`() {
        for (days in 2..6) {
            val plan = RoutineBuilder.plan(days, EquipmentAccess.FULL_GYM, TrainingFocus.GENERAL, catalogue)
            assertEquals(days, plan.presets.size)
        }
    }

    @Test
    fun `scheduled days never collide`() {
        for (plan in allPlans()) {
            val days = plan.presets.map { it.scheduledDay }
            assertEquals("duplicate scheduled day", days.size, days.distinct().size)
            days.forEach { day -> assertTrue("day $day outside ISO week", day in 1..7) }
        }
    }

    @Test
    fun `no movement appears twice in one session`() {
        for (plan in allPlans()) {
            plan.presets.forEach { preset ->
                val names = preset.entries.map { it.exerciseName }
                assertEquals(
                    "duplicate in ${preset.name}",
                    names.size,
                    names.distinct().size,
                )
            }
        }
    }

    @Test
    fun `strength plans sit on the low band and muscle plans on the high band`() {
        val strength = RoutineBuilder.plan(4, EquipmentAccess.FULL_GYM, TrainingFocus.STRENGTH, catalogue)
        val muscle = RoutineBuilder.plan(4, EquipmentAccess.FULL_GYM, TrainingFocus.MUSCLE, catalogue)
        val strengthReps = strength.presets.flatMap { it.entries }.map { it.reps }
        val muscleReps = muscle.presets.flatMap { it.entries }.map { it.reps }
        assertTrue(strengthReps.isNotEmpty() && muscleReps.isNotEmpty())
        assertTrue(
            "strength reps ${strengthReps.distinct()} not below muscle reps ${muscleReps.distinct()}",
            strengthReps.max() < muscleReps.min(),
        )
    }

    @Test
    fun `an unfilled slot shrinks the plan instead of breaking the constraint`() {
        // A gym whose only pushing is a pin-stack machine: under BODYWEIGHT the
        // push slots cannot be filled, so the plan must drop them rather than
        // prescribe the machine.
        val fixture = listOf(
            Exercise(name = "Machine Chest Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
            Exercise(name = "Inverted Row", muscleGroup = MuscleGroup.PULL, isWeighted = false),
            Exercise(name = "Glute Bridge", muscleGroup = MuscleGroup.LEGS, isWeighted = false),
            Exercise(name = "Hanging Knee Raise", muscleGroup = MuscleGroup.CORE, isWeighted = false),
        )
        val plan = RoutineBuilder.plan(6, EquipmentAccess.BODYWEIGHT, TrainingFocus.GENERAL, fixture)
        val names = plan.presets.flatMap { it.entries }.map { it.exerciseName }
        assertFalse("machine slipped into a BODYWEIGHT plan", "Machine Chest Press" in names)
        // Shrunk, not empty: the patterns the catalogue CAN fill are still there.
        assertTrue("plan shrank to nothing", names.contains("Inverted Row"))
        assertTrue(names.contains("Glute Bridge"))
    }

    @Test
    fun `skill plans draw compounds from the skill tree at accessible tiers`() {
        // BODYWEIGHT: every skill progression ties on equipment fit, so the
        // skill preference itself decides. Under a full gym the equipment-fit
        // rule may rightly lead with a barbell line root instead.
        val plan = RoutineBuilder.plan(6, EquipmentAccess.BODYWEIGHT, TrainingFocus.SKILL, catalogue)
        val compounds = plan.presets
            .flatMap { it.entries }
            .filter { MovementDifficulty.tier(it.exerciseName) >= 2 }
        assertTrue(compounds.isNotEmpty())
        compounds.forEach { entry ->
            val name = entry.exerciseName
            val key = name.trim().lowercase()
            assertTrue(
                "$name is not a skill-tree movement",
                MovementDifficulty.isClassified(name) && key !in MovementDifficulty.catalogueOnlyKeys,
            )
        }
    }

    @Test
    fun `claim-standard milestone rows are never prescribed`() {
        // The gym progression lines seed catalogue rows for their claim
        // standards - "1 rep at double bodyweight" is a deed, not a working
        // set. isLoadPriced marks exactly those rows.
        for (plan in allPlans()) {
            plan.presets.flatMap { it.entries }.forEach { entry ->
                assertFalse(
                    "milestone ${entry.exerciseName} prescribed as ${entry.sets} x ${entry.reps}",
                    MovementDifficulty.isLoadPriced(entry.exerciseName),
                )
            }
        }
        val plan = RoutineBuilder.plan(4, EquipmentAccess.FULL_GYM, TrainingFocus.MUSCLE, catalogue)
        val names = plan.presets.flatMap { it.entries }.map { it.exerciseName }
        assertFalse("Double-Bodyweight Deadlift" in names)
    }

    @Test
    fun `compound slots lead with equipment-matched movements`() {
        // A full gym must open its upper session with a loaded press, not the
        // lowest-tier bodyweight dip variant.
        val plan = RoutineBuilder.plan(4, EquipmentAccess.FULL_GYM, TrainingFocus.GENERAL, catalogue)
        val first = plan.presets.first().entries.first()
        assertTrue("upper session led with bodyweight ${first.exerciseName}", first.exerciseName == "Bench Press")
        val groups = plan.presets.first().entries.map { byName(it.exerciseName).muscleGroup }
        assertTrue(MuscleGroup.PULL in groups)
        val firstPull = plan.presets.first().entries
            .first { byName(it.exerciseName).isWeighted && byName(it.exerciseName).muscleGroup == MuscleGroup.PULL }
        assertEquals("Barbell Row", firstPull.exerciseName)
    }

    @Test
    fun `no session mixes pattern versions at different equipment fits`() {
        val fit = { name: String, equipment: EquipmentAccess ->
            val e = byName(name)
            when {
                equipment == EquipmentAccess.BODYWEIGHT -> 0
                !e.isWeighted -> 2
                MovementDifficulty.loadFactor(name) < MovementDifficulty.FREE_WEIGHT_LOAD ||
                    name.trim().lowercase().startsWith("assisted") -> 1
                else -> 0
            }
        }
        for (equipment in EquipmentAccess.entries) for (focus in TrainingFocus.entries) {
            val plan = RoutineBuilder.plan(6, equipment, focus, catalogue)
            plan.presets.forEach { preset ->
                preset.entries
                    .groupBy { byName(it.exerciseName).muscleGroup }
                    .forEach { (_, entries) ->
                        val fits = entries.map { fit(it.exerciseName, equipment) }.distinct()
                        assertTrue(
                            "session ${preset.name} mixes equipment fits $fits for one pattern",
                            fits.size == 1,
                        )
                    }
            }
        }
    }
}
