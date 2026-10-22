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
 * Words that rule a modifier out are trimmed too ([notOffered]): a wall
 * push-up is already the easy form, a walking lunge has no deficit.
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
    offered -= notOffered(name, MuscleMap.isTechnique(exercise.name))
    val named = buildSet {
        MODIFIER_ORDER.filterTo(this) { it in name }
        if ("pause" in name) add("paused")
        if ("one arm" in name || "one-arm" in name || "archer" in name) addAll(listOf("archer", "one-arm"))
        // An angle is already chosen: a feet-up Incline Push-up cancels out.
        if (ANGLES.any { it in name }) addAll(ANGLES)
    }
    return MODIFIER_ORDER.filter { it in offered && it !in named }
}

/**
 * What the pattern rules above would offer but a word in the [name] says makes no sense: a band is
 * already the load, a wall or knee push-up is already the easy form, a diamond or typewriter push-up's
 * hand placement is the point, and a lunge that travels or a squat already on a box has no deficit.
 */
private fun notOffered(name: String, technique: Boolean): Set<String> = buildSet {
    val pushUp = "push-up" in name
    if ("banded" in name) addAll(listOf("assisted", "archer", "one-arm"))
    if ("walk" in name) add("assisted")
    if ("lever" in name || "commando" in name) addAll(listOf("archer", "one-arm"))
    if (pushUp) {
        if (REGRESSED_PUSH_UPS.any { it in name }) addAll(listOf("archer", "one-arm", "deficit") + ANGLES)
        if ("wall" in name) add("assisted")
        if (FIXED_HAND_PUSH_UPS.any { it in name }) addAll(listOf("archer", "one-arm"))
        if ("hindu" in name || technique) addAll(listOf("deficit") + ANGLES)
        if ("incline" in name) add("deficit")
    }
    if (DEFICIT_NEVER.any { it in name }) add("deficit")
    val lunge = "lunge" in name || "split squat" in name
    val deadlift = "deadlift" in name
    val calf = "calf raise" in name
    if (!lunge && !deadlift && !calf && !pushUp) add("deficit")
}

private val REGRESSED_PUSH_UPS = listOf("wall", "knee", "ball")
private val FIXED_HAND_PUSH_UPS = listOf("diamond", "typewriter", "hindu", "shoulder tap", "explosive")
private val DEFICIT_NEVER = listOf(
    "walking", "lateral", "curtsy", "to curl", "front-foot elevated", "side deadlift",
    "bodyweight deadlift", "volume deadlift", "heavy deadlift", "leg press", "banded", "rocking",
)

private val ANGLES = listOf("incline", "decline", "elevated")

private val MODIFIER_ORDER = listOf(
    "assisted", "archer", "one-arm", "incline", "decline", "elevated", "deficit", "tempo", "paused",
)

/** A comma-separated modifier string as its tokens: trimmed, in order, a repeat (any case) dropped. */
fun modifierTokens(modifiers: String?): List<String> =
    modifiers.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

/** How a chip that is set stands against the movement it is on. */
enum class ModifierFlag {
    /** Offered for the movement. */
    NONE,

    /** A modifier the app knows and prices, but not one this movement offers (a deficit on a bench press). */
    NOT_APPLICABLE,

    /** A word the app does not know (a typo, a legacy "banded"): it scores nothing. */
    UNKNOWN,
}

/** One chip of the modifier picker: the token, whether it is set, and, if it is set though not offered, why it is flagged. */
data class ModifierChip(val token: String, val selected: Boolean, val flag: ModifierFlag = ModifierFlag.NONE)

/**
 * The chips the one modifier picker shows - the live trial's dialog and the rite editor alike - for a movement
 * that offers [offered] ([applicableModifiers]) and is done with [current].
 *
 * Every offered token first, set or not; then each token already set that is not offered, set and flagged so
 * it can be removed but is never dropped behind the lifter's back. "weighted" has no chip: the load sets it
 * ([modifiersFollowingLoad]).
 */
fun modifierChips(offered: List<String>, current: String): List<ModifierChip> {
    val tokens = modifierTokens(current)
    val offeredChips = offered.map { token -> ModifierChip(token, tokens.any { it.equals(token, ignoreCase = true) }) }
    val strays = tokens
        .filter { token -> !token.equals(WEIGHTED_MODIFIER, ignoreCase = true) && offered.none { it.equals(token, ignoreCase = true) } }
        .map { token ->
            ModifierChip(
                token, true,
                if (MovementDifficulty.pricesModifier(token)) ModifierFlag.NOT_APPLICABLE else ModifierFlag.UNKNOWN,
            )
        }
    return offeredChips + strays
}

/** [current] with [token] set when it was not and cleared when it was; the others keep their order. */
fun toggleModifier(current: String, token: String): String {
    val tokens = modifierTokens(current)
    val next = if (tokens.any { it.equals(token, ignoreCase = true) }) {
        tokens.filterNot { it.equals(token, ignoreCase = true) }
    } else {
        tokens + token
    }
    return next.joinToString(", ")
}

/**
 * [current] modifiers with "weighted" following the load a rite entry is set to: added when a bodyweight
 * [exercise] (the catalogue's isWeighted false: pull-up, dip, push-up) is [loaded], removed whenever it is not.
 * A barbell lift is never given the tag (its load is the lift), and an activity carries none. The same
 * rule [modifiersAfterLoadChange] gives the live trial; every other token is left as it is.
 */
fun modifiersFollowingLoad(current: String, exercise: Exercise?, loaded: Boolean): String {
    val counted = exercise != null && (exercise.metric == ExerciseMetric.REPS || exercise.metric == ExerciseMetric.HOLD)
    val isLoaded = loaded && counted
    return modifiersAfterLoadChange(current, becameLoaded = isLoaded && exercise?.isWeighted == false, anyLoaded = isLoaded)
}
