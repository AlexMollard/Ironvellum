package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.SkillPractice
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.titles.SkillGuidance.Effort
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Path tile status, the load-aware "best" cue, the attempt stepper and the Journal heatmap summary. */
class CodexUiTest {

    // ---- path tile status

    @Test
    fun `a path whose open techniques wait on other paths is locked, not complete`() {
        // Front Row Hold is the Lever path's only root; every other Lever technique needs
        // Pull-up, Hollow Hold or German Hang from elsewhere.
        val mastered = setOf("Front Row Hold")
        val status = pathStatusOf("Lever", mastered)
        assertTrue(status, status.startsWith("locked: needs "))
        assertTrue(status, status != "complete")
        val (done, total) = SkillGuidance.lineProgress("Lever", mastered)
        assertEquals(1, done)
        assertTrue(total > 1)
    }

    @Test
    fun `a path reads complete only when every technique is mastered`() {
        val all = Skills.ALL.map { it.name }.toSet()
        Skills.LINES.forEach { assertEquals(it, "complete", pathStatusOf(it, all)) }
        assertEquals("complete", pathStatus(done = 3, total = 3, next = null, blocker = null))
    }

    @Test
    fun `an open technique and a bare lock read plainly`() {
        assertEquals("next: Pull-up", pathStatus(1, 5, "Pull-up", null))
        assertEquals("locked", pathStatus(1, 5, null, null))
        assertEquals(
            "locked: needs Pull-up (Pull)",
            pathStatus(1, 5, null, CrossNeed("Pull-up", "Pull")),
        )
    }

    @Test
    fun `the tile reads its status out loud`() {
        val text = tileDescription("Lever", 1, 13, "locked: needs Pull-up (Pull)", recent = false)
        assertEquals("Lever path, 1 of 13 mastered, locked: needs Pull-up (Pull)", text)
    }

    // ---- best effort accounts for load

    private val squat = Skills.forName("Back Squat")!!

    @Test
    fun `on a loaded standard the loaded effort beats a higher unloaded rep count`() {
        val efforts = listOf(Effort(12, null), Effort(5, 100.0))
        assertEquals(Effort(5, 100.0), SkillGuidance.bestEffort(squat, efforts, bodyweightKg = 90.0))
        // without a bodyweight there is no verdict, but the load still outranks reps
        assertEquals(Effort(5, 100.0), SkillGuidance.bestEffort(squat, efforts))
    }

    @Test
    fun `an effort that clears the standard beats a heavier one that does not`() {
        val efforts = listOf(Effort(5, 95.0), Effort(1, 140.0))
        assertEquals(Effort(5, 95.0), SkillGuidance.bestEffort(squat, efforts, bodyweightKg = 90.0))
    }

    @Test
    fun `an unloaded standard still picks the highest figure`() {
        val hold = Skills.ALL.first { Skills.loadBar(it.name) == null }
        assertEquals(Effort(30, null), SkillGuidance.bestEffort(hold, listOf(Effort(10, null), Effort(30, null))))
    }

    @Test
    fun `the tree cue carries the load of the best effort`() {
        val best = bestEfforts(
            practices = listOf(
                SkillPractice(squat.name, practicedAtMs = 1, value = 12),
                SkillPractice(squat.name, practicedAtMs = 2, value = 5, weightKg = 100.0),
            ),
            training = emptyMap(),
            bodyweightKg = 90.0,
        )
        assertEquals(Effort(5, 100.0), best[squat.name])
        assertEquals("best 5/5 reps @ 100kg", SkillGuidance.progressCue(squat, best[squat.name], 90.0))
        assertEquals("5 reps @ 100kg", SkillGuidance.effortText(squat, Effort(5, 100.0)))
    }

    // ---- attempt stepper

    private val hold = Skills.ALL.first { it.metric == Skills.Metric.SECONDS }
    private val reps = Skills.ALL.first { it.metric == Skills.Metric.REPS }

    private fun walkUp(skill: Skills.SkillDef, taps: Int): Int =
        (1..taps).fold(0) { v, _ -> v + stepUp(skill, v) }

    @Test
    fun `a hold can be entered to the second and a minute is still a few taps`() {
        assertEquals(11, walkUp(hold, 11))
        assertEquals(20, walkUp(hold, 20))
        assertEquals(25, walkUp(hold, 21))
        assertEquals(60, walkUp(hold, 28))
        assertEquals(19, 20 - stepDown(hold, 20))
        assertEquals(20, 25 - stepDown(hold, 25))
        assertEquals(1, stepUp(reps, 40))
        assertEquals(1, stepDown(reps, 40))
    }

    @Test
    fun `a bar load rounds to a plate step`() {
        assertEquals(90.0, roundToPlate(90.0), 0.0)
        assertEquals(92.5, roundToPlate(91.4), 0.0)
        assertEquals(30.0, roundToPlate(30.0 - 0.9), 0.0)
    }

    // ---- journal heatmap

    @Test
    fun `the heatmap reads out as one sentence`() {
        val today = LocalDate.of(2026, 9, 30)
        val start = today.minusWeeks(11)
        val byDay = mapOf(
            LocalDate.of(2026, 9, 3) to 4,
            LocalDate.of(2026, 9, 10) to 2,
            LocalDate.of(2026, 9, 12) to 4, // tied for most: the later day is named
            start.minusDays(1) to 9, // outside the window
        )
        val text = heatmapSummary(byDay, start, today)
        assertTrue(text, text.startsWith("12 weeks: 3 days practised, most 4 attempts on 12 "))
        assertEquals("12 weeks: no attempts logged", heatmapSummary(emptyMap(), start, today))
        assertTrue(heatmapSummary(mapOf(today to 1), start, today).contains("1 day practised, most 1 attempt on"))
    }

    // ---- tree label lines

    @Test
    fun `measured label lines grow the rows they sit in`() {
        val layout = treeLayout("Pull", columns = 4)
        val guess = treeMetrics(layout, cellW = 82f, lineH = 12f)
        val threeLines = treeMetrics(layout, cellW = 82f, lineH = 12f) { 3 }
        assertTrue(threeLines.levelLines.all { it == 3 })
        assertTrue(threeLines.tops.last() > guess.tops.last())
    }

    // ---- locked deed ring

    @Test
    fun `the locked ring holds three to one on the panel`() {
        assertTrue(SkillGuidance.contrast(LockedRung, RungPanelTop) >= 3.0)
    }
}
