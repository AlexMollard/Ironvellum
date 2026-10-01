package com.ironvellum.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A finished session as plain text worth pasting into a chat.
 *
 * Short, self-contained and plain: no link, no emoji, no column padding (a
 * messaging client renders proportional text at whatever width it likes, so
 * aligned columns would arrive ragged). Only the sets done are listed and
 * counted; skipped sets are simply absent.
 *
 * The private note is never included. It is the one field the app promises
 * stays on the device.
 */
object WorkoutShare {

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

        append(session.title.ifBlank { session.label }).append(" \u00B7 Ironvellum")
        append('\n')
        append(dateFormat.format(Instant.ofEpochMilli(at).atZone(zone)))
        durationMinutes(session)?.let { append(" \u00B7 ").append(it).append(" min") }
        append('\n')

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
        // Kilograms moved counts only lifts whose load is the whole weight
        // (a barbell, a dumbbell): a belt on a pull-up is added load on top of
        // a bodyweight the card does not know, so it would undercount.
        val moved = counted
            .filter { exercises[it.exerciseId]?.isWeighted == true }
            .sumOf { (it.weightKg ?: 0.0) * it.reps }
            .toInt()
        val effort = buildList {
            if (done.isNotEmpty()) add("${done.size} ${if (done.size == 1) "set" else "sets"}")
            if (reps > 0) add("${format(reps)} reps")
            if (heldSeconds > 0) add("${format(heldSeconds)}s held")
        }
        val load = buildList {
            if (moved > 0) add("${format(moved)} kg moved")
            if (session.strengthScore > 0) add("${format(session.strengthScore)} STR")
        }
        if (effort.isNotEmpty() || load.isNotEmpty() || session.xpAwarded > 0) append('\n')
        if (effort.isNotEmpty()) append(effort.joinToString(" \u00B7 ")).append('\n')
        if (load.isNotEmpty()) append(load.joinToString(" \u00B7 ")).append('\n')
        if (session.xpAwarded > 0) append('+').append(format(session.xpAwarded)).append(" XP").append('\n')

        if (session.note.isNotBlank()) append('\n').append('\u201C').append(session.note.trim()).append('\u201D').append('\n')
    }.trimEnd('\n')

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
            ExerciseMetric.HOLD -> secondsBody(group) + loadSuffix(group, exercise)
            ExerciseMetric.DURATION -> {
                val total = group.sumOf { it.durationSec ?: 0 }
                val minutes = (total + 30) / 60
                (if (total < 60) "$total s" else "$minutes min") + loadSuffix(group, exercise)
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
                "$attempts ${if (attempts == 1) "attempt" else "attempts"}" + (grade?.let { " \u00B7 $it" } ?: "")
            }
            ExerciseMetric.REPS ->
                // An archive from before HOLD existed restores a hold as a
                // REPS movement with its seconds in the reps column.
                if (holdSet(exercise, group.first())) {
                    secondsBody(group) + loadSuffix(group, exercise)
                } else {
                    countsBody(group.map { it.reps }, "") + loadSuffix(group, exercise)
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
     * The load, blank when every set was bodyweight. "+20 kg" is ADDED load
     * on a bodyweight movement (a belt on a pull-up); a dumbbell or barbell
     * lift reads " · 18 kg", because the weight in the hand is the whole load
     * and "+18 kg" claimed it sat on top of the lifter's own.
     *
     * The reps body already refuses to collapse sets that differ - three fives
     * and an eight render "5/5/5/8", never "4x5" - and load answers to the same
     * rule. One set of a squat at 60 kg beside three bodyweight sets was
     * printing "4x5 +60 kg", which claims four loaded sets to whoever reads the
     * card. When the load is not the same on every set it is labelled as the
     * top set, which is the only figure it honestly is.
     *
     * The figure, its unit and a "top" label are joined by no-break spaces so
     * a narrow card never strands "kg" or "top" alone at a line end.
     */
    private fun loadSuffix(group: List<SessionSet>, exercise: Exercise?): String {
        val loads = group.map { it.weightKg?.takeIf { kg -> kg > 0.0 } }
        val carried = loads.filterNotNull()
        if (carried.isEmpty()) return ""
        val top = carried.max()
        // Two places, trailing zeros dropped: the 1.25 kg isolation step lands
        // on 8.75 kg, and one place printed "8.8 kg", a load nobody can rack.
        val text = String.format(Locale.ENGLISH, "%.2f", top).trimEnd('0').trimEnd('.')
        val figure = (if (exercise?.isWeighted == true) "" else "+") + text + "\u00A0kg"
        val uniform = carried.size == loads.size && carried.distinct().size == 1
        return when {
            !uniform -> " \u00B7 top\u00A0$figure"
            exercise?.isWeighted == true -> " \u00B7 $figure"
            else -> " $figure"
        }
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
