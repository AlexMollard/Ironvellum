package com.ironvellum.app.domain

import kotlin.math.floor

/**
 * What the lifter wears or hangs on a bodyweight lift, and so the real increments their added load moves in.
 * A vest is built from blocks ([vestBlockKg], 1 kg by default) up to [vestMaxKg]; past that a dip belt takes
 * plates in 2.5 kg steps with no ceiling. With no vest ([vestMaxKg] null) the kit changes nothing: a belt alone
 * moves in the same 2.5 kg a bodyweight lift always did. Stored in kg like every load; in lb the grid is built
 * on the displayed figure the way [Loads] does, so a 1 kg block reads as 2 lb and never as 2.2.
 *
 * The grid is block steps from 0 to the vest max, then (belt owned) 2.5 kg steps on up from there. Every
 * function returns kg and is only meaningful when [hasVest]; callers go through [WornLoad.forLift], which
 * hands back null when the kit does not apply, so "no kit" keeps today's code path untouched.
 */
data class LoadKit(
    val vestMaxKg: Double? = null,
    val vestBlockKg: Double = DEFAULT_VEST_BLOCK_KG,
    val dipBelt: Boolean = false,
) {
    val hasVest: Boolean get() = (vestMaxKg ?: 0.0) > 0.0 && vestBlockKg > 0.0

    private class Grid(val kg: Double, val lb: Double)

    private val vest get() = Grid(vestBlockKg, Loads.snap(vestBlockKg, LoadUnit.LB).coerceAtLeast(0.5))

    /** True when [loadKg] is past the vest and a belt carries it. */
    private fun onBelt(loadKg: Double): Boolean = dipBelt && loadKg > (vestMaxKg ?: 0.0) + TOLERANCE_KG

    private fun gridFor(onBelt: Boolean) = if (onBelt) BELT else vest

    /** The next load up, or null at the vest's top with no belt to go on with: hold the load and climb reps. */
    fun nextKg(loadKg: Double, unit: LoadUnit): Double? {
        val max = vestMaxKg ?: return null
        if (loadKg >= max - TOLERANCE_KG) {
            if (!dipBelt) return null
            return Loads.stepUp(loadKg, unit, BELT.lb, BELT.kg).round3()
        }
        val up = Loads.stepUp(loadKg, unit, vest.lb, vest.kg)
        return (if (up > max) max else up).round3()
    }

    /** The next load down, or null at or below bodyweight. Never steps from the belt past the vest's top. */
    fun previousKg(loadKg: Double, unit: LoadUnit): Double? {
        val onBelt = onBelt(loadKg)
        val grid = gridFor(onBelt)
        val down = Loads.stepDown(loadKg, unit, grid.lb, grid.kg) ?: return null
        val max = vestMaxKg ?: return down.round3()
        return (if (onBelt && down < max) max else down).round3()
    }

    /** [loadKg] to the nearest real load, never below one block: where a deload or a warm-up lands. */
    fun roundKg(loadKg: Double, unit: LoadUnit): Double {
        val grid = gridFor(onBelt(loadKg))
        if (unit == LoadUnit.LB) {
            val lb = Loads.snap(loadKg, unit)
            return Loads.toKg((Math.round(lb / grid.lb) * grid.lb).coerceAtLeast(grid.lb), unit)
        }
        return (Math.round(loadKg / grid.kg) * grid.kg).coerceAtLeast(grid.kg).round3()
    }

    /** [loadKg] down to the real load at or under it, never below one block: a working load derived from an e1RM. */
    fun floorKg(loadKg: Double): Double {
        val grid = gridFor(onBelt(loadKg))
        return (floor(loadKg / grid.kg + 1e-9) * grid.kg).coerceAtLeast(grid.kg).round3()
    }

    /** The first load a bodyweight lift takes: one block. */
    fun firstStepKg(unit: LoadUnit): Double = nextKg(0.0, unit) ?: vestBlockKg

    private fun Double.round3() = Math.round(this * 1000.0) / 1000.0

    companion object {
        /** The picker's vest-max stepper: 1 kg or 2 lb a tap, from one step up to 100 kg. */
        fun stepVestMax(maxKg: Double, delta: Int, unit: LoadUnit): Double {
            val step = if (unit == LoadUnit.KG) 1.0 else 2.0
            val moved = Loads.snap(maxKg, unit) + delta * step
            return Loads.toKg(moved.coerceIn(step, Loads.snap(VEST_TOP_KG, unit)), unit)
        }

        /** The picker's block stepper: 0.5 kg or 1 lb a tap, from one step up to 5 kg or 10 lb. */
        fun stepVestBlock(blockKg: Double, delta: Int, unit: LoadUnit): Double {
            val step = if (unit == LoadUnit.KG) 0.5 else 1.0
            val top = if (unit == LoadUnit.KG) 5.0 else 10.0
            val moved = Loads.snap(blockKg, unit) + delta * step
            return Loads.toKg(moved.coerceIn(step, top), unit)
        }

        private const val VEST_TOP_KG = 100.0
        const val DEFAULT_VEST_KG = 20.0
        const val DEFAULT_VEST_BLOCK_KG = 1.0
        val NONE = LoadKit()

        /** Dip-belt plates: 2.5 kg, or 5 lb for a lifter who counts in pounds. */
        private val BELT = Grid(2.5, 5.0)
        private const val TOLERANCE_KG = 0.005
    }
}

/**
 * Which lifts take the kit. The test is the app's own: a bodyweight movement whose progress is reps and then
 * added load ([Progression.Lever.REPS_THEN_LOAD]: pull-up, chin-up, dip, push-up and the unweighted rest), and
 * the tree's weighted movements, whose bar is load on the belt ([Skills.loadBar] with `added`). A barbell or
 * dumbbell lift never takes it.
 */
object WornLoad {

    fun applies(name: String, lever: Progression.Lever): Boolean =
        lever == Progression.Lever.REPS_THEN_LOAD || Skills.loadBar(name)?.added == true

    /**
     * For a movement whose catalogue flag is known ([weighted]; null when only the name is). With no flag only
     * the tree's weighted movements can be told apart, so the rest read as not worn: today's step.
     */
    fun applies(name: String, weighted: Boolean?, metric: ExerciseMetric = ExerciseMetric.REPS): Boolean =
        applies(name, Progression.leverFor(name, weighted ?: true, metric))

    /** [kit] when it has a vest and [name] takes it; null otherwise, which every caller reads as "step as before". */
    fun forLift(kit: LoadKit?, name: String, lever: Progression.Lever): LoadKit? =
        kit?.takeIf { it.hasVest && applies(name, lever) }

    fun forLift(kit: LoadKit?, name: String, weighted: Boolean?, metric: ExerciseMetric = ExerciseMetric.REPS): LoadKit? =
        kit?.takeIf { it.hasVest && applies(name, weighted, metric) }
}
