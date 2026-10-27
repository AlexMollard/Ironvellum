package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.domain.UiText
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.HouseEffects
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.RepMax
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.SchemeText
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
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

/**
 * A lifter with nothing sealed yet: a profile that exists and holds no XP. An unknown profile (still
 * loading) is not a first run, so nothing first-run flashes in before the truth arrives.
 */
internal fun isFirstRun(totalXp: Long?): Boolean = totalXp != null && totalXp <= 0L

/** The ways to start that a first-run Today adds when the day itself has nothing to begin. */
internal enum class FirstBegin {
    /** Begin the cycle's first rite, even when today is not its day. */
    RITE,

    /** Begin a trial with no rite. */
    OPEN,
}

/**
 * Right after setup, today may fall on a day the cycle leaves free: Respite, with nothing to begin. A
 * first-run lifter is still offered a way to start, the cycle's next rite when there is one and an open
 * trial always. Anywhere else the day card already carries its own primary action.
 */
internal fun firstBegins(kind: DayKind, isToday: Boolean, firstRun: Boolean, hasNextRite: Boolean): List<FirstBegin> =
    if (kind == DayKind.RESPITE && isToday && firstRun) {
        listOfNotNull(FirstBegin.RITE.takeIf { hasNextRite }, FirstBegin.OPEN)
    } else {
        emptyList()
    }

/**
 * The rite a new cycle opens with: the earliest scheduled day's, whatever weekday today is. A cycle taken on a
 * Thursday has its first two rites on days already past; offering the calendar's next rite (the third) would
 * start the lifter in the middle of it.
 */
internal fun firstCycleRite(presets: List<WorkoutPreset>): WorkoutPreset? =
    presets.filter { it.scheduledDay != null }.minByOrNull { it.scheduledDay!! }

/** One movement in the day card: its [value] reads "3×7" before, "4/5" under way, "3/3 · 90 kg × 7" sealed. */
internal data class DayRow(
    val name: String,
    val value: String,
    val done: Int,
    val total: Int,
    val checked: Boolean,
    val exerciseId: Long,
    val modifiers: String,
    /** A warm-up exercise: read as one, and not a movement of the rite's count. */
    val warmup: Boolean = false,
    /** A muscle this exercise leans on that is still High or above: its swatch and name ride the line. */
    val fatigue: FatigueTag? = null,
) {
    /** Some sets logged but not all: a mini bar rides the value. */
    val partly: Boolean get() = done in 1 until total
}

/** The rite's own movements, nothing logged yet; its warm-ups lead, marked as such. [tags] mark the exercises (by entry id) that lean on a tired muscle. */
internal fun plannedRows(entries: List<PresetEntry>, tags: Map<Long, FatigueTag> = emptyMap()): List<DayRow> =
    entries.sortedWith(compareBy<PresetEntry>({ !it.warmup }, { it.position })).map {
        if (it.warmup) {
            DayRow(it.exerciseName, "Warm-up", done = 0, total = it.targetSets, checked = false, exerciseId = it.exerciseId, modifiers = it.modifiers, warmup = true)
        } else {
            DayRow(it.exerciseName, SchemeText.figure(it.scheme, it.targetSets, it.targetReps, null, null), done = 0, total = it.targetSets, checked = false, exerciseId = it.exerciseId, modifiers = it.modifiers, fatigue = tags[it.id])
        }
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
                metric == ExerciseMetric.REPS -> "$total × ${first.reps}"
                metric == ExerciseMetric.HOLD -> "$total × ${first.durationSec ?: 0}s"
                else -> "$total ${if (total == 1) "set" else "sets"}"
            }
            DayRow(
                name = first.exerciseName,
                value = value,
                done = done,
                total = total,
                checked = if (sealed) done > 0 else total > 0 && done == total,
                exerciseId = first.exerciseId,
                modifiers = first.modifiers,
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
        return VeilStrength(0f, Words.text(UiText.res(R.string.always_veil_strength_tapering, (Idle.efficiencyAtHours(hours, houses) * 100).roundToInt())))
    }
    val left = 1.0 - Idle.fullStrengthFraction(elapsed, houses)
    return VeilStrength(left.toFloat(), Words.text(UiText.res(R.string.always_veil_strength_left, ceil(left * window).toInt())))
}

/** The set a peak was lifted with, as the Ledger's rows read it: "100 kg × 5", "+11 kg × 8" for load added to the body. */
internal fun peakSetText(peak: RepMax.Recent): String {
    val set = peak.hit.set
    val figure = setFigureText(set, if (peak.isHold) ExerciseMetric.HOLD else ExerciseMetric.REPS, weighted = !peak.addsToBody)
    return if (peak.addsToBody && (set.weightKg ?: 0.0) > 0.0) "+$figure" else figure
}

/** "2.5 kg" for a heavier peak (never below 0.1), "2 reps" or "5s" for a more-reps one: how far it passed the earlier best. */
internal fun peakGainText(peak: RepMax.Recent): String {
    val hit = peak.hit
    return when (hit.kind) {
        RepMax.Kind.HEAVIER -> {
            val gain = (RepMax.loadOf(hit.set) - hit.was.weightKg).coerceAtLeast(0.1)
            Units.withUnit(Math.round(gain * 10) / 10.0)
        }
        RepMax.Kind.MORE_REPS -> {
            val gain = RepMax.figureOf(hit.set, peak.isHold) - hit.was.figure
            Words.text(if (peak.isHold) UiText.res(R.string.today_peak_gain_seconds, gain) else UiText.plural(R.plurals.common_reps_count, gain, gain))
        }
    }
}

/** The most characters a name may have to share a line: two names side by side must stay readable. */
internal const val PAIR_NAME_CHARS = 13

/** The most characters a sets text may have to share a line ("3 × 8–12" is 8, "3 × 6–10 s" is 10). */
internal const val PAIR_VALUE_CHARS = 9

/**
 * The character rule for a half-width cell: short name, short sets text. The measured width is the second test. An
 * exercise carrying a fatigue tag (swatch and muscle name) always takes a line of its own.
 */
internal fun fitsHalfLine(row: DayRow): Boolean =
    row.fatigue == null && row.name.length <= PAIR_NAME_CHARS && row.value.length <= PAIR_VALUE_CHARS

/**
 * The card's exercises as lines, in routine order. Two consecutive exercises that both pass [fitsHalf] share
 * a line; any other exercise takes a line of its own. Never reorders: an exercise that cannot pair with its
 * neighbour stays alone and the next one starts a new line. Greedy from the top, so the same routine always
 * packs the same way.
 */
internal fun packLines(rows: List<DayRow>, fitsHalf: (DayRow) -> Boolean = ::fitsHalfLine): List<List<DayRow>> {
    val lines = ArrayList<List<DayRow>>(rows.size)
    var i = 0
    while (i < rows.size) {
        if (i + 1 < rows.size && fitsHalf(rows[i]) && fitsHalf(rows[i + 1])) {
            lines += listOf(rows[i], rows[i + 1])
            i += 2
        } else {
            lines += listOf(rows[i])
            i += 1
        }
    }
    return lines
}

/** The exercises behind "+N more": every one on the lines past the first [shown]. */
internal fun hiddenExercises(lines: List<List<DayRow>>, shown: Int): Int = lines.drop(shown).sumOf { it.size }

/**
 * The heights Today has to share, all in pixels. [chrome] is everything but the exercise lines and the
 * Veil (header, week rail, card chrome, plain rows, the bottom clearance); [rowsTopPad] is the gap above
 * the exercise block; lines run from [tightLine] to [naturalLine]; [moreLine] is the "+N more" line;
 * [veilFull] is null unless the full form is on offer. [lineCount] counts lines, not exercises: a line
 * holds one exercise or two.
 */
internal class TodayBudget(
    val available: Int,
    val chrome: Int,
    val lineCount: Int,
    val rowsTopPad: Int,
    val naturalLine: Int,
    val tightLine: Int,
    val moreLine: Int,
    val veilFull: Int?,
    val veilCompact: Int,
)

/** What fits: [lines] exercise lines each [lineHeight] px tall (the rest behind "+N more"), and which Veil form. */
internal data class TodayFit(val lines: Int, val lineHeight: Int, val fullVeil: Boolean)

/**
 * Today's one fitting rule. Degrades in a fixed order: the full Veil is kept only if every exercise
 * line still fits with it (lines tighten from [TodayBudget.naturalLine] toward [TodayBudget.tightLine]
 * first); otherwise the compact Veil, with lines tightening and then truncating behind the "+N more"
 * line, down to none. Slack goes to more lines before taller ones. Null when even that does not fit:
 * the caller scrolls rather than clip.
 */
internal fun fitToday(b: TodayBudget): TodayFit? {
    b.veilFull?.let { full ->
        val room = b.available - b.chrome - full
        linesThatFit(b, room, mayTruncate = false)?.let { (lines, height) -> return TodayFit(lines, height, fullVeil = true) }
    }
    val room = b.available - b.chrome - b.veilCompact
    return linesThatFit(b, room, mayTruncate = true)?.let { (lines, height) -> TodayFit(lines, height, fullVeil = false) }
}

/** Lines shown and their height within [room] px, or null when not even the minimum fits. */
private fun linesThatFit(b: TodayBudget, room: Int, mayTruncate: Boolean): Pair<Int, Int>? {
    if (b.lineCount == 0) return if (room >= 0) 0 to 0 else null
    val linesRoom = room - b.rowsTopPad
    if (linesRoom >= b.lineCount * b.tightLine) return b.lineCount to minOf(b.naturalLine, linesRoom / b.lineCount)
    if (!mayTruncate || linesRoom < b.moreLine) return null
    val listRoom = linesRoom - b.moreLine
    val shown = listRoom / b.tightLine
    return shown to if (shown == 0) b.naturalLine else minOf(b.naturalLine, listRoom / shown)
}

/** "40" for a whole rate, "12.5" otherwise. */
internal fun rateLabel(perHour: Double): String =
    if (perHour % 1.0 == 0.0) "%.0f".format(java.util.Locale.US, perHour) else "%.1f".format(java.util.Locale.US, perHour)

/**
 * The Veil's reserved inscriptions line. It is shown in every Veil form whether or not any wait, so the
 * layout never shifts when one arrives: with none (or while the Veil is still loading) it is a quiet
 * line. [InscriptionsLine.action] is the link that rides it: "Inscribe (N)" when some wait, "Buy one"
 * with its price when none wait and one is affordable, and nothing otherwise.
 */
internal data class InscriptionsLine(val text: String, val waiting: Boolean, val action: String? = null)

internal fun inscriptionsLine(waiting: Int?, buyPrice: Long? = null): InscriptionsLine = when {
    waiting == null -> InscriptionsLine("", waiting = false)
    waiting > 0 -> InscriptionsLine(
        Words.text(UiText.plural(R.plurals.always_veil_inscriptions_waiting, waiting, waiting)),
        waiting = true,
        action = Words.text(UiText.res(R.string.always_veil_inscribe_action, waiting)),
    )
    buyPrice != null -> InscriptionsLine(
        Words.text(UiText.res(R.string.always_veil_inscriptions_none)),
        waiting = false,
        action = Words.text(UiText.res(R.string.always_veil_buy_action, "%,d".format(buyPrice))),
    )
    else -> InscriptionsLine(Words.text(UiText.res(R.string.always_veil_inscriptions_none)), waiting = false)
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
