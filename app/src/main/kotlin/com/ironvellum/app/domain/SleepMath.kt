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

    /** Sessions closer than this belong to one night: a toilet trip or a wake-up between two halves. */
    private val SAME_NIGHT_GAP = Duration.ofHours(3)

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
     * Asleep minutes per WAKE-UP day. Sessions whose gaps are within
     * [SAME_NIGHT_GAP] chain into one night, whichever half is longer and
     * whichever side of midnight it falls on, so a split night counts in full.
     * A night is dated by when its last half ended. When several chains end on
     * one day the longest is the night; the others (an afternoon nap) are left
     * out rather than inflating it.
     */
    fun nightMinutesByWakeDay(spans: List<SleepSpan>, zone: ZoneId): Map<LocalDate, Int> {
        val chains = mutableListOf<MutableList<SleepSpan>>()
        var chainEnd: Instant? = null
        for (span in spans.sortedBy { it.start }) {
            val end = chainEnd
            if (end != null && Duration.between(end, span.start) <= SAME_NIGHT_GAP) {
                chains.last() += span
                if (span.end > end) chainEnd = span.end
            } else {
                chains += mutableListOf(span)
                chainEnd = span.end
            }
        }
        return chains
            .groupBy({ chain -> chain.maxOf { it.end }.atZone(zone).toLocalDate() }, { chain -> chain.sumOf { asleepMinutes(it) } })
            .mapValues { (_, minutes) -> minutes.max() }
            .filterValues { it > 0 }
    }
}
