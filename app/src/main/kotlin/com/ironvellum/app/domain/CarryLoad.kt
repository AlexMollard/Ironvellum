package com.ironvellum.app.domain

/** One set of a movement, as far as carrying its figures is concerned. */
data class CarrySet(
    val id: Long,
    val setIndex: Int,
    val weightKg: Double?,
    val done: Boolean,
    val reps: Int = 0,
    val durationSec: Int? = null,
    val warmup: Boolean = false,
)

/** What a tick can hand on: the load, the reps, or a hold's seconds. */
enum class CarryFigure { LOAD, REPS, SECONDS }

/**
 * The figures a movement's metric carries. Strength reps carry load and reps, a
 * hold carries load and seconds. Activity work (duration, distance, attempts)
 * carries only a load: its seconds, kilometres and attempts are different every
 * set, so copying one forward would invent a figure the lifter never chose.
 */
fun carryFiguresFor(metric: ExerciseMetric): Set<CarryFigure> = when {
    metric == ExerciseMetric.HOLD -> setOf(CarryFigure.LOAD, CarryFigure.SECONDS)
    metric.isStrength -> setOf(CarryFigure.LOAD, CarryFigure.REPS)
    else -> setOf(CarryFigure.LOAD)
}

/** The new figures for one later set; a null leaves that figure as it is. */
data class Carry(val id: Long, val weightKg: Double? = null, val reps: Int? = null, val durationSec: Int? = null)

/**
 * Which figures of which sets the lifter changed by hand while they were still
 * waiting (a stepper, a typed entry, the load dialog). In memory and per
 * figure: a trial lasts an hour, and a lifter who moved a set's load has not
 * said anything about its reps. Losing it with the process only means a tick
 * stops carrying, never that a figure is overwritten.
 */
class CarryEdits {
    private val load = HashSet<Long>()
    private val reps = HashSet<Long>()
    private val seconds = HashSet<Long>()

    /** Records which figures changed between [before] and [after] as hand edits of that set. */
    @Synchronized
    fun note(before: CarrySet, after: CarrySet) {
        if ((before.weightKg ?: 0.0) != (after.weightKg ?: 0.0)) load += after.id
        if (before.reps != after.reps) reps += after.id
        if (before.durationSec != after.durationSec) seconds += after.id
    }

    @Synchronized
    fun edited(id: Long, figure: CarryFigure): Boolean = when (figure) {
        CarryFigure.LOAD -> id in load
        CarryFigure.REPS -> id in reps
        CarryFigure.SECONDS -> id in seconds
    }
}

/**
 * What ticking [ticked] hands to the later sets of its movement ([siblings] may
 * include [ticked] itself). A figure the lifter moved by hand on [ticked] flows
 * to every later set that is not done, is not a warm-up and whose same figure
 * the lifter has not moved by hand: a figure typed in is never overwritten.
 * A tick that changed nothing (a pyramid ticked as prescribed) carries nothing,
 * with one standing exception: a later set with no load at all takes the
 * ticked load, so a first tick fills in the unloaded sets.
 */
fun carriesFrom(
    ticked: CarrySet,
    siblings: List<CarrySet>,
    figures: Set<CarryFigure>,
    edits: CarryEdits,
): List<Carry> {
    val tickedLoad = ticked.weightKg?.takeIf { it > 0.0 }
    val loadMoved = edits.edited(ticked.id, CarryFigure.LOAD)
    val repsMoved = edits.edited(ticked.id, CarryFigure.REPS)
    val secondsMoved = edits.edited(ticked.id, CarryFigure.SECONDS)
    return siblings
        .filter { it.setIndex > ticked.setIndex && !it.done && !it.warmup }
        .mapNotNull { later ->
            val weight = tickedLoad?.takeIf {
                CarryFigure.LOAD in figures && when {
                    (later.weightKg ?: 0.0) <= 0.0 -> true
                    else -> loadMoved && !edits.edited(later.id, CarryFigure.LOAD) && later.weightKg != it
                }
            }
            val reps = ticked.reps.takeIf {
                CarryFigure.REPS in figures && repsMoved && !edits.edited(later.id, CarryFigure.REPS) && later.reps != it
            }
            val seconds = ticked.durationSec?.takeIf {
                CarryFigure.SECONDS in figures && secondsMoved && !edits.edited(later.id, CarryFigure.SECONDS) && later.durationSec != it
            }
            if (weight == null && reps == null && seconds == null) null else Carry(later.id, weight, reps, seconds)
        }
}
