package com.ironvellum.app.data.localconnector

import com.ironvellum.app.data.db.*
import com.ironvellum.app.domain.*
import kotlinx.serialization.json.Json

/** One instance per export transaction. Page the database and charge emitted leaves before retaining them.
 * Header-only copies charge parent text/metadata exactly once. The final stream enforces framing overhead. */
internal class ReadTrainingProjection(private val dao: LocalConnectorDao, private val opaque: (String, Long) -> String) {
    private var bytesRemaining = MAX_CONTEXT_BYTES
    private val movementRows = mutableMapOf<Long, ReadMovement>()
    private val registry = mutableMapOf<String, ReadMovement>()
    private val routines = mutableMapOf<Long, ReadRoutine>()
    private val historicalKinds = mutableMapOf<Long, PreparationKind>()
    private val ambiguousKeys by lazy {
        val seen = mutableSetOf<String>()
        val ambiguous = mutableSetOf<String>()
        // Locale-independent Unicode lowercase emits at least one scalar per input scalar.
        // Before lowercasing, each scalar occupies at most four UTF-8 bytes.
        val maxCandidateBytes = 4 * LocalCatalogue.movements.keys.maxOf { it.codePointCount(0, it.length) }
        dao.candidateNames(kotlinTrimCharacters, maxCandidateBytes).useTrainingCursor { cursor ->
            while (cursor.moveToNext()) {
                val key = Titles.normaliseName(cursor.getString(0))
                if (key in LocalCatalogue.movements && !seen.add(key)) ambiguous.add(key)
            }
        }
        ambiguous
    }

    private inline fun <reified T> charge(value: T): T {
        bytesRemaining -= Json.encodeToString(value).encodeToByteArray().size
        if (bytesRemaining < 0) throw LocalExportException(LocalFailure.FILE_LIMIT)
        return value
    }

    private fun movement(id: Long): ReadMovement? {
        movementRows[id]?.let { return it }
        val row = dao.movement(id, MAX_CONTEXT_BYTES).useTrainingCursor { c ->
            if (c.moveToNext()) ReadMovementRow(c.integer(0),c.publicName(1),c.flag(2),c.getString(3)) else null
        } ?: return null
        val name = readText(row.name, empty = true)
        val metric = ExerciseMetric.valueOf(row.metric)
        val key = Titles.normaliseName(name)
        val canonical = !row.custom && LocalCatalogue.movements[key]?.metric == metric.name && key !in ambiguousKeys
        val move = ReadMovement(if (canonical) key else opaque("movement", id), name, metric, row.custom,
            if (canonical) "CATALOGUE" else "LOCAL", if (BodyShare.isAssisted(name)) "ASSISTANCE" else "EXTERNAL")
        movementRows[id] = move
        registry[move.key] = charge(move)
        return move
    }

    private fun step(s: PreparationStep, frozenKey: (() -> String)?): ReadStep {
        val name = readText(s.exerciseName)
        val move = movement(s.exerciseId) ?: run {
            val key = requireNotNull(frozenKey) { "Missing saved routine movement" }.invoke()
            ReadMovement(key, name, s.legacyTarget?.metric, null, "HISTORICAL", "UNKNOWN").also { registry[key] = charge(it) }
        }
        return charge(ReadStep(move.key, name, s.measure, s.target, s.rounds, s.repeat, validValue(s.weightKg),
            s.legacyTarget?.let { target(it.metric, it.targetReps, it.targetWeightKg) }))
    }

    private fun routine(id: Long, kind: PreparationKind, historical: Boolean): String {
        val key = opaque("routine", id)
        routines[id]?.let { require(it.kind == kind); return key }
        val row = dao.routine(id, MAX_CONTEXT_BYTES).useTrainingCursor { c ->
            if (c.moveToNext()) PreparationRoutineEntity(c.integer(0),c.getString(1),c.publicName(2),
                readPublicPreparation(c.getBlob(3)) { start -> dao.routineChunk(id, start) }) else null
        }
        if (row == null) {
            require(historical && historicalKinds.getOrPut(id) { kind } == kind) { "Invalid historical routine" }
            return key
        }
        require(row.kind == kind.name)
        val header = charge(ReadRoutine(key, kind, readText(row.name), emptyList()))
        val steps = readStoredSteps(row.stepsJson)
        require(steps.isNotEmpty())
        routines[id] = header.copy(steps = steps.map { step(it, null) })
        return key
    }

    private fun preparation(row: SessionPreparationEntity): ReadPreparation {
        val snapshot = readStoredSnapshot(row.snapshotJson)
        require(snapshot.kind.name == row.kind)
        val progress = readStoredProgress(row.progressJson, snapshot)
        val source = snapshot.sourceRoutineId?.let { require(it > 0); routine(it, snapshot.kind, historical = true) }
        val header = charge(ReadPreparation(snapshot.kind, readText(snapshot.name, empty = true), source,
            snapshot.legacyStepCount, emptyList(), progress))
        return header.copy(steps = snapshot.steps.mapIndexed { i, s ->
            step(s) { opaque("frozen-${row.kind}-$i", row.sessionId) }
        })
    }

    private fun workout(row: WorkoutTimes): ReadWorkout {
        require(row.startedAtMs in 0..MAX_LOCAL_TIME && row.completedAtMs in row.startedAtMs..MAX_LOCAL_TIME)
        val header = charge(ReadWorkout(opaque("workout", row.id), row.startedAtMs, row.completedAtMs, emptyList(), emptyList()))
        val supersets = Supersets()
        val sets = dao.sets(row.id).projectRows { i, c ->
            val move = requireNotNull(movement(c.integer(0)))
            val rpe = c.optionalInt(6)
            require(rpe == null || rpe in Effort.MIN..Effort.MAX)
            charge(ReadSet(i,move.key,c.naturalInt(1),c.naturalInt(2),SetKind.valueOf(c.getString(3)),
                readText(c.getString(4), empty = true),supersets.group(c.optionalGroup(5)),rpe,c.flag(7),c.flag(8),
                c.naturalInt(9),c.measurement(10),c.optionalInt(11),c.measurement(12),c.getString(13)?.let { readText(it, empty = true) }))
        }
        val preparations = dao.preparations(row.id).projectRows { _, c ->
            val kind = c.getString(1)
            val snapshot = readPublicPreparation(c.getBlob(2)) { start -> dao.snapshotChunk(row.id,kind,start) }
            val progress = readPublicPreparation(c.getBlob(3)) { start -> dao.progressChunk(row.id,kind,start) }
            preparation(SessionPreparationEntity(row.id,kind,snapshot,progress))
        }
        return header.copy(sets = sets, preparations = preparations)
    }

    private fun preset(row: ReadPresetRow): ReadPreset {
        val choices = dao.choices(row.id).projectRows { _, c ->
            val kind = PreparationKind.valueOf(c.getString(0))
            val mode = PreparationMode.valueOf(c.getString(1))
            val routineId = if (c.isNull(2)) null else c.integer(2)
            require((mode == PreparationMode.SAVED) == (routineId != null))
            kind to ReadChoice(mode, routineId?.let { require(it > 0); routine(it, kind, historical = false) })
        }.toMap()
        val auto = ReadChoice(PreparationMode.AUTO, null)
        val header = charge(ReadPreset(opaque("preset", row.id), readText(row.name, empty = true), choices[PreparationKind.WARMUP] ?: auto,
            choices[PreparationKind.COOLDOWN] ?: auto, emptyList()))
        val supersets = Supersets()
        return header.copy(entries = dao.entries(row.id).projectRows { i, c ->
            val move = requireNotNull(movement(c.integer(0)))
            val scheme = c.getString(7)
            charge(ReadEntry(i,c.naturalInt(5),move.key,c.naturalInt(1),target(requireNotNull(move.metric),c.naturalInt(2),c.measurement(3)),
                readText(c.getString(4), empty = true),supersets.group(c.optionalGroup(6)),c.flag(8),scheme,readScheme(scheme)))
        })
    }

    fun context(dataset: String, snapshot: String, generated: Long): ReadContext = try {
        if (dao.minimumOutputBytes() > MAX_CONTEXT_BYTES) throw LocalExportException(LocalFailure.FILE_LIMIT)
        val workouts = dao.completed().projectRows { _, c -> workout(WorkoutTimes(c.integer(0),c.integer(1),c.integer(2))) }
            .sortedWith(compareByDescending<ReadWorkout> { it.completedAtMs }.thenBy { it.id })
        val presets = dao.presets().projectRows { _, c -> preset(ReadPresetRow(c.integer(0),c.getString(1))) }.sortedBy { it.id }
        val coverage = LocalCoverage(workouts.size,0,workouts.sumOf { it.sets.size },0,0,presets.size,0,0)
        ReadContext("ironvellum.training-context",2,dataset,snapshot,generated,LocalCatalogue.identity,coverage,
            registry.values.sortedBy { it.key },routines.values.sortedBy { it.id },workouts,presets,readProgress(workouts, registry))
    } catch (e: NoSuchElementException) {
        throw LocalExportException(LocalFailure.INVALID_HISTORY, e)
    } catch (e: IllegalArgumentException) {
        throw LocalExportException(LocalFailure.INVALID_HISTORY, e)
    }
}

// Titles.normaliseName trims using Char.isWhitespace, including Unicode spaces and control separators.
private val kotlinTrimCharacters = (Char.MIN_VALUE..Char.MAX_VALUE).filter(Char::isWhitespace).joinToString("")

/** Local to the containing workout or preset. The first distinct group encountered is zero. */
private class Supersets {
    private val groups = mutableMapOf<Int, Int>()
    fun group(id: Int?) = id?.let { groups.getOrPut(it) { groups.size } }
}

internal fun readText(text: String, empty: Boolean = false): String {
    require(empty || text.isNotBlank())
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        require(cp !in 0xd800..0xdfff)
        i += Character.charCount(cp)
    }
    return text
}

private fun validValue(n: Double?) = n.also { require(it == null || it.isFinite() && it >= 0) }

private fun target(metric: ExerciseMetric, recorded: Int, weight: Double?): ReadTarget {
    require(recorded >= 0)
    validValue(weight)
    val unit = when (metric) {
        ExerciseMetric.REPS -> "REPS"
        ExerciseMetric.HOLD -> "SECONDS"
        ExerciseMetric.DURATION -> "MINUTES"
        ExerciseMetric.DISTANCE_TIME -> "UNUSED"
        ExerciseMetric.ATTEMPTS_GRADE -> "ATTEMPTS"
    }
    val distance = metric == ExerciseMetric.DISTANCE_TIME
    return ReadTarget(metric, recorded, unit, weight.takeUnless { distance }, weight.takeIf { distance })
}

private fun IntRange.wire() = ReadRange(first, last)
internal fun readScheme(raw: String?): ReadScheme {
    raw?.let { readText(it, empty = true) }
    val s = SetScheme.decode(raw)
    val top = s.takeIf { it.type == SchemeType.TOP_BACKOFF }
    val drops = s.takeIf { it.type == SchemeType.DROPS }
    return ReadScheme(s.type.wire,s.warmupRamp,s.workingReps?.wire(),top?.topReps?.wire(),top?.topRir?.wire(),
        top?.backoffSets,top?.backoffReps?.wire(),top?.backoffFixedKg,top?.takeIf { it.backoffFixedKg == null }?.backoffPercent,
        top?.restAfterTopSec,drops?.dropSets,drops?.dropCount,drops?.dropPercent,s.takeIf { it.type == SchemeType.CUSTOM }?.custom)
}
