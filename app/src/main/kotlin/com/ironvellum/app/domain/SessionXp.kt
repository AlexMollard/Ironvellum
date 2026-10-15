package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Prices a trial from the domain's own models, so the live tally, the seal,
 * a sealed-trial edit and an import all run the one [Xp.breakdown].
 *
 * Built from the lifter's completed trials: the best before this trial is
 * [history], and what the [RepMax] peak rule judges against is [peakBook]. [excludeSessionId] keeps a trial from being its own benchmark.
 */
class SessionXp(
    private val exercises: Map<Long, Exercise>,
    completed: List<Pair<WorkoutSession, List<SessionSet>>>,
    private val bodyweightAt: (Long) -> Double,
    excludeSessionId: Long? = null,
    /** The muscles a week must cover for the week-covered bonus: [Xp.WEEK_COVERED_MUSCLES] by default. */
    private val coverageMuscles: Collection<Muscle> = Xp.WEEK_COVERED_MUSCLES,
    /**
     * Only trials that started before this count as history: a trial is judged against
     * what the lifter had done by then, so an older imported workout is not priced
     * against sessions logged after it. Null (a trial still live) takes every completed trial.
     */
    beforeMs: Long? = null,
) {
    private val others = completed.filter { (session, _) ->
        session.id != excludeSessionId && session.completedAtMs != null &&
            (beforeMs == null || session.startedAtMs < beforeMs)
    }

    /** Best e1RM and longest hold before this trial. */
    val history: Xp.History = others
        .sortedBy { (session, _) -> session.startedAtMs }
        .fold(Xp.History.EMPTY) { acc, (session, sets) ->
            val bw = bodyweightAt(session.startedAtMs).takeIf { it > 0.0 }
            acc.after(sets.filter { it.done }.map(::effortOf), bw)
        }

    /** Every working set lifted before this trial, for the peak rule. */
    val peakBook: RepBook by lazy {
        RepMax.book(others) { set -> exercises[set.exerciseId]?.metric }
    }

    fun effortOf(set: SessionSet): Xp.SetEffort {
        val exercise = exercises[set.exerciseId]
        val metric = exercise?.metric ?: ExerciseMetric.REPS
        return Xp.SetEffort(
            exerciseName = set.exerciseName.ifEmpty { exercise?.name ?: "" },
            reps = set.reps,
            holdSeconds = if (metric == ExerciseMetric.HOLD) set.durationSec else null,
            weightKg = set.weightKg,
            modifiers = set.modifiers,
            metric = metric,
            exerciseId = set.exerciseId,
            rpe = set.rpe,
            kind = set.kind,
            externalLoad = exercise?.isWeighted == true,
        )
    }

    /** Movements ([Variation.liftKey]) whose done sets set a new peak by [RepMax], with what each pays. */
    fun peakLifts(sets: List<SessionSet>, bodyweightKg: Double?): Map<String, Xp.PeakAward> =
        SessionPeaks.of(sets, peakBook, bodyweightKg) { exercises[it.exerciseId] }
            .associate { it.liftKey to Xp.PeakAward(it.xp, it.kind.label) }

    private fun isStrength(set: SessionSet) = exercises[set.exerciseId]?.metric?.isStrength != false

    /** One segment per activity movement, priced by [ActivityScore]; activities are not judged by effort. */
    private fun activityLines(done: List<SessionSet>, bodyweightKg: Double?): List<Xp.Line> =
        done.filterNot(::isStrength).groupBy { it.exerciseId }.mapNotNull { (id, rows) ->
            val exercise = exercises[id] ?: return@mapNotNull null
            val xp = rows.sumOf { set ->
                val per = ActivityScore.xp(
                    exerciseName = exercise.name,
                    category = exercise.category,
                    metric = exercise.metric,
                    durationSec = set.durationSec,
                    distanceM = set.distanceM,
                    addedKg = set.weightKg,
                    bodyweightKg = bodyweightKg ?: 0.0,
                )
                if (exercise.metric == ExerciseMetric.ATTEMPTS_GRADE) per * set.reps else per
            }
            if (xp > 0) Xp.Line(Xp.Kind.ACTIVITY, exercise.name, xp) else null
        }

    /**
     * The segments the done sets of [sets] earn, in trial order: strength
     * work judged by effort, activities by [ActivityScore]. [doneSetCount]
     * is every done set including activities, 0 for a live tally (no
     * completion bonus yet) and [Xp.TrialFacts] the trial-level bonuses.
     */
    fun breakdown(
        sets: List<SessionSet>,
        bodyweightKg: Double?,
        trial: Xp.TrialFacts = Xp.TrialFacts.NONE,
        doneSetCount: Int = 0,
    ): Xp.Breakdown {
        val ticked = sets.filter { it.done && !it.warmup }
        val strength = ticked.filter(::isStrength).sortedWith(compareBy({ it.exercisePosition }, { it.setIndex }))
        return Xp.breakdown(
            sets = strength.map(::effortOf),
            bodyweightKg = bodyweightKg,
            history = history,
            peakLifts = if (trial.earnsBonuses) peakLifts(ticked, bodyweightKg) else emptyMap(),
            doneSetCount = doneSetCount,
            trial = trial,
            extra = activityLines(ticked, bodyweightKg),
        )
    }

    /** What the ticked sets have earned so far, and what each one added, by set id. */
    data class Tally(val total: Int, val gains: Map<Long, Int>) {
        companion object {
            val EMPTY = Tally(0, emptyMap())
        }
    }

    /**
     * What the last [tally] priced, so the next one need not price it again: each prefix's total is a pure function
     * of the sets up to it (and of the [SessionXp] and bodyweight that priced them), so a prefix the new call shares
     * with the last keeps its total. Hold one per live screen; it is only ever a cache.
     */
    class TallyMemo {
        internal var owner: SessionXp? = null
        internal var bodyweightKg: Double? = null
        internal var done: List<SessionSet> = emptyList()
        internal var totals: IntArray = IntArray(0)
    }

    /**
     * The live tally: the done strength sets in trial order, each priced by
     * [breakdown] itself, so the running total is exactly the set-driven part
     * of what the seal awards for the same sets. A set's gain is what it added
     * to the lift's segment (a peak or record landing on it included).
     *
     * Pricing a set prices the sets before it, so a cold call is quadratic in the sets. With a [memo] a tick that
     * adds a set at the end prices only that set's prefix, which is the one thing that changed: linear per tick.
     */
    fun tally(sets: List<SessionSet>, bodyweightKg: Double?, memo: TallyMemo? = null): Tally {
        val done = sets
            .filter { it.done && !it.warmup }
            .sortedWith(compareBy({ it.exercisePosition }, { it.setIndex }))
        val totals = IntArray(done.size)
        var shared = 0
        if (memo != null && memo.owner === this && memo.bodyweightKg == bodyweightKg) {
            val limit = minOf(done.size, memo.done.size)
            while (shared < limit && memo.done[shared] == done[shared]) shared++
            memo.totals.copyInto(totals, endIndex = shared)
        }
        for (i in shared until done.size) totals[i] = breakdown(done.take(i + 1), bodyweightKg).setDriven
        if (memo != null) {
            memo.owner = this
            memo.bodyweightKg = bodyweightKg
            memo.done = done
            memo.totals = totals
        }
        val gains = LinkedHashMap<Long, Int>()
        for (i in done.indices) gains[done[i].id] = totals[i] - (if (i == 0) 0 else totals[i - 1])
        return Tally(if (done.isEmpty()) 0 else totals.last(), gains)
    }

    /**
     * The trial-level facts of [session], decided from the OTHER completed
     * trials alone (never stored), so an unseal and reseal, an edit or an
     * import can never pay one twice. A trial is judged against the trials
     * before it only: a later one cannot take a bonus away or pay it again.
     * [sets] are this trial's own sets, [planned] the rite's exercise to sets.
     */
    fun factsFor(
        session: WorkoutSession,
        sets: List<SessionSet>,
        planned: Map<Long, Int>?,
        quest: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Xp.TrialFacts {
        if (session.imported) return Xp.TrialFacts(earnsBonuses = false)
        val earlier = others.filter { (other, _) -> isBefore(other, session) }
        val weekStart = weekStartOf(session.startedAtMs, zone)
        val sameWeek = earlier.filter { (other, _) -> weekStartOf(other.startedAtMs, zone) == weekStart }
        val previous = earlier.maxByOrNull { (other, _) -> other.startedAtMs }?.first
        val welcomeBack = previous != null &&
            session.startedAtMs - (previous.completedAtMs ?: previous.startedAtMs) >= Xp.WELCOME_BACK_DAYS * DAY_MS
        val firstOfWeek = sameWeek.isEmpty()
        return Xp.TrialFacts(
            quest = quest,
            weekCovered = weekCovered(session, sets, sameWeek, weekStart, zone),
            welcomeBack = welcomeBack,
            firstOfWeek = firstOfWeek,
            plan = planned,
        )
    }

    private fun weekCovered(
        session: WorkoutSession,
        sets: List<SessionSet>,
        sameWeek: List<Pair<WorkoutSession, List<SessionSet>>>,
        weekStart: LocalDate,
        zone: ZoneId,
    ): Boolean {
        val weekEndMs = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val before = sameWeek.flatMap { (other, otherSets) -> logged(other, otherSets) }
        // shortcut: pays by the chronological crossing, which is stateless. If A is unsealed (10 min window)
        // after a later B and C have sealed and then resealed, A can pay a crossing C already paid;
        // persist a per-week "paid" marker on the session row if that ever matters.
        // Judged chronologically: the trial that takes the week from short to covered pays, once.
        return !covered(before, weekEndMs) && covered(before + logged(session, sets), weekEndMs)
    }

    private fun covered(logged: List<LoggedSet>, weekEndMs: Long): Boolean {
        val weekly = HardSets.fromLog(logged, weekEndMs)
        return coverageMuscles.all { MuscleLoad(it, weekly[it] ?: 0.0, 0.0).coverage == Coverage.COVERED }
    }

    private fun logged(session: WorkoutSession, sets: List<SessionSet>): List<LoggedSet> = sets
        .filter { exercises[it.exerciseId]?.metric?.isStrength != false }
        .map {
            LoggedSet(
                exerciseName = it.exerciseName.ifEmpty { exercises[it.exerciseId]?.name ?: "" },
                modifiers = it.modifiers,
                weightKg = it.weightKg,
                reps = if (exercises[it.exerciseId]?.metric == ExerciseMetric.HOLD) maxOf(1, (it.durationSec ?: 0) / 5) else it.reps,
                rpe = it.rpe,
                kind = it.kind,
                done = it.done,
                atMs = session.startedAtMs,
                bodyweight = exercises[it.exerciseId]?.isWeighted == false,
            )
        }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000

        fun isBefore(other: WorkoutSession, session: WorkoutSession) =
            other.startedAtMs < session.startedAtMs ||
                (other.startedAtMs == session.startedAtMs && other.id < session.id)

        fun weekStartOf(ms: Long, zone: ZoneId): LocalDate =
            Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
