package com.ironvellum.app.domain

/** The three movement patterns Strength Rank averages over. */
enum class Pattern(val label: String) {
    PULL("Pull"),
    PUSH("Push"),
    LEGS("Legs"),
}

/**
 * Which pattern each TIERED lift and each counted skill-tree skill feeds.
 *
 * Core and Mobility never count, so core work cannot lower the rank. The gym
 * lines (Squat, Bench, Press, Deadlift) and the two weighted skills are
 * bodyweight-multiple standards the lift route already measures by e1RM. A
 * distance standard has no set figure to read.
 */
object RankPatterns {

    private val LIFTS: Map<Lift, Pattern> = mapOf(
        Lift.PULL_UP to Pattern.PULL,
        Lift.DIP to Pattern.PUSH,
        Lift.BENCH to Pattern.PUSH,
        Lift.OVERHEAD_PRESS to Pattern.PUSH,
        Lift.SQUAT to Pattern.LEGS,
        Lift.DEADLIFT to Pattern.LEGS,
    )

    private val LINES: Map<String, Pattern> = mapOf(
        "Pull" to Pattern.PULL,
        "Lever" to Pattern.PULL,
        "Push" to Pattern.PUSH,
        "Handstand" to Pattern.PUSH,
        "Planche" to Pattern.PUSH,
        "Legs" to Pattern.LEGS,
    )

    /** Rings and Movement mix patterns, so they are placed skill by skill. */
    private val SKILLS: Map<String, Pattern> = mapOf(
        "Ring Row" to Pattern.PULL,
        "Ring Muscle-up" to Pattern.PULL,
        "Banded Iron Cross" to Pattern.PULL,
        "Iron Cross" to Pattern.PULL,
        "Muscle-up" to Pattern.PULL,
        "Strict Muscle-up" to Pattern.PULL,
        "Inverted Muscle-up" to Pattern.PULL,
        "Tuck Human Flag" to Pattern.PULL,
        "Human Flag" to Pattern.PULL,
        "Ring Support Hold" to Pattern.PUSH,
        "RTO Support Hold" to Pattern.PUSH,
        "Ring Dip" to Pattern.PUSH,
    )

    val EXCLUDED_LINES: Set<String> = setOf("Core", "Mobility", "Squat", "Bench", "Press", "Deadlift")

    val EXCLUDED_SKILLS: Set<String> = setOf("Weighted Pull-up", "Weighted Dip", "Kip-up", "Handstand-to-Bridge")

    fun forLift(lift: Lift): Pattern? = LIFTS[lift]

    fun forSkill(skill: Skills.SkillDef): Pattern? = when {
        skill.name in EXCLUDED_SKILLS || skill.line in EXCLUDED_LINES -> null
        skill.metric == Skills.Metric.METRES -> null
        else -> SKILLS[skill.name] ?: LINES[skill.line]
    }

    /** The counted skills in catalogue order, each with its pattern. */
    val COUNTED: List<Pair<Skills.SkillDef, Pattern>> =
        Skills.ALL.mapNotNull { skill -> forSkill(skill)?.let { skill to it } }
}
