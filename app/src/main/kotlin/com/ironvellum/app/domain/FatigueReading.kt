package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How worked a muscle is: the residual against [FatigueTuning.REFERENCE_SETS], the estimate's own scale. */
enum class FatigueLevel {
    FRESH, LIGHT, MODERATE, HIGH, VERY_HIGH;

    companion object {
        /** Boundaries are inclusive at the lower edge, with a hair of tolerance for summed decays. */
        fun of(residual: Double): FatigueLevel {
            val fraction = residual / FatigueTuning.REFERENCE_SETS + EPS
            return when {
                fraction < FatigueTuning.FRESH_BELOW -> FRESH
                fraction < FatigueTuning.LIGHT_BELOW -> LIGHT
                fraction < FatigueTuning.MODERATE_BELOW -> MODERATE
                fraction < FatigueTuning.HIGH_BELOW -> HIGH
                else -> VERY_HIGH
            }
        }

        private const val EPS = 1e-9
    }
}

/** The local day a muscle drops under a level: already there ([None]), today, tomorrow, a weekday within a week, or later. */
sealed interface LightBy {
    data object None : LightBy
    data object Today : LightBy
    data object Tomorrow : LightBy
    data class Weekday(val day: DayOfWeek) : LightBy
    data object OverAWeek : LightBy
}

/** One source of a muscle's fatigue and its rough share of the current total, rounded to the nearest 5%. */
data class CauseShare(
    val exerciseName: String,
    val modifiers: String,
    val liftKey: String,
    val sessionId: Long,
    val sessionAtMs: Long,
    val percent: Int,
)

enum class SorenessReason { NEW_AND_STRETCH, NEW, STRETCH }

data class SorenessNote(val reason: SorenessReason, val exerciseName: String, val modifiers: String, val muscle: Muscle)

data class FatigueReading(
    val muscle: Muscle,
    val residual: Double,
    val level: FatigueLevel,
    /** The top three sources by current residual. */
    val causes: List<CauseShare>,
    /** 100 minus the shown percents, never negative; 0 when three or fewer sources. */
    val otherPercent: Int,
    val lightBy: LightBy,
    val freshBy: LightBy,
    val soreness: SorenessNote?,
)

private const val MS_PER_HOUR = 3_600_000.0
private const val MS_PER_DAY = 24L * 3_600_000L
private const val SHOWN_CAUSES = 3
private const val PERCENT_STEP = 5
private const val BISECTION_STEPS = 48

private sealed interface Crossing {
    data object Already : Crossing
    data class At(val ms: Long) : Crossing
    data object Beyond : Crossing
}

/** Where [muscle] first drops below [fraction] of the reference, looking one window ahead of now. */
private fun FatigueReport.crossing(muscle: Muscle, fraction: Double): Crossing {
    val threshold = fraction * FatigueTuning.REFERENCE_SETS
    if (residual(muscle) < threshold) return Crossing.Already
    var hi = nowMs + FatigueTuning.WINDOW_DAYS * MS_PER_DAY
    if (residual(muscle, hi) >= threshold) return Crossing.Beyond
    // Every term only decays, so the residual falls monotonically and bisection finds the crossing.
    var lo = nowMs
    repeat(BISECTION_STEPS) {
        val mid = lo + (hi - lo) / 2
        if (residual(muscle, mid) < threshold) hi = mid else lo = mid
    }
    return Crossing.At(hi)
}

/** The first instant [muscle] is below [fraction] of the reference; null when already below or still above after a week. */
internal fun FatigueReport.firstBelowMs(muscle: Muscle, fraction: Double): Long? =
    (crossing(muscle, fraction) as? Crossing.At)?.ms

private fun FatigueReport.day(muscle: Muscle, fraction: Double, zone: ZoneId): LightBy = when (val c = crossing(muscle, fraction)) {
    Crossing.Already -> LightBy.None
    Crossing.Beyond -> LightBy.OverAWeek
    is Crossing.At -> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val then = Instant.ofEpochMilli(c.ms).atZone(zone).toLocalDate()
        when (val days = ChronoUnit.DAYS.between(today, then)) {
            0L -> LightBy.Today
            1L -> LightBy.Tomorrow
            in 2L until FatigueTuning.WINDOW_DAYS -> LightBy.Weekday(then.dayOfWeek)
            else -> LightBy.OverAWeek
        }
    }
}

private fun roundedPercent(share: Double): Int = Math.round(share * 100.0 / PERCENT_STEP).toInt() * PERCENT_STEP

private fun FatigueReport.causes(muscle: Muscle): Pair<List<CauseShare>, Int> {
    val live = contributions[muscle].orEmpty().map { it to it.residualAt(nowMs) }.filter { it.second > 0.0 }
    val total = live.sumOf { it.second }
    if (total <= 0.0) return emptyList<CauseShare>() to 0
    val ranked = live.sortedWith(compareByDescending<Pair<FatigueContribution, Double>> { it.second }.thenByDescending { it.first.sessionAtMs })
    val shown = ranked.take(SHOWN_CAUSES).map { (c, residual) ->
        CauseShare(c.exerciseName, c.modifiers, c.liftKey, c.sessionId, c.sessionAtMs, roundedPercent(residual / total))
    }
    val other = if (ranked.size <= SHOWN_CAUSES) 0 else (100 - shown.sumOf { it.percent }).coerceAtLeast(0)
    return shown to other
}

/** Soreness is likely when, 12 to 72 hours on, a muscle took enough dose from stretch-loaded or new work. */
private fun FatigueReport.soreness(muscle: Muscle): SorenessNote? {
    val due = contributions[muscle].orEmpty().filter {
        val ageHours = (nowMs - it.sessionAtMs) / MS_PER_HOUR
        ageHours >= FatigueTuning.SORENESS_FROM_H && ageHours <= FatigueTuning.SORENESS_TO_H && it.qualifyingDose > 0.0
    }
    if (due.sumOf { it.qualifyingDose } < FatigueTuning.SORENESS_MIN_DOSE) return null
    val cause = due.maxBy { it.qualifyingDose }
    val reason = when {
        cause.novel && cause.stretched -> SorenessReason.NEW_AND_STRETCH
        cause.novel -> SorenessReason.NEW
        else -> SorenessReason.STRETCH
    }
    return SorenessNote(reason, cause.exerciseName, cause.modifiers, muscle)
}

/** Everything the breakdown of one muscle shows, from the one report; days are local calendar days in [zone]. */
fun FatigueReport.reading(muscle: Muscle, zone: ZoneId): FatigueReading {
    val residual = residual(muscle)
    val (causes, other) = causes(muscle)
    return FatigueReading(
        muscle = muscle,
        residual = residual,
        level = FatigueLevel.of(residual),
        causes = causes,
        otherPercent = other,
        lightBy = day(muscle, FatigueTuning.LIGHT_BELOW, zone),
        freshBy = day(muscle, FatigueTuning.FRESH_BELOW, zone),
        soreness = soreness(muscle),
    )
}

fun FatigueReport.levelAt(muscle: Muscle, atMs: Long): FatigueLevel = FatigueLevel.of(residual(muscle, atMs))

/** The most fatigued muscles, Light or above, highest residual first; ties by figure order. */
fun FatigueReport.mostWorked(zone: ZoneId, limit: Int = 4): List<FatigueReading> =
    Muscle.entries
        .map { it to residual(it) }
        .filter { FatigueLevel.of(it.second) >= FatigueLevel.LIGHT }
        .sortedWith(compareByDescending<Pair<Muscle, Double>> { it.second }.thenBy { it.first.ordinal })
        .take(limit)
        .map { reading(it.first, zone) }
