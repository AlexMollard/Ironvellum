package com.ironvellum.app.domain

import com.ironvellum.app.domain.ExportReader.Obj
import com.ironvellum.app.domain.ExportWriter.appendEscaped

/** Shares the archive's pure Kotlin JSON reader and escaping, without an Android JSON dependency. */
object PreparationCodec {
    fun encodeSteps(steps: List<PreparationStep>): String = steps.joinToString(",", "[", "]") { s -> buildString {
        append("{\"exerciseId\":${s.exerciseId},\"exerciseName\":"); appendEscaped(s.exerciseName)
        append(",\"measure\":"); appendEscaped(s.measure.name)
        append(",\"target\":${s.target},\"rounds\":${s.rounds},\"repeat\":"); appendEscaped(s.repeat.name)
        append(",\"note\":"); appendEscaped(s.note)
        append(",\"weightKg\":${s.weightKg}")
        s.legacyTarget?.let { legacy ->
            append(",\"legacyTarget\":{\"metric\":"); appendEscaped(legacy.metric.name)
            append(",\"targetReps\":${legacy.targetReps},\"targetWeightKg\":${legacy.targetWeightKg}}")
        }
        append('}')
    } }
    fun decodeSteps(json: String): Result<List<PreparationStep>> = runCatching {
        steps(ExportReader.Parser("{\"steps\":$json}").parseDocument().arr("steps") ?: error("missing steps"))
    }
    internal fun steps(values: List<Any?>): List<PreparationStep> = values.map { value ->
        val o = value as? Obj ?: error("invalid preparation step")
        PreparationStep(
            o.long("exerciseId") ?: error("missing exerciseId"), o.str("exerciseName") ?: error("missing exerciseName"),
            PreparationMeasure.valueOf(o.str("measure") ?: error("missing measure")), o.int("target") ?: error("missing target"),
            o.int("rounds") ?: 1, PreparationRepeat.valueOf(o.str("repeat") ?: "ONCE"), o.str("note") ?: "", o.dbl("weightKg"),
            o.obj("legacyTarget")?.let { legacy ->
                PreparationLegacyTarget(ExerciseMetric.valueOf(legacy.str("metric") ?: error("missing legacy metric")),
                    legacy.int("targetReps") ?: error("missing legacy target"), legacy.dbl("targetWeightKg"))
            },
        ).also {
            // Preserve positive legacy targets beyond today's editor bounds. Never accept an anonymous ID binding.
            require(it.exerciseId > 0 && it.exerciseName.isNotBlank() && it.target > 0 && it.rounds > 0 && it.note.length <= 1000)
            require(it.weightKg == null || it.weightKg.isFinite() && it.weightKg >= 0)
            require(it.legacyTarget?.targetWeightKg?.let { weight -> weight.isFinite() && weight >= 0 } != false)
        }
    }
    fun encodeSnapshot(snapshot: PreparationSnapshot): String = buildString {
        append("{\"kind\":"); appendEscaped(snapshot.kind.name)
        append(",\"name\":"); appendEscaped(snapshot.name)
        append(",\"sourceRoutineId\":${snapshot.sourceRoutineId},\"legacyStepCount\":${snapshot.legacyStepCount},\"steps\":${encodeSteps(snapshot.steps)}}")
    }
    fun decodeSnapshot(json: String): Result<PreparationSnapshot> = runCatching { snapshot(ExportReader.Parser(json).parseDocument()) }
    internal fun snapshot(o: Obj) = PreparationSnapshot(
        PreparationKind.valueOf(o.str("kind") ?: error("missing kind")), o.str("name") ?: error("missing name"),
        steps(o.arr("steps") ?: error("missing steps")), o.long("sourceRoutineId"), o.int("legacyStepCount") ?: 0,
    ).also { require(it.legacyStepCount in 0..it.steps.size) }
    fun encodeProgress(progress: PreparationProgress): String = buildString {
        append("{\"status\":"); appendEscaped(progress.status.name)
        append(",\"stepIndex\":${progress.stepIndex},\"roundIndex\":${progress.roundIndex},\"repeatIndex\":${progress.repeatIndex}")
        fun keys(name: String, values: Set<String>) {
            append(",\"$name\":[")
            values.forEachIndexed { i, key -> if (i > 0) append(','); appendEscaped(key) }
            append(']')
        }
        keys("completedUnitKeys", progress.completedUnitKeys); keys("skippedUnitKeys", progress.skippedUnitKeys)
        append(",\"remainingMs\":${progress.remainingMs},\"readyUntilMs\":${progress.readyUntilMs},\"deadlineMs\":${progress.deadlineMs}}")
    }
    fun decodeProgress(json: String): Result<PreparationProgress> = runCatching { progress(ExportReader.Parser(json).parseDocument()) }
    internal fun progress(o: Obj) = PreparationProgress(
        status = PreparationStatus.valueOf(o.str("status") ?: error("missing status")),
        stepIndex = o.int("stepIndex") ?: 0, roundIndex = o.int("roundIndex") ?: 0, repeatIndex = o.int("repeatIndex") ?: 0,
        completedUnitKeys = (o.arr("completedUnitKeys") ?: emptyList()).map { it as String }.toSet(),
        skippedUnitKeys = (o.arr("skippedUnitKeys") ?: emptyList()).map { it as String }.toSet(),
        remainingMs = o.long("remainingMs") ?: 0, readyUntilMs = o.long("readyUntilMs"), deadlineMs = o.long("deadlineMs"),
    ).also { p ->
        require(p.stepIndex >= 0 && p.roundIndex >= 0 && p.repeatIndex in 0..1 && p.remainingMs >= 0)
        require((p.completedUnitKeys + p.skippedUnitKeys).all { it.matches(Regex("\\d+:\\d+:[01]")) })
    }
}
