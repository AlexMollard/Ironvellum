package com.ironvellum.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live set row reads "kg each" for lifts done with a dumbbell in each hand (issue #3). */
class LoadIsEachTest {

    @Test
    fun everyEachHandNameIsACatalogueMovement() {
        // A misspelt name never matches a set, and the row silently reads plain "kg".
        val missing = GearRequirements.eachHandNames.filterNot { GearRequirements.hasEntry(it) }
        assertTrue("not in the catalogue: $missing", missing.isEmpty())
    }

    @Test
    fun twoDumbbellLiftsArePerDumbbell() {
        assertTrue(GearRequirements.loadIsEach("Dumbbell Bench Press"))
        assertTrue(GearRequirements.loadIsEach("Dumbbell Romanian Deadlift"))
    }

    @Test
    fun oneDumbbellAndShareableLiftsAreTheWholeLoad() {
        assertFalse(GearRequirements.loadIsEach("Goblet Squat"))
        assertFalse(GearRequirements.loadIsEach("Dumbbell Row"))
        // Barbell or dumbbells, and nothing logs which.
        assertFalse(GearRequirements.loadIsEach("Romanian Deadlift"))
    }
}
