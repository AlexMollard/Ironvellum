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
        assertEquals("Pull-up", ImportAliases.resolve("Pull Up (Assisted)", catalogue))
        // Strong's plain "Squat" is the barbell back squat.
        assertEquals("Back Squat", ImportAliases.resolve("Squat (Barbell)", catalogue))
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
