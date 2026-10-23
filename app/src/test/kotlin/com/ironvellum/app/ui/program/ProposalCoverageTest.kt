package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Forge chip ("2 below minimum") and the coverage page it opens count the UNSAVED proposal through one
 * function, [coverageTally], so they cannot disagree.
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

    @Test
    fun `the Forge chip and the preview page agree on the tally`() {
        // The chip: what CoverageChip reads from the proposal.
        val chip = coverageTally(sample)
        // The page: what CoveragePreviewScreen reads from the same proposal as caller data.
        val ui = previewCoverage(sample)
        val page = coverageTally(loadsOf(ui.plannedPresets))
        assertEquals(chip, ui.week.tally())

        assertEquals(chip, page)
        assertEquals(coverageHeadline(chip), coverageHeadline(page))
        // And it is the domain's own coverage rule, not a second one.
        val domain = ProgramRules.coverage(sample)
        val below = domain.values.count { it.weekly > 0.0 && it.weekly < ProgramRules.COVERED_MIN && it.muscle in ProgramRules.TRACKED }
        assertEquals(below, chip.below.size)
        // One push day leaves most of the body short, so the check is not vacuous.
        assertTrue(chip.gaps > 0)
    }

    @Test
    fun `the preview reads the proposal, so a first run with no saved rites is not empty`() {
        val ui = previewCoverage(sample)
        assertTrue(ui.hasAnyPreset)
        assertEquals(sample, ui.plannedPresets)
        assertTrue(ui.donePresets.isEmpty())
        assertTrue("nothing is done in a proposal", ui.week.muscles.values.all { it.done == 0.0 })
    }

    @Test
    fun `the Train card counts the same gaps the page does`() {
        // PresetsScreen reads coverageTally(ProgramRules.coverage(...)).gaps; the page reads loadsOf(...).
        assertEquals(coverageTally(ProgramRules.coverage(sample)).gaps, coverageTally(loadsOf(sample)).gaps)
    }
}
