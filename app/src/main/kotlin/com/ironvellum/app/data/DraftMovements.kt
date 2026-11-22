package com.ironvellum.app.data

import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.WorkoutDraft

internal data class DraftMovementCandidate(val id: Long, val name: String, val custom: Boolean, val metric: String)

internal class DraftMovementUnavailable : IllegalArgumentException("Unavailable movement")

internal object DraftMovements {
    private val builtIns by lazy { catalogue(Seed.exercises) }

    fun catalogue(rows: List<ExerciseEntity>): Map<String, ExerciseEntity> {
        val result = sortedMapOf<String, ExerciseEntity>()
        for (row in rows) {
            val key = Titles.normaliseName(row.name)
            require(!row.custom && key.isNotEmpty() && row.metric in ExerciseMetric.entries.map { it.name })
            require(result.put(key, row) == null) { "Duplicate movement key: $key" }
        }
        return result
    }

    fun resolve(draft: WorkoutDraft, rows: Sequence<DraftMovementCandidate>): List<Long> =
        resolveAvailable(draft, rows).map { it ?: throw DraftMovementUnavailable() }

    fun resolveAvailable(draft: WorkoutDraft, rows: Sequence<DraftMovementCandidate>): List<Long?> {
        val wanted = draft.entries.mapTo(mutableSetOf()) { it.exerciseKey }
        val matches = mutableMapOf<String, Long?>()
        for (row in rows) {
            val key = Titles.normaliseName(row.name)
            if (key !in wanted) continue
            matches[key] = row.id.takeIf {
                key !in matches && !row.custom && row.metric == ExerciseMetric.REPS.name &&
                    builtIns[key]?.metric == ExerciseMetric.REPS.name
            }
        }
        return draft.entries.map { matches[it.exerciseKey] }
    }
}
