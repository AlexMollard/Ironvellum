package com.ironvellum.app.data.localconnector

import com.ironvellum.app.domain.*
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

/** V2 is a read projection. The v1 catalogue and proposal admission live independently of it. */
@Serializable internal data class ReadMovement(
    val key: String, val name: String, val metric: ExerciseMetric?, val custom: Boolean?,
    val source: String, val loadSemantics: String,
)

@Serializable internal data class ReadSet(
    val order: Int, val exerciseKey: String, val exercisePosition: Int, val setIndex: Int,
    val kind: SetKind, val modifiers: String, val superset: Int?, val rpe: Int?,
    val done: Boolean, val warmupDone: Boolean, val recordedReps: Int, val weightKg: Double?,
    val durationSec: Int?, val distanceM: Double?, val grade: String?,
)

@Serializable internal data class ReadTarget(
    val metric: ExerciseMetric, val recordedTarget: Int, val unit: String, val weightKg: Double?, val distanceKm: Double?,
)

@Serializable internal data class ReadRange(val min: Int, val max: Int)
@Serializable internal data class ReadScheme(
    val type: String, val warmupRamp: Boolean, val workingRange: ReadRange?,
    val topRange: ReadRange?, val topRir: ReadRange?, val backoffSets: Int?, val backoffRange: ReadRange?,
    val backoffFixedKg: Double?, val backoffPercent: Int?, val restAfterTopSec: Int?,
    val dropSets: Int?, val dropCount: Int?, val dropPercent: Int?, val customKinds: List<SetKind>?,
)
@Serializable internal data class ReadEntry(
    val order: Int, val position: Int, val exerciseKey: String, val sets: Int, val target: ReadTarget,
    val modifiers: String, val superset: Int?, val warmup: Boolean, val schemeRaw: String?, val scheme: ReadScheme,
)
@Serializable internal data class ReadChoice(val mode: PreparationMode, val routineId: String?)
@Serializable internal data class ReadPreset(
    val id: String, val name: String, val warmup: ReadChoice, val cooldown: ReadChoice, val entries: List<ReadEntry>,
)
@Serializable internal data class ReadStep(
    val exerciseKey: String, val name: String, val measure: PreparationMeasure, val target: Int, val rounds: Int,
    val repeat: PreparationRepeat, val weightKg: Double?, val legacyTarget: ReadTarget?,
)
@Serializable internal data class ReadRoutine(val id: String, val kind: PreparationKind, val name: String, val steps: List<ReadStep>)
@Serializable internal data class ReadPreparationProgress(
    val status: PreparationStatus, val stepIndex: Int, val roundIndex: Int, val repeatIndex: Int, val legacyOnly: Boolean,
    val completedUnitKeys: List<String>, val skippedUnitKeys: List<String>,
)
@Serializable internal data class ReadPreparation(
    val kind: PreparationKind, val name: String, val sourceRoutineId: String?, val legacyStepCount: Int,
    val steps: List<ReadStep>, val progress: ReadPreparationProgress,
)
@Serializable internal data class ReadWorkout(
    val id: String, val startedAtMs: Long, val completedAtMs: Long, val sets: List<ReadSet>, val preparations: List<ReadPreparation>,
)

@Serializable internal data class MovementProgress(
    val exerciseKey: String, val completedSets: Int, val completedReps: Long, val externalVolumeKg: Double,
    val holdSeconds: Long, val activitySeconds: Long, val distanceM: Double, val attempts: Long,
    val maxReps: Int?, val maxExternalLoadKg: Double?, val maxAssistanceKg: Double?,
)
@Serializable internal data class ReadProgress(
    val version: Int, val scope: String, val completedWorkouts: Int, val completedSets: Int,
    val completedReps: Long, val externalVolumeKg: Double, val holdSeconds: Long, val activitySeconds: Long,
    val distanceM: Double, val attempts: Long, val firstCompletedAtMs: Long?, val lastCompletedAtMs: Long?,
    val lifts: List<MovementProgress>,
)

@Serializable internal data class ReadContext(
    val type: String, val schemaVersion: Int, val datasetId: String, val snapshotId: String, val exportedAtMs: Long,
    val catalogue: Catalogue, val coverage: LocalCoverage, val movements: List<ReadMovement>,
    val routines: List<ReadRoutine>, val workouts: List<ReadWorkout>, val savedWorkouts: List<ReadPreset>, val progress: ReadProgress,
) {
    @OptIn(ExperimentalSerializationApi::class)
    fun encode(): ByteArray {
        val output = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (len > MAX_CONTEXT_BYTES - size()) throw LocalExportException(LocalFailure.FILE_LIMIT)
                super.write(b, off, len)
            }
            override fun write(b: Int) {
                if (size() >= MAX_CONTEXT_BYTES) throw LocalExportException(LocalFailure.FILE_LIMIT)
                super.write(b)
            }
        }
        Json.encodeToStream(serializer(), this, output)
        return output.toByteArray()
    }
}

private class Totals {
    var count = 0
    var reps = 0L
    var volume = BigDecimal.ZERO
    var hold = 0L
    var seconds = 0L
    var distance = BigDecimal.ZERO
    var attempts = 0L
    var maxReps: Int? = null
    var maxExternal: Double? = null
    var maxAssistance: Double? = null

    fun record(row: ReadSet, movement: ReadMovement) {
        count++
        val kg = row.weightKg
        if (kg != null) {
            if (movement.loadSemantics == "ASSISTANCE") maxAssistance = maxOf(maxAssistance ?: kg, kg)
            else maxExternal = maxOf(maxExternal ?: kg, kg)
        }
        when (movement.metric) {
            ExerciseMetric.REPS -> {
                reps += row.recordedReps
                maxReps = maxOf(maxReps ?: 0, row.recordedReps)
                if (kg != null && movement.loadSemantics == "EXTERNAL") {
                    volume += BigDecimal.valueOf(kg) * row.recordedReps.toBigDecimal()
                }
            }
            ExerciseMetric.HOLD -> hold += row.durationSec ?: row.recordedReps
            ExerciseMetric.DURATION -> seconds += row.durationSec ?: 0
            ExerciseMetric.DISTANCE_TIME -> {
                seconds += row.durationSec ?: 0
                distance += BigDecimal.valueOf(row.distanceM ?: 0.0)
            }
            ExerciseMetric.ATTEMPTS_GRADE -> attempts += row.recordedReps
            null -> throw LocalExportException(LocalFailure.INVALID_HISTORY)
        }
    }
    fun movement(key: String) = MovementProgress(key,count,reps,volume.finiteDouble(),hold,seconds,distance.finiteDouble(),
        attempts,maxReps,maxExternal,maxAssistance)
}

private fun BigDecimal.finiteDouble() = toDouble().also { require(it.isFinite()) }

internal fun readProgress(workouts: List<ReadWorkout>, movements: Map<String, ReadMovement>): ReadProgress {
    val overall = Totals()
    val byMovement = mutableMapOf<String, Totals>()
    for (workout in workouts) for (row in workout.sets) {
        val stats = byMovement.getOrPut(row.exerciseKey) { Totals() }
        if (row.done && row.kind.countsAsWork) {
            val movement = movements.getValue(row.exerciseKey)
            stats.record(row, movement)
            overall.record(row, movement)
        }
    }
    val lifts = byMovement.map { (key, totals) -> totals.movement(key) }
        .sortedWith(compareByDescending<MovementProgress> { it.completedSets }.thenBy { it.exerciseKey })
    return ReadProgress(2,"completed-workouts",workouts.size,overall.count,overall.reps,overall.volume.finiteDouble(),
        overall.hold,overall.seconds,overall.distance.finiteDouble(),overall.attempts,workouts.minOfOrNull { it.completedAtMs },
        workouts.maxOfOrNull { it.completedAtMs },lifts)
}
