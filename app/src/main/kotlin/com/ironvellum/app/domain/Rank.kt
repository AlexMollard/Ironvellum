package com.ironvellum.app.domain

import java.util.Locale
import kotlin.math.ceil

/** One pattern's best mark in the window, and what raises it next. */
data class PatternScore(
    val pattern: Pattern,
    val step: Int,
    /** What earned [step]: "Pull-up 5 × +15.2 kg", or a skill's name. */
    val source: String,
    /** The least load that lifts the pattern one step on one of its lifts; null with no lift in it or at the top. */
    val liftTarget: String?,
    /** The next skill that would raise the pattern, with its standard; null when none is left. */
    val skillTarget: String?,
)

data class RankBreakdown(
    val band: String,
    val step: Int,
    val patterns: List<PatternScore>,
    /** The band above [band]; null at Elite. */
    val nextBand: String?,
    /** Pattern steps still to gain to reach [nextBand]; null at Elite. */
    val stepsToNext: Int?,
    /** 0..1 through the current band toward [nextBand]; 1 at Elite. */
    val progress: Float,
)

/**
 * Strength Rank: how strong the Ironbound is now, by movement pattern.
 *
 * The five words are the strength-standard bands. Each of Pull, Push and Legs
 * ([RankPatterns]) takes its best step in the last [WINDOW_DAYS] days from
 * either route:
 *  - a TIERED lift: estimated 1RM over bodyweight on [LiftBoards]' floors
 *    (Strength Level's Beginner..Elite), so it needs a weigh-in;
 *  - a skill-tree skill cleared at its standard by a done, unassisted set or a
 *    practice attempt: tier N is step 2N - 1, the lower half of band N.
 *
 * The rank is the mean step over the patterns that have one, rounded down. A
 * pattern with no work in the window is left out, never zero, and Core never
 * counts, so extra work can only raise the rank. It still falls when a pattern
 * goes untrained past the window or strength fades.
 *
 * Claims never count: a claim is an honours mark, not a measured figure.
 * Level never feeds this. Level follows XP and drives [ArmyClass].
 */
object Rank {

    const val UNRANKED = "Unranked"
    const val UNTRAINED = "Untrained"
    const val NOVICE = "Novice"
    const val INTERMEDIATE = "Intermediate"
    const val ADVANCED = "Advanced"
    const val ELITE = "Elite"

    const val WINDOW_DAYS = 90

    private const val WINDOW_MS = WINDOW_DAYS * 24L * 60 * 60 * 1000

    /** The bands in order; an index here is a band's place on the rank-up high-water mark. */
    val BANDS: List<String> = listOf(UNTRAINED, NOVICE, INTERMEDIATE, ADVANCED, ELITE)

    /** First step of each band in [BANDS]. */
    private val BAND_FLOORS = listOf(0, 3, 5, 7, 9)

    /** Band for a step 0..[LiftBoards.MAX_STEP]: two steps per band, step 0 with the first. */
    fun forStep(step: Int): String = when {
        step < 3 -> UNTRAINED
        step < 5 -> NOVICE
        step < 7 -> INTERMEDIATE
        step < 9 -> ADVANCED
        else -> ELITE
    }

    private val COUNTED_BY_NAME: Map<String, Pair<Skills.SkillDef, Pattern>> =
        RankPatterns.COUNTED.associateBy { Titles.normaliseName(it.first.name) }

    private fun skillStep(skill: Skills.SkillDef) = 2 * skill.tier - 1

    /** Current band from the trials and practices in the [WINDOW_DAYS] days up to [nowMs]; null when unranked. */
    fun current(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        bodyweightAt: (Long) -> Double,
        sex: Sex,
        nowMs: Long,
        practices: List<SkillPractice> = emptyList(),
    ): String? = breakdown(history, bodyweightAt, sex, nowMs, practices)?.band

    /** The rank with each pattern's mark and next targets; null when no pattern has a mark. */
    fun breakdown(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        bodyweightAt: (Long) -> Double,
        sex: Sex,
        nowMs: Long,
        practices: List<SkillPractice> = emptyList(),
    ): RankBreakdown? {
        fun inWindow(at: Long) = at <= nowMs && at > nowMs - WINDOW_MS

        val bestByLift = HashMap<Lift, LiftBoards.TieredScore>()
        val bestSkill = HashMap<Pattern, Skills.SkillDef>()
        val cleared = HashSet<String>()

        fun clear(skill: Skills.SkillDef, pattern: Pattern) {
            cleared += skill.name
            val held = bestSkill[pattern]
            if (held == null || skill.tier > held.tier) bestSkill[pattern] = skill
        }

        for ((session, sets) in history) {
            if (!inWindow(session.completedAtMs ?: session.startedAtMs)) continue
            val bodyweight = bodyweightAt(session.startedAtMs)
            for (set in sets) {
                LiftBoards.tieredScore(set, bodyweight, sex)?.let { score ->
                    val held = bestByLift[score.lift]
                    if (held == null || score.ratio > held.ratio) bestByLift[score.lift] = score
                }
                if (!set.done || LiftBoards.isAssisted(set)) continue
                val (skill, pattern) = COUNTED_BY_NAME[Titles.normaliseName(set.exerciseName)] ?: continue
                val figure = when (skill.metric) {
                    Skills.Metric.REPS -> set.reps
                    Skills.Metric.SECONDS -> set.durationSec ?: 0
                    Skills.Metric.METRES -> 0
                }
                if (skill.target > 0 && figure >= skill.target) clear(skill, pattern)
            }
        }
        for (practice in practices) {
            if (practice.claimed || !inWindow(practice.practicedAtMs)) continue
            val (skill, pattern) = COUNTED_BY_NAME[Titles.normaliseName(practice.skillName)] ?: continue
            if (skill.target > 0 && practice.value >= skill.target) clear(skill, pattern)
        }

        val patterns = Pattern.entries.mapNotNull { pattern ->
            val lifts = bestByLift.values.filter { RankPatterns.forLift(it.lift) == pattern }
            val skill = bestSkill[pattern]
            val liftStep = lifts.maxOfOrNull { it.step } ?: -1
            val skillStep = skill?.let(::skillStep) ?: -1
            val step = maxOf(liftStep, skillStep)
            if (step < 0) return@mapNotNull null
            val source = if (liftStep >= skillStep) liftSource(lifts.filter { it.step == liftStep }.maxBy { it.ratio }) else skill!!.name
            PatternScore(pattern, step, source, liftTarget(lifts, step, sex), skillTarget(pattern, step, cleared))
        }
        if (patterns.isEmpty()) return null

        val n = patterns.size
        val sum = patterns.sumOf { it.step }
        val step = sum / n
        val band = forStep(step)
        val index = BANDS.indexOf(band)
        val floor = BAND_FLOORS[index]
        val nextFloor = BAND_FLOORS.getOrNull(index + 1)
        return RankBreakdown(
            band = band,
            step = step,
            patterns = patterns,
            nextBand = BANDS.getOrNull(index + 1),
            stepsToNext = nextFloor?.let { it * n - sum },
            progress = if (nextFloor == null) 1f else (sum - floor * n).toFloat() / ((nextFloor - floor) * n),
        )
    }

    private fun liftSource(s: LiftBoards.TieredScore): String {
        val load = if (LiftBoards.movesBodyweight(s.lift)) {
            val added = s.movedKg - s.bodyweightKg
            if (added < 0.05) "BW" else "+${kg(added)} kg"
        } else {
            "${kg(s.movedKg)} kg"
        }
        return "${s.exerciseName} ${s.reps} × $load"
    }

    /** The smallest added load, over the pattern's lifts, that scores [step] + 1 at the same reps. */
    private fun liftTarget(lifts: List<LiftBoards.TieredScore>, step: Int, sex: Sex): String? {
        if (step >= LiftBoards.MAX_STEP) return null
        return lifts
            .map { s ->
                val needed = LiftBoards.ratioForStep(s.lift, sex, step + 1) * s.bodyweightKg / ProgramRules.epley(1.0, s.reps)
                s to roundUpTenth(needed - s.movedKg)
            }
            .minByOrNull { it.second }
            ?.let { (s, delta) -> "+${kg(delta)} kg on ${s.exerciseName} × ${s.reps}" }
    }

    /**
     * The next skill that raises the pattern past [step]: the lowest tier that
     * does, preferring one whose prerequisites the lifter has already cleared,
     * else catalogue order.
     */
    private fun skillTarget(pattern: Pattern, step: Int, cleared: Set<String>): String? {
        val above = RankPatterns.COUNTED
            .filter { (skill, p) -> p == pattern && skillStep(skill) > step && skill.name !in cleared }
            .map { it.first }
        val tier = above.minOfOrNull { it.tier } ?: return null
        val candidates = above.filter { it.tier == tier }
        val next = candidates.firstOrNull { c -> c.prerequisites.any { it in cleared } } ?: candidates.first()
        return "${next.name}: ${next.standard}"
    }

    /** Up to the next 0.1 kg, so meeting the target always clears the boundary. */
    private fun roundUpTenth(kg: Double): Double = (ceil(kg * 10 - 1e-9) / 10).coerceAtLeast(0.1)

    private fun kg(value: Double): String {
        val r = Math.round(value * 10) / 10.0
        return if (r % 1.0 == 0.0) String.format(Locale.ROOT, "%.0f", r) else String.format(Locale.ROOT, "%.1f", r)
    }
}
