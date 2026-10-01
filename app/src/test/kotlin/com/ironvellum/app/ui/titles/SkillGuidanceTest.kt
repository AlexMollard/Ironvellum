package com.ironvellum.app.ui.titles

import com.ironvellum.app.data.SkillTrainingEvidence
import com.ironvellum.app.domain.SkillPractice
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.components.Term
import com.ironvellum.app.ui.components.termsIn
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.titles.SkillGuidance.Effort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every case reads its techniques out of [Skills], so new or re-tiered skills keep it honest. */
class SkillGuidanceTest {

    private val timedHold = Skills.ALL.first {
        it.metric == Skills.Metric.SECONDS && SkillGuidance.judgeable(it) && SkillGuidance.requiredAddedKg(it) == null
    }
    private val addedLoad = Skills.ALL.first { SkillGuidance.requiredAddedKg(it) != null && SkillGuidance.judgeable(it) }
    private val bodyweightBar = Skills.ALL.first { it.standard.contains("bodyweight", ignoreCase = true) }

    // ---- the attempt default

    @Test
    fun `no attempts starts the stepper at zero and reads no attempt yet`() {
        assertEquals(0, SkillGuidance.defaultAttempt(emptyList()))
        val readout = SkillGuidance.attemptReadout(timedHold, attempt = 0, touched = false)
        assertEquals("No attempt yet", readout)
        assertFalse("an unperformed attempt must not read as a percentage", readout.contains("%"))
    }

    @Test
    fun `the stepper starts on the most recent attempt, not the best and not a claim`() {
        val entries = listOf(
            SkillPractice(timedHold.name, practicedAtMs = 100, value = 50),
            SkillPractice(timedHold.name, practicedAtMs = 300, value = 20),
            SkillPractice(timedHold.name, practicedAtMs = 400, claimed = true),
        )
        assertEquals(20, SkillGuidance.defaultAttempt(entries))
    }

    @Test
    fun `a set attempt reads as a share of the standard, capped at 100`() {
        val t = timedHold.target
        val half = t / 2
        assertEquals("${half * 100 / t}% of the ${t}s standard", SkillGuidance.attemptReadout(timedHold, half, true))
        assertTrue(SkillGuidance.attemptReadout(timedHold, t * 3, true).startsWith("100%"))
    }

    // ---- the "cleared" decision

    @Test
    fun `an effort at the standard clears it and one short of it does not`() {
        assertTrue(SkillGuidance.cleared(timedHold, listOf(Effort(timedHold.target, null))))
        assertFalse(SkillGuidance.cleared(timedHold, listOf(Effort(timedHold.target - 1, null))))
        assertFalse(SkillGuidance.cleared(timedHold, emptyList()))
    }

    @Test
    fun `an added-load standard needs the load as well as the count`() {
        val need = SkillGuidance.requiredAddedKg(addedLoad)!!
        assertFalse(SkillGuidance.cleared(addedLoad, listOf(Effort(addedLoad.target, null))))
        assertFalse(SkillGuidance.cleared(addedLoad, listOf(Effort(addedLoad.target, need - 2.5))))
        assertTrue(SkillGuidance.cleared(addedLoad, listOf(Effort(addedLoad.target, need))))
    }

    @Test
    fun `a bodyweight-multiple standard is never declared cleared from a rep count`() {
        assertFalse(SkillGuidance.judgeable(bodyweightBar))
        assertFalse(SkillGuidance.cleared(bodyweightBar, listOf(Effort(100, 500.0))))
        assertNull(SkillGuidance.progressCue(bodyweightBar, Effort(5, 100.0)))
    }

    @Test
    fun `the row cue reads best over the standard`() {
        assertEquals(
            "best 10/${timedHold.target}s",
            SkillGuidance.progressCue(timedHold, Effort(10, null)),
        )
        assertNull(SkillGuidance.progressCue(timedHold, null))
    }

    // ---- frontier and NEXT

    @Test
    fun `with nothing mastered the frontier is every technique without a prerequisite`() {
        for (line in Skills.LINES) {
            val ordered = treeOrder(line)
            assertEquals(
                line,
                ordered.filter { it.prerequisites().isEmpty() },
                SkillGuidance.frontier(ordered, emptySet()),
            )
        }
    }

    @Test
    fun `mastering a technique moves the frontier to what it opens`() {
        // Only children that need nothing else: one with a second prerequisite stays locked.
        fun opens(root: Skills.SkillDef) =
            Skills.ALL.filter { it.line == root.line && it.prerequisites() == listOf(root.name) }
        val root = Skills.ALL.first { it.prerequisites().isEmpty() && opens(it).isNotEmpty() }
        val frontier = SkillGuidance.frontier(treeOrder(root.line), setOf(root.name))
        assertFalse(root in frontier)
        assertTrue(opens(root).all { it in frontier })
    }

    @Test
    fun `the tree order holds every technique on the path once`() {
        for (line in Skills.LINES) {
            val names = treeOrder(line).map { it.name }
            assertEquals(Skills.ALL.filter { it.line == line }.map { it.name }.toSet(), names.toSet())
            assertEquals(names.size, names.distinct().size)
        }
    }

    // ---- per-path progress and the opening path

    @Test
    fun `path progress counts mastered techniques on that path only`() {
        val line = Skills.LINES.first()
        val onLine = Skills.ALL.filter { it.line == line }
        val elsewhere = Skills.ALL.first { it.line != line }
        assertEquals(0 to onLine.size, SkillGuidance.lineProgress(line, emptySet()))
        assertEquals(
            1 to onLine.size,
            SkillGuidance.lineProgress(line, setOf(onLine.first().name, elsewhere.name)),
        )
    }

    @Test
    fun `the tree opens on the path of the newest attempt or claim`() {
        val older = Skills.ALL.first()
        val newer = Skills.ALL.first { it.line != older.line }
        val log = listOf(
            SkillPractice(older.name, practicedAtMs = 100, value = 5),
            SkillPractice(newer.name, practicedAtMs = 200, claimed = true),
            SkillPractice("Retired Technique", practicedAtMs = 300, value = 1),
        )
        assertEquals(newer.line, SkillGuidance.initialLine(log))
        assertNull(SkillGuidance.initialLine(emptyList()))
    }

    // ---- evidence, contrast, terms, row description

    @Test
    fun `the best effort is the higher of practice and training`() {
        val best = bestEfforts(
            practices = listOf(SkillPractice(timedHold.name, practicedAtMs = 1, value = 10)),
            training = mapOf(timedHold.name.lowercase() to SkillTrainingEvidence(25, null, 2)),
        )
        assertEquals(Effort(25, null), best[timedHold.name])
    }

    @Test
    fun `locked text and dots stay readable against their background`() {
        assertTrue(SkillGuidance.contrast(IronvellumColors.InkMuted, LockedRowBg) >= 4.5)
        assertTrue(SkillGuidance.contrast(LockedDot, IronvellumColors.Abyss) >= 3.0)
    }

    @Test
    fun `jargon in a technique's copy finds its explainer`() {
        assertTrue(Term.TUCK in termsIn("Hold 15s, knees tucked"))
        assertTrue(Term.SCAPULA in termsIn("full scapular depression"))
        assertTrue(Term.NEGATIVE in termsIn("5 single-arm negatives per side"))
        assertEquals(emptyList<Term>(), termsIn("Hang 60s from a bar"))
    }

    @Test
    fun `a row describes its state in words`() {
        val locked = Skills.ALL.first { it.prerequisites().isNotEmpty() }
        val text = rowDescription(locked, mastered = false, unlocked = false, needs = locked.prerequisites().first(), cue = null)
        assertTrue(text, text.startsWith("${locked.name}, tier ${Skills.tierLabel(locked.tier)}, locked, needs "))
        assertTrue(rowDescription(timedHold, true, true, null, null).endsWith("mastered"))
    }
}
