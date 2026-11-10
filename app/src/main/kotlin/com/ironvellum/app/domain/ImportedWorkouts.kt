package com.ironvellum.app.domain

import java.util.Locale

data class ImportedWorkoutCandidate(val key: String, val workout: CsvWorkoutReader.ParsedWorkout)

object ImportedWorkouts {
    private val whitespace = Regex("\\s+")
    private fun normalise(label: String): String = label.trim().lowercase(Locale.ROOT).replace(whitespace, " ")

    fun candidates(parsed: CsvWorkoutReader.ParsedImport): List<ImportedWorkoutCandidate> =
        parsed.workouts.filter { workout ->
            normalise(workout.label).isNotEmpty() && workout.sets.any { SetKind.fromImport(it.setType).countsAsWork }
        }
            .groupBy { normalise(it.label) }
            .map { (label, workouts) ->
                ImportedWorkoutCandidate("csv:$label", workouts.maxWith(compareBy<CsvWorkoutReader.ParsedWorkout> { it.startedAtMs }
                    .thenBy { it.completedAtMs ?: it.startedAtMs }))
            }.sortedBy { it.key }

    /** Converts ordered historical blocks without changing the history that supplied them. */
    fun draft(name: String, note: String, sets: List<SessionSet>, metricOf: (Long) -> ExerciseMetric?): WorkoutPreset? {
        val blocks = sets.sortedWith(compareBy<SessionSet> { it.exercisePosition }.thenBy { it.setIndex })
            .groupBy { it.blockKey }.values
        val entries = blocks.mapNotNull { rows ->
            val work = rows.filter { !it.warmup }
            val represented = work.ifEmpty { rows }
            if (represented.isEmpty()) return@mapNotNull null
            val first = represented.first()
            val metric = metricOf(first.exerciseId) ?: return@mapNotNull null
            val done = represented.filter { it.done }.ifEmpty { represented }
            val base = done.filter { it.kind == SetKind.TOP }.ifEmpty {
                done.filter { it.kind != SetKind.DROP && it.kind != SetKind.BACKOFF }
            }.ifEmpty { done }
            val scheme = if (work.isEmpty()) SetScheme() else RoutineUpdate.inferScheme(
                SetScheme(warmupRamp = metric == ExerciseMetric.REPS && rows.any { it.warmup }), work,
            )
            val figures = base.map {
                when (metric) {
                    ExerciseMetric.HOLD -> it.durationSec ?: it.reps
                    ExerciseMetric.DURATION -> ((it.durationSec ?: 0) + 30) / 60
                    ExerciseMetric.DISTANCE_TIME -> 10
                    ExerciseMetric.ATTEMPTS_GRADE -> 1
                    ExerciseMetric.REPS -> it.reps
                }
            }
            PresetEntry(exerciseId = first.exerciseId, exerciseName = first.exerciseName,
                targetSets = represented.count { it.kind != SetKind.DROP }.coerceAtLeast(1),
                targetReps = RoutineUpdate.medianDown(figures).coerceIn(1, 500),
                targetWeightKg = when (metric) {
                    ExerciseMetric.DISTANCE_TIME -> RoutineUpdate.mostUsedLoad(base.map { it.distanceM?.div(1000.0) })
                    ExerciseMetric.DURATION, ExerciseMetric.ATTEMPTS_GRADE -> null
                    else -> RoutineUpdate.mostUsedLoad(base.map { it.weightKg })
                },
                modifiers = first.modifiers, supersetGroup = first.supersetGroup, scheme = scheme, warmup = work.isEmpty())
        }
        if (entries.none { !it.warmup }) return null
        val ordered = entries.filter { it.warmup } + entries.filterNot { it.warmup }
        val groups = Supersets.effective(ordered.map { if (it.warmup) null else it.supersetGroup })
        return WorkoutPreset(name = name, note = note, entries = ordered.mapIndexed { index, entry ->
            entry.copy(position = index, supersetGroup = groups[index])
        })
    }
}
