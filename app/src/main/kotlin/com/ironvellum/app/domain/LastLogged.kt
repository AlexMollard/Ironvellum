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
) {
    companion object {
        /**
         * Collapses the rows the DAO returns (every done set of each
         * exercise's latest workout) to one top set per exercise. Ordering
         * by start time first means a stray older row can never win.
         */
        fun topSets(rows: List<LastLogged>): Map<Long, LastLogged> =
            rows.groupBy { it.exerciseId }.mapValues { (_, sets) ->
                sets.maxWith(
                    compareBy<LastLogged>({ it.atMs }, { it.weightKg ?: 0.0 }, { it.durationSec ?: 0 }, { it.distanceM ?: 0.0 }, { it.reps }),
                )
            }
    }
}
