package com.ironvellum.app.data.localconnector

import com.ironvellum.app.data.DraftMovements
import com.ironvellum.app.data.Seed
import com.ironvellum.app.domain.BodyShare
import com.ironvellum.app.domain.LocalConnectorFiles
import com.ironvellum.app.domain.MAX_CONTEXT_BYTES
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal

@Serializable internal data class Catalogue(val version: Int, val count: Int, val sha256: String)
@Serializable internal data class LocalCoverage(
    val workoutsIncluded: Int, val workoutsTruncated: Long, val setsIncluded: Int, val setsTruncated: Long,
    val setsUnsupported: Long, val savedWorkoutsIncluded: Int, val savedWorkoutsTruncated: Long, val savedWorkoutsUnsupported: Long,
)
@Serializable internal data class LocalSet(val order: Int, val exerciseKey: String, val reps: Int, val loadKg: Double?)
@Serializable internal data class LocalWorkout(val id: String, val startedAtMs: Long, val completedAtMs: Long, val sets: List<LocalSet>)
@Serializable internal data class LocalEntry(val exerciseKey: String, val sets: Int, val reps: Int, val loadKg: Double?)
@Serializable internal data class LocalPreset(val id: String, val name: String, val warmup: String, val cooldown: String, val entries: List<LocalEntry>)
@Serializable internal data class LocalLift(val exerciseKey: String, val completedSets: Int, val maxLoadKg: Double?, val maxReps: Int)
@Serializable internal data class LocalProgress(val version: Int, val scope: String, val completedWorkouts: Int,
    val completedSets: Int, val completedReps: Int, val externalVolumeKg: Double, val firstCompletedAtMs: Long?,
    val lastCompletedAtMs: Long?, val lifts: List<LocalLift>, val liftsTruncated: Int)

@Serializable internal data class LocalContext(
    val type: String, val schemaVersion: Int, val datasetId: String, val snapshotId: String, val exportedAtMs: Long,
    val catalogue: Catalogue, val coverage: LocalCoverage, val workouts: List<LocalWorkout>, val savedWorkouts: List<LocalPreset>,
    val progress: LocalProgress,
) {
    fun encode(): ByteArray = Json.encodeToString(serializer(), this).encodeToByteArray().also {
        if (it.size > MAX_CONTEXT_BYTES) throw LocalExportException(LocalFailure.FILE_LIMIT)
    }
}

internal object LocalCatalogue {
    val movements by lazy { DraftMovements.catalogue(Seed.exercises) }
    fun acceptsDraft(key: String): Boolean = movements[key]?.metric == "REPS" && !BodyShare.isAssisted(key)
    val identity by lazy {
        val records = buildString {
            append("ironvellum-movements-v1\u0000")
            for ((key, row) in movements) {
                append(key).append('\u0000').append(row.name).append('\u0000').append(row.metric).append('\u0000')
            }
        }
        Catalogue(1, movements.size, LocalConnectorFiles.sha256(records.encodeToByteArray()))
    }
}

internal fun progress(workouts: List<LocalWorkout>): LocalProgress {
    val sets = workouts.flatMap { it.sets }
    val lifts = sets.groupBy { it.exerciseKey }.map { (key, values) ->
        LocalLift(key, values.size, values.mapNotNull { it.loadKg }.maxOrNull(), values.maxOf { it.reps })
    }.sortedWith(compareByDescending<LocalLift> { it.completedSets }.thenBy { it.exerciseKey })
    val volume = sets.fold(BigDecimal.ZERO) { total, row ->
        total + BigDecimal.valueOf(row.loadKg ?: 0.0) * row.reps.toBigDecimal()
    }
    return LocalProgress(1, "included-workouts", workouts.size, sets.size, sets.sumOf { it.reps }, volume.toDouble(),
        workouts.minOfOrNull { it.completedAtMs }, workouts.maxOfOrNull { it.completedAtMs }, lifts.take(20),
        (lifts.size - 20).coerceAtLeast(0))
}
