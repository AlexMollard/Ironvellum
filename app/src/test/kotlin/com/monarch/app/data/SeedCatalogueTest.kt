package com.monarch.app.data

import com.monarch.app.domain.ExerciseMetric
import com.monarch.app.domain.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seeded catalogue and the pre-loaded programs are edited by hand, and a
 * mismatch between them fails silently: a misspelt movement is created as a
 * duplicate catalogue row on first launch, and a load on a movement that is not
 * weighted renders an unexplained "@8kg" on the quest board.
 *
 * These are structural invariants rather than behaviour, so they are asserted
 * over the real data instead of a fixture.
 */
class SeedCatalogueTest {

    private val catalogue = Seed.exercises.associateBy { it.name }

    @Test
    fun `every programmed movement exists in the catalogue`() {
        val missing = Seed.presets.flatMap { preset ->
            preset.entries.filter { it.exercise !in catalogue }.map { "${preset.name}: ${it.exercise}" }
        }
        // resolveExercise() creates unknown names on import, so a typo here does
        // not crash — it mints a near-duplicate row nobody asked for.
        assertEquals("programmed movements missing from the catalogue", emptyList<String>(), missing)
    }

    @Test
    fun `a programmed load always belongs to a movement that can carry one`() {
        val unexplained = Seed.presets.flatMap { preset ->
            preset.entries.filter { entry ->
                val kg = entry.weightKg ?: 0.0
                val exercise = catalogue[entry.exercise]
                // Either the movement is weighted by nature (barbell press), or
                // this program says the hunter adds load to a bodyweight one.
                kg > 0.0 && exercise != null &&
                    !exercise.isWeighted && "weighted" !in entry.modifiers
            }.map { "${preset.name}: ${it.exercise} @${it.weightKg}" }
        }
        assertEquals("loads on movements that cannot carry one", emptyList<String>(), unexplained)
    }

    @Test
    fun `catalogue names are unique and free of stray whitespace`() {
        val names = Seed.exercises.map { it.name }
        val duplicates = names.groupingBy { it.lowercase() }.eachCount().filter { it.value > 1 }
        // Matching on import is case-insensitive, so two rows differing only by
        // case would both match and the loser would never be reachable.
        assertEquals("duplicate movement names", emptyMap<String, Int>(), duplicates)
        assertEquals("names with surrounding whitespace", emptyList<String>(), names.filter { it != it.trim() })
    }

    @Test
    fun `every catalogue movement declares a real muscle group`() {
        val valid = MuscleGroup.entries.map { it.name }.toSet()
        val bad = Seed.exercises.filter { it.muscleGroup !in valid }.map { "${it.name}: ${it.muscleGroup}" }
        // The group is stored as a String, so a typo survives compilation and
        // then fails valueOf() at read time — the launch-crash shape already
        // seen once on a stored enum.
        assertEquals("movements with an unknown muscle group", emptyList<String>(), bad)
    }

    @Test
    fun `every catalogue movement declares a real metric`() {
        val valid = ExerciseMetric.entries.map { it.name }.toSet()
        val bad = Seed.exercises.filter { it.metric !in valid }.map { "${it.name}: ${it.metric}" }
        // Same stored-as-a-String launch-crash shape as muscleGroup: a typo
        // survives compilation and fails valueOf() the first time the row is
        // read back.
        assertEquals("movements with an unknown metric", emptyList<String>(), bad)
    }

    @Test
    fun `every program is scheduled on a real day and has movements to do`() {
        Seed.presets.forEach { preset ->
            assertTrue("${preset.name} has no movements", preset.entries.isNotEmpty())
            preset.scheduledDay?.let {
                assertTrue("${preset.name} scheduled on ISO day $it", it in 1..7)
            }
            preset.entries.forEach { entry ->
                assertTrue("${preset.name}: ${entry.exercise} has ${entry.sets} sets", entry.sets > 0)
                assertTrue("${preset.name}: ${entry.exercise} has ${entry.reps} reps", entry.reps > 0)
            }
        }
    }

    @Test
    fun `at most one program per weekday, so the quest board is unambiguous`() {
        val clashes = Seed.presets.mapNotNull { it.scheduledDay }
            .groupingBy { it }.eachCount().filter { it.value > 1 }
        // Today's quest is picked by day: two programs on one day makes the
        // board depend on list order rather than intent.
        assertEquals("weekdays carrying more than one program", emptyMap<Int, Int>(), clashes)
    }

    @Test
    fun `mixed-line movements are grouped by what they train, not by line`() {
        // The Rings and Movement lines mix pushing holds and core tricks with
        // pulls; the per-movement override files each by what it actually trains.
        val expected = mapOf(
            "Ring Support Hold" to MuscleGroup.PUSH,
            "Ring Dip" to MuscleGroup.PUSH,
            "Iron Cross" to MuscleGroup.PUSH,
            "Kip-up" to MuscleGroup.CORE,
            "Handstand-to-Bridge" to MuscleGroup.CORE,
            "Human Flag" to MuscleGroup.CORE,
            // Line-mates that genuinely pull stay on the line rule.
            "Ring Row" to MuscleGroup.PULL,
            "Ring Muscle-up" to MuscleGroup.PULL,
        )
        val wrong = expected.mapNotNull { (name, group) ->
            val entity = catalogue[name]
                ?: return@mapNotNull "skill movement \"$name\" missing from the catalogue"
            val actual = MuscleGroup.valueOf(entity.muscleGroup)
            if (actual == group) null else "$name: expected $group, found $actual"
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `no obvious press is filed under PULL and vice versa`() {
        // A future mixed-line movement must not silently inherit its line's
        // group again: when a name cue and the line disagree, the cue wins.
        val pressCue = listOf("Push-up", "Dip", "Press", "Support Hold", "Cross", "Planche")
        val pullCue = listOf("Pull-up", "Row", "Hang", "Curl", "Lever")
        val sanctioned = setOf("Muscle-up", "Strict Muscle-up", "Inverted Muscle-up")
        val wrong = Seed.exercises.mapNotNull { e ->
            val group = MuscleGroup.valueOf(e.muscleGroup)
            when {
                e.name in sanctioned -> null
                pressCue.any { it in e.name } && group == MuscleGroup.PULL -> "${e.name} presses but is PULL"
                pullCue.any { it in e.name } && group == MuscleGroup.PUSH -> "${e.name} pulls but is PUSH"
                else -> null
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }
}
