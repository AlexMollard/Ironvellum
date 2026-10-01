package com.ironvellum.app.domain

/**
 * The modifiers the live-trial picker offers for [exercise], in display order.
 *
 * Derived from what the app already knows about the movement - its metric,
 * whether it is bodyweight, and its [MuscleMap] pattern - never from a list
 * per exercise, so a new catalogue row is covered the day it is seeded.
 * Every token here is one [MovementDifficulty.modifierFactor] prices.
 *
 * Never offered: "weighted" (set by load, [modifiersAfterLoadChange]),
 * "banded" (it cannot mean one thing, so it scores nothing) and "hold
 * seconds" (the metric decides a hold now).
 *
 * - tempo, paused: any counted strength movement.
 * - assisted: bodyweight pushes and pulls.
 * - archer, one-arm: bodyweight pulls and push-ups, not technique work.
 * - incline, decline, elevated: push-ups - a bench press has its own rows.
 * - deficit: deadlifts, push-ups, split squats and lunges, calf raises.
 *
 * A word the name already carries is not offered again: a Paused Bench
 * Press has no "paused", an Archer Push-up neither "archer" nor "one-arm".
 */
fun applicableModifiers(exercise: Exercise): List<String> {
    if (exercise.metric != ExerciseMetric.REPS || exercise.category.isNotEmpty()) return emptyList()
    val name = exercise.name.trim().lowercase()
    // The gym lines' upper tiers are barbell lifts. They are seeded weighted
    // now, and the load-priced flag also says so for any row an older build
    // seeded unweighted.
    val bodyweight = !exercise.isWeighted && !MovementDifficulty.isLoadPriced(exercise.name)
    val pattern = MuscleMap.profile(exercise.name)?.pattern
    val offered = mutableSetOf("tempo", "paused")
    if (pattern == null) {
        // A movement the lifter named himself: only what fits anything.
        if (bodyweight) offered += "assisted"
    } else {
        val pull = pattern == MovementPattern.HORIZONTAL_PULL || pattern == MovementPattern.VERTICAL_PULL
        val push = pattern == MovementPattern.HORIZONTAL_PUSH || pattern == MovementPattern.VERTICAL_PUSH
        val pushUp = bodyweight && pattern == MovementPattern.HORIZONTAL_PUSH && "push-up" in name
        if (bodyweight && (push || pull)) offered += "assisted"
        // Technique work (muscle-ups, skin the cat, planche push-ups) is its
        // own progression; an archer muscle-up is not a thing.
        if ((bodyweight && pull || pushUp) && !MuscleMap.isTechnique(exercise.name)) {
            offered += listOf("archer", "one-arm")
        }
        if (pushUp) offered += ANGLES + "deficit"
        if (pattern == MovementPattern.HINGE && "deadlift" in name) offered += "deficit"
        if (pattern == MovementPattern.LUNGE && "step-up" !in name) offered += "deficit"
        if ("calf raise" in name) offered += "deficit"
    }
    val named = buildSet {
        MODIFIER_ORDER.filterTo(this) { it in name }
        if ("pause" in name) add("paused")
        if ("one arm" in name || "one-arm" in name || "archer" in name) addAll(listOf("archer", "one-arm"))
        // An angle is already chosen: a feet-up Incline Push-up cancels out.
        if (ANGLES.any { it in name }) addAll(ANGLES)
    }
    return MODIFIER_ORDER.filter { it in offered && it !in named }
}

private val ANGLES = listOf("incline", "decline", "elevated")

private val MODIFIER_ORDER = listOf(
    "assisted", "archer", "one-arm", "incline", "decline", "elevated", "deficit", "tempo", "paused",
)
