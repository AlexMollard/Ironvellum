package com.ironvellum.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A finished trial as a Wordle-style grid worth pasting into a chat:
 *
 * ```
 * Ironvellum · Push day · 8 Oct
 *
 * Bench    🟩🟩🟩🟩🏆 5×5 @ 80kg
 * OHP      🟩🟩🟩🟨 6/6/7/4 @ 45kg
 * Laterals 🟩🟩⬛ 15/15 @ 8kg
 *
 * 52 min · 17 sets · +214 XP · 380 STR
 * ```
 *
 * One line per movement, one square per working set: 🏆 set a personal
 * record, green hit the target (or beat it, or had none), yellow was done
 * under it, black was prescribed and not done. Warm-ups are not working sets
 * and never show. After the squares come the done sets' figures ("5×5" when
 * three or more match, else "6/6/7/4") and the load ("@ 80kg", "@ top 85kg"
 * when it varies). Activities (distance, time, attempts) have no sets to
 * score, so they read as one compact figure instead.
 *
 * The private note is never included. It is the one field the app promises
 * stays on the device. Per-exercise notes are the lifter's own words and ride
 * along only when [format] is asked to include them.
 */
object WorkoutShare {

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    const val PEAK = "🏆"
    const val HIT = "🟩"
    const val UNDER = "🟨"
    const val SKIPPED = "⬛"

    /** A movement's name never takes more columns than this; longer ones are cut with an ellipsis. */
    const val NAME_MAX = 10

    /** A note line is cut to this many characters, ellipsis included. */
    const val NOTE_MAX = 80

    /** Common lifts by their catalogue name, lower-cased, to the name lifters actually say. */
    private val shortNames = mapOf(
        "bench press" to "Bench",
        "dumbbell bench press" to "DB Bench",
        "incline bench press" to "Incline",
        "close-grip bench press" to "CG Bench",
        "overhead press" to "OHP",
        "dumbbell shoulder press" to "DB OHP",
        "lateral raise" to "Laterals",
        "cable lateral raise" to "Cable Lat",
        "dip" to "Dips",
        "ring dip" to "Ring Dips",
        "back squat" to "Squat",
        "bulgarian split squat" to "BSS",
        "romanian deadlift" to "RDL",
        "barbell row" to "Row",
        "dumbbell row" to "DB Row",
        "lat pulldown" to "Pulldown",
        "hanging leg raise" to "Leg raise",
    )

    /**
     * @param exercises catalogue by id, for metric and unit rendering. A set
     *   whose exercise is missing renders as reps, which is the default metric.
     * @param peaks the records this trial set ([SessionPeaks]), which says
     *   which set positions peaked. Only the victory screen knows them: they
     *   are judged against the history as it stood before the trial sealed, and
     *   a later share from history passes none, so it shows no 🏆.
     * @param targets the prescribed reps (seconds, for a hold) per movement id,
     *   from the rite the trial started from. A movement with no entry has no
     *   known target, so its done sets all read as hit.
     * @param exerciseNotes the trial's per-exercise notes by movement id; they
     *   are printed only when [includeNotes] is true.
     */
    fun format(
        session: WorkoutSession,
        sets: List<SessionSet>,
        exercises: Map<Long, Exercise>,
        zone: ZoneId = ZoneId.systemDefault(),
        peaks: List<SessionPeaks.Peak> = emptyList(),
        targets: Map<Long, Int> = emptyMap(),
        exerciseNotes: Map<Long, String> = emptyMap(),
        includeNotes: Boolean = false,
    ): String = buildString {
        val at = session.completedAtMs ?: session.startedAtMs

        append("Ironvellum · ").append(session.title.ifBlank { session.label })
        append(" · ").append(dateFormat.format(Instant.ofEpochMilli(at).atZone(zone)))
        append('\n')

        val peakByName = peaks.associateBy { it.exerciseName.trim().lowercase(Locale.ENGLISH) }
        val lines = sets
            .groupBy { it.exerciseId }
            .entries
            .sortedBy { (_, group) -> group.minOf { it.exercisePosition } }
            .mapNotNull { (id, group) ->
                val exercise = exercises[id]
                val working = group.filter { !it.warmup }.sortedBy { it.setIndex }
                if (working.isEmpty()) return@mapNotNull null
                val full = group.first().exerciseName.ifBlank { exercise?.name ?: "Movement" }
                val peak = peakByName[full.trim().lowercase(Locale.ENGLISH)]
                val body = movementBody(working, exercise, targets[id], peak) ?: return@mapNotNull null
                val note = if (includeNotes) exerciseNotes[id]?.let(::noteLine) else null
                Triple(shortName(full), body, note)
            }
        val width = lines.maxOfOrNull { it.first.length } ?: 0
        if (lines.isNotEmpty()) append('\n')
        lines.forEach { (name, body, note) ->
            append(name.padEnd(width)).append(' ').append(body).append('\n')
            if (note != null) append(note).append('\n')
        }

        val workingDone = sets.count { it.done && !it.warmup }
        val footer = buildList {
            durationMinutes(session)?.takeIf { it > 0 }?.let { add("$it min") }
            if (workingDone > 0) add(if (workingDone == 1) "1 set" else "$workingDone sets")
            if (session.xpAwarded > 0) add("+${format(session.xpAwarded)} XP")
            if (session.strengthScore > 0) add("${format(session.strengthScore)} STR")
        }
        if (footer.isNotEmpty()) append('\n').append(footer.joinToString(" · ")).append('\n')

        if (session.note.isNotBlank()) append('\n').append('“').append(session.note.trim()).append('”').append('\n')
    }.trimEnd('\n')

    /** A trial's done-set figures; the card and the victory screen print the same ones. */
    data class Totals(val sets: Int, val reps: Int, val heldSeconds: Int, val movedKg: Int)

    fun totals(sets: List<SessionSet>, exercises: Map<Long, Exercise>): Totals {
        val done = sets.filter { it.done }
        val counted = done.filter { repCounted(exercises[it.exerciseId], it) }
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
        return Totals(done.size, counted.sumOf { it.reps }, heldSeconds, moved)
    }

    /** The name a lifter would say: a known short form, else the catalogue name cut to [NAME_MAX]. */
    fun shortName(full: String): String {
        val name = full.trim()
        shortNames[name.lowercase(Locale.ENGLISH)]?.let { return it }
        return if (name.length <= NAME_MAX) name else name.take(NAME_MAX - 1).trimEnd() + "…"
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

    /** What follows the name: the squares and load for strength work, one figure for an activity; null when there is nothing to say. */
    private fun movementBody(
        working: List<SessionSet>,
        exercise: Exercise?,
        target: Int?,
        peak: SessionPeaks.Peak?,
    ): String? {
        val done = working.filter { it.done }
        return when (exercise?.metric ?: ExerciseMetric.REPS) {
            ExerciseMetric.DURATION -> {
                if (done.isEmpty()) return null
                val total = done.sumOf { it.durationSec ?: 0 }
                if (total < 60) "${total}s" else "${(total + 30) / 60} min"
            }
            ExerciseMetric.DISTANCE_TIME -> {
                if (done.isEmpty()) return null
                val km = done.sumOf { it.distanceM ?: 0.0 } / 1000.0
                val secs = done.sumOf { it.durationSec ?: 0 }
                val distance = if (km > 0.0) trimmed(km) + "km" else ""
                val time = if (secs > 0) clock(secs) else ""
                when {
                    distance.isNotEmpty() && time.isNotEmpty() -> "$distance in $time"
                    else -> distance + time
                }.ifEmpty { return null }
            }
            ExerciseMetric.ATTEMPTS_GRADE -> {
                if (done.isEmpty()) return null
                val attempts = done.sumOf { it.reps }
                val grade = done.firstNotNullOfOrNull { it.grade?.takeIf(String::isNotBlank) }
                "$attempts ${if (attempts == 1) "attempt" else "attempts"}" + (grade?.let { " · $it" } ?: "")
            }
            ExerciseMetric.REPS, ExerciseMetric.HOLD -> {
                val hold = holdSet(exercise, working.first())
                fun figure(set: SessionSet) = if (hold) heldSeconds(set) else set.reps
                val targeted = target != null && target > 0
                val squares = working.joinToString("") { set ->
                    when {
                        !set.done -> SKIPPED
                        peak != null && set.setIndex in peak.setIndexes -> PEAK
                        targeted && figure(set) < target -> UNDER
                        else -> HIT
                    }
                }
                listOf(squares, figures(done.map(::figure), hold), load(done, exercise))
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
            }
        }
    }

    /**
     * The done sets' figures: "5×5" when three or more are alike (two alike
     * read "15/15", which is no longer), else "6/6/7/4"; seconds take one "s"
     * at the end. Blank when nothing was done.
     */
    private fun figures(done: List<Int>, hold: Boolean): String {
        if (done.isEmpty()) return ""
        val unit = if (hold) "s" else ""
        return if (done.size >= 3 && done.distinct().size == 1) "${done.size}×${done.first()}$unit"
        else done.joinToString("/") + unit
    }

    /**
     * "@ 80kg" when every done set carried the same load, "@ top 85kg" when it
     * varied, blank when every set was bodyweight. "+10kg" is ADDED load on a
     * bodyweight movement (a belt on a dip); a barbell or dumbbell lift reads
     * "80kg", because the weight in the hand is the whole load and "+80kg"
     * claimed it sat on top of the lifter's own. Two places, trailing zeros
     * dropped: the 1.25 kg isolation step lands on 8.75 kg, and one place would
     * print "8.8kg", a load nobody can rack.
     */
    private fun load(done: List<SessionSet>, exercise: Exercise?): String {
        val loads = done.map { it.weightKg?.takeIf { kg -> kg > 0.0 } ?: 0.0 }
        val top = loads.maxOrNull()?.takeIf { it > 0.0 } ?: return ""
        val text = (if (exercise?.isWeighted == true) "" else "+") + trimmed(top) + "kg"
        return if (loads.distinct().size > 1) "@ top $text" else "@ $text"
    }

    /** `  ↳ "note"`: one line, whitespace collapsed, cut to [NOTE_MAX] with an ellipsis. */
    private fun noteLine(note: String): String? {
        val flat = note.trim().replace(Regex("\\s+"), " ")
        if (flat.isEmpty()) return null
        val text = if (flat.length <= NOTE_MAX) flat else flat.take(NOTE_MAX - 1).trimEnd() + "…"
        return "  ↳ \"$text\""
    }

    private fun trimmed(value: Double): String =
        String.format(Locale.ENGLISH, "%.2f", value).trimEnd('0').trimEnd('.')

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
