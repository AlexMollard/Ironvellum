package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.MuscleArea
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.VolumeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Forge row ("N muscles short") and the coverage page it opens count the UNSAVED proposal through
 * one function, [shortCount], so they cannot disagree.
 */
class ProposalCoverageTest {

    private val sample = listOf(
        PlannedPreset(
            name = "Push",
            note = "",
            scheduledDay = 1,
            entries = listOf(
                PlannedEntry("Bench Press", sets = 4, reps = 8, targetWeightKg = null),
                PlannedEntry("Overhead Press", sets = 3, reps = 8, targetWeightKg = null),
            ),
        ),
    )
    private val priorities = setOf(MuscleArea.entries.first())

    @Test
    fun `the Forge row and the preview page agree on the short count`() {
        val tier = VolumeLevel.STANDARD
        val focus = TrainingFocus.MUSCLE

        // The row: what CoverageSummaryRow computes from the proposal.
        val rowShort = shortCount(sample, coverageGoal(tier, focus, priorities))
        // The page: what CoveragePreviewScreen computes from the same proposal as caller data.
        val ui = previewCoverage(sample, tier, focus, priorities)
        val pageShort = shortCount(ui.plannedPresets, CoverageGoal(ui.tier, ui.focus, ui.priorities))

        assertEquals(rowShort, pageShort)
        assertEquals(shortHeadline(rowShort), shortHeadline(pageShort))
        // And it is the domain's own gap rule, not a second one.
        val goal = coverageGoal(tier, focus, priorities)
        assertEquals(coverageGaps(ProgramRules.weeklyVolume(sample), goal).size, rowShort)
        // One push day leaves most of the body short, so the check is not vacuous.
        assertTrue(rowShort > 0)
    }

    @Test
    fun `the preview reads the proposal, so a first run with no saved rites is not empty`() {
        val ui = previewCoverage(sample, VolumeLevel.LOW, TrainingFocus.MUSCLE, emptySet())
        assertTrue(ui.hasAnyPreset)
        assertEquals(sample, ui.plannedPresets)
        assertTrue(ui.loggedPresets.isEmpty())
    }

    @Test
    fun `headline words match the row`() {
        assertEquals("Every muscle covered", shortHeadline(0))
        assertEquals("1 muscle short", shortHeadline(1))
        assertEquals("5 muscles short", shortHeadline(5))
    }
}
