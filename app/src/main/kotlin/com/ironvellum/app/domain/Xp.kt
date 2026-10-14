package com.ironvellum.app.domain

import kotlin.math.roundToInt

/**
 * Session XP, built around effort.
 *
 * The app is effort-first: a hard set, one taken to within a few reps of
 * failure, is the stimulus, and rep totals are not. So each set is judged
 * against the lifter's own recent best for that movement and pays by how hard
 * it was:
 *
 *  1. A HARD set pays a flat [HARD_UNITS] credit. Hard is [HardSets.isHard]:
 *     a logged RPE or RIR decides it; failing that the set's estimated reps in
 *     reserve do (Epley, [ProgramRules.epley], against the best e1RM held
 *     before this trial); a lift with no history to judge it by counts as hard.
 *  2. Reps past [FULL_CREDIT_REPS] add only [REP_UNITS] each, so a longer set
 *     is not a better one. Heavier load relative to the lifter's best adds up
 *     to [LOAD_UNITS]: bounded, a little.
 *  3. An EASY set (about [EASY_RIR] or more reps left) pays a small volume
 *     rate, and easy sets together pay no more than [EASY_XP_CAP] a trial, so
 *     stacking light sets cannot farm XP.
 *  4. Holds convert as ever ([MovementDifficulty.holdRepEquivalents]) and are
 *     judged the same way: seconds against the best hold.
 *  5. Movement intensity, the modifier factor, [BASE_PER_SET] and the
 *     completion bonus are unchanged.
 *  6. A new peak ([RepMax], the one rule every peak reader shares) pays up to
 *     [PEAK_BONUS], scaled by how meaningful the jump is, and a new estimated
 *     1-rep max pays [RECORD_BONUS]; one lift never pays both, it pays the larger.
 *
 * Trial-level bonuses (honest effort, rite as planned, week covered, welcome
 * back, first of the week) are separate named segments of the [Breakdown], and
 * so is everything above: the victory screen and the live tally read the SAME
 * breakdown the award is the sum of, never a second calculation.
 *
 * Past sessions keep the XP they were awarded; nothing is recomputed.
 */
object Xp {
    /** Paid once per completed set, regardless of what was in it. */
    const val BASE_PER_SET = 5

    /** XP per unit of effort. */
    const val PER_EFFORT_UNIT = 0.8

    const val COMPLETION_BONUS = 25
    const val QUEST_BONUS = 25

    /** Paid once per lifter and week when the server settles the circle's week as met and this lifter trained in it. */
    const val CIRCLE_GOAL_BONUS = 40

    /** Effort units a hard set is worth, flat. */
    const val HARD_UNITS = 6.0

    /** Reps in one set up to which a hard set earns nothing extra. */
    const val FULL_CREDIT_REPS = 5

    /** What each rep past [FULL_CREDIT_REPS] adds to a hard set. */
    const val REP_UNITS = 0.3

    /** Reps (or rep-equivalents) past this add nothing to either rate. */
    const val MAX_PAID_REPS = 15

    /** Most a heavy-for-you load adds to a hard set, in effort units. */
    const val LOAD_UNITS = 1.5

    /** Added to a hard set done at a heavier load than the lifter has ever used on the movement. */
    const val HEAVIER_UNITS = 2.0

    /** A load must top the old heaviest by this much to count as heavier: bodyweight drift is not a heavier lift. */
    const val HEAVIER_MARGIN = 1.01

    /** A set judged at least this many reps from failure is easy. */
    const val EASY_RIR = 4

    /** What each rep of an easy set pays, in effort units. */
    const val EASY_UNITS_PER_REP = 0.25

    /** Most the easy sets of one trial pay in total, base included. */
    const val EASY_XP_CAP = 30

    /** Most a lift's new peak pays, at a full-sized jump ([RepMax.xp] scales it down). */
    const val PEAK_BONUS = 15

    /** Paid to a lift whose estimated 1-rep max beat its best before this trial. */
    const val RECORD_BONUS = 25

    /**
     * A record must beat the old best by this much. A weigh-in a few hundred
     * grams heavier moves a bodyweight lift's e1RM by about half a percent,
     * and that is not a record; one more rep at ten is 3.3%.
     */
    const val RECORD_MARGIN = 1.02

    /** Lifts per trial that can pay a peak or a record. */
    const val MAX_BONUS_LIFTS = 3

    const val HONEST_EFFORT_BONUS = 10
    const val RITE_AS_PLANNED_BONUS = 20
    const val WEEK_COVERED_BONUS = 60
    const val WELCOME_BACK_BONUS = 50
    const val FIRST_OF_WEEK_BONUS = 15

    /**
     * The muscles a week must bring to [ProgramRules.COVERED_MIN] hard sets for the week-covered bonus: the
     * major groups, chest (all three), lats and upper back, shoulders (front, side, rear), biceps, triceps,
     * quads, hamstrings, glutes, calves and abs. Neck, tibialis, adductors, abductors, rotator cuff, forearms,
     * serratus, brachialis, obliques, lower back and hip flexors are small or incidental and never block it.
     * The one place this is defined; [SessionXp] reads it.
     */
    val WEEK_COVERED_MUSCLES: Set<Muscle> = setOf(
        Muscle.UPPER_CHEST, Muscle.MID_CHEST, Muscle.LOWER_CHEST,
        Muscle.LATS, Muscle.RHOMBOIDS, Muscle.TRAPS,
        Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS,
        Muscle.BICEPS, Muscle.TRICEPS,
        Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES,
        Muscle.ABS,
    )

    /** Days without a trial before the next one is a welcome back. */
    const val WELCOME_BACK_DAYS = 10

    /**
     * Seconds of a hold worth one rep-equivalent: the shared conversion, so
     * XP and the strength score cannot price a hold differently.
     */
    const val SECONDS_PER_EFFORT_UNIT = MovementDifficulty.SECONDS_PER_REP_EQUIVALENT

    /** Used only when no bodyweight has ever been recorded. */
    const val ASSUMED_BODYWEIGHT_KG = 75.0

    /** Reps beyond this are not read as reps: a 200-rep set is not 200 reps of evidence. */
    private const val MAX_JUDGED_REPS = 30

    /** One completed set, as XP sees it. */
    data class SetEffort(
        val exerciseName: String,
        /** Repetitions. Always 0 for a hold. */
        val reps: Int = 0,
        /** Seconds held. Null unless this is a hold. */
        val holdSeconds: Int? = null,
        val weightKg: Double? = null,
        val modifiers: String = "",
        /** The catalogue's metric, when known; decides hold-ness outright. */
        val metric: ExerciseMetric? = null,
        val exerciseId: Long = 0,
        /** Logged RPE (RIR is 10 minus it); null when never rated. */
        val rpe: Int? = null,
        val kind: SetKind = SetKind.WORKING,
        /** The marked weight IS the load (a dumbbell row), not added to the body (a pull-up). */
        val externalLoad: Boolean = false,
    )

    /**
     * The lifter's best before this trial, per movement (lowercased name):
     * the best estimated 1-rep max of a rep lift, in kilos of real system
     * load, and the longest hold in seconds.
     */
    class History(
        val bestE1rmKg: Map<String, Double> = emptyMap(),
        val bestHoldSec: Map<String, Int> = emptyMap(),
        /** The heaviest system load a rep lift was done at, in kilos. */
        val bestLoadKg: Map<String, Double> = emptyMap(),
        /**
         * The best e1RM per movement AND [Variation] ([Variation.liftKey]): what the strength-record bonus
         * judges. [bestE1rmKg] stays on the bare name, the benchmark a set's effort is priced against, so
         * the pricing of a set does not move with the variation split.
         */
        val bestE1rmByLift: Map<String, Double> = emptyMap(),
    ) {
        /** This history with [sets] done at [bodyweightKg] added to it. */
        fun after(sets: List<SetEffort>, bodyweightKg: Double?): History {
            val e1rm = bestE1rmKg.toMutableMap()
            val holds = bestHoldSec.toMutableMap()
            val loads = bestLoadKg.toMutableMap()
            val byLift = bestE1rmByLift.toMutableMap()
            for (set in sets) {
                if (!set.kind.countsAsWork) continue
                val key = keyOf(set.exerciseName)
                if (isHold(set)) {
                    val seconds = holdSecondsOf(set)
                    if (seconds > (holds[key] ?: 0)) holds[key] = seconds
                } else {
                    val e1 = e1rmOf(set, bodyweightKg) ?: continue
                    if (e1 > (e1rm[key] ?: 0.0)) e1rm[key] = e1
                    val liftKey = Variation.liftKey(set.exerciseName, set.modifiers)
                    if (e1 > (byLift[liftKey] ?: 0.0)) byLift[liftKey] = e1
                    val load = systemLoadKg(set, bodyweightKg) ?: continue
                    if (load > (loads[key] ?: 0.0)) loads[key] = load
                }
            }
            return History(e1rm, holds, loads, byLift)
        }

        companion object {
            val EMPTY = History()
        }
    }

    internal fun keyOf(name: String) = name.trim().lowercase()

    private fun isHold(set: SetEffort) = MovementDifficulty.isHoldSet(set.metric, set.exerciseName, set.modifiers)

    // holdSeconds is the field; falling back to reps recovers a set restored
    // from an archive written before HOLD existed, which still carries its
    // seconds in the reps column.
    private fun holdSecondsOf(set: SetEffort) = (set.holdSeconds ?: set.reps).coerceAtLeast(0)

    /**
     * Real kilos the set moved: the body plus the added load for a bodyweight
     * movement (less it, when assisted), the marked load alone for a lift
     * that is not bodyweight. Null when a loaded lift has no load to judge.
     */
    internal fun systemLoadKg(set: SetEffort, bodyweightKg: Double?): Double? {
        val bw = bodyweightKg?.takeIf { it > 0.0 } ?: ASSUMED_BODYWEIGHT_KG
        val marked = (set.weightKg ?: 0.0).coerceAtLeast(0.0) * MovementDifficulty.loadFactor(set.exerciseName)
        if (set.externalLoad) return marked.takeIf { it > 0.0 }
        val sign = if (set.modifiers.contains("assisted", ignoreCase = true)) -1.0 else 1.0
        return (bw + sign * marked).coerceAtLeast(bw * 0.1)
    }

    /** Estimated 1-rep max of a rep set, null for a hold or a set with nothing to judge. */
    internal fun e1rmOf(set: SetEffort, bodyweightKg: Double?): Double? {
        if (isHold(set) || set.reps < 1) return null
        val load = systemLoadKg(set, bodyweightKg) ?: return null
        return ProgramRules.epley(load, set.reps.coerceAtMost(MAX_JUDGED_REPS))
    }

    /** One set, judged. */
    data class SetPrice(
        val hard: Boolean,
        /** Estimated reps in reserve; null when there was nothing to judge by. */
        val rir: Double?,
        val xp: Int,
    )

    private fun price(set: SetEffort, bodyweightKg: Double?, history: History): SetPrice {
        val key = keyOf(set.exerciseName)
        val hold = isHold(set)
        val figure: Double
        var rir: Double? = null
        var pct: Double? = null
        var heavier = false
        if (hold) {
            val seconds = holdSecondsOf(set)
            figure = MovementDifficulty.holdRepEquivalents(seconds)
            val best = history.bestHoldSec[key]
            if (best != null && best > 0) {
                rir = MovementDifficulty.holdRepEquivalents(best - seconds)
                pct = seconds.toDouble() / maxOf(best, seconds)
            }
        } else {
            figure = set.reps.coerceAtLeast(0).toDouble()
            val best = history.bestE1rmKg[key]
            val load = systemLoadKg(set, bodyweightKg)
            if (best != null && load != null && load > 0.0 && set.reps > 0) {
                // Epley inverted: the reps this load would have taken at the best e1RM, less the reps done.
                rir = 30.0 * (best / load - 1.0) - set.reps
                val own = ProgramRules.epley(load, set.reps.coerceAtMost(MAX_JUDGED_REPS))
                pct = load / maxOf(best, own)
                heavier = load > (history.bestLoadKg[key] ?: Double.MAX_VALUE) * HEAVIER_MARGIN
            }
        }
        val rpe = set.rpe ?: rir?.let { Effort.rpeOfRir(it.roundToInt().coerceIn(0, Effort.MAX)) }
        val hard = HardSets.isHard(
            kind = set.kind,
            rpe = rpe,
            done = true,
            reps = if (hold) maxOf(1, figure.toInt()) else set.reps,
            weightKg = set.weightKg,
            bodyweight = true,
        )
        if (figure <= 0.0) return SetPrice(false, rir, 0)
        val paid = figure.coerceAtMost(MAX_PAID_REPS.toDouble())
        val units = if (hard) {
            val share = if (pct == null) 0.5 else ((pct - 0.6) / 0.4).coerceIn(0.0, 1.0)
            HARD_UNITS + REP_UNITS * (paid - FULL_CREDIT_REPS).coerceAtLeast(0.0) +
                LOAD_UNITS * share + if (heavier) HEAVIER_UNITS else 0.0
        } else {
            EASY_UNITS_PER_REP * paid
        }
        val xp = BASE_PER_SET + (
            PER_EFFORT_UNIT * units *
                MovementDifficulty.intensity(set.exerciseName) *
                MovementDifficulty.modifierFactor(set.modifiers)
            ).roundToInt()
        return SetPrice(hard, rir, xp)
    }

    /** A single set judged against [history]; an empty set pays nothing, not a base rate. */
    fun judge(set: SetEffort, bodyweightKg: Double?, history: History = History.EMPTY): SetPrice =
        price(set, bodyweightKg, history)

    /** XP for one completed set, easy-set cap not applied. */
    fun setXp(set: SetEffort, bodyweightKg: Double?, history: History = History.EMPTY): Int =
        price(set, bodyweightKg, history).xp

    /**
     * Alias for [MovementDifficulty.taperedVolume], where the taper lives:
     * the strength score tapers identically.
     */
    fun taperedVolume(rawUnits: Double): Double = MovementDifficulty.taperedVolume(rawUnits)

    // ------------------------------------------------------------ breakdown

    enum class Kind(val label: String, val setDriven: Boolean) {
        EXERCISE("", true),
        ACTIVITY("", true),
        PEAK("New peak", true),
        RECORD("Strength record", true),
        HONEST_EFFORT("Honest effort", false),
        RITE_AS_PLANNED("Rite as planned", false),
        WEEK_COVERED("Week covered", false),
        WELCOME_BACK("Welcome back", false),
        FIRST_OF_WEEK("First of the week", false),
        QUEST("Today's trial", false),
        COMPLETION("Trial complete", false),
    }

    /**
     * One segment of the award: a movement or a named bonus. A movement also
     * says what it was made of, so the screen can write "4 hard sets · +17.7kg"
     * without pricing anything itself; a bonus may name the lifts it is for.
     */
    data class Line(
        val kind: Kind,
        val label: String,
        val xp: Int,
        val hardSets: Int = 0,
        val easySets: Int = 0,
        /** The heaviest marked load among the movement's sets, null when it was bodyweight. */
        val topLoadKg: Double? = null,
        /** The movement is a static hold: its sets are holds. */
        val hold: Boolean = false,
        /** What a bonus is for ("Pull-up, Dip"); empty when the label says it all. */
        val detail: String = "",
    )

    /** What one lift's peak pays and what kind it was ("heavier", "more reps"), as [SessionPeaks] priced it. */
    data class PeakAward(val xp: Int, val kind: String)

    /** The award, in segments. [total] is the sum of them, exactly. */
    data class Breakdown(val lines: List<Line>) {
        val total: Int get() = lines.sumOf { it.xp }

        /** What the sets themselves earned: what the live tally runs on. */
        val setDriven: Int get() = lines.filter { it.kind.setDriven }.sumOf { it.xp }

        /** The segments in the order they are shown; empty lines are never in it. */
        fun of(kind: Kind): List<Line> = lines.filter { it.kind == kind }

        /** Spoken or written in one breath: "Pull-up 142, Peak bonus 15, total 180". */
        fun describe(): String =
            lines.joinToString(", ") { "${it.label} ${it.xp}" } + (if (lines.isEmpty()) "" else ", total $total")

        companion object {
            val EMPTY = Breakdown(emptyList())
        }
    }

    /** Facts about the trial as a whole, decided from other trials, never stored. */
    data class TrialFacts(
        /** False for an imported trial: it earns the sets and the completion bonus, no other bonus. */
        val earnsBonuses: Boolean = true,
        val quest: Boolean = false,
        val weekCovered: Boolean = false,
        val welcomeBack: Boolean = false,
        val firstOfWeek: Boolean = false,
        /** What the rite the trial began from prescribed: exercise id to planned sets; null for an open trial. */
        val plan: Map<Long, Int>? = null,
    ) {
        companion object {
            val NONE = TrialFacts(earnsBonuses = true)
        }
    }

    /**
     * Prices a trial's completed sets, in the order given, as segments:
     * one per movement in trial order, [extra] lines (activities), then the
     * peak and record bonuses, then the trial-level bonuses.
     *
     * @param peakLifts movements ([Variation.liftKey]) whose set was a new peak, with what it pays, from [SessionPeaks].
     * @param doneSetCount every done set of the trial, activities included; 0 pays no completion bonus.
     * @param trial the trial-level facts; pass [TrialFacts.NONE] for a live tally (which asks for no completion bonus either).
     */
    fun breakdown(
        sets: List<SetEffort>,
        bodyweightKg: Double?,
        history: History = History.EMPTY,
        peakLifts: Map<String, PeakAward> = emptyMap(),
        doneSetCount: Int = sets.size,
        trial: TrialFacts = TrialFacts.NONE,
        extra: List<Line> = emptyList(),
    ): Breakdown {
        val working = sets.filter { it.kind.countsAsWork }
        val blocks = LinkedHashMap<Pair<Long, String>, MutableList<SetEffort>>()
        for (set in working) blocks.getOrPut(set.exerciseId to Variation.liftKey(set.exerciseName, set.modifiers)) { mutableListOf() } += set

        var easyLeft = EASY_XP_CAP
        val hardCount = HashMap<Long, Int>()
        val lines = mutableListOf<Line>()
        val topSetRated = mutableListOf<Boolean>()
        val recordLifts = mutableListOf<String>()
        for ((id, block) in blocks) {
            var blockXp = 0
            var hard = 0
            var easy = 0
            var best: SetEffort? = null
            var bestE1 = -1.0
            for (set in block) {
                val priced = price(set, bodyweightKg, history)
                var xp = priced.xp
                if (xp > 0 && !priced.hard) {
                    xp = xp.coerceAtMost(easyLeft)
                    easyLeft -= xp
                }
                if (priced.hard && xp > 0) hardCount.merge(id.first, 1, Int::plus)
                if (xp > 0) { if (priced.hard) hard++ else easy++ }
                blockXp += xp
                val e1 = e1rmOf(set, bodyweightKg)
                if (e1 != null && e1 > bestE1) { bestE1 = e1; best = set }
            }
            if (blockXp > 0) {
                lines += Line(
                    Kind.EXERCISE, blockLabel(block), blockXp,
                    hardSets = hard, easySets = easy,
                    topLoadKg = block.mapNotNull { it.weightKg?.takeIf { w -> w > 0.0 } }.maxOrNull(),
                    hold = isHold(block.first()),
                )
            }
            if (best != null) {
                topSetRated += best.rpe != null
                val prior = history.bestE1rmByLift[id.second]
                if (prior != null && bestE1 > prior * RECORD_MARGIN) recordLifts += id.second
            }
        }
        lines += extra

        val award = trial.earnsBonuses
        if (award) {
            var paid = 0
            for ((id, block) in blocks) {
                if (paid >= MAX_BONUS_LIFTS) break
                val key = id.second
                val bonus = when {
                    key in recordLifts -> Line(Kind.RECORD, blockLabel(block), RECORD_BONUS)
                    // A peak that pays nothing is shown on the victory screen but takes no bonus slot.
                    else -> peakLifts[key]?.takeIf { it.xp > 0 }
                        ?.let { Line(Kind.PEAK, blockLabel(block), it.xp, detail = it.kind) }
                } ?: continue
                lines += bonus
                paid++
            }
        }
        // A lift's peak or record names its movement, but the screen shows one segment each.
        val merged = mergeBonusLines(lines)

        val trialLines = mutableListOf<Line>()
        if (award) {
            if (topSetRated.isNotEmpty() && topSetRated.all { it }) {
                trialLines += Line(Kind.HONEST_EFFORT, Kind.HONEST_EFFORT.label, HONEST_EFFORT_BONUS)
            }
            val plan = trial.plan
            if (plan != null && plan.isNotEmpty() && plan.all { (id, planned) -> planned > 0 && (hardCount[id] ?: 0) >= planned }) {
                trialLines += Line(Kind.RITE_AS_PLANNED, Kind.RITE_AS_PLANNED.label, RITE_AS_PLANNED_BONUS)
            }
            if (trial.weekCovered) trialLines += Line(Kind.WEEK_COVERED, Kind.WEEK_COVERED.label, WEEK_COVERED_BONUS)
            // The two never stack: a welcome back is also the week's first trial.
            if (trial.welcomeBack) {
                trialLines += Line(Kind.WELCOME_BACK, Kind.WELCOME_BACK.label, WELCOME_BACK_BONUS)
            } else if (trial.firstOfWeek) {
                trialLines += Line(Kind.FIRST_OF_WEEK, Kind.FIRST_OF_WEEK.label, FIRST_OF_WEEK_BONUS)
            }
            if (trial.quest) trialLines += Line(Kind.QUEST, Kind.QUEST.label, QUEST_BONUS)
        }
        if (doneSetCount > 0) trialLines += Line(Kind.COMPLETION, Kind.COMPLETION.label, COMPLETION_BONUS)
        return Breakdown(merged + trialLines)
    }

    /** A block's movement as a line names it: the name, then its [Variation] when it has one. */
    private fun blockLabel(block: List<SetEffort>): String = Variation.label(block.first().exerciseName, block.first().modifiers)

    /** Folds the per-lift PEAK and RECORD lines into one segment each, in place of the first. */
    private fun mergeBonusLines(lines: List<Line>): List<Line> {
        val out = mutableListOf<Line>()
        for (kind in listOf(Kind.PEAK, Kind.RECORD)) {
            val of = lines.filter { it.kind == kind }
            if (of.isNotEmpty()) {
                out += Line(kind, kind.label, of.sumOf { it.xp }, detail = of.joinToString(", ") { if (it.detail.isEmpty()) it.label else "${it.label} \u00B7 ${it.detail}" })
            }
        }
        return lines.filter { it.kind != Kind.PEAK && it.kind != Kind.RECORD } + out
    }

    /**
     * The live tally: what the sets done so far have earned, which is exactly
     * the [Breakdown.setDriven] part of what a seal would award for them. The
     * completion and trial-level bonuses arrive at the seal.
     */
    fun tally(
        sets: List<SetEffort>,
        bodyweightKg: Double?,
        history: History = History.EMPTY,
        peakLifts: Map<String, PeakAward> = emptyMap(),
    ): Int = breakdown(sets, bodyweightKg, history, peakLifts, doneSetCount = 0).setDriven

    /** XP for the sets alone plus the completion bonus; kept for callers that need only the figure. */
    fun award(
        sets: List<SetEffort>,
        bodyweightKg: Double?,
        doneSetCount: Int = sets.size,
        history: History = History.EMPTY,
    ): Int = breakdown(sets, bodyweightKg, history, emptyMap(), doneSetCount, TrialFacts(earnsBonuses = false)).total

    fun xpForNextLevel(level: Int): Long = 100L * level

    data class Progress(val level: Int, val intoLevel: Long, val needed: Long)

    fun progress(totalXp: Long): Progress {
        var remaining = totalXp.coerceAtLeast(0)
        var level = 1
        while (remaining >= xpForNextLevel(level)) {
            remaining -= xpForNextLevel(level)
            level++
        }
        return Progress(level, remaining, xpForNextLevel(level))
    }

    fun levelFor(totalXp: Long): Int = progress(totalXp).level

    /**
     * Inscriptions owed for a rise from [levelBefore] to [levelAfter], given
     * the highest level that has ever paid ([paidThrough]). Each level pays
     * once, ever: dropping a level (an unclaim, a deleted trial) and climbing
     * back re-crosses levels that already paid, and must not mint again.
     */
    fun rollsDue(levelBefore: Int, levelAfter: Int, paidThrough: Int): Int =
        (levelAfter - maxOf(levelBefore, paidThrough)).coerceAtLeast(0)
}
