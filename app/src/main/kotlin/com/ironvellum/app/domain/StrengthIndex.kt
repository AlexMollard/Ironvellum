package com.ironvellum.app.domain

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Body-scaled strength scoring. A rep of bodyweight work scores
 * `reps * (bodyweight + added kg) / bodyweight^0.67` — allometric exponent
 * 2/3, the standard strength-vs-mass scaling. Heavier lifters must move more
 * absolute load for the same score, so milestones compare athletes fairly.
 *
 * A static hold contributes through the SAME conversion XP uses
 * ([MovementDifficulty.SECONDS_PER_REP_EQUIVALENT]), so a minute of L-sit is
 * worth twelve rep-equivalents rather than the sixty repetitions the old
 * reps-column encoding made it.
 *
 * Per-movement weighting and the volume taper ARE applied: the rep-equivalents
 * pass through [MovementDifficulty.taperedVolume] — the same taper XP uses, so
 * the two currencies cannot disagree about volume — and the result is scaled
 * by [MovementDifficulty.strengthWeight], sqrt-damped so bodyweight scaling
 * stays dominant. The earlier version applied neither, and the flat score let
 * cheap volume (five sets of 100 bodyweight squats) farm a leaderboard that
 * sums lifetime scores.
 *
 * v1 assumptions (deliberate): every bodyweight rep moves ~full bodyweight;
 * height is not separately compensated (it correlates with mass).
 *
 * Sex is normalised at the SESSION boundary only - see [sessionScore] and
 * [sexFactor].
 */
object StrengthIndex {

    const val MASS_EXPONENT = 0.67

    /**
     * The formula version stamp for stored scores. Bump this whenever the
     * scoring formula changes in a way that restates history, so the migration
     * layer can rescore every completed session from its stored sets exactly
     * once — scores and the lifetime sum are meaningless if old and new
     * formulas mix in one leaderboard.
     */
    const val SCORING_VERSION = 2

    /**
     * Female-to-male strength normalisation, applied so the shared board ranks
     * the FEAT rather than the physiology. At equal bodyweight the gap is not
     * one number: women reach roughly 60-70% of men's upper-body strength
     * relative to bodyweight but 75-80% of lower-body (Miller et al., Eur J
     * Appl Physiol 1993, women at 52%/66% of absolute upper/lower strength;
     * Bishop 1983 for the bodyweight-relative figures). A single coefficient
     * would therefore under-credit every pull-up and over-credit every squat.
     *
     * Cross-check: these weights average to ~1.45 over a typical mixed
     * session, against the IPF's own 2020 GL coefficients, which value a
     * woman's powerlifting total at 1.36-1.44x a man's at equal bodyweight.
     * The IPF total is lower-body dominated, so sitting slightly above it is
     * the expected direction.
     *
     * MALE is 1.0 by construction: it is the scale every stored score was
     * already computed on, so normalisation moves nobody's existing history.
     */
    fun sexFactor(sex: Sex, muscleGroup: MuscleGroup): Double = when (sex) {
        Sex.MALE -> 1.0
        Sex.FEMALE -> when (muscleGroup) {
            MuscleGroup.PULL, MuscleGroup.PUSH -> UPPER_BODY_FEMALE
            MuscleGroup.LEGS -> LOWER_BODY_FEMALE
            // Trunk work sits between the limb figures, and no separate
            // trunk dataset backs a third number - say so rather than
            // implying a precision that was never measured.
            else -> (UPPER_BODY_FEMALE + LOWER_BODY_FEMALE) / 2.0
        }
    }

    /** 1 / 0.65: upper-body strength relative to bodyweight, Bishop 1983. */
    const val UPPER_BODY_FEMALE = 1.54

    /** 1 / 0.775: lower-body, the 75-80% band from the same review. */
    const val LOWER_BODY_FEMALE = 1.29

    fun repScore(exerciseName: String, reps: Int, addedKg: Double?, bodyweightKg: Double): Double =
        effortScore(exerciseName, reps.toDouble(), addedKg, bodyweightKg)

    /** A hold's contribution: its seconds converted to rep-equivalents. */
    fun holdScore(exerciseName: String, seconds: Int, addedKg: Double?, bodyweightKg: Double): Double =
        effortScore(exerciseName, MovementDifficulty.holdRepEquivalents(seconds), addedKg, bodyweightKg)

    private fun effortScore(
        exerciseName: String,
        repEquivalents: Double,
        addedKg: Double?,
        bodyweightKg: Double,
    ): Double {
        if (repEquivalents <= 0.0 || bodyweightKg <= 0.0) return 0.0
        // The marked number becomes real load first, for the same reason XP
        // converts it: an implement's plate count is not a claim about how
        // much the body moved. Uncapped on purpose - a 200 kg deadlift is
        // genuinely that heavy, and capping it would punish the exact work
        // this score exists to reward.
        val marked = (addedKg ?: 0.0).coerceAtLeast(0.0)
        val load = bodyweightKg + marked * MovementDifficulty.loadFactor(exerciseName)
        return MovementDifficulty.taperedVolume(repEquivalents) *
            load / bodyweightKg.pow(MASS_EXPONENT) *
            MovementDifficulty.strengthWeight(exerciseName)
    }

    /**
     * One completed strength set: reps, or seconds when it is a hold.
     *
     * Carries its [muscleGroup] because the sex normalisation differs between
     * upper and lower body, and the catalogue row the caller already holds is
     * the only honest source for it - deriving the group from the name would
     * be a fourth name-keyed table to drift out of step with the other three.
     */
    data class Effort(
        val exerciseName: String,
        val reps: Int,
        val holdSeconds: Int?,
        val addedKg: Double?,
        val muscleGroup: MuscleGroup,
    )

    /**
     * Total score for a session's done sets; null when bodyweight is unknown.
     *
     * This is the ONE place [sexFactor] is applied. Session score feeds the
     * lifetime sum and the shared leaderboard, which is where lifters are
     * compared to each other. Per-set scores and personal records deliberately
     * stay raw: those compare a lifter against their own past, where a constant
     * factor cannot change any ordering, and scaling them would only make two
     * numbers on the same screen disagree.
     */
    fun sessionScore(sets: List<Effort>, bodyweightKg: Double?, sex: Sex): Int? {
        if (bodyweightKg == null || bodyweightKg <= 0.0) return null
        return sets.sumOf { effort ->
            val units = effort.holdSeconds
                ?.let { MovementDifficulty.holdRepEquivalents(it) }
                ?: effort.reps.toDouble()
            effortScore(effort.exerciseName, units, effort.addedKg, bodyweightKg) *
                sexFactor(sex, effort.muscleGroup)
        }.roundToInt()
    }
}
