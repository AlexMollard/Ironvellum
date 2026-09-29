package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.VolumeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Train card's "N SHORT OR MISSING" and the coverage screen's flags: a
 * major muscle counts when under its range or untrained. A helper (front
 * delts, forearms, the rotator cuff and the rest) trained but below its
 * floor reads LIGHT and is never a gap - the floor is a convention, and a
 * lifter's goal outranks it - and it is never over, because a helper has no
 * ceiling. A helper at zero sets is untrained like any muscle, and a gap.
 */
class CoverageGapsTest {

    private val target = CoverageGoal(VolumeLevel.STANDARD, TrainingFocus.STRENGTH)

    private fun everyMuscleAt(sets: Double): MutableMap<Muscle, Double> =
        Muscle.entries.associateWith { sets }.toMutableMap()

    @Test
    fun `a trained helper under its floor reads light and is never a gap`() {
        val volume = everyMuscleAt(8.0)
        volume[Muscle.LOWER_BACK] = ProgramRules.HELPER_FLOOR_SETS - 0.5
        volume[Muscle.ROTATOR_CUFF] = 0.5
        volume[Muscle.QUADS] = 4.0
        assertEquals(listOf(Muscle.QUADS), coverageGaps(volume, target))
        assertEquals(CoverageLevel.LIGHT, levelOf(Muscle.LOWER_BACK, 2.5, target))
        assertEquals(CoverageLevel.LIGHT, levelOf(Muscle.ROTATOR_CUFF, 0.5, target))
        assertEquals(CoverageLevel.IN_RANGE, levelOf(Muscle.LOWER_BACK, ProgramRules.HELPER_FLOOR_SETS, target))
        // A major muscle short of its range is still under, never light.
        assertEquals(CoverageLevel.UNDER, levelOf(Muscle.QUADS, 4.0, target))
    }

    @Test
    fun `an untrained helper reads none and is a gap`() {
        // Tibialis and abductors at zero read LIGHT while the Train card said
        // ALL COVERED: a whole muscle group with no work is a gap.
        val volume = everyMuscleAt(8.0)
        volume.remove(Muscle.TIBIALIS)
        volume[Muscle.ABDUCTORS] = 0.0
        assertEquals(CoverageLevel.NONE, levelOf(Muscle.TIBIALIS, 0.0, target))
        assertEquals(setOf(Muscle.TIBIALIS, Muscle.ABDUCTORS), coverageGaps(volume, target).toSet())
    }

    @Test
    fun `a helper is never over - it has a floor and no ceiling`() {
        val volume = everyMuscleAt(8.0)
        volume[Muscle.FRONT_DELTS] = 30.0
        assertTrue(coverageGaps(volume, target).isEmpty())
        assertEquals(CoverageLevel.IN_RANGE, levelOf(Muscle.FRONT_DELTS, 30.0, target))
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
        assertEquals(emptyList<Muscle>(), JUDGED.filterNot { it in DRAWN })
    }
}
