package com.ironvellum.app.domain

/**
 * Which variation of a movement a set was done in: the one rule every record, peak, rep max,
 * progression and chart reads, so "Pull-up assisted" and "Pull-up" never share a history.
 *
 * Only the modifiers that change what the movement IS split it: [TOKENS]. Tempo and paused are
 * the same lift done slower, "weighted" is the load itself (which every record already compares),
 * and a token nothing prices (a typo, a legacy "banded") must not split a history either.
 *
 * The plain movement has an empty variation, so its [liftKey] is exactly the bare name key every
 * lookup used before: nothing stored changes, a lift's old history reads as its plain variation.
 */
object Variation {

    /** The modifiers that make a different lift: easier, harder or a different range of motion. */
    val TOKENS: Set<String> = setOf("assisted", "archer", "one-arm", "incline", "decline", "elevated", "deficit")

    /** Separates the name from the variation in [liftKey]; no catalogue or custom exercise name carries it. */
    const val SEPARATOR = " | "

    /** The splitting tokens of [modifiers], lowercased, distinct and sorted, comma-joined; "" for the plain movement. */
    fun key(modifiers: String): String =
        modifiers.split(",").map { it.trim().lowercase() }.filter { it in TOKENS }.toSortedSet().joinToString(",")

    /** True when [modifiers] leave the movement as itself: no splitting token. */
    fun isPlain(modifiers: String): Boolean = key(modifiers).isEmpty()

    /** The key a movement's history is kept under: its lowercased, trimmed name, then the variation when it has one. */
    fun liftKey(exerciseName: String, modifiers: String): String {
        val name = exerciseName.lowercase().trim()
        val variation = key(modifiers)
        return if (variation.isEmpty()) name else name + SEPARATOR + variation
    }

    /** The movement as a record or peak names it: "Single-Leg Calf Raise · deficit"; just the name when plain. */
    fun label(exerciseName: String, modifiers: String): String {
        val name = exerciseName.trim()
        val variation = key(modifiers)
        return if (variation.isEmpty()) name else name + " · " + variation.replace(",", ", ")
    }
}
