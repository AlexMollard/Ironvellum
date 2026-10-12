package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-change Temper: improve() proposes independent changes the lifter can
 * tick one by one, and [Improvement.apply] turns any ticked subset into a
 * consistent preset.
 */
class ProgramChangesTest {

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
        listOf(
            LoggedLift("Bench Press", 75.0, 10),
            LoggedLift("Back Squat", 80.0, 10),
            LoggedLift("Barbell Row", 60.0, 10),
        ),
    )

    private val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM)

    private val sissy = PlannedEntry("Spanish Squat", 3, 12, null, modifiers = "tempo", why = "CSV import")

    /** Reps out of range, a short-length curl to swap, no loads, and a week that never trains calves. */
    private val legs = PlannedPreset(
        "Legs", "", 2,
        listOf(
            PlannedEntry("Back Squat", 3, 5, null),
            PlannedEntry("Lying Leg Curl", 3, 20, null),
            sissy,
        ),
    )

    private val restOfWeek = listOf(
        PlannedPreset(
            "Squat Day", "", 1,
            listOf(
                PlannedEntry("Back Squat", 6, 8, null),
                PlannedEntry("Leg Press", 6, 10, null),
                PlannedEntry("Romanian Deadlift", 8, 10, null),
                PlannedEntry("Hip Thrust", 8, 10, null),
                PlannedEntry("Hanging Leg Raise", 4, 10, null),
                PlannedEntry("Ab Wheel Rollout", 8, 10, null),
            ),
        ),
    )

    private val overCap = PlannedPreset(
        "Full", "", 1,
        listOf(
            PlannedEntry("Pull-up", 4, 5, null), PlannedEntry("Handstand Push-up", 3, 5, null),
            PlannedEntry("Push-up", 3, 5, null), PlannedEntry("Door Sheet Row", 3, 5, null),
            PlannedEntry("Pistol Squat", 3, 5, null), PlannedEntry("Romanian Deadlift", 3, 5, null),
            PlannedEntry("Hanging Leg Raise", 3, 10, null),
        ),
    )

    private fun improveLegs() = ProgramGenerator.improve(legs, restOfWeek, request, catalogue, strength)

    private fun improveOverCap() =
        ProgramGenerator.improve(overCap, emptyList(), request.copy(equipment = Equipment.FULL_GYM), catalogue, strength)

    private fun ids(improvement: Improvement) = improvement.changes.map { it.id }.toSet()

    @Test
    fun `changes carry stable unique ids tags and a swap carries its options`() {
        val improvement = improveLegs()
        assertTrue(improvement.changes.size >= 4)
        assertEquals(improvement.changes.size, ids(improvement).size)
        assertEquals(ids(improvement), ids(improveLegs()))
        improvement.changes.forEach { assertTrue("${it.id} tags ${it.tags}", it.tags.size <= 2) }
        val swap = improvement.changes.first { it.kind == PlanChange.Kind.SWAPPED }
        assertTrue(swap.options.options.size in 1..3)
        assertEquals((swap.op as ChangeOp.SwapEntry).to.exerciseName, swap.options.options.first().exercise.name)
        val added = improvement.changes.first { it.kind == PlanChange.Kind.ADDED }
        assertTrue(added.options.options.isNotEmpty())
        assertEquals(added.exerciseName, added.options.options.first().exercise.name)
        assertTrue(added.coverageDelta.getValue(Muscle.CALVES) > 0.0)
    }

    @Test
    fun `every change applies alone`() {
        val improvement = improveLegs()
        for (change in improvement.changes) {
            val closure = closureOf(improvement, change.id)
            val applied = improvement.apply(closure)
            assertTrue("${change.id} alone changed nothing", applied != improvement.before)
            val names = applied.entries.map { it.exerciseName }
            assertEquals("${change.id} duplicated a movement: $names", names.distinct(), names)
            when (change.kind) {
                PlanChange.Kind.REMOVED -> assertFalse(change.exerciseName in names)
                PlanChange.Kind.ADDED -> assertTrue(change.exerciseName in names)
                PlanChange.Kind.SWAPPED -> assertTrue(change.exerciseName in names)
                else -> Unit
            }
        }
    }

    @Test
    fun `any subset of changes stays consistent with no duplicates and the cap held`() {
        for (improvement in listOf(improveLegs(), improveOverCap())) {
            val all = improvement.changes.map { it.id }
            assertTrue("${all.size} changes: $all", all.size in 1..12)
            val cap = ProgramRules.movementCap(request.maxExercises)
            for (mask in 0 until (1 shl all.size)) {
                val picked = all.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
                val applied = improvement.apply(picked)
                val names = applied.entries.map { it.exerciseName.lowercase() }
                assertEquals("subset $picked duplicated: $names", names.distinct(), names)
                // Nothing ticked is the preset as the lifter has it, over the cap or not.
                assertTrue("subset $picked broke the cap: ${names.size}", picked.isEmpty() || names.size <= cap)
                assertEquals(improvement.before.name, applied.name)
                assertEquals(improvement.before.scheduledDay, applied.scheduledDay)
            }
        }
    }

    @Test
    fun `skipping a swap skips the load that depends on it`() {
        val improvement = improveLegs()
        val swap = improvement.changes.first { it.kind == PlanChange.Kind.SWAPPED }
        val dependents = improvement.changes.filter { it.requires == swap.id }
        assertTrue("nothing depends on the swap: ${improvement.changes.map { it.id to it.requires }}", dependents.isNotEmpty())
        val without = improvement.apply(ids(improvement) - swap.id)
        val names = without.entries.map { it.exerciseName }
        assertTrue("Lying Leg Curl", "Lying Leg Curl" in names)
        assertFalse(swap.exerciseName in names)
        // The dependents went with it: the same result as skipping them by hand.
        assertEquals(without, improvement.apply(ids(improvement) - swap.id - dependents.map { it.id }.toSet()))
    }

    @Test
    fun `applying everything gives after and applying nothing gives before`() {
        for (improvement in listOf(improveLegs(), improveOverCap())) {
            assertEquals(improvement.after, improvement.apply(ids(improvement)))
            assertEquals(improvement.before, improvement.apply(emptySet()))
            assertEquals(improvement.before, improvement.apply(setOf("not-a-change")))
        }
        assertEquals(legs, improveLegs().before)
    }

    @Test
    fun `undo is the preset it was given`() {
        val improvement = improveLegs()
        assertEquals(legs, improvement.before)
        assertTrue(improvement.after != improvement.before)
        assertEquals(legs, improvement.apply(emptySet()))
    }

    @Test
    fun `improving the applied result proposes nothing`() {
        for ((preset, rest) in listOf(legs to restOfWeek, overCap to emptyList())) {
            val first = ProgramGenerator.improve(preset, rest, request, catalogue, strength)
            val second = ProgramGenerator.improve(first.after, rest, request, catalogue, strength)
            assertTrue("second improve changed: ${second.changes}", second.changes.isEmpty())
            assertEquals(first.after, second.after)
        }
    }

    @Test
    fun `a partial apply then improve does not undo what was applied`() {
        val improvement = improveLegs()
        val picked = improvement.changes.filter { it.kind == PlanChange.Kind.ADJUSTED || it.kind == PlanChange.Kind.LOAD_SET }
            .filter { it.requires == null }.map { it.id }.toSet()
        assertTrue(picked.isNotEmpty())
        val partial = improvement.apply(picked)
        val again = ProgramGenerator.improve(partial, restOfWeek, request, catalogue, strength)
        assertTrue("re-proposed: ${again.changes.map { it.id }}", again.changes.map { it.id }.none { it in picked })
        val appliedNames = improvement.changes.filter { it.id in picked }.map { it.exerciseName }.toSet()
        assertTrue(again.changes.none { it.kind == PlanChange.Kind.REMOVED && it.exerciseName in appliedNames })
        // What was left unticked is still on offer.
        assertTrue(again.changes.any { it.kind == PlanChange.Kind.SWAPPED })
        assertNotNull(again.after)
    }

    @Test
    fun `cap removals stay removable one by one and the cap holds without them`() {
        val improvement = improveOverCap()
        val removals = improvement.changes.filter { it.kind == PlanChange.Kind.REMOVED }
        assertTrue(removals.size >= 2)
        val cap = ProgramRules.movementCap(request.maxExercises)
        assertTrue(improvement.apply(ids(improvement) - removals.map { it.id }.toSet()).entries.size <= cap)
        assertEquals(cap, improvement.after.entries.size)
    }

    private fun closureOf(improvement: Improvement, id: String): Set<String> {
        val byId = improvement.changes.associateBy { it.id }
        val out = mutableSetOf<String>()
        var next: String? = id
        while (next != null && out.add(next)) next = byId.getValue(next).requires
        return out
    }
}
