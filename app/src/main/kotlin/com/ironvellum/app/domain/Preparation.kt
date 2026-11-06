package com.ironvellum.app.domain

enum class PreparationKind { WARMUP, COOLDOWN }
enum class PreparationMode { AUTO, SAVED, NONE }
enum class PreparationMeasure { REPS, SECONDS }
enum class PreparationRepeat { ONCE, SIDES, DIRECTIONS }
enum class PreparationStatus { OPEN, RUNNING, PAUSED, DONE, SKIPPED }
enum class PreparationAction { START, PAUSE, RESUME, COMPLETE_UNIT, SKIP_UNIT, SKIP_ROUTINE }

/** Original workout prescription; activity distance occupies the old weight column, not a preparation load. */
data class PreparationLegacyTarget(val metric: ExerciseMetric, val targetReps: Int, val targetWeightKg: Double? = null)

data class PreparationStep(
    val exerciseId: Long,
    val exerciseName: String,
    val measure: PreparationMeasure,
    val target: Int,
    val rounds: Int = 1,
    val repeat: PreparationRepeat = PreparationRepeat.ONCE,
    val note: String = "",
    val weightKg: Double? = null,
    val legacyTarget: PreparationLegacyTarget? = null,
) {
    /** Existing workout labels keep minutes, attempts and kilometres, independently of the runner's timer. */
    fun legacyTargetLine(kg: (Double) -> String): String? = legacyTarget?.let {
        WarmupStep.Item(exerciseId, exerciseName, it.metric, rounds, it.targetReps, it.targetWeightKg).line(kg)
    }
    val repetitions: Int get() = if (repeat == PreparationRepeat.ONCE) 1 else 2
    fun validForEditor(): Boolean = exerciseId > 0 && exerciseName.isNotBlank() &&
        target in (if (measure == PreparationMeasure.REPS) 1..100 else 5..600) &&
        rounds in 1..10 && note.length <= 1000 && (weightKg == null || weightKg.isFinite() && weightKg >= 0)
}

data class PreparationChoice(val mode: PreparationMode = PreparationMode.AUTO, val routineId: Long? = null)
data class PreparationRoutine(val id: Long = 0, val kind: PreparationKind, val name: String, val steps: List<PreparationStep>) {
    fun validForEditor(): Boolean = name.isNotBlank() && steps.isNotEmpty() && steps.all { it.validForEditor() }
}
data class PreparationSnapshot(
    val kind: PreparationKind,
    val name: String,
    val steps: List<PreparationStep>,
    val sourceRoutineId: Long? = null,
    /** Explicit workout warmups lead the snapshot and survive changing the general routine. */
    val legacyStepCount: Int = 0,
)
data class SessionPreparation(val sessionId: Long, val snapshot: PreparationSnapshot, val progress: PreparationProgress)
