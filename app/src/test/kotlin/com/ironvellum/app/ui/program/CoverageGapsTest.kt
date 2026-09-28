package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.ProgramRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Train card's "N SHORT OR MISSING" and the coverage screen's flags: a
 * tracked muscle counts when under its range, an unjudged one (forearms,
 * lower back, front delts, adductors) only when nothing trains it at all.
 */
class CoverageGapsTest {

    private val target = 5.0..15.0

    private fun everyMuscleAt(sets: Double): MutableMap<Muscle, Double> =
        Muscle.entries.associateWith { sets }.toMutableMap()

    @Test
    fun `an unjudged muscle with no sets is a gap, with any sets it is not`() {
        val volume = everyMuscleAt(8.0)
        volume.remove(Muscle.LOWER_BACK)
        volume[Muscle.FOREARMS] = 0.5
        assertEquals(listOf(Muscle.LOWER_BACK), coverageGaps(volume, target))
    }

    @Test
    fun `an unjudged muscle is never judged against the range`() {
        // Far over the top of the range is not a gap: there is no target to exceed.
        val volume = everyMuscleAt(8.0)
        volume[Muscle.FRONT_DELTS] = 30.0
        assertTrue(coverageGaps(volume, target).isEmpty())
    }

    @Test
    fun `tracked muscles count when under the range or untrained`() {
        val volume = everyMuscleAt(8.0)
        volume[Muscle.QUADS] = 4.0
        volume.remove(Muscle.CALVES)
        volume[Muscle.CHEST] = 20.0
        assertEquals(setOf(Muscle.QUADS, Muscle.CALVES), coverageGaps(volume, target).toSet())
    }

    @Test
    fun `a pull-up week no longer reads as forearms untrained`() {
        val week = listOf(
            com.ironvellum.app.domain.PlannedPreset(
                "Pull", "", 1,
                listOf(com.ironvellum.app.domain.PlannedEntry("Pull-up", 5, 5, null)),
            ),
        )
        assertTrue((ProgramRules.weeklyVolume(week)[Muscle.FOREARMS] ?: 0.0) > 0.0)
    }
}
