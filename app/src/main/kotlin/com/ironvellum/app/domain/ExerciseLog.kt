package com.ironvellum.app.domain

/**
 * Room projection: one DONE set of one exercise in a COMPLETED trial, with what the history tab names the trial by.
 * [exercisePosition] is the block's place in the trial, so the same movement done twice stays two blocks.
 */
data class ExerciseLogRow(
    val sessionId: Long,
    val startedAtMs: Long,
    val label: String,
    val title: String,
    val exercisePosition: Int,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double?,
    val modifiers: String,
    val durationSec: Int?,
    val distanceM: Double?,
    val grade: String?,
    val kind: String,
)

/** One set as the history tab shows it. [figure] is the number the movement is judged by: reps, or seconds for a hold. */
data class ExerciseLogSet(
    val figure: Int,
    val weightKg: Double?,
    val durationSec: Int?,
    val distanceM: Double?,
    val grade: String?,
    val kind: SetKind,
    val e1rmKg: Double?,
)

/** One block of the movement in one trial: its own modifiers, its sets in order, and the set that stands for it. */
data class ExerciseLogBlock(
    val position: Int,
    val modifiers: String,
    val sets: List<ExerciseLogSet>,
    /** Index into [sets] of the best counted set; null when the movement is not judged by a figure. */
    val bestIndex: Int?,
    /** The block set a new best for its variation: higher than every earlier trial's. The first ever log is not one. */
    val record: Boolean,
)

class ExerciseLogSession(val sessionId: Long, val atMs: Long, val label: String, val blocks: List<ExerciseLogBlock>)

/** A point of the trend: the best figure of one trial. */
class ExerciseLogPoint(val atMs: Long, val value: Double)

/**
 * One variation of the movement the lifter has used: the splitting modifiers of [Variation.key] ("" for the plain
 * movement, "deficit,elevated"), when it was last done and in how many trials.
 */
class LogVariation(val key: String, val lastAtMs: Long, val sessions: Int) {
    /** "Deficit \u00b7 elevated", "Incline"; [plain] names the plain movement. The words are the lifter's own modifiers. */
    fun label(plain: String): String = variationLabel(key, plain)
}

/** [LogVariation.label] without the object: the first word capitalised, the rest as picked, joined by a middle dot. */
fun variationLabel(key: String, plain: String): String =
    key.split(",").filter { it.isNotBlank() }.joinToString(" \u00b7 ") { it.trim() }
        .replaceFirstChar { it.uppercase() }.ifEmpty { plain }

/**
 * Everything the History tab shows for one movement. [sessions] is newest first; [visible] is the capped view.
 * [best] and [bestE1rmKg] follow the app's rule: a trial's top set alone when it had one, warm-ups never.
 */
class ExerciseLog(
    val metric: ExerciseMetric,
    /** Loaded rep work: judged by estimated 1RM like the Stats records board. Otherwise reps, or seconds for a hold. */
    val byE1rm: Boolean,
    val sessions: List<ExerciseLogSession>,
    val lastAtMs: Long?,
    val best: ExerciseLogSet?,
    val bestE1rmKg: Double?,
    /** Oldest first; a trend line needs two. Empty on "All" when the movement has more than one variation: they differ in load. */
    val trend: List<ExerciseLogPoint>,
    /** The marked load is ADDED to the body, so an estimated 1RM reads "+46 kg" ([BodyShare.addsToBody]). */
    val addsToBody: Boolean = false,
    /** Every variation the lifter has used, the most recent first; the chips are shown when there are two or more. */
    val variations: List<LogVariation> = emptyList(),
    /** The variation this log shows, a [Variation.key]; null for all of them. */
    val selected: String? = null,
    /** The variation of the most recent block, when there are two or more variations: where the tab opens. */
    val defaultVariation: String? = null,
    /** An estimate on a bodyweight lift had no body weight to count (none logged), so it is the added load alone. */
    val bodyMissing: Boolean = false,
) {
    val isEmpty: Boolean get() = sessions.isEmpty()

    fun visible(count: Int): List<ExerciseLogSession> = sessions.take(count)

    companion object {
        /** Trials drawn before "Show more", and what each tap adds. */
        const val PAGE = 50

        /** The choice that shows every variation at once (a [Variation.key] is never this). */
        const val ALL = "*"
    }
}

/** The rows and the weigh-ins an [ExerciseLog] is built from ([ExerciseLogCalculator.build]). */
class ExerciseLogInputs(val rows: List<ExerciseLogRow>, val bodyweightAt: (Long) -> Double)

/** Pure: turns an exercise's logged rows into the History tab's content. */
object ExerciseLogCalculator {

    /**
     * [bodyweightAt] is the lifter's weight at a moment ([SetRecords.bodyweightLookup]; 0 when none is logged): a
     * bodyweight lift's estimated 1RM counts the body ([BodyShare]) and falls back to the added load alone without one.
     * [variation] is the [Variation.key] to show, or null for every variation at once.
     */
    fun build(
        exercise: Exercise,
        allRows: List<ExerciseLogRow>,
        bodyweightAt: (Long) -> Double = { 0.0 },
        variation: String? = null,
    ): ExerciseLog {
        val metric = exercise.metric
        val judged = metric == ExerciseMetric.REPS || metric == ExerciseMetric.HOLD

        // A block's variation is its first set's: the same movement twice in a trial is two blocks.
        val blockKey = HashMap<Pair<Long, Int>, String>()
        allRows.groupBy { it.sessionId to it.exercisePosition }.forEach { (id, group) ->
            blockKey[id] = Variation.key(group.minBy { it.setIndex }.modifiers)
        }
        fun variationOf(r: ExerciseLogRow) = blockKey.getValue(r.sessionId to r.exercisePosition)

        val worked = allRows.filter { SetKind.fromName(it.kind) != SetKind.WARMUP }
        val variations = worked.groupBy(::variationOf).map { (key, group) ->
            LogVariation(key, group.maxOf { it.startedAtMs }, group.map { it.sessionId }.distinct().size)
        }.sortedWith(compareByDescending<LogVariation> { it.lastAtMs }.thenBy { it.key })
        val latest = worked.maxWithOrNull(compareBy({ it.startedAtMs }, { it.sessionId }, { it.exercisePosition }))
        val defaultVariation = latest?.takeIf { variations.size >= 2 }?.let(::variationOf)

        val rows = if (variation == null) allRows else allRows.filter { variationOf(it) == variation }
        var bodyMissing = false

        fun e1rmOf(r: ExerciseLogRow): Double? {
            val kg = r.weightKg ?: return null
            if (kg <= 0.0 || r.reps < 1 || r.reps > ProgramRules.MAX_E1RM_REPS) return null
            val setShare = BodyShare.of(exercise, r.modifiers)
            val body = bodyweightAt(r.startedAtMs).takeIf { it > 0.0 }
            if (!BodyShare.knowsBody(setShare, body)) bodyMissing = true
            return BodyShare.e1rmAddedKg(kg, r.reps, setShare, body)
        }

        val byE1rm = metric == ExerciseMetric.REPS && rows.any { (it.weightKg ?: 0.0) > 0.0 && it.reps in 1..ProgramRules.MAX_E1RM_REPS }

        fun setOf(r: ExerciseLogRow) = ExerciseLogSet(
            figure = if (metric == ExerciseMetric.HOLD) (r.durationSec ?: r.reps) else r.reps,
            weightKg = r.weightKg,
            durationSec = r.durationSec,
            distanceM = r.distanceM,
            grade = r.grade,
            kind = SetKind.fromName(r.kind),
            e1rmKg = if (metric == ExerciseMetric.REPS) e1rmOf(r) else null,
        )

        fun score(s: ExerciseLogSet): Double = if (byE1rm) s.e1rmKg ?: 0.0 else s.figure.toDouble()

        // Oldest trial first, so a record is judged against what came before it.
        val chrono = rows.filter { SetKind.fromName(it.kind) != SetKind.WARMUP }
            .groupBy { it.sessionId }.values
            .sortedWith(compareBy({ it.first().startedAtMs }, { it.first().sessionId }))
        val priorBest = HashMap<String, Double>()
        val built = ArrayList<ExerciseLogSession>(chrono.size)
        val trend = ArrayList<ExerciseLogPoint>()
        var bestSet: ExerciseLogSet? = null
        var bestE1rm: Double? = null
        for (session in chrono) {
            val head = session.first()
            val sessionBest = HashMap<String, Double>()
            val scored = session.groupBy { it.exercisePosition }.toSortedMap().map { (position, blockRows) ->
                val sets = blockRows.sortedBy { it.setIndex }.map(::setOf)
                // A block with a top set is judged on it alone; its back-offs are lighter by design.
                val counted = sets.indices.filter { sets[it].kind == SetKind.TOP }.ifEmpty { sets.indices.toList() }
                val bestIndex = if (!judged) null else counted.maxByOrNull { score(sets[it]) }?.takeIf { score(sets[it]) > 0.0 }
                val key = Variation.key(blockRows.first().modifiers)
                val value = bestIndex?.let { score(sets[it]) }
                if (value != null) sessionBest[key] = maxOf(sessionBest[key] ?: 0.0, value)
                Scored(ExerciseLogBlock(position, blockRows.first().modifiers, sets, bestIndex, record = false), key, value)
            }
            // Two blocks tying the trial's peak for a variation: only the first wears the mark.
            val marked = HashSet<String>()
            val blocks = scored.map { s ->
                val earlier = priorBest[s.key]
                val isRecord = s.value != null && earlier != null && s.value > earlier && s.value == sessionBest[s.key] && marked.add(s.key)
                if (isRecord) s.block.copy(record = true) else s.block
            }
            for ((key, value) in sessionBest) priorBest[key] = maxOf(priorBest[key] ?: 0.0, value)
            val peak = blocks.mapNotNull { b -> b.bestIndex?.let { b.sets[it] } }.maxByOrNull(::score)
            if (peak != null) {
                trend += ExerciseLogPoint(head.startedAtMs, score(peak))
                if (bestSet == null || score(peak) >= score(bestSet)) bestSet = peak
                peak.e1rmKg?.let { bestE1rm = maxOf(bestE1rm ?: 0.0, it) }
            }
            built += ExerciseLogSession(head.sessionId, head.startedAtMs, head.title.ifBlank { head.label }, blocks)
        }
        val mixed = variation == null && variations.size > 1
        return ExerciseLog(
            metric = metric,
            byE1rm = byE1rm,
            sessions = built.reversed(),
            lastAtMs = built.lastOrNull()?.atMs,
            // Variations differ in load: one best, or one line, through all of them mixes lifts, so "All" shows neither.
            // Each variation (and a lift with one) keeps its own.
            best = if (mixed) null else bestSet,
            bestE1rmKg = if (mixed) null else bestE1rm,
            trend = if (mixed) emptyList() else trend,
            addsToBody = BodyShare.addsToBody(exercise),
            variations = variations,
            selected = variation,
            defaultVariation = defaultVariation,
            bodyMissing = bodyMissing && byE1rm,
        )
    }

    private class Scored(val block: ExerciseLogBlock, val key: String, val value: Double?)
}
