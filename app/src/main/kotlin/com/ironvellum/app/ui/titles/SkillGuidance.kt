package com.ironvellum.app.ui.titles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ironvellum.app.domain.SkillPractice
import com.ironvellum.app.domain.Skills

/**
 * Every prerequisite of a technique. Read through here, never `requires`
 * directly, so the tree, the detail and the chips follow one rule.
 */
internal fun Skills.SkillDef.prerequisites(): List<String> = listOfNotNull(requires)

/** The first prerequisite still to master; null when the technique is open. */
internal fun Skills.SkillDef.firstUnmetPrerequisite(mastered: Set<String>): String? =
    prerequisites().firstOrNull { it !in mastered }

/**
 * Pure decisions behind the technique screens: what the attempt stepper
 * starts on, whether logged evidence clears a standard, and where a path's
 * frontier is. Kept out of the composables so each rule has a test.
 */
object SkillGuidance {

    /** One logged effort: reps, seconds or metres, and any load on top. */
    data class Effort(val value: Int, val weightKg: Double?)

    private val addedKg = Regex("""\+\s*(\d+(?:\.\d+)?)\s*kg""", RegexOption.IGNORE_CASE)

    /** The "+25 kg" a standard asks for on top of bodyweight; null when it asks for none. */
    fun requiredAddedKg(skill: Skills.SkillDef): Double? =
        addedKg.find(skill.standard)?.groupValues?.get(1)?.toDouble()

    /**
     * A standard set as a multiple of bodyweight ("1 rep at double
     * bodyweight") cannot be judged from a rep count: [Skills.SkillDef.target]
     * keeps the reps and drops the load, so one light rep would read as
     * cleared. Those, and any standard without a figure, are left to the
     * person claiming.
     */
    fun judgeable(skill: Skills.SkillDef): Boolean =
        skill.target > 0 && !skill.standard.contains("bodyweight", ignoreCase = true)

    /** Whether one effort meets the standard; always false for an unjudgeable one. */
    fun clears(skill: Skills.SkillDef, effort: Effort): Boolean {
        if (!judgeable(skill) || effort.value < skill.target) return false
        val needKg = requiredAddedKg(skill) ?: return true
        return (effort.weightKg ?: 0.0) >= needKg
    }

    /** True when any logged effort meets the standard. Never claims: it only tells. */
    fun cleared(skill: Skills.SkillDef, efforts: List<Effort>): Boolean =
        efforts.any { clears(skill, it) }

    /**
     * What the attempt stepper starts on: the most recent logged attempt, or
     * zero when there is none. Starting on the target read "100%" before a
     * single attempt existed.
     */
    fun defaultAttempt(entries: List<SkillPractice>): Int =
        entries.filterNot { it.claimed }.maxByOrNull { it.practicedAtMs }?.value ?: 0

    /** A figure with its unit, spaced for reps ("8 reps") and tight for "40s" and "20m". */
    fun withUnit(value: Int, skill: Skills.SkillDef): String =
        if (skill.metric == Skills.Metric.REPS) "$value reps" else "$value${skill.unit}"

    /**
     * The line under the stepper. Nothing touched and nothing logged reads
     * "No attempt yet" rather than a percentage of a value nobody performed.
     */
    fun attemptReadout(skill: Skills.SkillDef, attempt: Int, touched: Boolean): String {
        if (attempt <= 0 && !touched) return "No attempt yet"
        val pct = if (skill.target <= 0) 0 else ((attempt * 100) / skill.target).coerceIn(0, 100)
        return "$pct% of the ${withUnit(skill.target, skill)} standard"
    }

    /** "best 40/60s" for a tree row; null with nothing logged or a bodyweight-multiple standard. */
    fun progressCue(skill: Skills.SkillDef, best: Effort?): String? {
        if (best == null || best.value <= 0 || !judgeable(skill)) return null
        val load = best.weightKg?.takeIf { it > 0.0 && requiredAddedKg(skill) != null }
        return "best ${best.value}/${withUnit(skill.target, skill)}" +
            (load?.let { " @ ${formatLoad(it)}kg" } ?: "")
    }

    /** Available and not yet mastered: where the next claim on a path can come from. */
    fun isFrontier(skill: Skills.SkillDef, mastered: Set<String>): Boolean =
        skill.name !in mastered && Skills.unlocked(skill, mastered)

    /** The frontier of [ordered], kept in that order. */
    fun frontier(ordered: List<Skills.SkillDef>, mastered: Set<String>): List<Skills.SkillDef> =
        ordered.filter { isFrontier(it, mastered) }

    /** Mastered and total techniques on [line], for the "3/12" on the path bar. */
    fun lineProgress(line: String, mastered: Set<String>): Pair<Int, Int> {
        val skills = Skills.ALL.filter { it.line == line }
        return skills.count { it.name in mastered } to skills.size
    }

    /** The path of the most recent attempt or claim; null with nothing logged. */
    fun initialLine(log: List<SkillPractice>): String? =
        log.sortedByDescending { it.practicedAtMs }
            .firstNotNullOfOrNull { Skills.forName(it.skillName)?.line }

    /** WCAG contrast ratio of two opaque colours, 1.0 to 21.0. */
    fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }
}
