package com.ironvellum.app.domain

/**
 * The peaks one trial set, rolled up per movement for the victory screen and the XP bonus.
 *
 * A set is a peak by [RepMax], the one rule every peak reader shares: the live badges, the
 * victory panel, the XP bonus, the share card and Today. Deciding it twice in two ways would let
 * one screen disagree with another.
 */
object SessionPeaks {

    /** One movement's peaks: how many, and the one that pays most. */
    data class Peak(
        val exerciseName: String,
        val count: Int,
        val setIndex: Int,
        /** Reps, or seconds when [isHold]. */
        val figure: Int,
        val weightKg: Double?,
        val isHold: Boolean,
        val kind: RepMax.Kind,
        /** The earlier best the headline set passed. */
        val was: RepMax.Mark,
        /** What the lift's peak pays, [Xp.PEAK_BONUS] at most; 0 for a jump too small to matter (it is still a peak). */
        val xp: Int,
        /** Every set position that peaked, for the share grid's purple squares. */
        val setIndexes: Set<Int> = emptySet(),
        /** [Variation.key] of the movement the peak was set in; "" for the plain lift. */
        val variation: String = "",
    ) {
        /** The key the lift's peak is filed under: its name and variation ([Variation.liftKey]). */
        val liftKey: String get() = Variation.liftKey(exerciseName, variation)

        /** The lift as a screen names it: "Single-Leg Calf Raise · deficit". */
        val label: String get() = Variation.label(exerciseName, variation)
    }

    /**
     * @param book what the lifter had done before this trial, from [RepMax.book].
     * @param bodyweightKg today's weigh-in, for a body-weight lift's total load; null counts the added load alone.
     */
    fun of(
        sets: List<SessionSet>,
        book: RepBook,
        bodyweightKg: Double?,
        exerciseOf: (SessionSet) -> Exercise?,
    ): List<Peak> =
        RepMax.blocks(sets)
            .sortedBy { block -> block.minOf { it.exercisePosition } }
            .mapNotNull { block ->
                val exercise = exerciseOf(block.first())
                val metric = exercise?.metric ?: ExerciseMetric.REPS
                if (!metric.isStrength) return@mapNotNull null
                val hold = metric == ExerciseMetric.HOLD
                val addsToBody = exercise?.isWeighted == false
                val isolation = MovementDifficulty.isIsolation(block.first().exerciseName)
                fun pay(hit: RepMax.Hit) = RepMax.xp(hit, hold, addsToBody, isolation, bodyweightKg)
                val hits = RepMax.hits(block, book[RepMax.key(block.first())].orEmpty(), hold)
                val best = RepMax.headline(hits, hold, ::pay) ?: return@mapNotNull null
                Peak(
                    exerciseName = best.set.exerciseName,
                    variation = Variation.key(best.set.modifiers),
                    count = hits.size,
                    setIndex = best.set.setIndex,
                    figure = RepMax.figureOf(best.set, hold),
                    weightKg = best.set.weightKg,
                    isHold = hold,
                    kind = best.kind,
                    was = best.was,
                    xp = pay(best),
                    setIndexes = hits.map { it.set.setIndex }.toSet(),
                )
            }
}
