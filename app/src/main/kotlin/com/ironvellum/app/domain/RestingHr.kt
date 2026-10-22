package com.ironvellum.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Resting heart rate for days that have no RestingHeartRateRecord. Samsung Health shares heart-rate samples
 * with Health Connect but not a resting figure, so the app derives one. Pure, so the rule is unit-tested
 * without a Health Connect provider.
 *
 * Method: the day's samples are averaged into fixed 30-minute windows ([windows]). The resting rate of a day
 * is the LOWEST window average, provided
 * - the window holds at least [MIN_SAMPLES] samples (a single reading is a blip, not a rate),
 * - the day has at least [MIN_WINDOWS] such windows (about 1.5 hours of wear: a watch put on for one workout
 *   says nothing about rest), and
 * - the result is within [MIN_BPM]..[MAX_BPM] (a day recorded only during exercise would otherwise report a
 *   training rate as a resting one).
 * The lowest window, not BPM_MIN, because the single lowest sample is the one a loose strap or a motion
 * artefact corrupts. Exercise only raises a rate, so the lowest window sits outside a workout by
 * construction and no exercise sessions (and no extra permission to read them) are needed.
 */
object RestingHr {

    const val MIN_SAMPLES = 3
    const val MIN_WINDOWS = 3
    const val MIN_BPM = 30.0
    const val MAX_BPM = 100.0

    private const val WINDOW_SECONDS = 30 * 60L

    data class Sample(val time: Instant, val bpm: Long)

    /** One 30-minute window: its start, the mean bpm of its samples and how many there were. */
    data class Window(val start: Instant, val avgBpm: Double, val samples: Long)

    /** Samples averaged into windows aligned to the half hour. */
    fun windows(samples: List<Sample>): List<Window> =
        samples.groupBy { it.time.epochSecond / WINDOW_SECONDS }
            .map { (slot, group) -> Window(Instant.ofEpochSecond(slot * WINDOW_SECONDS), group.map { it.bpm }.average(), group.size.toLong()) }

    fun derive(windows: List<Window>, zone: ZoneId): Map<LocalDate, Int> =
        windows
            .filter { it.samples >= MIN_SAMPLES }
            .groupBy { it.start.atZone(zone).toLocalDate() }
            .filterValues { it.size >= MIN_WINDOWS }
            .mapValues { (_, day) -> day.minOf { it.avgBpm } }
            .filterValues { it in MIN_BPM..MAX_BPM }
            .mapValues { it.value.roundToInt() }

    /** A day's own RestingHeartRateRecord always wins over a derived figure. */
    fun merge(recorded: Map<LocalDate, Int>, derived: Map<LocalDate, Int>): Map<LocalDate, Int> =
        derived + recorded
}
