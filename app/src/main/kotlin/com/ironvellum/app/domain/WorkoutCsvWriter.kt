package com.ironvellum.app.domain

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Writes trials as a Strong-format CSV, one row per ticked set — the layout
 * [CsvWorkoutReader] reads, so the app (and Strong-aware tools) can take its
 * own export back in. Weight is kg and distance km, as the reader expects.
 *
 * Only the PUBLIC note is written: a CSV is made to be shared, and the private
 * note stays in the JSON archive. Grade and modifiers have no Strong column.
 *
 * Pure Kotlin so it is unit-testable on the JVM.
 */
object WorkoutCsvWriter {

    /** A calendar period ending today: the week from Monday, the month from the 1st, the year from 1 January. */
    enum class Period(val noun: String) {
        WEEK("week"),
        MONTH("month"),
        YEAR("year");

        fun startOf(today: LocalDate): LocalDate = when (this) {
            WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            MONTH -> today.withDayOfMonth(1)
            YEAR -> today.withDayOfYear(1)
        }
    }

    const val HEADER =
        "Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes"

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Completed trials with a ticked set that began on or after the period's first day, in [zone]. */
    fun inPeriod(
        sessions: List<Pair<WorkoutSession, List<SessionSet>>>,
        period: Period,
        today: LocalDate,
        zone: ZoneId,
    ): List<Pair<WorkoutSession, List<SessionSet>>> {
        val fromMs = period.startOf(today).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessions.filter { (s, sets) -> s.completedAtMs != null && s.startedAtMs >= fromMs && sets.any { it.done } }
    }

    fun write(sessions: List<Pair<WorkoutSession, List<SessionSet>>>, zone: ZoneId): String = buildString {
        // BOM so Excel reads names and notes as UTF-8; the reader strips it.
        append('﻿').append(HEADER).append("\r\n")
        sessions.sortedBy { it.first.startedAtMs }.forEach { (session, sets) ->
            val date = DATE.format(Instant.ofEpochMilli(session.startedAtMs).atZone(zone))
            val name = session.title.ifBlank { session.label }
            val duration = session.completedAtMs?.let { duration((it - session.startedAtMs) / 1000) } ?: ""
            sets.filter { it.done }
                .sortedWith(compareBy({ it.exercisePosition }, { it.setIndex }))
                .groupBy { it.exercisePosition }
                .values
                .forEach { block ->
                    // Set Order restarts at 1 per movement, counting ticked sets only.
                    block.forEachIndexed { i, set ->
                        appendRow(
                            date,
                            name,
                            duration,
                            set.exerciseName,
                            (i + 1).toString(),
                            set.weightKg?.let(::number) ?: "",
                            set.reps.toString(),
                            set.distanceM?.let { number(it / 1000.0) } ?: "",
                            set.durationSec?.toString() ?: "",
                            "",
                            session.note,
                        )
                    }
                }
        }
    }

    /** Strong's `2h 38m`; a trial under a minute keeps its seconds so it still reads back. */
    internal fun duration(totalSec: Long): String {
        if (totalSec <= 0) return ""
        val h = totalSec / 3600
        val m = totalSec % 3600 / 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m"
            else -> "${totalSec}s"
        }
    }

    private fun number(v: Double): String = BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()

    private fun StringBuilder.appendRow(vararg fields: String) {
        fields.forEachIndexed { i, f ->
            if (i > 0) append(',')
            append(quote(f))
        }
        append("\r\n")
    }

    /** RFC 4180: quote a field holding a comma, quote or line break; double its quotes. */
    internal fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
