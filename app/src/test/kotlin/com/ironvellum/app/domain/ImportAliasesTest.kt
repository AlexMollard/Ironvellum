package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportAliasesTest {

    private val catalogue = listOf(
        "Bench Press", "Pull-up", "Chin-up", "Back Squat", "Deadlift",
        "Overhead Press", "Triceps Pushdown", "Leg Press", "L-sit", "Running",
    )

    @Test
    fun equipmentSuffixIsStrippedBeforeMatching() {
        assertEquals("Bench Press", ImportAliases.stripEquipment("Bench Press (Barbell)"))
        assertEquals("Leg Press", ImportAliases.stripEquipment("Leg Press (Machine)"))
        assertEquals("Pull Up", ImportAliases.stripEquipment("Pull Up (Assisted)"))
        assertEquals("Bench Press", ImportAliases.resolve("Bench Press (Barbell)", catalogue))
        assertEquals("Triceps Pushdown", ImportAliases.resolve("Triceps Pushdown (Cable - Straight Bar)", catalogue))
    }

    @Test
    fun namingDifferencesResolveThroughTheAliasTable() {
        // Strong/Hevy write "Pull Up"; the catalogue writes "Pull-up".
        assertEquals("Pull-up", ImportAliases.resolve("Pull Up", catalogue))
        assertEquals("Pull-up", ImportAliases.resolve("Pull Up (Weighted)", catalogue))
        // Strong's plain "Squat" is the barbell back squat.
        assertEquals("Back Squat", ImportAliases.resolve("Squat (Barbell)", catalogue))
    }

    @Test
    fun assistedMachineSuffixesResolveBeforeStrippingOrGoToReview() {
        val machines = catalogue + listOf("Assisted Pull-up", "Assisted Chin-up", "Assisted Dip")
        val cases = mapOf(
            "Pull Up (Assisted)" to "Assisted Pull-up",
            " pull-up (ASSISTED) " to "Assisted Pull-up",
            "Chin Up (Assisted)" to "Assisted Chin-up",
            "chin-up(assisted)" to "Assisted Chin-up",
            "Dip (Assisted)" to "Assisted Dip",
        )
        cases.forEach { (raw, expected) ->
            assertEquals(expected, ImportAliases.resolve(raw, machines))
            assertNull(ImportAliases.resolve(raw, catalogue))
        }
        assertEquals("Pull Up (Assisted)", ImportAliases.resolve("pull up (assisted)", machines + "Pull Up (Assisted)"))
        assertEquals("Bench Press", ImportAliases.resolve("Bench Press (Assisted)", catalogue))
    }

    @Test
    fun hevyAssistanceKeepsItsRawKilograms() {
        val csv = """
            "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
            "Upper","22 Dec 2025, 08:00","22 Dec 2025, 09:00","","Pull Up (Assisted)",,"",0,"normal",21,5,,,
        """.trimIndent()
        val set = CsvWorkoutReader.read(csv).workouts.single().sets.single()
        assertEquals(21.0, set.weightKg!!, 0.0)
        assertEquals("Assisted Pull-up", ImportAliases.resolve(set.exerciseName, catalogue + "Assisted Pull-up"))
        assertNull(ImportAliases.resolve(set.exerciseName, catalogue))
    }

    @Test
    fun unknownNamesResolveToNullAndGoToReview() {
        assertNull(ImportAliases.resolve("Custom Wizard Lift", catalogue))
        assertNull(ImportAliases.resolve("", catalogue))
    }

    @Test
    fun exactCatalogueNameMatchesCaseInsensitively() {
        assertEquals("L-sit", ImportAliases.resolve("l-sit", catalogue))
        assertEquals("Running", ImportAliases.resolve("RUNNING", catalogue))
    }
}
