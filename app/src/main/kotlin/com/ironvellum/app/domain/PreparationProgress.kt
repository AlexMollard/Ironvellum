package com.ironvellum.app.domain

/** Room validates each unit transition; foreground timer expiry completes only the current timed unit. */
data class PreparationProgress(
    val status: PreparationStatus = PreparationStatus.OPEN,
    val stepIndex: Int = 0,
    val roundIndex: Int = 0,
    val repeatIndex: Int = 0,
    val completedUnitKeys: Set<String> = emptySet(),
    val skippedUnitKeys: Set<String> = emptySet(),
    val remainingMs: Long = 0,
    val readyUntilMs: Long? = null,
    val deadlineMs: Long? = null,
    /** This run began with automatic general preparation disabled; its explicit legacy prefix remains its scope. */
    val legacyOnly: Boolean = false,
) {
    val currentUnitKey: String get() = "$stepIndex:$roundIndex:$repeatIndex"

    private fun skippedThroughEnd(snapshot: PreparationSnapshot): Set<String> {
        val size = if (legacyOnly) snapshot.legacyStepCount else snapshot.steps.size
        var skipped: MutableSet<String>? = null
        for (index in 0 until size) {
            val step = snapshot.steps[index]
            for (round in 0 until step.rounds) {
                for (repeat in 0 until step.repetitions) {
                    val key = "$index:$round:$repeat"
                    if (key !in completedUnitKeys && key !in skippedUnitKeys) {
                        if (skipped == null) skipped = skippedUnitKeys.toMutableSet()
                        skipped.add(key)
                    }
                }
            }
        }
        return skipped ?: skippedUnitKeys
    }

    fun remainingMs(nowMs: Long): Long = if (status == PreparationStatus.RUNNING && deadlineMs != null) {
        (deadlineMs - maxOf(nowMs, readyUntilMs ?: nowMs)).coerceIn(0, remainingMs.coerceAtLeast(0))
    } else remainingMs.coerceAtLeast(0)

    /** A clock moving behind readiness (e.g. reboot) cannot start a many-hour timer. */
    fun recovered(snapshot: PreparationSnapshot, nowMs: Long): PreparationProgress {
        if (status == PreparationStatus.SKIPPED) {
            val skipped = skippedThroughEnd(snapshot)
            return if (skipped === skippedUnitKeys) this else copy(skippedUnitKeys = skipped)
        }
        val step = snapshot.steps.getOrNull(stepIndex)
        val limit = if (step?.measure == PreparationMeasure.SECONDS) step.target.toLong() * 1000 else 0
        if (status == PreparationStatus.RUNNING && step?.measure == PreparationMeasure.SECONDS &&
            (readyUntilMs == null || deadlineMs == null)) {
            return copy(status = PreparationStatus.PAUSED, remainingMs = limit, readyUntilMs = null, deadlineMs = null)
        }
        return copy(remainingMs = remainingMs.coerceIn(0, limit)).recovered(nowMs)
    }

    fun recovered(nowMs: Long): PreparationProgress {
        if (status != PreparationStatus.RUNNING) return this
        if (remainingMs == 0L && readyUntilMs == null && deadlineMs == null) return this
        val ready = readyUntilMs
        val end = deadlineMs
        return if (ready == null || end == null || end < ready || end - ready > remainingMs || nowMs < ready - 5000) {
            copy(status = PreparationStatus.PAUSED, readyUntilMs = null, deadlineMs = null)
        } else this
    }

    fun advance(snapshot: PreparationSnapshot, action: PreparationAction, nowMs: Long, expectedUnitKey: String? = null): PreparationProgress {
        if (status == PreparationStatus.DONE || status == PreparationStatus.SKIPPED) return this
        if ((action == PreparationAction.COMPLETE_UNIT || action == PreparationAction.SKIP_UNIT ||
                action == PreparationAction.TIMER_EXPIRED) && expectedUnitKey != currentUnitKey) return this
        if (action == PreparationAction.SKIP_ROUTINE) return copy(status = PreparationStatus.SKIPPED,
            skippedUnitKeys = skippedThroughEnd(snapshot), readyUntilMs = null, deadlineMs = null)
        val step = snapshot.steps.getOrNull(stepIndex) ?: return copy(status = PreparationStatus.DONE, readyUntilMs = null, deadlineMs = null)
        val duration = if (step.measure == PreparationMeasure.SECONDS) step.target.toLong().coerceAtLeast(0) * 1000 else 0
        if (action == PreparationAction.TIMER_EXPIRED &&
            (status != PreparationStatus.RUNNING || duration == 0L || deadlineMs == null || nowMs < deadlineMs)) return this
        return when (action) {
            PreparationAction.START, PreparationAction.RESUME -> {
                if (status == PreparationStatus.RUNNING) this else {
                    val left = if (status == PreparationStatus.PAUSED) remainingMs.coerceIn(0, duration) else duration
                    copy(status = PreparationStatus.RUNNING, remainingMs = left,
                        readyUntilMs = if (duration > 0) nowMs + 5000 else null,
                        deadlineMs = if (duration > 0) nowMs + 5000 + left else null)
                }
            }
            PreparationAction.PAUSE -> if (status != PreparationStatus.RUNNING) this else copy(
                status = PreparationStatus.PAUSED, remainingMs = remainingMs(nowMs).coerceIn(0, duration), readyUntilMs = null, deadlineMs = null)
            PreparationAction.COMPLETE_UNIT, PreparationAction.SKIP_UNIT, PreparationAction.TIMER_EXPIRED -> {
                var s = stepIndex; var r = roundIndex; var repeat = repeatIndex + 1
                if (repeat >= step.repetitions) { repeat = 0; r++ }
                if (r >= step.rounds) { r = 0; s++ }
                // A legacy checklist may have ticked later exercises before earlier ones.
                while (s < snapshot.steps.size && ("$s:$r:$repeat" in completedUnitKeys || "$s:$r:$repeat" in skippedUnitKeys)) {
                    val next = snapshot.steps[s]
                    repeat++
                    if (repeat >= next.repetitions) { repeat = 0; r++ }
                    if (r >= next.rounds) { r = 0; s++ }
                }
                val next = copy(status = if (s >= snapshot.steps.size) PreparationStatus.DONE else PreparationStatus.OPEN,
                    stepIndex = s, roundIndex = r, repeatIndex = repeat,
                    completedUnitKeys = if (action != PreparationAction.SKIP_UNIT) completedUnitKeys + currentUnitKey else completedUnitKeys,
                    skippedUnitKeys = if (action == PreparationAction.SKIP_UNIT) skippedUnitKeys + currentUnitKey else skippedUnitKeys,
                    remainingMs = 0, readyUntilMs = null, deadlineMs = null)
                if (status != PreparationStatus.RUNNING || next.status == PreparationStatus.DONE) next else {
                    val nextStep = snapshot.steps[s]
                    val nextDuration = if (nextStep.measure == PreparationMeasure.SECONDS) nextStep.target.toLong().coerceAtLeast(0) * 1000 else 0
                    // Start from now, not the expired deadline: returning late must never burn through unseen units.
                    val ready = if (nextDuration > 0) nowMs + if (s == stepIndex) 0 else 5000 else null
                    next.copy(status = PreparationStatus.RUNNING, remainingMs = nextDuration,
                        readyUntilMs = ready, deadlineMs = ready?.plus(nextDuration))
                }
            }
            PreparationAction.SKIP_ROUTINE -> this
        }
    }
}

/** One-time import when an old live session has no Room preparation row yet. */
fun legacyPreparationProgress(snapshot: PreparationSnapshot, state: WarmupStep.State, ticked: Set<Long>): PreparationProgress {
    val keys = snapshot.steps.flatMapIndexed { index, step ->
        if (state == WarmupStep.State.DONE || index < snapshot.legacyStepCount && step.exerciseId in ticked)
            (0 until step.rounds).flatMap { round -> (0 until step.repetitions).map { repeat -> "$index:$round:$repeat" } }
        else emptyList()
    }.toSet()
    if (state == WarmupStep.State.SKIPPED) return PreparationProgress(status = PreparationStatus.SKIPPED, completedUnitKeys = keys)
    val first = snapshot.steps.indices.firstOrNull { "$it:0:0" !in keys }
    return PreparationProgress(status = if (first == null && keys.isNotEmpty()) PreparationStatus.DONE else PreparationStatus.OPEN,
        stepIndex = first ?: if (keys.isNotEmpty()) snapshot.steps.size else 0, completedUnitKeys = keys)
}
