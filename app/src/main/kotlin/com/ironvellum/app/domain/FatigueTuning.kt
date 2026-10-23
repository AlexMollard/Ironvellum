package com.ironvellum.app.domain

/**
 * Every constant of the muscle fatigue estimate, in one place so they can be tuned together. They are starting
 * estimates, not derived from the lifter's data; the estimate is always labelled as one.
 */
object FatigueTuning {
    /** A residual of this many hard-set equivalents reads as 100%. */
    const val REFERENCE_SETS = 6.0

    /** Level boundaries as a fraction of [REFERENCE_SETS]; at or above [HIGH_BELOW] is very high. */
    const val FRESH_BELOW = 0.15
    const val LIGHT_BELOW = 0.35
    const val MODERATE_BELOW = 0.60
    const val HIGH_BELOW = 0.85

    /** Effort factor by reps left; failure and rest-pause sets read [EFFORT_FAILURE], an unrated hard set [EFFORT_UNRATED]. */
    const val EFFORT_FAILURE = 1.15
    const val EFFORT_RIR0 = 1.0
    const val EFFORT_RIR1 = 0.9
    const val EFFORT_RIR2 = 0.8
    const val EFFORT_RIR3 = 0.65
    const val EFFORT_UNRATED = 0.8

    /** Factor for a movement that loads the muscle long, and for a heavy whole-body lift. */
    const val STRETCH = 1.3
    const val SYSTEMIC = 1.2

    /** The lower back's synthetic share on a heavy whole-body lift that does not credit it. */
    const val LOWER_BACK_BONUS_SHARE = 0.25

    /** Novelty by earlier exposures within [NOVELTY_WINDOW_DAYS]: none, one, two or more. */
    const val NOVELTY_NEW = 1.4
    const val NOVELTY_SECOND = 1.2
    const val NOVELTY_WINDOW_DAYS = 21L

    /** Only sessions in this many days before now count towards the residual. */
    const val WINDOW_DAYS = 7L

    /** History a report needs: the residual window plus the novelty look-back. */
    const val HISTORY_DAYS = WINDOW_DAYS + NOVELTY_WINDOW_DAYS

    const val HALF_LIFE_FAST_H = 18.0
    const val HALF_LIFE_MEDIUM_H = 24.0
    const val HALF_LIFE_SLOW_H = 36.0

    /** A session dose to one muscle above [BIG_SESSION_DOSE] stretches that session's half-life by [BIG_SESSION_STRETCH]. */
    const val BIG_SESSION_DOSE = 10.0
    const val BIG_SESSION_STRETCH = 1.25

    /** A lifter whose first workout is under [NEW_LIFTER_WEEKS] weeks old recovers [NEW_LIFTER_STRETCH] times slower. */
    const val NEW_LIFTER_WEEKS = 8L
    const val NEW_LIFTER_STRETCH = 1.2

    /** Soreness is likely from [SORENESS_MIN_DOSE] qualifying dose, between [SORENESS_FROM_H] and [SORENESS_TO_H] hours after. */
    const val SORENESS_MIN_DOSE = 1.5
    const val SORENESS_FROM_H = 12.0
    const val SORENESS_TO_H = 72.0

    /** MuscleMap: a share from here up is main work, which a deficit stretches. */
    const val TRAINS_SHARE = 0.5                          // MuscleMap doc: 0.5+ is "trains the muscle"
    const val MAIN_SHARE = 0.7

    /** The exercise fatigue page: this many hard sets, this many reps left. */
    const val FOOTPRINT_SETS = 3
    const val FOOTPRINT_RIR = 1

    /** Footprint bands by dose; a muscle under [BAND_OMIT_BELOW] is left out. */
    const val BAND_STRONG = 2.0
    const val BAND_MEDIUM = 1.0
    const val BAND_OMIT_BELOW = 0.3
}
