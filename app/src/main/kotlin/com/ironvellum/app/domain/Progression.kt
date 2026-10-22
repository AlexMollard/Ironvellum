package com.ironvellum.app.domain

import com.ironvellum.app.R
import com.ironvellum.app.data.Words

enum class TrainingMode {
    STRENGTH,
    HYPERTROPHY,
}

/**
 * Progressive overload, two schools — each with its own rep band and load step,
 * plus a shared stall rule so a plateau is met with a deload instead of
 * repeating a session you already failed three times.
 *
 *  - STRENGTH: a low band (default 4-6). Clear every set at the band ceiling ->
 *    add the movement's load step and drop back to the band floor.
 *  - HYPERTROPHY: double progression on a higher band (default 8-12). Climb
 *    reps by [REP_STEP] to the ceiling, then add load and reset to the floor.
 *
 * Load steps scale with the movement: legs/compounds 5 kg, standard upper
 * 2.5 kg, isolation 1.25 kg — 2.5 kg on a pull-up is huge; on a squat it is
 * nothing.
 */
object Progression {

    const val WEIGHT_STEP_KG = 2.5
    const val REP_STEP = 2

    /** Seconds a cleared hold grows by. */
    const val HOLD_STEP_SECONDS = 5

    /** Bodyweight reps stop climbing here: past it the stimulus is better bought with load or a harder variation. */
    const val BODYWEIGHT_REP_CEILING = 15

    /** Failed sessions at the same load before the engine backs the weight off. */
    const val STALLS_BEFORE_DELOAD = 3

    /** How much load comes off on a deload. */
    const val DELOAD_FRACTION = 0.10

    /** What progress means for a movement: the one thing the next trial should ask for more of. */
    enum class Lever {
        /** Reps to the band ceiling, then load: the engine's own rule. */
        LOAD,
        /** Bodyweight reps to a ceiling, then added load or a harder variation. */
        REPS_THEN_LOAD,
        /** Range, then tempo, then reps; never load first. */
        CONTROL,
        /** Range, then end-range hold, then tempo; never reps or load. */
        MOBILITY,
        /** Seconds held. */
        HOLD,
        /** Clean attempts at this step, then the next rung of the tree. */
        SKILL,
    }

    /** Lines of the skill tree whose rows are technique steps, not a dose of reps or load. */
    private val skillLines = setOf("Handstand", "Lever", "Planche", "Rings", "Movement")

    /**
     * The lever for a movement: a hold's metric first, then the named control
     * and mobility drills, then technique and the technique lines of the skill
     * tree, then load for anything weighted or load-priced, else bodyweight reps.
     * An unmapped (custom) movement is LOAD if weighted, else REPS_THEN_LOAD.
     */
    fun leverFor(name: String, weighted: Boolean, metric: ExerciseMetric): Lever = when {
        metric == ExerciseMetric.HOLD -> Lever.HOLD
        MuscleMap.isControl(name) -> Lever.CONTROL
        MuscleMap.isMobility(name) -> Lever.MOBILITY
        MuscleMap.isTechnique(name) ||
            Skills.ALL.any { it.name.equals(name.trim(), ignoreCase = true) && it.line in skillLines } -> Lever.SKILL
        weighted || MovementDifficulty.isLoadPriced(name) -> Lever.LOAD
        else -> Lever.REPS_THEN_LOAD
    }

    data class Attempt(val weightKg: Double?, val reps: Int)

    /**
     * One logged set as an attempt. A hold keeps its figure in seconds
     * ([durationSec], reps stay 0; an archive from before HOLD kept it in
     * reps), and that is the number the target is measured against: read
     * from reps it never cleared and an unloaded hold never advanced.
     */
    fun attemptOf(weightKg: Double?, reps: Int, durationSec: Int?, hold: Boolean): Attempt =
        Attempt(weightKg, if (hold) durationSec ?: reps else reps)

    data class Recommendation(
        val weightKg: Double?,
        val reps: Int,
        val reason: String,
        /** True when the engine backed the load off after repeated stalls. */
        val deload: Boolean = false,
        /**
         * True when the prescription differs from last time (the load or the
         * reps moved), so [reason] has something to explain. A first attempt
         * or a repeat is not news and stays quiet.
         */
        val changed: Boolean = false,
        /**
         * True when [reason] is advice that moves no figure (a control, mobility or skill cue, "take a harder
         * variation"): [weightKg] and [reps] are what the lifter last did, not something to start on.
         */
        val cueOnly: Boolean = false,
    )

    /**
     * The rep band each school works in, anchored on the preset's target.
     * Strength trains under the target, hypertrophy climbs above it.
     */
    fun repBand(mode: TrainingMode, targetReps: Int): IntRange = when (mode) {
        TrainingMode.STRENGTH -> (targetReps - 2).coerceAtLeast(3)..targetReps.coerceAtLeast(4)
        TrainingMode.HYPERTROPHY -> targetReps..(targetReps + 4)
    }

    /**
     * Load step scales with the movement: legs/compounds 5 kg, standard
     * 2.5 kg, isolation 1.25 kg - but only where the lifter can actually make
     * that jump. A pinned stack moves one plate at a time and a loaded sled
     * moves a disc a side, so prescribing 2.5 kg on a leg press is an
     * instruction nobody in a real gym can follow. Which movements are
     * machines is read from [MovementDifficulty.loadFactor], the table that
     * already knows, rather than a second list of names to drift.
     */
    fun weightStepKg(muscleGroup: String, exerciseName: String): Double {
        val name = exerciseName.lowercase()
        val implement = MovementDifficulty.loadFactor(exerciseName)
        val isSled = implement == MovementDifficulty.SLED_LOAD
        val isStack = implement < MovementDifficulty.FREE_WEIGHT_LOAD && !isSled
        val isIsolation = listOf("curl", "raise", "wrist", "hang", "plank", "dorsiflexion").any { it in name }
        val isLowerBody = muscleGroup.equals("LEGS", ignoreCase = true) ||
            listOf("squat", "bridge", "lunge", "hinge").any { it in name }
        return when {
            isSled -> 10.0
            isStack -> 5.0
            isIsolation -> 1.25
            isLowerBody -> 5.0
            else -> 2.5
        }
    }

    fun next(
        mode: TrainingMode,
        targetReps: Int,
        setsDone: Int,
        minSets: Int,
        allSetsAtTarget: Boolean,
        lastWeightKg: Double?,
        lastReps: Int?,
        weightStepKg: Double = WEIGHT_STEP_KG,
        /** Consecutive earlier sessions that failed at this same load. */
        stalls: Int = 0,
        /** What kind of progress this movement can honestly show; LOAD is the engine as it always was. */
        lever: Lever = Lever.LOAD,
        /** Consecutive trials cleared, the latest included: rotates the cue of a movement with several levers. */
        clearedRun: Int = 0,
        /** The unit the advice is worded in; lb moves loads on the lb grid, so "225 lb" follows "220.5 lb". */
        unit: LoadUnit = LoadUnit.KG,
        /** The vest and belt this lift is loaded with ([WornLoad.forLift]), or null: the step above, as ever. */
        worn: LoadKit? = null,
        /**
         * The reps (seconds, for a hold) the lifter set for this lift: when present it is the band, and the lift
         * climbs it as double progression whatever the training mode. [targetReps] is then its floor.
         */
        range: IntRange? = null,
    ): Recommendation {
        val band = range ?: repBand(mode, targetReps)
        val weight = lastWeightKg
        val step = weightStepKg.coerceAtLeast(0.5)
        // A hold's figure is seconds: never quote it as reps.
        val hold = lever == Lever.HOLD

        if (setsDone <= 0 || lastReps == null) {
            val reason = if (hold) UiText.res(R.string.progression_first_hold, targetReps)
            else UiText.plural(R.plurals.progression_first_reps, targetReps)
            return Recommendation(weight, targetReps, Words.text(reason))
        }
        if (!allSetsAtTarget || setsDone < minSets) {
            // Grinding a load you have already failed three times is how people
            // stay stuck: back it off and climb again.
            if (stalls + 1 >= STALLS_BEFORE_DELOAD && weight != null && weight > 0.0) {
                val deloaded = weight * (1 - DELOAD_FRACTION)
                val backedOff = worn?.roundKg(deloaded, unit) ?: roundToStep(deloaded, step, unit)
                val count = stalls + 1
                val load = Loads.withUnit(backedOff, unit)
                val reason = if (hold) {
                    UiText.themedPlural(R.plurals.progression_stalled_hold, R.plurals.progression_stalled_hold__t, count, count, load, targetReps)
                } else {
                    UiText.themedPlural(R.plurals.progression_stalled_reps, R.plurals.progression_stalled_reps__t, count, count, load, band.first)
                }
                return Recommendation(
                    backedOff,
                    if (hold) targetReps else band.first,
                    Words.text(reason),
                    deload = true,
                    changed = true,
                )
            }
            return Recommendation(
                weight,
                targetReps,
                Words.text(
                    UiText.plural(
                        if (hold) R.plurals.progression_repeat_hold else R.plurals.progression_repeat_reps,
                        minSets,
                        minSets,
                        targetReps,
                    ),
                ),
            )
        }
        // Cleared. Where load is not the lever, the cue names what is, and the
        // load and reps stay as they were.
        val cue = Math.max(clearedRun - 1, 0) % 3
        when (lever) {
            Lever.CONTROL -> return Recommendation(
                weight, targetReps,
                clearedCue(
                    cue,
                    R.string.progression_cleared_control_1 to R.string.progression_cleared_control_1__t,
                    R.string.progression_cleared_control_2 to R.string.progression_cleared_control_2__t,
                    R.string.progression_cleared_control_3 to R.string.progression_cleared_control_3__t,
                ),
                changed = true,
                cueOnly = true,
            )
            Lever.MOBILITY -> return Recommendation(
                weight, targetReps,
                clearedCue(
                    cue,
                    R.string.progression_cleared_mobility_1 to R.string.progression_cleared_mobility_1__t,
                    R.string.progression_cleared_mobility_2 to R.string.progression_cleared_mobility_2__t,
                    R.string.progression_cleared_mobility_3 to R.string.progression_cleared_mobility_3__t,
                ),
                changed = true,
                cueOnly = true,
            )
            Lever.SKILL -> return Recommendation(
                weight, targetReps,
                clearedCue(0, R.string.progression_cleared_skill to R.string.progression_cleared_skill__t),
                changed = true,
                cueOnly = true,
            )
            Lever.HOLD -> {
                val grown = (lastReps + HOLD_STEP_SECONDS).coerceAtMost(range?.last ?: Int.MAX_VALUE)
                return if (grown > lastReps) {
                    Recommendation(
                        weight,
                        grown,
                        clearedCue(
                            0,
                            R.string.progression_cleared_hold_longer to R.string.progression_cleared_hold_longer__t,
                            args = arrayOf<Any>(grown - lastReps),
                        ),
                        changed = true,
                    )
                } else {
                    Recommendation(
                        weight,
                        lastReps,
                        clearedCue(
                            0,
                            R.string.progression_cleared_hold_top to R.string.progression_cleared_hold_top__t,
                            args = arrayOf<Any>(lastReps),
                        ),
                    )
                }
            }
            Lever.LOAD, Lever.REPS_THEN_LOAD -> Unit
        }
        // Bodyweight work progresses in reps. Adding a step to a null load
        // turned a cleared 5×6 push-up into "2.5 kg × 3": a vest or belt the
        // lifter may not own, prescribed on a lift they never loaded.
        if (weight == null || weight <= 0.0) {
            // The lifter's own range is the ceiling when there is one, else the bodyweight rep ceiling.
            val ceiling = range?.last ?: if (lever == Lever.REPS_THEN_LOAD) BODYWEIGHT_REP_CEILING else null
            if (ceiling != null && lastReps >= ceiling) {
                return Recommendation(
                    weight, lastReps,
                    Words.text(
                        UiText.res(
                            R.string.progression_reps_high,
                            Loads.withUnit(worn?.firstStepKg(unit) ?: firstStepKg(unit), unit),
                        ),
                    ),
                    changed = true,
                    cueOnly = true,
                )
            }
            val climbed = lastReps + REP_STEP
            val nextReps = if (ceiling != null) climbed.coerceAtMost(ceiling) else climbed
            return Recommendation(weight, nextReps, Words.text(UiText.res(R.string.progression_climb_reps, nextReps)), changed = true)
        }
        // Null when the vest is full and no belt carries the load on: it holds and reps do the climbing.
        val loaded = if (worn != null) worn.nextKg(weight, unit) else nextLoad(weight, step, unit)
        val addedLabel = loaded?.let { Loads.withUnit(it - weight, unit) }.orEmpty()
        return when (if (range != null) TrainingMode.HYPERTROPHY else mode) {
            TrainingMode.STRENGTH -> if (loaded == null) {
                heldAtVestMax(weight, lastReps, worn, unit)
            } else {
                val nextReps = band.first
                Recommendation(
                    loaded,
                    nextReps,
                    Words.text(UiText.res(R.string.progression_add_drop, addedLabel, nextReps)),
                    changed = true,
                )
            }
            TrainingMode.HYPERTROPHY ->
                if (lastReps >= band.last) {
                    if (loaded == null) {
                        heldAtVestMax(weight, lastReps, worn, unit)
                    } else {
                        Recommendation(
                            loaded,
                            targetReps,
                            Words.text(UiText.res(R.string.progression_ceiling_add, addedLabel, targetReps)),
                            changed = true,
                        )
                    }
                } else {
                    val nextReps = (lastReps + REP_STEP).coerceAtMost(band.last)
                    Recommendation(weight, nextReps, Words.text(UiText.res(R.string.progression_same_load, nextReps)), changed = true)
                }
        }
    }

    /**
     * A cleared trial on a worn load that cannot go up: the vest is full and no belt carries it on. The load
     * holds and reps climb as bodyweight reps do, to [BODYWEIGHT_REP_CEILING], then a harder variation.
     */
    private fun heldAtVestMax(weight: Double, lastReps: Int, worn: LoadKit?, unit: LoadUnit): Recommendation {
        val top = Loads.withUnit(worn?.vestMaxKg ?: weight, unit)
        if (lastReps >= BODYWEIGHT_REP_CEILING) {
            return Recommendation(
                weight, lastReps, Words.text(UiText.res(R.string.progression_vest_full_harder, top)), changed = true,
                cueOnly = true,
            )
        }
        val nextReps = (lastReps + REP_STEP).coerceAtMost(BODYWEIGHT_REP_CEILING)
        return Recommendation(
            weight,
            nextReps,
            Words.text(UiText.res(R.string.progression_vest_full_climb, top, nextReps)),
            changed = true,
        )
    }

    /** The cleared-trial cue at [cue] of [options] (plain and themed wording of each), with [args] filled in. */
    private fun clearedCue(cue: Int, vararg options: Pair<Int, Int>, args: Array<Any> = emptyArray()): String {
        val (plain, themed) = options[cue.coerceIn(0, options.lastIndex)]
        return Words.text(UiText.themed(plain, themed, *args))
    }

    /**
     * The next load a lifter can actually pick up: one step on, then down onto
     * the 2.5 kg grid dumbbells and plate pairs come in (the step's own grid
     * when it is finer). 16 kg + 2.5 kg is 18.5 kg, which no rack holds; this
     * gives 17.5 kg. Always at least one grid point above the current load.
     */
    private fun nextLoad(weightKg: Double, step: Double, unit: LoadUnit = LoadUnit.KG): Double {
        if (unit == LoadUnit.LB) return Loads.stepUp(weightKg, unit, Loads.lbGrid(step), WEIGHT_STEP_KG)
        val grid = minOf(step, WEIGHT_STEP_KG)
        val snapped = Math.floor((weightKg + step) / grid + 1e-9) * grid
        val oneUp = (Math.floor(weightKg / grid + 1e-9) + 1) * grid
        return maxOf(snapped, oneUp)
    }

    /** The next load up from [weightKg] for a lift with the progression [stepKg]: where a top set climbs to when its reps are full. */
    fun nextLoadKg(weightKg: Double, stepKg: Double, unit: LoadUnit): Double = nextLoad(weightKg, stepKg.coerceAtLeast(0.5), unit)

    /** The smallest bar jump the first loaded step of a bodyweight movement is quoted as, in kg. */
    private fun firstStepKg(unit: LoadUnit): Double =
        if (unit == LoadUnit.LB) Loads.toKg(Loads.lbGrid(WEIGHT_STEP_KG), unit) else WEIGHT_STEP_KG

    /** Keeps deloaded loads on the bar's real increments. */
    private fun roundToStep(weightKg: Double, step: Double, unit: LoadUnit = LoadUnit.KG): Double {
        if (unit == LoadUnit.LB) {
            val grid = Loads.lbGrid(step)
            return Loads.toKg((Math.round(Loads.snap(weightKg, unit) / grid) * grid).coerceAtLeast(grid), unit)
        }
        return (Math.round(weightKg / step) * step).coerceAtLeast(step)
    }

    /**
     * Recommendation from logged history, newest session first. Earlier
     * sessions are only read to count stalls at the current load.
     */
    fun fromSessions(
        mode: TrainingMode,
        targetReps: Int,
        minSets: Int,
        sessions: List<List<Attempt>>,
        muscleGroup: String = "",
        exerciseName: String = "",
        lever: Lever = Lever.LOAD,
        unit: LoadUnit = LoadUnit.KG,
        /** The lifter's vest and belt; applied only to the movements that take them. */
        kit: LoadKit? = null,
        /** The lifter's own rep (or seconds) range for the lift; replaces the mode's band, its floor the target. */
        range: IntRange? = null,
    ): Recommendation {
        val target = range?.first ?: targetReps
        val latest = sessions.firstOrNull().orEmpty()
        val last = latest.lastOrNull()
        // "Cleared" stays what it always was: every prescribed set at target.
        val cleared = { sets: List<Attempt> ->
            sets.isNotEmpty() && sets.all { it.reps >= target }
        }
        val stalls = sessions
            .drop(1)
            .takeWhile { earlier ->
                earlier.isNotEmpty() &&
                    earlier.lastOrNull()?.weightKg == last?.weightKg &&
                    !cleared(earlier)
            }
            .count()
        val clearedRun = sessions.takeWhile { cleared(it) }.count()
        return next(
            mode = mode,
            targetReps = target,
            setsDone = latest.size,
            minSets = minSets,
            allSetsAtTarget = cleared(latest),
            lastWeightKg = last?.weightKg,
            lastReps = last?.reps,
            weightStepKg = weightStepKg(muscleGroup, exerciseName),
            stalls = stalls,
            lever = lever,
            clearedRun = clearedRun,
            unit = unit,
            worn = WornLoad.forLift(kit, exerciseName, lever),
            range = range,
        )
    }

    /** Single-session convenience wrapper. */
    fun fromSets(
        mode: TrainingMode,
        targetReps: Int,
        minSets: Int,
        sets: List<Attempt>,
        muscleGroup: String = "",
        exerciseName: String = "",
        lever: Lever = Lever.LOAD,
        unit: LoadUnit = LoadUnit.KG,
        kit: LoadKit? = null,
        range: IntRange? = null,
    ): Recommendation = fromSessions(mode, targetReps, minSets, listOf(sets), muscleGroup, exerciseName, lever, unit, kit, range)
}
