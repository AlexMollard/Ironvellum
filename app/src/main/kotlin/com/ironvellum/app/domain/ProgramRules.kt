package com.ironvellum.app.domain

import kotlin.math.floor

/**
 * The evidence-backed constants the generator, the templates and the improve
 * pass share: weekly volume tiers, RIR and rest prescriptions, the tracked
 * muscle set, and the load arithmetic that turns the lifter's own e1RMs into
 * working weights.
 *
 * Every constant names the [Evidence] entry behind it. The volume tier BOUNDARIES
 * (8-12 / 12-18 / 15-22) are informed interpolations on the diminishing-returns
 * curves of Pelland 2026, not directly tested values - the brief says so
 * plainly, and so does this file.
 */
object ProgramRules {

    // ---------------------------------------------------------------- volume

    /**
     * Fractional weekly hard sets per muscle, per volume level and goal
     * (low 8-12, standard 12-18, high 15-22; strength focus 5-15 mostly
     * direct on the practised lift because the strength dose saturates early
     * - Pelland 2026, Ralston 2017). The levels come from the evidence's
     * training-age tiers (ACSM 2009 / Moesgaard 2022: novices progress on low
     * volume; periodisation adds little for hypertrophy).
     */
    fun weeklySetTarget(tier: VolumeLevel, focus: TrainingFocus): ClosedFloatingPointRange<Double> {
        if (focus == TrainingFocus.STRENGTH || focus == TrainingFocus.SKILL) return 5.0..15.0
        return when (tier) {
            VolumeLevel.LOW -> 8.0..12.0
            VolumeLevel.STANDARD -> 12.0..18.0
            VolumeLevel.HIGH -> 15.0..22.0
        }
    }

    /** GENERAL trains like a hypertrophy block with strength-style compounds. */
    val TRACKED: List<Muscle> = listOf(
        Muscle.MID_CHEST,
        Muscle.LATS,
        Muscle.RHOMBOIDS,
        Muscle.SIDE_DELTS,
        Muscle.REAR_DELTS,
        Muscle.BICEPS,
        Muscle.TRICEPS,
        Muscle.QUADS,
        Muscle.HAMSTRINGS,
        Muscle.GLUTES,
        Muscle.CALVES,
        Muscle.ABS,
    )

    /**
     * Muscles judged against the tier range are the "major" set. The
     * HELPERS are mostly worked as synergists or stabilisers - front delts
     * in every press (0.5-0.7 per the press family, Lanza 2024), forearms in
     * every grip, lower back bracing hinges and squats, adductors in squats
     * (Kubo 2019), the upper and lower chest beside the mid chest in every
     * press, traps in hinges and overhead lockouts, the rotator cuff and
     * serratus steering the shoulder blade, the brachialis in every curl and
     * pull, obliques and hip flexors in leg raises, the abductors
     * steadying single-leg work, and the tibialis against every calf raise -
     * and no study sets a weekly dose for any of them, so the full range
     * would pad weeks with front raises and wrist curls. They get a floor
     * instead: at least [HELPER_FLOOR_SETS] fractional sets a week, no
     * ceiling. The floor value is a convention, not a trial result.
     */
    val HELPERS: List<Muscle> = listOf(
        Muscle.FRONT_DELTS,
        Muscle.FOREARMS,
        Muscle.LOWER_BACK,
        Muscle.ADDUCTORS,
        Muscle.UPPER_CHEST,
        Muscle.LOWER_CHEST,
        Muscle.TRAPS,
        Muscle.SERRATUS,
        Muscle.OBLIQUES,
        Muscle.HIP_FLEXORS,
        Muscle.ROTATOR_CUFF,
        Muscle.TIBIALIS,
        Muscle.ABDUCTORS,
        Muscle.BRACHIALIS,
    )

    const val HELPER_FLOOR_SETS = 3.0

    /** The helpers' "range" for coverage verdicts: the floor, and no top. */
    val HELPER_RANGE: ClosedFloatingPointRange<Double> = HELPER_FLOOR_SETS..Double.MAX_VALUE

    /**
     * The range [muscle]'s weekly sets are judged against on the coverage
     * map. Helpers get their floor. A strength or skill week is judged 5-15,
     * where strength gains flatten (Pelland 2026) - but a muscle the lifter
     * [prioritised][priorities] keeps growing past that, so its ceiling is
     * the muscle-growth range's top at the same volume level: sixteen sets of
     * lats on a V-taper week is the point, not an overshoot. The floor and
     * the generator's targets are unchanged.
     */
    fun judgedRange(
        muscle: Muscle,
        volume: VolumeLevel,
        focus: TrainingFocus,
        priorities: Set<Muscle> = emptySet(),
    ): ClosedFloatingPointRange<Double> {
        if (muscle in HELPERS) return HELPER_RANGE
        val target = weeklySetTarget(volume, focus)
        if (muscle !in priorities) return target
        return target.start..maxOf(target.endInclusive, weeklySetTarget(volume, TrainingFocus.MUSCLE).endInclusive)
    }

    // ------------------------------------------------------------- fractions

    /**
     * Fractional weekly sets per muscle: direct sets count 1.0 per set,
     * indirect sets per their MuscleMap share (0.5 model - Pelland 2026;
     * movements without a profile contribute nothing, honestly).
     */
    fun weeklyVolume(presets: List<PlannedPreset>): Map<Muscle, Double> {
        val volume = mutableMapOf<Muscle, Double>()
        for (preset in presets) for (entry in preset.entries) {
            val profile = MuscleMap.profile(entry) ?: continue
            for ((muscle, share) in profile.muscles) {
                if (share > 0.0) volume.merge(muscle, entry.sets * share, Double::plus)
            }
        }
        return volume
    }

    /**
     * One exercise's part in a muscle's [weeklyVolume]: its sets in the week
     * times its [share] of that muscle. The same exercise with the same
     * modifiers on several days is one credit; different modifiers are
     * separate credits because their profiles differ.
     */
    data class MuscleCredit(
        val exerciseName: String,
        val modifiers: String,
        val sets: Int,
        val share: Double,
    ) {
        val credited: Double get() = sets * share
    }

    /**
     * [weeklyVolume] broken down by exercise: for each muscle, the credits
     * that add up to its total, largest first.
     */
    fun muscleCredits(presets: List<PlannedPreset>): Map<Muscle, List<MuscleCredit>> {
        val setsByEntry = linkedMapOf<Pair<String, String>, Int>()
        for (preset in presets) for (entry in preset.entries) {
            setsByEntry.merge(entry.exerciseName to entry.modifiers, entry.sets, Int::plus)
        }
        val credits = mutableMapOf<Muscle, MutableList<MuscleCredit>>()
        for ((key, sets) in setsByEntry) {
            val profile = MuscleMap.profile(key.first, key.second) ?: continue
            for ((muscle, share) in profile.muscles) {
                if (share > 0.0) credits.getOrPut(muscle) { mutableListOf() } += MuscleCredit(key.first, key.second, sets, share)
            }
        }
        return credits.mapValues { (_, list) -> list.sortedByDescending { it.credited } }
    }

    // ------------------------------------------------------------------ tier

    /**
     * Training age to a suggested level: under a year low, one to three
     * years standard, beyond that high (ACSM 2009 tiering; boundaries are
     * convention anchored on the diminishing-returns curves). No history
     * means low - a new lifter must never be offered a high dose by default.
     */
    fun suggestVolume(firstSessionEpochDay: Long?, todayEpochDay: Long): VolumeLevel {
        if (firstSessionEpochDay == null) return VolumeLevel.LOW
        val days = (todayEpochDay - firstSessionEpochDay).coerceAtLeast(0)
        return when {
            days < 365 -> VolumeLevel.LOW
            days < 1095 -> VolumeLevel.STANDARD
            else -> VolumeLevel.HIGH
        }
    }

    // ----------------------------------------------------------------- loads

    /** Reps above 12 stop flattering the Epley estimate (LeSuer 1997). */
    const val MAX_E1RM_REPS = 12

    /** Accuracy of the Epley inversion degrades past ~10 reps (LeSuer 1997). */
    const val MAX_WORKING_REPS = 10

    /**
     * Best estimated one-rep max per movement, in the MARKED kilos the lifter
     * logs, from working sets with real load and an honest rep count
     * (weight > 0, 1..12 reps - Epley with the rep term capped at 12, the
     * same convention TitleEngine uses, LeSuer 1997).
     */
    fun strengthProfile(lifts: List<LoggedLift>): StrengthProfile {
        val best = mutableMapOf<String, Double>()
        for (lift in lifts) {
            if (lift.weightKg <= 0.0) continue
            if (lift.reps < 1 || lift.reps > MAX_E1RM_REPS) continue
            val e1rm = lift.weightKg * (1.0 + lift.reps / 30.0)
            val key = lift.exerciseName.trim().lowercase()
            if (e1rm > (best[key] ?: 0.0)) best[key] = e1rm
        }
        return StrengthProfile(best)
    }

    /**
     * Working load for [reps] at [rir] reps in reserve, straight from the
     * lifter's own e1RM: e1RM x (1 - (reps + rir) / 30) - the Epley inversion
     * (Zourdos 2016 RIR scale; LeSuer 1997 validity; Helms 2016). Rounded
     * DOWN to the movement's real load step so the prescription is on the
     * bar's actual increments. Null when the lifter never logged the lift.
     */
    fun workingLoadKg(
        exerciseName: String,
        muscleGroup: String,
        strength: StrengthProfile,
        reps: Int,
        rir: Int,
    ): Pair<Double, String>? {
        val e1rm = strength.bestE1rmKg[exerciseName.trim().lowercase()] ?: return null
        return loadFromE1rm(exerciseName, muscleGroup, e1rm, reps, rir) to
            "from your ${maxE1rmLabel(e1rm)} kg estimated 1-rep max"
    }

    private fun maxE1rmLabel(e1rm: Double): String =
        if (e1rm % 1.0 == 0.0) e1rm.toInt().toString() else "%.1f".format(e1rm)

    private fun loadFromE1rm(
        exerciseName: String,
        muscleGroup: String,
        e1rm: Double,
        reps: Int,
        rir: Int,
    ): Double {
        val raw = e1rm * (1.0 - (reps + rir) / 30.0)
        val step = Progression.weightStepKg(muscleGroup, exerciseName)
        val floored = floor(raw / step) * step
        return floored.coerceAtLeast(step)
    }

    // ------------------------------------------------- related-lift estimates

    /**
     * One hop of the related-lift graph, as PAIRS: the second lift's FORCE
     * e1RM is roughly [ratio] x the first lift's. Ratios are marked-kilogram
     * independent: both sides run through MovementDifficulty.loadFactor, so
     * a sled's inflated numbers do not leak into the estimate.
     */
    private val related: List<Triple<String, String, Double>> = listOf(
        Triple("bench press", "incline bench press", 0.85),
        Triple("bench press", "dumbbell bench press", 0.80),
        Triple("bench press", "machine chest press", 0.90),
        Triple("bench press", "smith machine bench press", 0.95),
        Triple("bench press", "close-grip bench press", 0.85),
        Triple("bench press", "dip", 0.90),
        Triple("overhead press", "dumbbell shoulder press", 0.80),
        Triple("overhead press", "machine shoulder press", 0.85),
        Triple("overhead press", "arnold press", 0.80),
        Triple("overhead press", "smith machine overhead press", 0.95),
        Triple("overhead press", "push press", 1.20),
        Triple("back squat", "front squat", 0.85),
        Triple("back squat", "leg press", 1.20),
        Triple("back squat", "hack squat", 1.00),
        Triple("back squat", "smith machine squat", 1.00),
        Triple("back squat", "goblet squat", 0.50),
        Triple("back squat", "bulgarian split squat", 0.50),
        Triple("deadlift", "sumo deadlift", 0.95),
        Triple("deadlift", "rack pull", 1.20),
        Triple("deadlift", "romanian deadlift", 0.80),
        Triple("deadlift", "good morning", 0.70),
        Triple("barbell row", "dumbbell row", 0.80),
        Triple("barbell row", "pendlay row", 0.95),
        Triple("barbell row", "t-bar row", 0.95),
        Triple("barbell row", "seated cable row", 0.90),
        Triple("barbell row", "chest-supported row", 0.90),
        Triple("barbell row", "machine row", 0.90),
        Triple("barbell row", "smith machine row", 0.95),
    )

    /**
     * Estimated working load for a movement the lifter never logged, via a
     * logged relative (specificity: strength transfers only within a task -
     * Buckner 2017, TaskSpec 2025). The ratio runs in FORCE units (marked kg
     * x loadFactor), the result is shaved by a further safety margin because
     * the transfer is one-directional, converted back to this implement's
     * marked kilos, and rounded DOWN to the load step. The loadNote says
     * plainly where the number came from.
     */
    fun estimatedLoadKg(
        exerciseName: String,
        muscleGroup: String,
        strength: StrengthProfile,
        reps: Int,
        rir: Int,
    ): Pair<Double, String>? {
        val key = exerciseName.trim().lowercase()
        val ownFactor = MovementDifficulty.loadFactor(exerciseName)
        // Deterministic order: the table's own sequence, so the first logged
        // relative always wins and two logged relatives cannot tie-break.
        for ((a, b, ratio) in related) {
            val (source, target, r) =
                if (b == key) Triple(a, b, ratio)
                else if (a == key) Triple(b, a, 1.0 / ratio)
                else continue
            val sourceE1rm = strength.bestE1rmKg[source] ?: continue
            val sourceFactor = MovementDifficulty.loadFactor(source)
            val forceE1rm = sourceE1rm * sourceFactor
            val estimatedForce = forceE1rm * r * ESTIMATE_SAFETY_MARGIN
            val marked = estimatedForce / ownFactor
            val load = loadFromE1rm(exerciseName, muscleGroup, marked, reps, rir)
            val sourceLabel = source.split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            return load to "estimate from $sourceLabel"
        }
        return null
    }

    /**
     * Transferred strength is not owned strength: shave the estimate so the
     * first session on the new implement is always completable.
     */
    const val ESTIMATE_SAFETY_MARGIN = 0.90

    // ------------------------------------------------------------------- sex

    /**
     * The one line the preview shows for the sex question. Roberts 2020
     * (relative hypertrophy identical, ES 0.07 +- 0.16) and Hunter 2014
     * (fatigability, task- and muscle-specific) say the same thing: nothing
     * in the STRUCTURE of the plan may change by sex, so the generator takes
     * sex and deliberately ignores it.
     */
    const val SEX_NOTE: String =
        "Same cycle for men and women; loads scale off your own estimated 1-rep max (Roberts 2020; Hunter 2014)."

    // ------------------------------------------------------------------ rest

    /**
     * Rest in seconds for a working set: strength compounds 3-5 min (300 s
     * cap on the main lifts, Schoenfeld 2016, Grgic 2018), hypertrophy work
     * >=90 s with 2-3 min typical (Singer 2024: benefit above 60 s, plateau
     * near 90 s).
     */
    fun restSeconds(focus: TrainingFocus, compound: Boolean): Int = when {
        focus == TrainingFocus.STRENGTH && compound -> 300
        compound -> 150
        else -> 90
    }

    /**
     * Reps in reserve per focus. Hypertrophy lives at 1-3 RIR (Robinson 2024,
     * Refalo 2023: failure adds nothing and costs recovery); strength at ~2
     * RIR on the practice sets (Zourdos 2016: trust the RIR report near
     * failure); beginners get the conservative end (ACSM 2009: never program
     * failure for novices).
     */
    fun targetRir(tier: VolumeLevel, focus: TrainingFocus): Int = when {
        focus == TrainingFocus.MUSCLE && tier == VolumeLevel.LOW -> 3
        focus == TrainingFocus.MUSCLE -> 2
        else -> 2
    }

    /**
     * The rep anchor per focus: MUSCLE compounds 8, isolation 12 (inside the
     * 6-15 band, Lopez 2021); STRENGTH/GENERAL/SKILL compounds 5 (1-6 band at
     * >=80% 1RM, Lopez 2021).
     */
    fun repAnchor(focus: TrainingFocus, compound: Boolean): Int = when {
        focus == TrainingFocus.MUSCLE -> if (compound) 8 else 12
        else -> 5
    }

    /** Per-session exercise cap by volume level: 6 low, 8 standard, 9
     * high. Practical scheduling heuristic - the evidence brief lists no
     * verified per-session ceiling; it exists so a generated session stays
     * finishable in roughly an hour. */
    fun sessionCap(tier: VolumeLevel): Int = when (tier) {
        VolumeLevel.LOW -> 6
        VolumeLevel.STANDARD -> 8
        VolumeLevel.HIGH -> 9
    }

    /** The lifter's own movements-per-workout ceiling: 5 by default, 3 to 8 allowed. */
    const val DEFAULT_MAX_EXERCISES = 5
    val MAX_EXERCISES_RANGE = 3..8

    /**
     * Movements one session may hold: the volume level's [sessionCap], or
     * the lifter's own [maxExercises] when that is lower (clamped to
     * [MAX_EXERCISES_RANGE]). Every generator and template path reads this.
     */
    fun exerciseCap(tier: VolumeLevel, maxExercises: Int): Int =
        minOf(sessionCap(tier), maxExercises.coerceIn(MAX_EXERCISES_RANGE))

    /**
     * Time under load for one working set, added to the prescribed rest to
     * give the clock time a set costs. 8-12 reps at a controlled tempo.
     */
    const val SET_WORK_SECONDS = 40

    /** Clock time one working set costs: work plus its prescribed rest. */
    fun setSeconds(focus: TrainingFocus, compound: Boolean): Int =
        restSeconds(focus, compound) + SET_WORK_SECONDS

    /**
     * Session time ceiling, warm-up excluded. PRACTICAL HEURISTIC: the brief
     * lists no verified per-session ceiling; only the weekly dose (rule 6) is
     * evidence-backed. The limit is TIME, not a set count, because a set
     * costs its rest: a 90 s lateral-raise set (Singer 2024) is two-thirds of
     * a 150 s squat set, and a flat 24-set cap charged both the same - which
     * left a 4-day intermediate week unable to reach 12 sets on its upper
     * muscles while the sessions still finished early. When the week's dose
     * still does not fit the chosen days, the generator lands muscles at the
     * reachable level and says so in the plan note instead of cramming.
     */
    const val SESSION_BUDGET_SECONDS = 75 * 60

    /** Estimated clock time of a session; unprofiled movements count as compounds. */
    fun sessionSeconds(entries: List<PlannedEntry>, focus: TrainingFocus): Int =
        entries.sumOf { it.sets * setSeconds(focus, MuscleMap.profile(it.exerciseName)?.compound ?: true) }
}
