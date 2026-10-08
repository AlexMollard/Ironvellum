package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.HouseEffects
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.LiftRecord
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.ui.components.formatLoadKg
import com.ironvellum.app.ui.components.plural
import com.ironvellum.app.ui.train.setFigureText
import kotlin.math.ceil
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

/** How much of the Veil's full-strength day is left, and what that means in words. */
internal data class VeilStrength(val fraction: Float, val caption: String)

/**
 * The Veil's full-strength bar, read as time in hand: full right after collecting, draining across
 * the full-strength window (24 h, 26 h with a full Iron house: [HouseEffects.fullStrengthHours]) away,
 * with the whole hours left in the caption (rounded up, so the last stretch never reads "0 h left").
 * Past that the bar is empty and the caption reports the tapering output. Null when there is nothing
 * to measure: no collection baseline yet, or a clock set backwards.
 */
internal fun veilStrength(lastCollectedAtMs: Long, nowMs: Long, houses: HouseEffects = HouseEffects.NONE): VeilStrength? {
    if (lastCollectedAtMs <= 0L || nowMs < lastCollectedAtMs) return null
    val elapsed = nowMs - lastCollectedAtMs
    val hours = elapsed / 3_600_000.0
    val window = houses.fullStrengthHours
    if (hours >= window) {
        return VeilStrength(0f, "Tapering, at ${(Idle.efficiencyAtHours(hours, houses) * 100).roundToInt()}% strength")
    }
    val left = 1.0 - Idle.fullStrengthFraction(elapsed, houses)
    return VeilStrength(left.toFloat(), "${ceil(left * window).toInt()} h left at full strength")
}

/** The set a peak was lifted with, as the Ledger's rows read it: "100 kg × 5", "+11 kg × 8" for load added to the body. */
internal fun peakSetText(peak: LiftRecord): String {
    val set = peak.bestSet ?: return "est. ${"%.0f".format(java.util.Locale.US, peak.bestE1rmKg)} kg"
    val figure = setFigureText(set, ExerciseMetric.REPS, weighted = !peak.bestSetAddsToBody)
    return if (peak.bestSetAddsToBody && (set.weightKg ?: 0.0) > 0.0) "+$figure" else figure
}

/** "3 kg", "2.5 kg": how far the peak rose above the lift's earlier best (an estimated one-rep max), never below 0.1. */
internal fun peakGainText(peak: LiftRecord): String {
    val best = peak.series.indexOfFirst { it == peak.bestE1rmKg }
    val gain = (peak.bestE1rmKg - (peak.series.take(best).maxOrNull() ?: peak.bestE1rmKg)).coerceAtLeast(0.1)
    return "${formatLoadKg(Math.round(gain * 10) / 10.0)} kg"
}

/**
 * The heights Today has to share, all in pixels. [chrome] is everything but the exercise rows and the
 * Veil (header, week rail, card chrome, plain rows, the bottom clearance); [rowsTopPad] is the gap above
 * the exercise block; rows run from [tightRow] to [naturalRow]; [moreLine] is the "+N more" line;
 * [veilFull] is null unless the full form is on offer.
 */
internal class TodayBudget(
    val available: Int,
    val chrome: Int,
    val rowCount: Int,
    val rowsTopPad: Int,
    val naturalRow: Int,
    val tightRow: Int,
    val moreLine: Int,
    val veilFull: Int?,
    val veilCompact: Int,
)

/** What fits: [rows] exercise rows each [rowHeight] px tall (the rest behind "+N more"), and which Veil form. */
internal data class TodayFit(val rows: Int, val rowHeight: Int, val fullVeil: Boolean)

/**
 * Today's one fitting rule. Degrades in a fixed order: the full Veil is kept only if every exercise
 * row still fits with it (rows tighten from [TodayBudget.naturalRow] toward [TodayBudget.tightRow]
 * first); otherwise the compact Veil, with rows tightening and then truncating behind the "+N more"
 * line, down to none. Null when even that does not fit: the caller scrolls rather than clip.
 */
internal fun fitToday(b: TodayBudget): TodayFit? {
    b.veilFull?.let { full ->
        val room = b.available - b.chrome - full
        rowsThatFit(b, room, mayTruncate = false)?.let { (rows, height) -> return TodayFit(rows, height, fullVeil = true) }
    }
    val room = b.available - b.chrome - b.veilCompact
    return rowsThatFit(b, room, mayTruncate = true)?.let { (rows, height) -> TodayFit(rows, height, fullVeil = false) }
}

/** Rows shown and their height within [room] px, or null when not even the minimum fits. */
private fun rowsThatFit(b: TodayBudget, room: Int, mayTruncate: Boolean): Pair<Int, Int>? {
    if (b.rowCount == 0) return if (room >= 0) 0 to 0 else null
    val rowsRoom = room - b.rowsTopPad
    if (rowsRoom >= b.rowCount * b.tightRow) return b.rowCount to minOf(b.naturalRow, rowsRoom / b.rowCount)
    if (!mayTruncate || rowsRoom < b.moreLine) return null
    val listRoom = rowsRoom - b.moreLine
    val shown = listRoom / b.tightRow
    return shown to if (shown == 0) b.naturalRow else minOf(b.naturalRow, listRoom / shown)
}

/** "40" for a whole rate, "12.5" otherwise. */
internal fun rateLabel(perHour: Double): String =
    if (perHour % 1.0 == 0.0) "%.0f".format(java.util.Locale.US, perHour) else "%.1f".format(java.util.Locale.US, perHour)

/**
 * The one italic line under the rite name, by state. All of Today's narration lives here so the voice
 * is read, tested and kept free of retired words in one place. [setsDone] and [setsTotal] are the live
 * trial's working sets; [daysKept] is the oath's count.
 */
internal fun narratorLine(kind: DayKind, setsDone: Int = 0, setsTotal: Int = 0, daysKept: Int = 0): String = when (kind) {
    DayKind.BEGIN ->
        if (daysKept > 0) "The page is blank. $daysKept ${plural(daysKept, "day", "days")} of ink behind it."
        else "The page is blank."
    DayKind.LIVE -> when {
        setsDone <= 0 -> "The page is blank. Ink the first set."
        setsDone >= setsTotal -> "Every set is inked. Seal the page."
        setsDone * 3 < setsTotal -> "$setsDone ${plural(setsDone, "set", "sets")} inked. The page begins to fill."
        else -> "$setsDone ${plural(setsDone, "set", "sets")} inked. The page is half written."
    }
    DayKind.SEALED -> "The Ledger gilds its page."
    DayKind.PLANNED -> "The page waits for its day."
    DayKind.RESPITE ->
        if (daysKept > 0) "The Ledger rests its pen. Your oath holds." else "The Ledger rests its pen."
    DayKind.NO_CYCLE -> "Your cycle is unwritten. Forge one and its rites land here."
}

/**
 * The Veil's reserved inscriptions line. It is shown in every Veil form whether or not any wait, so the
 * layout never shifts when one arrives: with none (or while the Veil is still loading) it is a quiet
 * line, and [InscriptionsLine.inscribe] says whether the "Inscribe" link rides it.
 */
internal data class InscriptionsLine(val text: String, val waiting: Boolean) {
    val inscribe: Boolean get() = waiting
}

internal fun inscriptionsLine(waiting: Int?): InscriptionsLine = when {
    waiting == null -> InscriptionsLine("", waiting = false)
    waiting > 0 -> InscriptionsLine("$waiting ${plural(waiting, "inscription", "inscriptions")} waiting", waiting = true)
    else -> InscriptionsLine("No inscriptions waiting", waiting = false)
}

/** The SEALED stamp thuds in once, and only for a rite sealed this recently: an old seal is simply there. */
internal const val STAMP_FRESH_MS = 60L * 60 * 1000

internal fun stampIsFresh(completedAtMs: Long?, nowMs: Long): Boolean =
    completedAtMs != null && nowMs - completedAtMs in 0..STAMP_FRESH_MS

/**
 * One drifting mote of the Veil: where it starts as a fraction of the area, its size, how long a rise
 * takes ([periodS], always a divisor of [MOTION_LOOP_S] so the shared loop wraps without a jump), its
 * [phase] into that rise, and how far it drifts sideways and up.
 */
internal data class VeilMote(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val periodS: Int,
    val phase: Float,
    val driftDp: Float,
    val rise: Float,
    val bright: Boolean,
)

/** One full turn of Today's motion clock, in seconds: every period below divides it. */
internal const val MOTION_LOOP_S = 360

private val MOTE_PERIODS_S = intArrayOf(10, 12, 15, 18, 20)

/** A seeded field, so the same Veil always drifts the same way. */
internal fun veilMotes(count: Int, seed: Long, maxRadiusDp: Float = 2f): List<VeilMote> {
    val rng = kotlin.random.Random(seed)
    return List(count) {
        VeilMote(
            x = 0.02f + rng.nextFloat() * 0.94f,
            y = rng.nextFloat(),
            radiusDp = 0.7f + rng.nextFloat() * (maxRadiusDp - 0.7f),
            periodS = MOTE_PERIODS_S[rng.nextInt(MOTE_PERIODS_S.size)],
            phase = rng.nextFloat(),
            driftDp = (rng.nextFloat() - 0.5f) * 52f,
            rise = 0.35f + rng.nextFloat() * 0.45f,
            bright = rng.nextFloat() < 0.2f,
        )
    }
}

/** How far along its rise a mote is at [loop] (0..1 of [MOTION_LOOP_S]), 0..1. */
internal fun moteProgress(mote: VeilMote, loop: Float): Float {
    val p = (loop * MOTION_LOOP_S / mote.periodS + mote.phase) % 1f
    return if (p < 0f) p + 1f else p
}

/** A mote's opacity over its rise: fades in, holds, fades out, so none ever pops. */
internal fun moteAlpha(progress: Float): Float = when {
    progress < 0.15f -> progress / 0.15f * 0.75f
    progress < 0.8f -> 0.75f - (progress - 0.15f) / 0.65f * 0.3f
    else -> (1f - progress) / 0.2f * 0.45f
}.coerceIn(0f, 1f)
