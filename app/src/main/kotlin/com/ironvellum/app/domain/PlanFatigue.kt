package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** The muscles still recovering, worst first, each with the source that put it there. */
data class RightNowRow(val items: List<RightNowItem>)

data class RightNowItem(val muscle: Muscle, val level: FatigueLevel, val cause: CauseShare)

/** A planned day that meets a muscle still at [level] by 08:00 on [weekday]. */
data class DayFatigueNote(val muscle: Muscle, val level: FatigueLevel, val weekday: DayOfWeek)

/**
 * What the plan builder says about fatigue. It only reads: nothing here changes a preset, so the proposal is
 * the same with or without it.
 */
object PlanFatigue {

    private const val ROW_MUSCLES = 3
    private const val DAY_NOTES = 2
    private val MORNING = LocalTime.of(8, 0)

    /** Muscles at Moderate or above, up to three, highest residual first; null when none is. */
    fun rightNow(report: FatigueReport, zone: ZoneId): RightNowRow? {
        val items = Muscle.entries
            .map { report.reading(it, zone) }
            .filter { it.level >= FatigueLevel.MODERATE && it.causes.isNotEmpty() }
            .sortedWith(compareByDescending<FatigueReading> { it.residual }.thenBy { it.muscle.ordinal })
            .take(ROW_MUSCLES)
            .map { RightNowItem(it.muscle, it.level, it.causes.first()) }
        return if (items.isEmpty()) null else RightNowRow(items)
    }

    /** The next 08:00 local on ISO weekday [scheduledDay] (1 = Monday) that is strictly after [nowMs]. */
    fun nextEightAm(scheduledDay: Int, nowMs: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val sameWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(scheduledDay)))
        val first = ZonedDateTime.of(sameWeek, MORNING, zone)
        val at = if (first.toInstant().toEpochMilli() > nowMs) first else ZonedDateTime.of(sameWeek.plusWeeks(1), MORNING, zone)
        return at.toInstant().toEpochMilli()
    }

    /**
     * Muscles [preset] trains (a share of 0.5 or more in any movement) that will still be at Moderate or above at
     * 08:00 on its day; up to two, worst first. Empty for an unscheduled day or when they are Light or Fresh.
     */
    fun dayNotes(preset: PlannedPreset, report: FatigueReport, zone: ZoneId): List<DayFatigueNote> {
        val day = preset.scheduledDay ?: return emptyList()
        if (day !in 1..7) return emptyList()
        val at = nextEightAm(day, report.nowMs, zone)
        val trained = preset.entries
            .flatMap { MuscleMap.profile(it)?.muscles.orEmpty().filterValues { share -> share >= FatigueTuning.TRAINS_SHARE }.keys }
            .toSet()
        return trained
            .filter { report.levelAt(it, at) >= FatigueLevel.MODERATE }
            .sortedWith(compareByDescending<Muscle> { report.residual(it, at) }.thenBy { it.ordinal })
            .take(DAY_NOTES)
            .map { DayFatigueNote(it, report.levelAt(it, at), DayOfWeek.of(day)) }
    }
}
