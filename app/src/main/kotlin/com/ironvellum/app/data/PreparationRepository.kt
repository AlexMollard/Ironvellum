package com.ironvellum.app.data

import androidx.room.withTransaction
import com.ironvellum.app.R
import com.ironvellum.app.data.db.*
import com.ironvellum.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Owns preparation only. It never writes a set, score, profile or reward. */
class PreparationRepository(private val db: IronvellumDatabase, private val warmupStore: WarmupStore? = null) {
    private val dao = db.preparationDao()
    val warmupEnabled: Boolean get() = warmupStore?.enabled ?: true
    fun observeRoutines(kind: PreparationKind): Flow<List<PreparationRoutine>> = dao.observeRoutines(kind.name).map { rows -> rows.map { it.domain() } }
    fun observeChoices(presetId: Long): Flow<Map<PreparationKind, PreparationChoice>> = dao.observeChoices(presetId).map { it.domainChoices() }
    fun observeSession(sessionId: Long, kind: PreparationKind): Flow<SessionPreparation?> = dao.observeSession(sessionId, kind.name).map { it?.domain() }

    suspend fun saveRoutine(routine: PreparationRoutine): Long = db.withTransaction {
        if (routine.id != 0L) snapshotLiveSession()
        require(routine.validForEditor()) { Words.text(UiText.themed(R.string.preparation_invalid, R.string.preparation_invalid__t)) }
        routine.steps.forEach { requireNotNull(db.exerciseDao().byId(it.exerciseId)) }
        val row = PreparationRoutineEntity(routine.id, routine.kind.name, routine.name.trim(), PreparationCodec.encodeSteps(routine.steps))
        if (routine.id == 0L) dao.insertRoutine(row) else {
            val old = requireNotNull(dao.routine(routine.id))
            require(old.kind == row.kind)
            dao.updateRoutine(row); routine.id
        }
    }
    suspend fun deleteRoutine(id: Long) = db.withTransaction { snapshotLiveSession(); dao.detach(id); dao.deleteRoutine(id) }
    suspend fun choices(presetId: Long): Map<PreparationKind, PreparationChoice> = dao.choices(presetId).domainChoices()
    suspend fun setChoices(presetId: Long, choices: Map<PreparationKind, PreparationChoice>) = db.withTransaction {
        snapshotLiveSession()
        PreparationKind.entries.forEach { kind ->
            val choice = choices[kind] ?: PreparationChoice()
            val routine = choice.routineId?.let { dao.routine(it) }
            require(choice.mode != PreparationMode.SAVED || routine == null || routine.kind == kind.name)
            val effective = if (choice.mode == PreparationMode.SAVED && routine == null) PreparationChoice() else choice
            dao.putChoice(PresetPreparationEntity(presetId, kind.name, effective.mode.name, effective.routineId.takeIf { effective.mode == PreparationMode.SAVED }))
        }
    }

    private suspend fun snapshotLiveSession() { db.sessionDao().liveSession()?.let { ensureSession(it.id) } }

    suspend fun ensureSession(sessionId: Long): Unit = db.withTransaction {
        if (db.sessionDao().byId(sessionId) == null) return@withTransaction
        PreparationKind.entries.forEach { kind ->
            if (dao.session(sessionId, kind.name) == null) {
                val presetId = db.sessionDao().byId(sessionId)?.presetId
                val choice = presetId?.let { choices(it)[kind] } ?: PreparationChoice()
                val snapshot = resolve(sessionId, kind, choice)
                // Read at the single first-writer boundary, including preset/routine mutation guards.
                val progress = if (kind == PreparationKind.WARMUP) legacyPreparationProgress(snapshot,
                    warmupStore?.stateFor(sessionId) ?: WarmupStep.State.OPEN,
                    warmupStore?.tickedFor(sessionId).orEmpty()) else PreparationProgress()
                put(sessionId, snapshot, progress)
            }
        }
    }
    suspend fun changeSession(sessionId: Long, kind: PreparationKind, choice: PreparationChoice): Unit = db.withTransaction {
        if (db.sessionDao().byId(sessionId) == null) return@withTransaction
        put(sessionId, resolve(sessionId, kind, choice), PreparationProgress())
    }
    suspend fun act(sessionId: Long, kind: PreparationKind, action: PreparationAction, nowMs: Long, expectedUnitKey: String? = null): Unit = db.withTransaction {
        ensureSession(sessionId)
        val current = dao.session(sessionId, kind.name)?.domain(nowMs) ?: return@withTransaction
        // Compare against the persisted cursor under the same Room write transaction, not a caller's stale state.
        val selected = if (action in listOf(PreparationAction.START, PreparationAction.RESUME) &&
            kind == PreparationKind.WARMUP && !warmupEnabled && current.snapshot.sourceRoutineId == null && current.progress == PreparationProgress()) {
            current.copy(progress = current.progress.copy(legacyOnly = true))
        } else current
        // Freeze the chosen execution scope, not a destructively shortened snapshot. Unit indices stay unchanged.
        val next = selected.progress.advance(selected.forWarmupSetting(true).snapshot, action, nowMs, expectedUnitKey)
        if (next != current.progress) put(sessionId, current.snapshot, next)
    }

    private suspend fun resolve(sessionId: Long, kind: PreparationKind, choice: PreparationChoice): PreparationSnapshot {
        val session = requireNotNull(db.sessionDao().byId(sessionId))
        val catalogue = db.exerciseDao().observeAll().first().map { it.domainExercise() }
        val byId = catalogue.associateBy { it.id }
        val workIds = db.sessionDao().setsFor(sessionId).filter { it.kind != SetKind.WARMUP.name }.map { it.exerciseId }.toSet()
        val saved = choice.routineId?.takeIf { choice.mode == PreparationMode.SAVED }?.let { dao.routine(it) }?.takeIf { it.kind == kind.name }?.domain()
        val general = when {
            choice.mode == PreparationMode.NONE -> PreparationSnapshot(kind, "", emptyList())
            saved != null -> PreparationSnapshot(kind, saved.name, saved.steps, saved.id)
            else -> PreparationStarters.suggest(kind, workIds.mapNotNull { byId[it] }, catalogue)
        }
        if (kind != PreparationKind.WARMUP) return general
        // On a changed selection retain the sealed legacy prefix rather than re-reading an edited preset.
        val previous = dao.session(sessionId, kind.name)?.let { PreparationCodec.decodeSnapshot(it.snapshotJson).getOrNull() }
        val legacy = if (previous != null) previous.steps.take(previous.legacyStepCount)
        else session.presetId?.let { db.presetDao().presetWithEntries(it) }?.entries.orEmpty()
            .filter { it.warmup }.sortedBy { it.position }.mapNotNull { entry ->
                val e = byId[entry.exerciseId] ?: return@mapNotNull null
                val timed = e.metric == ExerciseMetric.HOLD || e.metric == ExerciseMetric.DURATION
                val target = when (e.metric) {
                    ExerciseMetric.DURATION -> Math.multiplyExact(entry.targetReps, 60)
                    ExerciseMetric.DISTANCE_TIME -> 1 // One manual unit; its distance label comes from the original target.
                    else -> entry.targetReps
                }
                PreparationStep(e.id, e.name, if (timed) PreparationMeasure.SECONDS else PreparationMeasure.REPS,
                    target, entry.targetSets, weightKg = entry.targetWeightKg.takeIf { e.metric.isStrength },
                    legacyTarget = PreparationLegacyTarget(e.metric, entry.targetReps, entry.targetWeightKg))
            }.distinctBy { it.exerciseId }
        return general.copy(steps = legacy + general.steps.filter { s -> legacy.none { it.exerciseId == s.exerciseId } }, legacyStepCount = legacy.size)
    }

    private suspend fun put(sessionId: Long, snapshot: PreparationSnapshot, progress: PreparationProgress) {
        dao.putSession(SessionPreparationEntity(sessionId, snapshot.kind.name, PreparationCodec.encodeSnapshot(snapshot), PreparationCodec.encodeProgress(progress)))
    }
    internal suspend fun exerciseUsage(id: Long): Int = dao.routines().sumOf { PreparationCodec.decodeSteps(it.stepsJson).getOrThrow().count { s -> s.exerciseId == id } } +
        dao.sessions().sumOf { PreparationCodec.decodeSnapshot(it.snapshotJson).getOrThrow().steps.count { s -> s.exerciseId == id } }

    internal suspend fun allRoutines(): List<PreparationRoutine> = dao.routines().map { it.domain() }
    internal suspend fun allSessions(ids: Set<Long>? = null): List<SessionPreparation> = dao.sessions().filter { ids == null || it.sessionId in ids }.map { it.domain() }
    internal suspend fun hasProgress(sessionId: Long): Boolean = dao.sessionsFor(sessionId).any {
        val p = PreparationCodec.decodeProgress(it.progressJson).getOrNull()
        p != null && (p.status != PreparationStatus.OPEN || p.completedUnitKeys.isNotEmpty() || p.skippedUnitKeys.isNotEmpty())
    }

    internal suspend fun twinMoves(exercises: List<Exercise>, kept: Set<String>): Int {
        fun moves(steps: List<PreparationStep>) = preparationTwinMoves(steps, exercises, kept).size
        return dao.routines().sumOf { moves(PreparationCodec.decodeSteps(it.stepsJson).getOrThrow()) } +
            dao.sessions().sumOf { moves(PreparationCodec.decodeSnapshot(it.snapshotJson).getOrThrow().steps) }
    }

    internal suspend fun convertTwins(exercises: List<Exercise>, kept: Set<String>): Int {
        val names = exercises.associate { it.id to it.name }
        var count = 0
        fun converted(steps: List<PreparationStep>): List<PreparationStep> {
            val moves = preparationTwinMoves(steps, exercises, kept).associateBy { it.block.position }
            count += moves.size
            return steps.mapIndexed { i, step -> moves[i]?.let { step.copy(exerciseId = it.toId, exerciseName = names.getValue(it.toId)) } ?: step }
        }
        dao.routines().forEach { row ->
            val before = PreparationCodec.decodeSteps(row.stepsJson).getOrThrow()
            val after = converted(before)
            if (after != before) dao.updateRoutine(row.copy(stepsJson = PreparationCodec.encodeSteps(after)))
        }
        dao.sessions().forEach { row ->
            val before = PreparationCodec.decodeSnapshot(row.snapshotJson).getOrThrow()
            val after = before.copy(steps = converted(before.steps))
            if (after != before) dao.putSession(row.copy(snapshotJson = PreparationCodec.encodeSnapshot(after)))
        }
        return count
    }

    private fun preparationTwinMoves(steps: List<PreparationStep>, exercises: List<Exercise>, kept: Set<String>) = TwinConversion.plan(
        steps.mapIndexed { i, s -> TwinConversion.Row(0, i, s.exerciseId, s.weightKg, "", warmup = true) }, exercises,
    ).filterNot { it.keepKey in kept }

    internal suspend fun restoreSession(value: SessionPreparation) { put(value.sessionId, value.snapshot, value.progress) }
    private fun PreparationRoutineEntity.domain() = PreparationRoutine(id, PreparationKind.valueOf(kind), name, PreparationCodec.decodeSteps(stepsJson).getOrThrow())
    private fun SessionPreparationEntity.domain(nowMs: Long = System.currentTimeMillis()): SessionPreparation {
        val snapshot = PreparationCodec.decodeSnapshot(snapshotJson).getOrThrow()
        val progress = PreparationCodec.decodeProgress(progressJson).getOrNull()?.takeIf { p ->
            p.stepIndex <= snapshot.steps.size && (p.stepIndex == snapshot.steps.size || snapshot.steps.getOrNull(p.stepIndex)?.let { p.roundIndex < it.rounds && p.repeatIndex < it.repetitions } == true)
        }?.recovered(snapshot, nowMs) ?: PreparationProgress(status = PreparationStatus.PAUSED,
            remainingMs = snapshot.steps.firstOrNull()?.takeIf { it.measure == PreparationMeasure.SECONDS }?.target?.toLong()?.times(1000) ?: 0)
        return SessionPreparation(sessionId, snapshot, progress)
    }
    private fun List<PresetPreparationEntity>.domainChoices(): Map<PreparationKind, PreparationChoice> = PreparationKind.entries.associateWith { kind ->
        firstOrNull { it.kind == kind.name }?.let { PreparationChoice(PreparationMode.valueOf(it.mode), it.routineId) } ?: PreparationChoice()
    }
    private fun ExerciseEntity.domainExercise() = Exercise(id, name, MuscleGroup.valueOf(muscleGroup), isWeighted, ExerciseMetric.valueOf(metric), category, custom)
}
