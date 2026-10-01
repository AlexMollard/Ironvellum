package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.theme.IronvellumColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillGlyphTest {

    @Test
    fun `every path has a default family and every skill maps to one`() {
        for (line in Skills.LINES) assertTrue("no glyph family for path $line", line in GlyphByLine)
        Skills.ALL.forEach { glyphFamily(it) }
    }

    @Test
    fun `every family is worn by some technique`() {
        val worn = Skills.ALL.map(::glyphFamily).toSet()
        assertEquals(GlyphFamily.entries.toSet(), worn)
    }

    @Test
    fun `techniques whose movement is not their path's override it`() {
        fun family(name: String) = glyphFamily(Skills.forName(name)!!)
        assertEquals(GlyphFamily.PULL, family("Pull-up"))
        assertEquals(GlyphFamily.HANG, family("Dead Hang"))
        assertEquals(GlyphFamily.HANG, family("German Hang"))
        assertEquals(GlyphFamily.HOLD, family("Front Lever"))
        assertEquals(GlyphFamily.HOLD, family("Full Planche"))
        assertEquals(GlyphFamily.HOLD, family("Human Flag"))
        assertEquals(GlyphFamily.RINGS, family("Iron Cross"))
        assertEquals(GlyphFamily.HINGE, family("Deadlift"))
        assertEquals(GlyphFamily.HINGE, family("Nordic Curl"))
        assertEquals(GlyphFamily.SQUAT, family("Pistol Squat"))
        assertEquals(GlyphFamily.MOBILITY, family("Handstand-to-Bridge"))
        assertEquals(GlyphFamily.CORE, family("L-sit"))
        assertEquals(GlyphFamily.INVERSION, family("Freestanding Handstand"))
        assertEquals(GlyphFamily.PUSH, family("Bench Press"))
        assertEquals(GlyphFamily.DIP, family("Parallel Bar Dip"))
        assertEquals(GlyphFamily.RINGS, family("Ring Dip"))
    }

    @Test
    fun `node text and badges stay readable on the page`() {
        val page = IronvellumColors.Abyss
        // labels sit on the page-coloured plate
        assertTrue(SkillGuidance.contrast(IronvellumColors.InkMuted, page) >= 4.5)
        assertTrue(SkillGuidance.contrast(IronvellumColors.Ink, page) >= 4.5)
        assertTrue(SkillGuidance.contrast(IronvellumColors.SovereignGold, page) >= 4.5)
        // the tier chip's numeral, on the locked (dimmest) chip fill
        assertTrue(SkillGuidance.contrast(IronvellumColors.Abyss, IronvellumColors.InkMuted) >= 4.5)
        // the lock badge icon and ring
        assertTrue(SkillGuidance.contrast(IronvellumColors.InkMuted, page) >= 3.0)
        assertTrue(SkillGuidance.contrast(LockedDot, page) >= 3.0)
        // the mastered glyph, dark ink on the gold disc
        assertTrue(SkillGuidance.contrast(IronvellumColors.Abyss, IronvellumColors.SovereignGold) >= 4.5)
    }
}
