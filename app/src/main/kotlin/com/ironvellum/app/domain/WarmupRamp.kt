package com.ironvellum.app.domain

import kotlin.math.round

/**
 * The warm-up sets offered before a loaded movement's first working set: the
 * empty bar for ten (barbell only), then about 40% for five, 60% for three and
 * 80% for two of the working load, each rounded to the movement's own load step
 * ([Progression.weightStepKg]). They are ordinary Warm-up sets, so they never
 * earn XP, count toward volume or set a peak.
 */
object WarmupRamp {

    /** An Olympic bar. The app does not know the lifter's own bar, so this is the usual one. */
    const val BAR_KG = 20.0
    const val BAR_REPS = 10

    /** Fraction of the working load, and the reps done at it. */
    private val STEPS = listOf(0.40 to 5, 0.60 to 3, 0.80 to 2)

    data class Step(val weightKg: Double, val reps: Int)

    /** True when the movement is loaded with a barbell alone, the way [RestRules] reads it. */
    fun isBarbell(exerciseName: String): Boolean {
        val needs = GearRequirements.needs(exerciseName)
        return needs.isNotEmpty() && needs.all { Gear.BARBELL in it }
    }

    /**
     * The ramp for a working load of [workingKg], lightest first, or empty when
     * there is nothing worth warming up with. A step is skipped when it rounds
     * to the bar or below (barbell) or to nothing (other loads), to the working
     * load or above, or to the same figure as the step before it.
     */
    fun plan(
        workingKg: Double,
        stepKg: Double,
        barbell: Boolean,
        /** The vest a worn-load movement is done in: its blocks replace [stepKg] as the rounding. */
        worn: LoadKit? = null,
        unit: LoadUnit = LoadUnit.KG,
    ): List<Step> {
        // No working load yet (a generated rite starts a lift empty): a barbell still warms up on the bar.
        if (workingKg <= 0.0) return if (barbell) listOf(Step(BAR_KG, BAR_REPS)) else emptyList()
        val step = stepKg.coerceAtLeast(MIN_STEP_KG)
        val floor = if (barbell) BAR_KG else 0.0
        if (workingKg <= floor) return emptyList()
        val steps = mutableListOf<Step>()
        if (barbell) steps += Step(BAR_KG, BAR_REPS)
        for ((fraction, reps) in STEPS) {
            val kg = worn?.roundKg(workingKg * fraction, unit) ?: (round(workingKg * fraction / step) * step)
            val last = steps.lastOrNull()?.weightKg ?: floor
            if (kg <= floor || kg >= workingKg || kg <= last) continue
            steps += Step(kg, reps)
        }
        return steps
    }

    /**
     * The ramp for one catalogue movement given the load of its first working set: only
     * rep-counted movements with a load are warmed up, at the movement's own load step.
     * The one place the live screen's offer and the repository's insert both ask.
     */
    fun planFor(
        exerciseName: String,
        muscleGroup: String,
        metric: ExerciseMetric,
        workingKg: Double?,
        /** The lifter's vest and belt, and the unit they count in; the live screen's offer and the insert pass the same. */
        kit: LoadKit? = null,
        weighted: Boolean? = null,
        unit: LoadUnit = LoadUnit.KG,
    ): List<Step> =
        if (metric != ExerciseMetric.REPS) {
            emptyList()
        } else {
            plan(
                workingKg = workingKg ?: 0.0,
                stepKg = Progression.weightStepKg(muscleGroup, exerciseName),
                barbell = isBarbell(exerciseName),
                worn = WornLoad.forLift(kit, exerciseName, weighted, metric),
                unit = unit,
            )
        }

    private const val MIN_STEP_KG = 0.5
}
