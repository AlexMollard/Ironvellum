package com.ironvellum.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One sleep session from Health Connect with the spans inside it that were not sleep. */
data class SleepSpan(
    val start: Instant,
    val end: Instant,
    /** Awake, awake-in-bed and out-of-bed stages: inside the session, not asleep. */
    val awake: List<Pair<Instant, Instant>> = emptyList(),
)

/**
 * How many minutes a night counts as sleep. Pure, so the arithmetic is tested
 * without Health Connect: the platform's session length includes every awake
 * stage, and a bedtime-dated sum credited a night to the evening it started.
 */
object SleepMath {

    /** Sessions starting at or after this local hour are naps unless they are the day's only/longest sleep. */
    private const val NAP_FROM_HOUR = 12

    /** Session length minus the awake stages inside it (clipped to the session). */
    fun asleepMinutes(span: SleepSpan): Int {
        val total = Duration.between(span.start, span.end).toMinutes()
        val awake = span.awake.sumOf { (from, to) ->
            val a = maxOf(from, span.start)
            val b = minOf(to, span.end)
            if (b > a) Duration.between(a, b).toMinutes() else 0L
        }
        return (total - awake).coerceAtLeast(0L).toInt()
    }

    /**
     * Asleep minutes per WAKE-UP day. The longest session of a day is the night;
     * any other session ending that day counts only when it began before
     * [NAP_FROM_HOUR] (a split night) - an afternoon sleep is a nap and is left
     * out of the night's figure rather than inflating it.
     */
    fun nightMinutesByWakeDay(spans: List<SleepSpan>, zone: ZoneId): Map<LocalDate, Int> =
        spans.groupBy { it.end.atZone(zone).toLocalDate() }
            .mapValues { (_, sessions) ->
                val ranked = sessions.sortedByDescending { asleepMinutes(it) }
                val night = ranked.first()
                val extra = ranked.drop(1).filter { it.start.atZone(zone).hour < NAP_FROM_HOUR }
                (listOf(night) + extra).sumOf { asleepMinutes(it) }
            }
            .filterValues { it > 0 }
}
