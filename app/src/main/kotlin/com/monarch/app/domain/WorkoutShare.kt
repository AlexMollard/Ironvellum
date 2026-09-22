package com.monarch.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A finished session as plain text worth pasting into a chat.
 *
 * Shaped after Wordle's share card: short, self-contained, no link, and laid
 * out with block characters rather than column padding — a messaging client
 * renders emoji at a fixed width but proportional text at whatever width it
 * likes, so aligned columns would arrive ragged.
 *
 * The private note is never included. It is the one field the app promises
 * stays on the device.
 */
object WorkoutShare {

    const val BAR_CELLS = 10
    private const val FILLED = "\uD83D\uDFE9" // green square
    private const val EMPTY = "\u2B1B" // black square

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /**
     * @param exercises catalogue by id, for metric and unit rendering. A set
     *   whose exercise is missing renders as reps, which is the default metric.
     */
    fun format(
        session: WorkoutSession,
        sets: List<SessionSet>,
        exercises: Map<Long, Exercise>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        val done = sets.filter { it.done }
        val at = session.completedAtMs ?: session.startedAtMs

        append("\u2694\uFE0F MONARCH \u00B7 ").append(session.title.ifBlank { session.label })
        append('\n')
        append(dateFormat.format(Instant.ofEpochMilli(at).atZone(zone)))
        durationMinutes(session)?.let { append(" \u00B7 ").append(it).append(" min") }
        append('\n')

        if (sets.isNotEmpty()) {
            append('\n').append(bar(done.size, sets.size))
            append("  ").append(done.size).append('/').append(sets.size).append(" sets")
            append('\n')
        }

        val blocks = done
            .groupBy { it.exerciseId }
            .entries
            .sortedBy { (_, group) -> group.minOf { it.exercisePosition } }
        if (blocks.isNotEmpty()) append('\n')
        blocks.forEach { (id, group) ->
            val exercise = exercises[id]
            append(group.first().exerciseName.ifBlank { exercise?.name ?: "Movement" })
            append(" \u00B7 ").append(summarise(group, exercise))
            append('\n')
        }

        val counted = done.filter { repCounted(exercises[it.exerciseId], it) }
        val reps = counted.sumOf { it.reps }
        // Seconds are their own figure, never added to the rep total: doing so
        // printed "140 reps" for a session that was 65 reps and 75 seconds of
        // hollow hold.
        val heldSeconds = done.filter { holdSet(exercises[it.exerciseId], it) }.sumOf { heldSeconds(it) }
        val hasTotals = reps > 0 || heldSeconds > 0
        if (hasTotals) {
            append('\n')
            val parts = buildList {
                if (reps > 0) add("${format(reps)} reps")
                if (heldSeconds > 0) add("${format(heldSeconds)}s held")
                if (session.strengthScore > 0) add("${format(session.strengthScore)} STR")
            }
            append(parts.joinToString("  \u00B7  "))
            append('\n')
        }
        if (session.xpAwarded > 0) {
            if (!hasTotals) append('\n')
            append('+').append(format(session.xpAwarded)).append(" XP")
            append('\n')
        }

        if (session.note.isNotBlank()) append('\n').append('\u201C').append(session.note.trim()).append('\u201D').append('\n')
    }.trimEnd('\n')

    /** Completion as fixed-width blocks — the one element that survives any renderer. */
    fun bar(done: Int, total: Int): String {
        if (total <= 0) return ""
        val filled = (done.toDouble() / total * BAR_CELLS).toInt().coerceIn(0, BAR_CELLS)
        return FILLED.repeat(filled) + EMPTY.repeat(BAR_CELLS - filled)
    }

    private fun durationMinutes(session: WorkoutSession): Long? =
        session.completedAtMs?.let { ((it - session.startedAtMs) / 60_000L).coerceAtLeast(1) }

    /**
     * Sets measured in seconds. The catalogue metric decides; the name and
     * legacy modifier still catch a hold restored from an older archive.
     */
    private fun holdSet(exercise: Exercise?, set: SessionSet): Boolean =
        MovementDifficulty.isHoldSet(
            exercise?.metric,
            set.exerciseName.ifBlank { exercise?.name ?: "" },
            set.modifiers,
        )

    /** Reps only count as reps when the movement is counted, not timed. */
    private fun repCounted(exercise: Exercise?, set: SessionSet): Boolean =
        (exercise?.metric ?: ExerciseMetric.REPS) == ExerciseMetric.REPS && !holdSet(exercise, set)

    /** Seconds held by one set, wherever this build stored them. */
    private fun heldSeconds(set: SessionSet): Int = set.durationSec ?: set.reps

    private fun summarise(group: List<SessionSet>, exercise: Exercise?): String =
        when (exercise?.metric ?: ExerciseMetric.REPS) {
            ExerciseMetric.HOLD -> secondsBody(group) + loadSuffix(group)
            ExerciseMetric.DURATION -> {
                val minutes = group.sumOf { it.durationSec ?: 0 } / 60
                "$minutes min" + loadSuffix(group)
            }
            ExerciseMetric.DISTANCE_TIME -> {
                val km = group.sumOf { it.distanceM ?: 0.0 } / 1000.0
                val secs = group.sumOf { it.durationSec ?: 0 }
                buildString {
                    append(String.format(Locale.ENGLISH, "%.2f", km).trimEnd('0').trimEnd('.'))
                    append(" km")
                    if (secs > 0) append(" in ").append(clock(secs))
                }
            }
            ExerciseMetric.ATTEMPTS_GRADE -> {
                val attempts = group.sumOf { it.reps }
                val grade = group.firstNotNullOfOrNull { it.grade?.takeIf(String::isNotBlank) }
                "$attempts sent" + (grade?.let { " \u00B7 $it" } ?: "")
            }
            ExerciseMetric.REPS ->
                // An archive from before HOLD existed restores a hold as a
                // REPS movement with its seconds in the reps column.
                if (holdSet(exercise, group.first())) {
                    secondsBody(group) + loadSuffix(group)
                } else {
                    countsBody(group.map { it.reps }, "") + loadSuffix(group)
                }
        }

    private fun secondsBody(group: List<SessionSet>): String =
        countsBody(group.map { heldSeconds(it) }, "s")

    private fun countsBody(counts: List<Int>, unit: String): String =
        if (counts.distinct().size == 1) {
            "${counts.size}\u00D7${counts.first()}$unit"
        } else {
            counts.joinToString("/") { "$it$unit" }
        }

    /**
     * "+20 kg" for added load; blank when every set was bodyweight.
     *
     * The reps body already refuses to collapse sets that differ - three fives
     * and an eight render "5/5/5/8", never "4x5" - and load answers to the same
     * rule. One set of a squat at 60 kg beside three bodyweight sets was
     * printing "4x5 +60 kg", which claims four loaded sets to whoever reads the
     * card. When the load is not the same on every set it is labelled as the
     * top set, which is the only figure it honestly is.
     */
    private fun loadSuffix(group: List<SessionSet>): String {
        val loads = group.map { it.weightKg?.takeIf { kg -> kg > 0.0 } }
        val carried = loads.filterNotNull()
        if (carried.isEmpty()) return ""
        val top = carried.max()
        val text = if (top % 1.0 == 0.0) top.toInt().toString() else String.format(Locale.ENGLISH, "%.1f", top)
        val uniform = carried.size == loads.size && carried.distinct().size == 1
        return if (uniform) " +$text kg" else " \u00B7 top +$text kg"
    }

    private fun clock(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(Locale.ENGLISH, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ENGLISH, "%d:%02d", m, s)
        }
    }

    private fun format(value: Int): String = String.format(Locale.ENGLISH, "%,d", value)
}
