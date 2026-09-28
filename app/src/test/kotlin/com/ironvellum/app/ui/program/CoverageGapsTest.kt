package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Train card's "N SHORT OR MISSING" and the coverage screen's flags: a
 * major muscle counts when under its range or untrained, a helper (front
 * delts, forearms, lower back, adductors and the rest) when under its floor - and never
 * for being over it, because a helper has no ceiling.
 */
class CoverageGapsTest {

    private val target = 5.0..15.0

    private fun everyMuscleAt(sets: Double): MutableMap<Muscle, Double> =
        Muscle.entries.associateWith { sets }.toMutableMap()

    @Test
    fun `a helper under its floor is a gap, at the floor it is not`() {
        val volume = everyMuscleAt(8.0)
        volume[Muscle.LOWER_BACK] = ProgramRules.HELPER_FLOOR_SETS - 0.5
        volume[Muscle.ADDUCTORS] = ProgramRules.HELPER_FLOOR_SETS
        volume.remove(Muscle.FOREARMS)
        assertEquals(setOf(Muscle.LOWER_BACK, Muscle.FOREARMS), coverageGaps(volume, target).toSet())
    }

    @Test
    fun `a helper is never over - it has a floor and no ceiling`() {
        val volume = everyMuscleAt(8.0)
        volume[Muscle.FRONT_DELTS] = 30.0
        assertTrue(coverageGaps(volume, target).isEmpty())
        assertEquals(CoverageLevel.IN_RANGE, coverageLevel(30.0, rangeFor(Muscle.FRONT_DELTS, target)))
    }

    @Test
    fun `major muscles count when under the range or untrained`() {
        val volume = everyMuscleAt(8.0)
        volume[Muscle.QUADS] = 4.0
        volume.remove(Muscle.CALVES)
        volume[Muscle.MID_CHEST] = 20.0
        assertEquals(setOf(Muscle.QUADS, Muscle.CALVES), coverageGaps(volume, target).toSet())
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
        assertEquals(emptyList<Muscle>(), JUDGED.filterNot { it in DRAWN })
    }
}
