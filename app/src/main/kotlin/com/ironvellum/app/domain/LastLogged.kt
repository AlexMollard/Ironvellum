package com.ironvellum.app.domain

/**
 * One completed set as the picker remembers it: the TOP set of the lifter's
 * most recent completed workout for an exercise. "Top" is heaviest load, then
 * longest hold, then furthest distance, then most reps; that single ordering
 * is metric-agnostic because a field a metric does not carry is null or 0 on
 * every one of its sets. [atMs] is the workout's start.
 */
data class LastLogged(
    val exerciseId: Long,
    val reps: Int,
    val weightKg: Double?,
    val durationSec: Int?,
    val distanceM: Double?,
    val grade: String?,
    val atMs: Long,
    /** How hard the set was, RPE 5 to 10; null when it was never rated. */
    val rpe: Int? = null,
    /** The set's modifiers as stored; [variation] is the part that makes it a different lift. */
    val modifiers: String = "",
) {
    /** [Variation.key] of the set: "" for the plain movement. */
    val variation: String get() = Variation.key(modifiers)

    companion object {
        /**
         * Collapses the rows the DAO returns (every done set of each exercise's latest workout in each stored
         * modifier string) to one top set per lift: the exercise in one [Variation]. Ordering by start time first
         * means a stray older row can never win, and a deficit set never stands in for a flat one.
         */
        fun topSets(rows: List<LastLogged>): LastLoggedIndex =
            LastLoggedIndex(
                rows.groupBy { it.exerciseId to it.variation }.mapValues { (_, sets) ->
                    sets.maxWith(
                        compareBy<LastLogged>({ it.atMs }, { it.weightKg ?: 0.0 }, { it.durationSec ?: 0 }, { it.distanceM ?: 0.0 }, { it.reps }),
                    )
                },
            )
    }
}

/** The last top set of every lift, by exercise and [Variation.key]. A variation never logged is simply absent. */
class LastLoggedIndex(private val byLift: Map<Pair<Long, String>, LastLogged>) {
    val size: Int get() = byLift.size

    /** The last top set of [exerciseId] done in [variation]; null when that variation was never logged. */
    operator fun get(exerciseId: Long, variation: String): LastLogged? = byLift[exerciseId to variation]

    /** The most recent top set of [exerciseId] in whichever variation it was last logged. */
    fun latest(exerciseId: Long): LastLogged? =
        byLift.entries.filter { it.key.first == exerciseId }.maxByOrNull { it.value.atMs }?.value

    companion object {
        val EMPTY = LastLoggedIndex(emptyMap())
    }
}
