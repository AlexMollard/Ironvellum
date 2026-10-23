package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Setup's preview, the builder's preview and Muscles > Coverage all read [coverageMapModel]. */
class CoverageMapStateTest {

    private val week = listOf(
        PlannedPreset(
            name = "Push", note = "", scheduledDay = 1,
            entries = listOf(PlannedEntry("Bench Press", sets = 30, reps = 8, targetWeightKg = null)),
        ),
    )

    @Test
    fun `previewAndMusclesShareOneStateRule`() {
        val preview = coverageMapModel(previewCoverage(week).plannedPresets, dismissed = emptySet())
        val muscles = coverageMapModel(week, dismissed = emptySet())
        assertEquals(preview, muscles)
        assertEquals(coverageTally(week), muscles.tally)
    }

    @Test
    fun `states cover trained below untrained and heavy`() {
        val tally = coverageMapModel(week, dismissed = emptySet()).tally
        assertEquals(CoverageState.HEAVY, tally.states[Muscle.MID_CHEST])
        assertEquals(CoverageState.NOT_TRAINED, tally.states[Muscle.CALVES])
        assertTrue(tally.heavy.isNotEmpty())
    }

    @Test
    fun `a dismissed heavy flag reads as covered`() {
        val tally = coverageMapModel(week, dismissed = setOf(Muscle.MID_CHEST)).tally
        assertEquals(CoverageState.COVERED, tally.states[Muscle.MID_CHEST])
    }

}
