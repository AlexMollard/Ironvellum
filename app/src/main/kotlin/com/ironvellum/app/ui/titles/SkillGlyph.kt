package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.Skills

/** The movement a technique is, drawn as one small pictogram on its node. */
internal enum class GlyphFamily { PUSH, DIP, PULL, HANG, INVERSION, HOLD, CORE, SQUAT, HINGE, MOBILITY, RINGS }

/** Each path's default family; a few techniques override it by name, see [glyphFamily]. */
internal val GlyphByLine: Map<String, GlyphFamily> = mapOf(
    "Push" to GlyphFamily.PUSH,
    "Bench" to GlyphFamily.PUSH,
    "Press" to GlyphFamily.PUSH,
    "Pull" to GlyphFamily.PULL,
    "Movement" to GlyphFamily.PULL,
    "Handstand" to GlyphFamily.INVERSION,
    "Lever" to GlyphFamily.HOLD,
    "Planche" to GlyphFamily.HOLD,
    "Core" to GlyphFamily.CORE,
    "Legs" to GlyphFamily.SQUAT,
    "Squat" to GlyphFamily.SQUAT,
    "Deadlift" to GlyphFamily.HINGE,
    "Mobility" to GlyphFamily.MOBILITY,
    "Rings" to GlyphFamily.RINGS,
)

/**
 * Which pictogram a technique wears. A path gives the default; the exceptions
 * are the techniques whose movement is not their path's: a hang on a pulling
 * path, a bridge on the movement path, a balance on the handstand path.
 */
internal fun glyphFamily(skill: Skills.SkillDef): GlyphFamily {
    val n = skill.name
    return when {
        n.contains("Hang") && skill.line != "Core" -> GlyphFamily.HANG
        (n.contains("Dip") || n.contains("Support Hold")) && skill.line != "Rings" -> GlyphFamily.DIP
        n == "Skin the Cat" -> GlyphFamily.HANG
        n == "Crow Pose" || n.contains("Human Flag") -> GlyphFamily.HOLD
        n == "Handstand-to-Bridge" -> GlyphFamily.MOBILITY
        n == "Hamstring Bridge" || n.startsWith("Nordic") -> GlyphFamily.HINGE
        else -> GlyphByLine[skill.line] ?: GlyphFamily.HOLD
    }
}
