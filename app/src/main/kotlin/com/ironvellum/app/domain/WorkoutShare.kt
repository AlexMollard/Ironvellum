package com.ironvellum.app.domain

import com.ironvellum.app.R
import com.ironvellum.app.data.Words
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
 * Bench Press · 80kg
 * 🟩🟩🟩🟩🏆 5×5
 *
 * Overhead Press · 45kg
 * 🟩🟩🟩🟨 6/6/7/4
 *
 * Lateral Raise · 8kg
 * 🟩🟩⬛ 15/15
 *
 * Run · 5km in 25:00
 *
 * 52 min · 17 sets · +214 XP · 380 STR
 * ```
 *
 * Two lines per lift and a blank line between lifts, nothing aligned (a proportional chat font cannot hold a column), the full name as
 * logged: one square per working set: 🏆 set a personal
 * record, green hit the target (or beat it, or had none), yellow was done
 * under it, black was prescribed and not done. Warm-ups are not working sets
 * and never show. The first line is the name and, when there is one, " · " and the
 * load ("80kg", "top 85kg" when it varies, "+10kg" added to bodyweight). The second is the squares, then the done
 * sets' figures ("5×5" when three or more match, else "6/6/7/4"). Activities (distance, time, attempts) have no
 * sets to score, so they read as one line, "Run · 5km in 25:00".
 *
 * With effort asked for ([format]'s includeEffort), the top set's effort ends the figures line: "3×5 · RPE 8",
 * or "· 2 RIR" on the RIR scale. Only that one set's, never a figure per set, and never "@8".
 *
 * The private note is never included. It is the one field the app promises
 * stays on the device. Per-exercise notes are the lifter's own words and ride
 * along only when [format] is asked to include them.
 */
object WorkoutShare {

    const val PEAK = "🏆"
    const val HIT = "🟩"
    const val UNDER = "🟨"
    const val SKIPPED = "⬛"

    /** A note line is cut to this many characters, ellipsis included. */
    const val NOTE_MAX = 80

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
     * @param includeEffort print each movement's top set's effort, read on [effortScale].
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
        unit: LoadUnit = LoadUnit.KG,
        includeEffort: Boolean = false,
        effortScale: EffortScale = EffortScale.RPE,
        locale: Locale = Locale.getDefault(),
    ): String = buildString {
        val at = session.completedAtMs ?: session.startedAtMs

        val dateFormat = DateTimeFormatter.ofPattern(Words.text(UiText.res(R.string.share_date_pattern)), locale)
        append(
            Words.text(
                UiText.res(
                    R.string.share_header,
                    session.title.ifBlank { session.label },
                    dateFormat.format(Instant.ofEpochMilli(at).atZone(zone)),
                ),
            ),
        )
        append('\n')

        val peakByLift = peaks.associateBy { it.liftKey }
        val lines = sets
            .groupBy { it.exerciseId }
            .entries
            .sortedBy { (_, group) -> group.minOf { it.exercisePosition } }
            .mapNotNull { (id, group) ->
                val exercise = exercises[id]
                val working = group.filter { !it.warmup }.sortedBy { it.setIndex }
                if (working.isEmpty()) return@mapNotNull null
                val full = group.first().exerciseName.ifBlank { exercise?.name ?: Words.text(UiText.res(R.string.share_movement_fallback)) }
                val peak = peakByLift[Variation.liftKey(full, group.first().modifiers)]
                val text = movementText(full.trim(), working, exercise, targets[id], peak, unit, effortScale.takeIf { includeEffort })
                    ?: return@mapNotNull null
                val note = if (includeNotes) exerciseNotes[id]?.let(::noteLine) else null
                text to note
            }
        if (lines.isNotEmpty()) append('\n')
        lines.forEachIndexed { i, (text, note) ->
            if (i > 0) append('\n')
            append(text).append('\n')
            if (note != null) append(note).append('\n')
        }

        val workingDone = sets.count { it.done && !it.warmup }
        val footer = buildList {
            durationMinutes(session)?.takeIf { it > 0 }?.let { add(Words.text(UiText.res(R.string.share_footer_minutes, it))) }
            if (workingDone > 0) add(Words.text(UiText.plural(R.plurals.share_footer_sets, workingDone)))
            if (session.xpAwarded > 0) add(Words.text(UiText.res(R.string.share_footer_xp, format(session.xpAwarded, locale))))
            if (session.strengthScore > 0) add(Words.text(UiText.res(R.string.share_footer_strength, format(session.strengthScore, locale))))
        }
        if (footer.isNotEmpty()) append('\n').append(footer.joinToString(" · ")).append('\n')

        if (session.note.isNotBlank()) append('\n').append(Words.text(UiText.res(R.string.share_note_quote, session.note.trim()))).append('\n')
    }.trimEnd('\n')

    /**
     * [text] signed with the worn crest as its last line: the crest's name and nothing else, so no username
     * rides along. The card is plain text, so the crest is its name, not a picture. A null [crest] leaves it as it was.
     */
    fun signed(text: String, crest: CrestDef?): String =
        if (crest == null) text else text.trimEnd('\n') + "\n\n" + crest.sentenceName.replaceFirstChar { it.uppercase() }

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

    /** A lift's two lines (name and load, then squares and figures), or an activity's one; null when there is nothing to say. */
    private fun movementText(
        name: String,
        working: List<SessionSet>,
        exercise: Exercise?,
        target: Int?,
        peak: SessionPeaks.Peak?,
        unit: LoadUnit,
        effort: EffortScale?,
    ): String? {
        val done = working.filter { it.done }
        return when (exercise?.metric ?: ExerciseMetric.REPS) {
            ExerciseMetric.DURATION -> {
                if (done.isEmpty()) return null
                val total = done.sumOf { it.durationSec ?: 0 }
                "$name · " + Words.text(
                    if (total < 60) UiText.res(R.string.share_time_seconds, total) else UiText.res(R.string.share_time_minutes, (total + 30) / 60),
                )
            }
            ExerciseMetric.DISTANCE_TIME -> {
                if (done.isEmpty()) return null
                val km = done.sumOf { it.distanceM ?: 0.0 } / 1000.0
                val secs = done.sumOf { it.durationSec ?: 0 }
                val distance = if (km > 0.0) Words.text(UiText.res(R.string.share_distance_km, trimmed(km))) else ""
                val time = if (secs > 0) ClockText.elapsed(secs.toLong()) else ""
                val figure = when {
                    distance.isNotEmpty() && time.isNotEmpty() -> Words.text(UiText.res(R.string.share_distance_in_time, distance, time))
                    else -> distance + time
                }.ifEmpty { return null }
                "$name · $figure"
            }
            ExerciseMetric.ATTEMPTS_GRADE -> {
                if (done.isEmpty()) return null
                val attempts = done.sumOf { it.reps }
                val grade = done.firstNotNullOfOrNull { it.grade?.takeIf(String::isNotBlank) }
                "$name · " + Words.text(UiText.plural(R.plurals.share_attempts, attempts)) + (grade?.let { " · $it" } ?: "")
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
                // The top set is the one the load line names: heaviest, then most reps or longest.
                val top = done.maxWithOrNull(compareBy<SessionSet>({ it.weightKg ?: 0.0 }, { figure(it) }))
                val rated = if (effort != null) top?.rpe?.let { "· ${Effort.value(it, effort)}" }.orEmpty() else ""
                val head = listOf(name, load(done, exercise, unit)).filter { it.isNotEmpty() }.joinToString(" · ")
                val tail = listOf(squares, figures(done.map(::figure), hold), rated).filter { it.isNotEmpty() }.joinToString(" ")
                "$head\n$tail"
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
        val text = if (done.size >= 3 && done.distinct().size == 1) "${done.size} × ${done.first()}" else done.joinToString("/")
        return if (hold) Words.text(UiText.res(R.string.share_figures_seconds, text)) else text
    }

    /**
     * "80kg" when every done set carried the same load, "top 85kg" when it
     * varied, blank when every set was bodyweight. "+10kg" is ADDED load on a
     * bodyweight movement (a belt on a dip); a barbell or dumbbell lift reads
     * "80kg", because the weight in the hand is the whole load and "+80kg"
     * claimed it sat on top of the lifter's own. Two places, trailing zeros
     * dropped: the 1.25 kg isolation step lands on 8.75 kg, and one place would
     * print "8.8kg", a load nobody can rack.
     */
    private fun load(done: List<SessionSet>, exercise: Exercise?, unit: LoadUnit): String {
        val loads = done.map { it.weightKg?.takeIf { kg -> kg > 0.0 } ?: 0.0 }
        val top = loads.maxOrNull()?.takeIf { it > 0.0 } ?: return ""
        val text = (if (exercise?.isWeighted == true) "" else "+") + (if (unit == LoadUnit.LB) Loads.format(top, unit) else trimmed(top)) + unit.label
        return if (loads.distinct().size > 1) Words.text(UiText.res(R.string.share_load_top, text)) else text
    }

    /** `  ↳ "note"`: one line, whitespace collapsed, cut to [NOTE_MAX] with an ellipsis. */
    private fun noteLine(note: String): String? {
        val flat = note.trim().replace(Regex("\\s+"), " ")
        if (flat.isEmpty()) return null
        val text = if (flat.length <= NOTE_MAX) flat else flat.take(NOTE_MAX - 1).trimEnd() + "…"
        return "  " + Words.text(UiText.res(R.string.share_exercise_note, text))
    }

    private fun trimmed(value: Double): String =
        String.format(Locale.ENGLISH, "%.2f", value).trimEnd('0').trimEnd('.')

    private fun format(value: Int, locale: Locale): String = String.format(locale, "%,d", value)
}
