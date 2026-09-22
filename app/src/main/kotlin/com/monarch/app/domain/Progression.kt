package com.monarch.app.domain

enum class TrainingMode {
    STRENGTH,
    HYPERTROPHY,
}

/**
 * Progressive overload, two schools — each with its own rep band, load step and
 * rest, plus a shared stall rule so a plateau is met with a deload instead of
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

    /** Failed sessions at the same load before the engine backs the weight off. */
    const val STALLS_BEFORE_DELOAD = 3

    /** How much load comes off on a deload. */
    const val DELOAD_FRACTION = 0.10

    data class Attempt(val weightKg: Double?, val reps: Int)

    data class Recommendation(
        val weightKg: Double?,
        val reps: Int,
        val reason: String,
        /** Rest between sets for this school of training. */
        val restSeconds: Int = 120,
        /** True when the engine backed the load off after repeated stalls. */
        val deload: Boolean = false,
    )

    /**
     * The rep band each school works in, anchored on the preset's target.
     * Strength trains under the target, hypertrophy climbs above it.
     */
    fun repBand(mode: TrainingMode, targetReps: Int): IntRange = when (mode) {
        TrainingMode.STRENGTH -> (targetReps - 2).coerceAtLeast(3)..targetReps.coerceAtLeast(4)
        TrainingMode.HYPERTROPHY -> targetReps..(targetReps + 4)
    }

    /** Rest is part of the prescription: heavy singles need it, pump work does not. */
    fun restSeconds(mode: TrainingMode): Int = when (mode) {
        TrainingMode.STRENGTH -> 180
        TrainingMode.HYPERTROPHY -> 90
    }

    /**
     * Load step scales with the movement: legs/compounds 5 kg, standard
     * 2.5 kg, isolation 1.25 kg - but only where the hunter can actually make
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
    ): Recommendation {
        val band = repBand(mode, targetReps)
        val rest = restSeconds(mode)
        val weight = lastWeightKg
        val step = weightStepKg.coerceAtLeast(0.5)
        val stepLabel = if (step % 1.0 == 0.0) step.toInt().toString() else step.toString()

        if (setsDone <= 0 || lastReps == null) {
            return Recommendation(weight, targetReps, "First attempt — hit $targetReps reps every set", rest)
        }
        if (!allSetsAtTarget || setsDone < minSets) {
            // Grinding a load you have already failed three times is how people
            // stay stuck: back it off and climb again.
            if (stalls + 1 >= STALLS_BEFORE_DELOAD && weight != null && weight > 0.0) {
                val backedOff = roundToStep(weight * (1 - DELOAD_FRACTION), step)
                return Recommendation(
                    backedOff,
                    band.first,
                    "Stalled ${stalls + 1} sessions — deload to $backedOff kg, rebuild from ${band.first} reps",
                    rest,
                    deload = true,
                )
            }
            return Recommendation(
                weight,
                targetReps,
                "Repeat until every one of the $minSets sets reaches $targetReps reps",
                rest,
            )
        }
        return when (mode) {
            TrainingMode.STRENGTH -> {
                val nextReps = band.first
                Recommendation(
                    (weight ?: 0.0) + step,
                    nextReps,
                    "Target cleared — add $stepLabel kg, drop to $nextReps reps",
                    rest,
                )
            }
            TrainingMode.HYPERTROPHY ->
                if (lastReps >= band.last) {
                    Recommendation(
                        (weight ?: 0.0) + step,
                        targetReps,
                        "Rep ceiling reached — add $stepLabel kg, back to $targetReps reps",
                        rest,
                    )
                } else {
                    val nextReps = (lastReps + REP_STEP).coerceAtMost(band.last)
                    Recommendation(weight, nextReps, "Same load — climb to $nextReps reps", rest)
                }
        }
    }

    /** Keeps deloaded loads on the bar's real increments. */
    private fun roundToStep(weightKg: Double, step: Double): Double =
        (Math.round(weightKg / step) * step).coerceAtLeast(step)

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
    ): Recommendation {
        val latest = sessions.firstOrNull().orEmpty()
        val last = latest.lastOrNull()
        // "Cleared" stays what it always was: every prescribed set at target.
        val cleared = { sets: List<Attempt> ->
            sets.isNotEmpty() && sets.all { it.reps >= targetReps }
        }
        val stalls = sessions
            .drop(1)
            .takeWhile { earlier ->
                earlier.isNotEmpty() &&
                    earlier.lastOrNull()?.weightKg == last?.weightKg &&
                    !cleared(earlier)
            }
            .count()
        return next(
            mode = mode,
            targetReps = targetReps,
            setsDone = latest.size,
            minSets = minSets,
            allSetsAtTarget = cleared(latest),
            lastWeightKg = last?.weightKg,
            lastReps = last?.reps,
            weightStepKg = weightStepKg(muscleGroup, exerciseName),
            stalls = stalls,
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
    ): Recommendation = fromSessions(mode, targetReps, minSets, listOf(sets), muscleGroup, exerciseName)
}
