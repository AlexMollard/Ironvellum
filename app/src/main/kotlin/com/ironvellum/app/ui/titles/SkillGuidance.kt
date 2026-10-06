package com.ironvellum.app.ui.titles

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ironvellum.app.domain.SkillPractice
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.components.plural

/** The first prerequisite still to master; null when the technique is open. */
internal fun Skills.SkillDef.firstUnmetPrerequisite(mastered: Set<String>): String? =
    prerequisites.firstOrNull { it !in mastered }

/**
 * Pure decisions behind the technique screens: what the attempt stepper
 * starts on, whether logged evidence clears a standard, and where a path's
 * frontier is. Kept out of the composables so each rule has a test.
 */
object SkillGuidance {

    /** One logged effort: reps, seconds or metres, and any load on top. */
    data class Effort(val value: Int, val weightKg: Double?)

    /**
     * The load a standard asks for, in kg, for a lifter of [bodyweightKg]
     * ([Skills.loadBar]'s share of it); null when it asks for none or the
     * bodyweight is unknown.
     */
    fun requiredKg(skill: Skills.SkillDef, bodyweightKg: Double?, female: Boolean = false): Double? {
        val bar = Skills.loadBar(skill.name, female) ?: return null
        return bodyweightKg?.takeIf { it > 0.0 }?.let { bar.share * it }
    }

    /**
     * A load set as a share of bodyweight needs the bodyweight to be judged;
     * without it there is no verdict, and the person claiming decides. Any
     * unloaded standard that still reads as a bodyweight multiple, and any
     * without a figure, is left to them too.
     */
    fun judgeable(
        skill: Skills.SkillDef,
        bodyweightKg: Double? = null,
        female: Boolean = false,
    ): Boolean {
        if (skill.target <= 0) return false
        if (Skills.loadBar(skill.name, female) != null) return bodyweightKg != null && bodyweightKg > 0.0
        return !skill.standard.contains("bodyweight", ignoreCase = true)
    }

    /** Whether one effort meets the standard; always false for an unjudgeable one. */
    fun clears(
        skill: Skills.SkillDef,
        effort: Effort,
        bodyweightKg: Double? = null,
        female: Boolean = false,
    ): Boolean = judgeable(skill, bodyweightKg, female) &&
        Skills.meetsStandard(skill, effort.value, effort.weightKg, bodyweightKg, female) == true

    /** True when any logged effort meets the standard. Never claims: it only tells. */
    fun cleared(
        skill: Skills.SkillDef,
        efforts: List<Effort>,
        bodyweightKg: Double? = null,
        female: Boolean = false,
    ): Boolean = efforts.any { clears(skill, it, bodyweightKg, female) }

    /**
     * What the attempt stepper starts on: the most recent logged attempt, or
     * zero when there is none. Starting on the target read "100%" before a
     * single attempt existed.
     */
    fun defaultAttempt(entries: List<SkillPractice>): Int =
        entries.filterNot { it.claimed }.maxByOrNull { it.practicedAtMs }?.value ?: 0

    /** A figure with its unit, spaced for reps ("8 reps") and tight for "40s" and "20m". */
    fun withUnit(value: Int, skill: Skills.SkillDef): String =
        if (skill.metric == Skills.Metric.REPS) "$value ${plural(value, "rep", "reps")}" else "$value${skill.unit}"

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
    fun progressCue(
        skill: Skills.SkillDef,
        best: Effort?,
        bodyweightKg: Double? = null,
        female: Boolean = false,
    ): String? {
        if (best == null || best.value <= 0 || !judgeable(skill, bodyweightKg, female)) return null
        val load = best.weightKg?.takeIf { it > 0.0 && Skills.loadBar(skill.name, female) != null }
        return "best ${best.value}/${withUnit(skill.target, skill)}" +
            (load?.let { " @ ${formatLoad(it)}kg" } ?: "")
    }

    /**
     * The effort to show as "best". A bare rep count is only comparable on a
     * bodyweight standard; on a loaded one 12 reps with no bar is not better
     * than 5 at 100 kg, so an effort that clears the standard wins, then the
     * heavier one, then the higher figure.
     */
    fun bestEffort(
        skill: Skills.SkillDef,
        efforts: List<Effort>,
        bodyweightKg: Double? = null,
        female: Boolean = false,
    ): Effort? {
        if (Skills.loadBar(skill.name, female) == null) return efforts.maxByOrNull { it.value }
        return efforts.maxWithOrNull(
            compareBy<Effort>(
                { clears(skill, it, bodyweightKg, female) },
                { it.weightKg ?: 0.0 },
                { it.value },
            ),
        )
    }

    /** An effort as the lifter reads it: "5 reps @ 100kg", or "40s" for an unloaded one. */
    fun effortText(skill: Skills.SkillDef, effort: Effort, female: Boolean = false): String {
        val load = effort.weightKg?.takeIf { it > 0.0 && Skills.loadBar(skill.name, female) != null }
        return withUnit(effort.value, skill) + (load?.let { " @ ${formatLoad(it)}kg" } ?: "")
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
