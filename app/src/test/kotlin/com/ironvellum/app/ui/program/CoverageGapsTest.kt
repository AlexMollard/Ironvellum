package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a week credits to the muscles the coverage page judges: helpers (the rotator cuff, the abductors, the
 * forearms) are credited by the movements that load them, and every judged muscle has a region on the figure.
 * The states themselves are CoverageStateTest's.
 */
class CoverageGapsTest {

    @Test
    fun `pull-ups, rows, presses and raises credit the rotator cuff`() {
        // It keeps the shoulder in its socket under load; a pulling week
        // with no cuff isolation must not read as zero.
        val week = listOf(
            PlannedPreset(
                "Week", "", 1,
                listOf(
                    PlannedEntry("Pull-up", 5, 5, null),
                    PlannedEntry("Dumbbell Row", 4, 8, null),
                    PlannedEntry("Handstand Push-up", 5, 5, null),
                    PlannedEntry("Lateral Raise", 4, 12, null),
                ),
            ),
        )
        assertEquals(9.0, ProgramRules.weeklyVolume(week)[Muscle.ROTATOR_CUFF] ?: 0.0, 1e-9)
    }

    @Test
    fun `every single-leg squat credits the abductors`() {
        // The glute med holds the pelvis level over one foot; a sideways
        // lunge loads it as much as a forward one (DiStefano 2009).
        listOf("Pistol Squat", "Bulgarian Split Squat", "Cossack Squat", "Shrimp Squat").forEach { name ->
            val week = listOf(PlannedPreset("Legs", "", 3, listOf(PlannedEntry(name, 2, 8, null))))
            assertEquals(name, 1.0, ProgramRules.weeklyVolume(week)[Muscle.ABDUCTORS] ?: 0.0, 1e-9)
        }
    }

    @Test
    fun `a pull-up week credits the forearms`() {
        val week = listOf(PlannedPreset("Pull", "", 1, listOf(PlannedEntry("Pull-up", 5, 5, null))))
        assertTrue((ProgramRules.weeklyVolume(week)[Muscle.FOREARMS] ?: 0.0) > 0.0)
    }

    @Test
    fun `every judged muscle has a region on the figure`() {
        // A judged muscle without a region is flagged in the list but never
        // coloured, so the figure would show it as trained when it is not.
        val drawn = BodyFigures.MALE.drawn intersect BodyFigures.FEMALE.drawn
        assertEquals(emptyList<Muscle>(), JUDGED_MUSCLES.filterNot { it in drawn })
    }
}
