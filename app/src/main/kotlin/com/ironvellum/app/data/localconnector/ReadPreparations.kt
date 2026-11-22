package com.ironvellum.app.data.localconnector

import com.ironvellum.app.domain.*
import kotlinx.serialization.json.*

/** Stored history can predate optional fields. Parse identifiers and indexes as exact integers
 * before interpreting them. In particular, never truncate a fractional reference to a Room id. */
private fun stored(source: String): JsonElement {
    if (source.length > MAX_CONTEXT_BYTES) throw LocalExportException(LocalFailure.FILE_LIMIT)
    return LocalJsonReader(source).document()
}
private fun JsonElement.text() = jsonPrimitive.also { require(it.isString) }.content
private fun JsonElement.number() = jsonPrimitive.also { require(!it.isString) }
private fun JsonObject.value(key: String) = get(key)?.takeUnless { it == JsonNull }
private fun JsonObject.integer(key: String, default: Int) = get(key)?.number()?.int ?: default
private fun JsonObject.weight(key: String) = value(key)?.number()?.double?.also { require(it.isFinite() && it >= 0) }
private fun readSteps(array: JsonElement) = array.jsonArray.map {
    val row = it.jsonObject
    val target = row.getValue("target").number().int
    val rounds = row.integer("rounds", 1)
    val id = row.getValue("exerciseId").number().long
    require(id > 0 && target > 0 && rounds > 0)
    val legacy = row.value("legacyTarget")?.jsonObject?.let { l ->
        PreparationLegacyTarget(ExerciseMetric.valueOf(l.getValue("metric").text()),
            l.getValue("targetReps").number().int, l.weight("targetWeightKg"))
    }
    PreparationStep(id,readText(row.getValue("exerciseName").text()),PreparationMeasure.valueOf(row.getValue("measure").text()),
        target,rounds,row["repeat"]?.let { r -> PreparationRepeat.valueOf(r.text()) } ?: PreparationRepeat.ONCE,
        weightKg=row.weight("weightKg"),legacyTarget=legacy)
}
internal fun readStoredSteps(source: String) = readSteps(stored(source))

internal fun readStoredSnapshot(source: String): PreparationSnapshot {
    val row = stored(source).jsonObject
    val steps = readSteps(row.getValue("steps"))
    val legacy = row.integer("legacyStepCount", 0)
    require(legacy in 0..steps.size)
    return PreparationSnapshot(PreparationKind.valueOf(row.getValue("kind").text()),row.getValue("name").text(),steps,
        row.value("sourceRoutineId")?.number()?.long,legacy)
}

internal fun readStoredProgress(source: String, snapshot: PreparationSnapshot): ReadPreparationProgress {
    val row = stored(source).jsonObject
    val stepIndex = row.integer("stepIndex", 0)
    val roundIndex = row.integer("roundIndex", 0)
    val repeatIndex = row.integer("repeatIndex", 0)
    val legacyOnly = row["legacyOnly"]?.number()?.boolean ?: false
    val limit = if (legacyOnly) snapshot.legacyStepCount else snapshot.steps.size
    fun valid(s: Int, r: Int, p: Int) = s in 0 until limit && r in 0 until snapshot.steps[s].rounds &&
        p in 0 until snapshot.steps[s].repetitions
    require(valid(stepIndex, roundIndex, repeatIndex) || stepIndex == limit && roundIndex == 0 && repeatIndex == 0)
    fun keys(field: String) = row[field]?.jsonArray.orEmpty().map { element ->
        val key = element.text()
        val parts = key.split(':')
        require(parts.size == 3)
        val (s,r,p) = parts.map(String::toInt)
        require(key == "$s:$r:$p" && valid(s,r,p))
        key
    }.also { require(it.distinct().size == it.size) }.sorted()
    val done = keys("completedUnitKeys")
    val skipped = keys("skippedUnitKeys")
    require(done.toSet().intersect(skipped.toSet()).isEmpty())
    return ReadPreparationProgress(PreparationStatus.valueOf(row.getValue("status").text()),stepIndex,roundIndex,
        repeatIndex,legacyOnly,done,skipped)
}
