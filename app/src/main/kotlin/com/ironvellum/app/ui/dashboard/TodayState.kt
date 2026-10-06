package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.ui.train.setFigureText
import kotlin.math.roundToInt

/** Which face the Today day card wears. One primary action at most, and only for LIVE, BEGIN and NO_CYCLE. */
internal enum class DayKind {
    /** A trial is under way and today is selected: Continue. */
    LIVE,

    /** The selected day's rite was sealed this week: what it paid, and a tap opens the trial. */
    SEALED,

    /** Today's rite, not yet begun: Begin. */
    BEGIN,

    /** Another day's rite, to read: no button. */
    PLANNED,

    /** The cycle leaves the day free. */
    RESPITE,

    /** No cycle at all: Forge a cycle. */
    NO_CYCLE,
}

/**
 * The card's state. A live trial wins on today, whatever rite it belongs to, so there is one
 * obvious next action; a sealed rite wins over its own Begin; with no rite on the day the
 * cycle is either unwritten ([hasCycle] false) or leaves the day free.
 */
internal fun dayKind(
    hasCycle: Boolean,
    rite: WorkoutPreset?,
    isToday: Boolean,
    live: WorkoutSession?,
    sealed: WorkoutSession?,
): DayKind = when {
    isToday && live != null -> DayKind.LIVE
    !hasCycle -> DayKind.NO_CYCLE
    rite == null -> DayKind.RESPITE
    sealed != null -> DayKind.SEALED
    isToday -> DayKind.BEGIN
    else -> DayKind.PLANNED
}

/** One movement in the day card: its [value] reads "3×7" before, "4/5" under way, "3/3 · 90 kg × 7" sealed. */
internal data class DayRow(val name: String, val value: String, val done: Int, val total: Int, val checked: Boolean) {
    /** Some sets logged but not all: a mini bar rides the value. */
    val partly: Boolean get() = done in 1 until total
}

/** The rite's own movements, nothing logged yet. */
internal fun plannedRows(entries: List<PresetEntry>): List<DayRow> =
    entries.sortedBy { it.position }.map {
        DayRow(it.exerciseName, "${it.targetSets}×${it.targetReps}", done = 0, total = it.targetSets, checked = false)
    }

/**
 * A trial's movements as it stands, from its own sets (warm-ups left out, so a trial can
 * complete): one row per exercise block, in the order it was set out. A live trial reads "4/5"
 * once it has a set logged; a [sealed] one adds its best set, and any block with a set logged
 * counts as done.
 */
internal fun trialRows(sets: List<SessionSet>, exercises: Map<Long, Exercise>, sealed: Boolean): List<DayRow> =
    sets.filter { !it.warmup }
        .groupBy { it.exercisePosition }
        .toSortedMap()
        .values
        .map { block ->
            val first = block.first()
            val exercise = exercises[first.exerciseId]
            val metric = exercise?.metric ?: ExerciseMetric.REPS
            val done = block.count { it.done }
            val total = block.size
            val logged = block.filter { it.done }
            val value = when {
                sealed && logged.isNotEmpty() -> {
                    val best = if (metric == ExerciseMetric.REPS || metric == ExerciseMetric.HOLD) {
                        logged.maxWith(compareBy<SessionSet>({ it.weightKg ?: 0.0 }, { if (metric == ExerciseMetric.HOLD) it.durationSec ?: 0 else it.reps }))
                    } else {
                        logged.maxBy { (it.durationSec ?: 0) + it.reps }
                    }
                    "$done/$total · ${setFigureText(best, metric, exercise?.isWeighted ?: false)}"
                }
                done > 0 || sealed -> "$done/$total"
                metric == ExerciseMetric.REPS -> "$total×${first.reps}"
                metric == ExerciseMetric.HOLD -> "$total×${first.durationSec ?: 0}s"
                else -> "$total ${if (total == 1) "set" else "sets"}"
            }
            DayRow(
                name = first.exerciseName,
                value = value,
                done = done,
                total = total,
                checked = if (sealed) done > 0 else total > 0 && done == total,
            )
        }

/** "5 EXERCISES · 19 SETS · ~54 MIN" read as a sentence: "5 exercises · 19 sets · about 54 min". */
internal fun sentencePlan(line: String): String = line.lowercase().replace("~", "about ")

/** How far through the Veil's full-strength day, and what that means in words. */
internal data class VeilStrength(val fraction: Float, val caption: String)

/**
 * The Veil's full-strength bar: it fills across [Idle.FULL_RATE_HOURS] away, and past that stays
 * full while the caption reports the tapering output. Null when there is nothing to measure:
 * no collection baseline yet, or a clock set backwards.
 */
internal fun veilStrength(lastCollectedAtMs: Long, nowMs: Long): VeilStrength? {
    if (lastCollectedAtMs <= 0L || nowMs < lastCollectedAtMs) return null
    val elapsed = nowMs - lastCollectedAtMs
    val hours = elapsed / 3_600_000.0
    val caption = if (hours < Idle.FULL_RATE_HOURS) {
        "${hours.toInt()} h of ${Idle.FULL_RATE_HOURS.toInt()} at full strength, then it tapers"
    } else {
        "Tapering, at ${(Idle.efficiencyAtHours(hours) * 100).roundToInt()}% strength"
    }
    return VeilStrength(Idle.fullStrengthFraction(elapsed).toFloat(), caption)
}

/** "40" for a whole rate, "12.5" otherwise. */
internal fun rateLabel(perHour: Double): String =
    if (perHour % 1.0 == 0.0) "%.0f".format(java.util.Locale.US, perHour) else "%.1f".format(java.util.Locale.US, perHour)
