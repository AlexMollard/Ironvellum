package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** Window of the weight chart. [days] null is the whole history. */
enum class LedgerRange(val label: String, val days: Int?) {
    D30("30D", 30),
    D90("90D", 90),
    ALL("ALL", null),
}

/** A fully derived weight chart: values placed by date, not by index. */
data class WeightPlot(
    val values: List<Double>,
    /** 0..1 along the window, same size as [values]. */
    val positions: List<Double>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Latest minus earliest reading in the window; null with fewer than two. */
    val deltaKg: Double?,
)

/** Mean of the days that had data, and how many those were of the days asked. */
data class WindowAverage(val value: Double, val tracked: Int, val of: Int)

data class FfmiReading(val value: Double, val takenAtMs: Long)

data class StrengthTrend(val latest: Int, val delta: Int?, val trialsBack: Int)

/**
 * Every number the Ledger derives, as pure functions of data plus an explicit
 * "today" and zone. Nothing here reads a clock, so the date windows - which
 * used to be "the newest rows we happen to have" - are testable and a midnight
 * rollover is just a new [today].
 */
object Ledger {

    // ------------------------------------------------------------ dates

    fun dateOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** The [days] calendar dates ending on [today], oldest first. */
    fun window(today: LocalDate, days: Int): List<LocalDate> =
        (days - 1 downTo 0).map { today.minusDays(it.toLong()) }

    /**
     * One slot per calendar date in the window. A date with no row, or a zero
     * figure, is null - "no data" is not zero, and the chart shows the gap.
     */
    fun slots(
        rows: List<HealthDay>,
        today: LocalDate,
        days: Int,
        pick: (HealthDay) -> Double?,
    ): List<Double?> {
        val byDate = rows.associateBy { it.date }
        return window(today, days).map { date ->
            byDate[date]?.let(pick)?.takeIf { it > 0.0 }
        }
    }

    fun tracked(slots: List<Double?>): Int = slots.count { it != null }

    /** Today's row, or null when Health Connect has not delivered one: never an older day. */
    fun todayRow(rows: List<HealthDay>, today: LocalDate): HealthDay? = rows.firstOrNull { it.date == today }

    /**
     * Mean over the days in the window that have data. A cumulative figure
     * (steps, kcal) passes [includeToday] false: today is partial and would
     * drag the mean down; a window of [days] then ends yesterday.
     */
    fun average(
        rows: List<HealthDay>,
        today: LocalDate,
        days: Int,
        includeToday: Boolean,
        pick: (HealthDay) -> Double?,
    ): WindowAverage? {
        val end = if (includeToday) today else today.minusDays(1)
        val values = slots(rows, end, days, pick).filterNotNull()
        if (values.isEmpty()) return null
        return WindowAverage(values.average(), values.size, days)
    }

    // ----------------------------------------------------------- weight

    /** Profile height wins (it is the current one); a row's own height is the fallback. */
    fun heightFor(entry: StatEntry, profileHeightCm: Double?): Double? =
        profileHeightCm?.takeIf { it > 0.0 } ?: entry.heightCm.takeIf { it > 0.0 }

    fun bmiOf(entry: StatEntry, profileHeightCm: Double?): Double? =
        heightFor(entry, profileHeightCm)?.let { BodyStats.bmi(entry.weightKg, it) }

    fun ffmiOf(entry: StatEntry, profileHeightCm: Double?): Double? {
        val bf = entry.bodyFatPct ?: return null
        val h = heightFor(entry, profileHeightCm) ?: return null
        return BodyStats.ffmiNormalised(entry.weightKg, h, bf)
    }

    /** FFMI from the newest reading that carries body fat, with when it was taken. */
    fun latestFfmi(stats: List<StatEntry>, profileHeightCm: Double?): FfmiReading? =
        stats.sortedByDescending { it.takenAtMs }
            .firstNotNullOfOrNull { s -> ffmiOf(s, profileHeightCm)?.let { FfmiReading(it, s.takenAtMs) } }

    /** Newest reading that carries body fat; resting burn needs it. */
    fun latestWithBodyFat(stats: List<StatEntry>): StatEntry? =
        stats.filter { it.bodyFatPct != null }.maxByOrNull { it.takenAtMs }

    fun weightPlot(stats: List<StatEntry>, range: LedgerRange, today: LocalDate, zone: ZoneId): WeightPlot? {
        val sorted = stats.sortedBy { it.takenAtMs }
        if (sorted.isEmpty()) return null
        val first = dateOf(sorted.first().takenAtMs, zone)
        val start = range.days?.let { today.minusDays(it - 1L) } ?: first.coerceAtMost(today)
        val inWindow = sorted.filter { dateOf(it.takenAtMs, zone) >= start }
        if (inWindow.isEmpty()) return null
        val span = java.time.temporal.ChronoUnit.DAYS.between(start, today).coerceAtLeast(1L).toDouble()
        val positions = inWindow.map { s ->
            (java.time.temporal.ChronoUnit.DAYS.between(start, dateOf(s.takenAtMs, zone)) / span).coerceIn(0.0, 1.0)
        }
        val delta = if (inWindow.size >= 2) inWindow.last().weightKg - inWindow.first().weightKg else null
        return WeightPlot(inWindow.map { it.weightKg }, positions, start, today, delta)
    }

    // ------------------------------------------------------------- text

    /**
     * "+1.2 kg" / "-0.4 kg" / "0.0 kg": one sign, never an arrow and a sign
     * together (the tape tile printed "up-arrow -0.3"). Uses a true minus.
     */
    fun signed(value: Double, unit: String): String {
        val rounded = Math.round(value * 10.0) / 10.0
        val sign = when {
            rounded > 0.0 -> "+"
            rounded < 0.0 -> "\u2212"
            else -> ""
        }
        return "$sign${String.format(java.util.Locale.US, "%.1f", abs(rounded))} $unit"
    }

    /** A typed decimal as the keyboard may give it: a comma is a point. */
    fun parseDecimal(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    // ----------------------------------------------------------- energy

    /** Sessions completed on [date], with their logged sets. */
    fun sessionsOn(
        date: LocalDate,
        sessions: List<WorkoutSession>,
        sessionSets: Map<Long, List<SessionSet>>,
        zone: ZoneId,
    ): List<Pair<WorkoutSession, List<SessionSet>>> = sessions
        .filter { dateOf(it.completedAtMs ?: it.startedAtMs, zone) == date }
        .map { it to sessionSets[it.id].orEmpty() }

    /**
     * One day's burn. A measured Health Connect figure wins outright and the
     * estimate is never added on top. No row and no session is null - unknown,
     * not a zero-kcal day.
     */
    fun dayBurn(
        row: HealthDay?,
        sessionsThatDay: List<Pair<WorkoutSession, List<SessionSet>>>,
        exercises: Map<Long, Exercise>,
        weightKg: Double?,
        heightCm: Double?,
    ): EnergyEstimate? {
        if (row == null && sessionsThatDay.isEmpty()) return null
        val steps = row?.steps ?: 0
        val stepsEst = if (steps > 0) {
            Energy.stepsKcal(steps, row?.distanceKm?.takeIf { it > 0.0 }, weightKg, heightCm)
        } else {
            null
        }
        val sessionEsts = sessionsThatDay.mapNotNull { (session, sets) ->
            val minutes = session.completedAtMs?.let { ((it - session.startedAtMs) / 60_000L).toInt().coerceAtLeast(0) }
            Energy.sessionKcal(sets, exercises, weightKg, minutes)
        }
        return Energy.dayKcal(row?.activeKcal?.takeIf { it > 0 }, stepsEst, sessionEsts)
    }

    /** [dayBurn] for every date of the window, oldest first. */
    fun burnSlots(
        rows: List<HealthDay>,
        today: LocalDate,
        days: Int,
        sessions: List<WorkoutSession>,
        sessionSets: Map<Long, List<SessionSet>>,
        exercises: Map<Long, Exercise>,
        weightKg: Double?,
        heightCm: Double?,
        zone: ZoneId,
    ): List<EnergyEstimate?> {
        val byDate = rows.associateBy { it.date }
        return window(today, days).map { date ->
            dayBurn(byDate[date], sessionsOn(date, sessions, sessionSets, zone), exercises, weightKg, heightCm)
        }
    }

    // ---------------------------------------------------------- training

    /** Per-trial strength score trend: latest scored trial against [lookback] scored trials earlier. */
    fun strengthTrend(sessions: List<WorkoutSession>, lookback: Int = 5): StrengthTrend? {
        // 0 means "not scored" (no bodyweight yet), not a collapse in strength.
        val scored = sessions.sortedBy { it.startedAtMs }.map { it.strengthScore }.filter { it > 0 }
        if (scored.isEmpty()) return null
        val latest = scored.last()
        if (scored.size < 2) return StrengthTrend(latest, null, 0)
        val back = lookback.coerceAtMost(scored.size - 1)
        return StrengthTrend(latest, latest - scored[scored.size - 1 - back], back)
    }

    /** Sealed days per week (Monday start) for the [weeks] weeks ending with today's, oldest first. */
    fun weeklyCounts(sealedDays: Set<LocalDate>, today: LocalDate, weeks: Int = 12): List<Int> {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (weeks - 1 downTo 0).map { back ->
            val monday = thisMonday.minusWeeks(back.toLong())
            (0..6).count { monday.plusDays(it.toLong()) in sealedDays }
        }
    }

    fun sealedThisMonth(sealedDays: Set<LocalDate>, today: LocalDate): Int =
        sealedDays.count { it.year == today.year && it.month == today.month && it <= today }

    // ------------------------------------------------------------ summary text

    /** 8240 -> "8.2k", 950 -> "950": a step count short enough for a row. */
    fun compactCount(n: Int): String =
        if (n >= 1000) String.format(java.util.Locale.US, "%.1fk", n / 1000.0) else n.toString()

    /** 430 -> "7h 10m". */
    fun sleepText(minutes: Int): String = "${minutes / 60}h ${minutes % 60}m"

    /**
     * What the Daily row on the body tab says: steps, sleep, active kcal for
     * today, only the parts Health Connect delivered, or null when it has
     * delivered nothing for today.
     */
    fun dailySummary(todayRow: HealthDay?): String? {
        val parts = listOfNotNull(
            todayRow?.steps?.takeIf { it > 0 }?.let { "${compactCount(it)} steps" },
            todayRow?.sleepMinutes?.takeIf { it > 0 }?.let { sleepText(it) },
            todayRow?.activeKcal?.takeIf { it > 0 }?.let { "$it kcal" },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" \u00B7 ")
    }
}
