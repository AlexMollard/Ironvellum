package com.ironvellum.app.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One day a movement could sit on: the day number and its session's name,
 * the [tags] that justify it (at most two) and its [penalty] (lower is better).
 */
data class DayCandidate(
    val day: Int,
    val presetName: String,
    val tags: List<ReasonTag>,
    val penalty: Double,
)

/** Up to three days, best first; empty when the week has no other session to move to. */
data class RankedDays(val options: List<DayCandidate>)

/**
 * Recovery-aware days. A muscle is "hard" on a day when that day's session
 * credits it two or more fractional sets; two hard days closer than 48 hours
 * cost the shortfall in days. Every rule here only penalises, never refuses:
 * a week that cannot be spaced is scheduled as well as it can be.
 */
object Recovery {

    /** Hours a muscle is given before it is hit hard again. A convention, not a trial result. */
    const val MIN_GAP_HOURS = 48

    /** Fractional sets one session must credit a muscle for the day to count as hard for it. */
    const val HARD_CREDIT = 2.0

    private const val EPS = 1e-9

    /** Penalty weights of [bestFitDays]: spacing counts triple, a heavy session five per muscle. */
    private const val SPACING_WEIGHT = 3.0
    private const val HEAVY_WEIGHT = 5.0

    /** Most days [bestFitDays] offers. */
    private const val MAX_OPTIONS = 3

    /** Hours between two ISO weekdays (1 = Monday .. 7 = Sunday), wrapping around the week. */
    fun gapHours(a: Int, b: Int): Int {
        val apart = abs((a - 1) - (b - 1)) % 7
        return 24 * min(apart, 7 - apart)
    }

    /** Each muscle's hard days, from the sessions that carry a day. */
    fun hardDays(presets: List<PlannedPreset>): Map<Muscle, List<Int>> {
        val hard = mutableMapOf<Muscle, MutableSet<Int>>()
        for ((day, sessions) in presets.filter { it.scheduledDay != null }.groupBy { it.scheduledDay!! }) {
            for ((muscle, credit) in HardSets.planned(sessions)) {
                if (credit >= HARD_CREDIT - EPS) hard.getOrPut(muscle) { sortedSetOf() } += day
            }
        }
        return hard.mapValues { it.value.toList() }
    }

    /** Sum over muscles and pairs of hard days of the shortfall under 48 hours, in days. */
    fun spacingPenalty(presets: List<PlannedPreset>): Double {
        var penalty = 0.0
        for (days in hardDays(presets).values) {
            for (i in days.indices) for (j in i + 1 until days.size) {
                penalty += max(0, MIN_GAP_HOURS - gapHours(days[i], days[j])) / 24.0
            }
        }
        return penalty
    }

    /**
     * The three best sessions of [week] to put [entry] in, best first. Each
     * is scored by the week's spacing penalty (x3) plus the minutes it runs
     * past the session clock plus 5 per muscle it pushes past the heavy
     * session line; ties go to the lowest day. The session the entry already
     * sits in ([fromPreset], or every session holding its name) is not offered,
     * nor is one that already holds the movement. Tags: [Reason.RECOVERY_SPACED] when no muscle is
     * hit hard under 48 hours apart, [Reason.FITS_DAY_LENGTH] when the day
     * stays inside the clock and under the heavy line.
     */
    fun bestFitDays(
        entry: PlannedEntry,
        week: List<PlannedPreset>,
        request: ProgramRequest,
        fromPreset: String? = null,
    ): RankedDays {
        val budget = ProgramRules.sessionBudgetSeconds(request.sessionMinutes)
        fun holds(p: PlannedPreset) = p.entries.any { it.exerciseName.equals(entry.exerciseName, ignoreCase = true) }
        val holders = week.filter { if (fromPreset != null) it.name == fromPreset else holds(it) }
        val without = week.map { p ->
            if (p in holders) p.copy(entries = p.entries.filter { !it.exerciseName.equals(entry.exerciseName, ignoreCase = true) }) else p
        }
        val options = week
            .filter { it.scheduledDay != null && it !in holders && !holds(it) }
            .distinctBy { it.scheduledDay }
            .map { target ->
                val moved = target.copy(entries = target.entries + entry)
                val after = without.map { if (it.scheduledDay == target.scheduledDay) moved else it }
                val spacing = spacingPenalty(after)
                val over = max(0, ProgramRules.sessionSeconds(moved.entries, request.focus) - budget) / 60.0
                val heavy = HardSets.planned(listOf(moved)).values.count { it > ProgramRules.HEAVY_SESSION + EPS }
                val tags = buildList {
                    if (spacing <= EPS) add(ReasonTag(Reason.RECOVERY_SPACED))
                    if (over <= EPS && heavy == 0) add(ReasonTag(Reason.FITS_DAY_LENGTH))
                }
                DayCandidate(
                    day = target.scheduledDay!!,
                    presetName = target.name,
                    tags = tags,
                    penalty = spacing * SPACING_WEIGHT + over + heavy * HEAVY_WEIGHT,
                )
            }
            .sortedWith(compareBy({ it.penalty }, { it.day }))
            .take(MAX_OPTIONS)
        return RankedDays(options)
    }

    /**
     * Permutes only the day numbers among sessions of the same role (the
     * name without its trailing A/B/C ordinal) to minimise [spacingPenalty].
     * Deterministic: permutations are tried in order and only a strictly
     * lower penalty replaces the current assignment, so an already-spaced week
     * is returned untouched. Sessions without a day stay as they are; the
     * result is ordered by day when anything moved.
     */
    fun spaceDays(presets: List<PlannedPreset>): List<PlannedPreset> {
        val groups = presets.indices
            .filter { presets[it].scheduledDay != null }
            .groupBy { roleOf(presets[it]) }
            .values
            .filter { it.size > 1 }
        if (groups.isEmpty()) return presets

        val identity = presets.map { it.scheduledDay }
        var bestDays = identity
        var bestPenalty = spacingPenalty(presets)
        if (bestPenalty <= EPS) return presets

        fun search(index: Int, days: List<Int?>) {
            if (index == groups.size) {
                if (days == identity) return
                val penalty = spacingPenalty(presets.mapIndexed { i, p -> p.copy(scheduledDay = days[i]) })
                if (penalty < bestPenalty - EPS) {
                    bestPenalty = penalty
                    bestDays = days
                }
                return
            }
            val slots = groups[index]
            val original = slots.map { presets[it].scheduledDay!! }
            for (order in permutations(original)) {
                val next = days.toMutableList()
                slots.forEachIndexed { k, slot -> next[slot] = order[k] }
                search(index + 1, next)
            }
        }
        search(0, identity)

        if (bestDays == identity) return presets
        return presets.mapIndexed { i, p -> p.copy(scheduledDay = bestDays[i]) }
            .sortedBy { it.scheduledDay ?: Int.MAX_VALUE }
    }

    private fun roleOf(preset: PlannedPreset): String = preset.name.substringBeforeLast(' ')

    /** Every ordering of [items], the original order first. */
    private fun permutations(items: List<Int>): List<List<Int>> {
        if (items.size <= 1) return listOf(items)
        return items.indices.flatMap { i ->
            permutations(items.filterIndexed { k, _ -> k != i }).map { listOf(items[i]) + it }
        }
    }
}
