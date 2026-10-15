package com.ironvellum.app.domain

/**
 * What a generated rite stands on when the lifter has logged nothing yet: their bodyweight, sex and the level
 * they picked. Carried on [StrengthProfile.starter]; real logged lifts always win over it.
 */
data class StarterBasis(val bodyweightKg: Double, val sex: Sex, val level: VolumeLevel)

/**
 * First loads for a loaded lift nobody has logged: without them a new lifter's trial opens at "0 kg x 5".
 *
 * The four board lifts start at a rung of the bodyweight-ratio tiers [LiftBoards] already ranks by (Ash I for a
 * Low level, Bone I Standard, Silver I High), the barbell row beside the bench. Every other lift goes through the
 * related-lift graph [ProgramRules.estimatedE1rmKg] already uses, else a fixed share of the board lift that trains
 * the same region. All of it takes the same safety margin as a transferred estimate and the same RIR and rounding
 * as a logged e1RM; a barbell lift is never below the empty bar. The result is a place to begin, not a prescription.
 */
object StarterLoads {

    const val EMPTY_BAR_KG = 20.0

    private val STEP_BY_LEVEL = mapOf(VolumeLevel.LOW to 1, VolumeLevel.STANDARD to 3, VolumeLevel.HIGH to 5)

    private enum class Anchor { SQUAT, BENCH, DEADLIFT }

    /** Shares of the anchor lift's e1RM, in marked kilos, for lifts the related graph does not reach. */
    private val shares: Map<String, Pair<Anchor, Double>> = mapOf(
        "lat pulldown" to (Anchor.BENCH to 0.9), "face pull" to (Anchor.BENCH to 0.3),
        "reverse fly" to (Anchor.BENCH to 0.15), "lateral raise" to (Anchor.BENCH to 0.15),
        "front raise" to (Anchor.BENCH to 0.15), "cable lateral raise" to (Anchor.BENCH to 0.1),
        "bicep curl" to (Anchor.BENCH to 0.45), "hammer curl" to (Anchor.BENCH to 0.2),
        "preacher curl" to (Anchor.BENCH to 0.4), "cable curl" to (Anchor.BENCH to 0.35),
        "reverse curl" to (Anchor.BENCH to 0.3), "wrist curl" to (Anchor.BENCH to 0.3),
        "triceps pushdown" to (Anchor.BENCH to 0.6), "overhead cable extension" to (Anchor.BENCH to 0.4),
        "triceps kickback" to (Anchor.BENCH to 0.1), "cable fly" to (Anchor.BENCH to 0.25),
        "dumbbell fly" to (Anchor.BENCH to 0.2), "pec deck" to (Anchor.BENCH to 0.6),
        "dumbbell pullover" to (Anchor.BENCH to 0.35), "dumbbell external rotation" to (Anchor.BENCH to 0.07),
        "barbell shrug" to (Anchor.BENCH to 1.0), "dumbbell shrug" to (Anchor.BENCH to 0.5),
        "weighted plank" to (Anchor.BENCH to 0.15), "woodchop" to (Anchor.BENCH to 0.2),
        "pallof press" to (Anchor.BENCH to 0.2),
        "leg extension" to (Anchor.BENCH to 0.7), "seated leg curl" to (Anchor.BENCH to 0.5),
        "lying leg curl" to (Anchor.BENCH to 0.45), "hip adduction" to (Anchor.BENCH to 0.8),
        "hip abduction" to (Anchor.BENCH to 0.8), "seated calf raise" to (Anchor.BENCH to 1.2),
        "standing calf raise" to (Anchor.BENCH to 2.0),
        "hip thrust" to (Anchor.DEADLIFT to 0.8), "single-leg romanian deadlift" to (Anchor.DEADLIFT to 0.3),
        "cable pull-through" to (Anchor.DEADLIFT to 0.4),
        "barbell lunge" to (Anchor.SQUAT to 0.5), "walking lunge" to (Anchor.SQUAT to 0.15),
        "barbell step-up" to (Anchor.SQUAT to 0.4), "dumbbell step-up" to (Anchor.SQUAT to 0.12),
    )

    /** Share of the bench e1RM for a loaded lift in no table at all (a custom movement). */
    private const val UNLISTED_SHARE = 0.3

    private fun boardE1rms(basis: StarterBasis): Map<String, Double> {
        val step = STEP_BY_LEVEL.getValue(basis.level)
        fun e1rm(lift: Lift) = LiftBoards.ratioForStep(lift, basis.sex, step) * basis.bodyweightKg
        val bench = e1rm(Lift.BENCH)
        return mapOf(
            "back squat" to e1rm(Lift.SQUAT),
            "bench press" to bench,
            "deadlift" to e1rm(Lift.DEADLIFT),
            "overhead press" to e1rm(Lift.OVERHEAD_PRESS),
            "barbell row" to bench,
        )
    }

    /** The marked-kilo e1RM a lifter on [basis] is taken to have on [exerciseName]. */
    internal fun e1rmKg(exerciseName: String, basis: StarterBasis): Double {
        val key = exerciseName.trim().lowercase()
        val boards = boardE1rms(basis)
        boards[key]?.let { return it * ProgramRules.ESTIMATE_SAFETY_MARGIN }
        ProgramRules.estimatedE1rmKg(exerciseName, StrengthProfile(boards))?.let { return it.first }
        val (anchor, share) = shares[key] ?: (Anchor.BENCH to UNLISTED_SHARE)
        val base = boards.getValue(
            when (anchor) {
                Anchor.SQUAT -> "back squat"
                Anchor.BENCH -> "bench press"
                Anchor.DEADLIFT -> "deadlift"
            },
        )
        return base * share * ProgramRules.ESTIMATE_SAFETY_MARGIN
    }

    /** The starting load for [exercise] at [reps] and [rir], on the lifter's real load steps; null with no basis. */
    internal fun loadKg(
        exercise: Exercise,
        basis: StarterBasis?,
        reps: Int,
        rir: Int,
        worn: LoadKit?,
    ): Pair<Double, String>? {
        if (basis == null || basis.bodyweightKg <= 0.0) return null
        val load = ProgramRules.loadFromE1rm(
            exercise.name, exercise.muscleGroup.name, e1rmKg(exercise.name, basis), reps, rir, worn,
        )
        val floored = if (GearRequirements.isBarbellLift(exercise.name)) maxOf(load, EMPTY_BAR_KG) else load
        return floored to "starting estimate from your bodyweight and level"
    }
}
