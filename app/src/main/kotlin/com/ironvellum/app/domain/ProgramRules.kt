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
    @Deprecated("Coverage is judged against COVERED_MIN and HEAVY_WEEK; the aim comes from setRange and startingSets.")
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
    @Deprecated("Use coverage(): a muscle is NOT_TRAINED, BELOW_MINIMUM or COVERED, and heavy past HEAVY_WEEK.")
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

    /**
     * Epley estimate of a one-rep max from [loadKg] for [reps]. The one copy:
     * the strength profile, the lift boards and the Ledger's records board
     * all read it, so they cannot disagree. Callers bound [reps] themselves
     * ([MAX_E1RM_REPS]).
     */
    fun epley(loadKg: Double, reps: Int): Double = loadKg * (1.0 + reps / 30.0)

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
            val e1rm = epley(lift.weightKg, lift.reps)
            val key = lift.exerciseName.trim().lowercase()
            if (e1rm > (best[key] ?: 0.0)) best[key] = e1rm
        }
        return StrengthProfile(best)
    }

    /**
     * Working load for [reps] at [rir] reps in reserve, straight from the
     * lifter's own e1RM: e1RM / (1 + (reps + rir) / 30) - the Epley inversion
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
        /** The vest and belt a worn-load lift is done in ([WornLoad.forLift]); null floors to the usual step. */
        worn: LoadKit? = null,
    ): Pair<Double, String>? {
        val e1rm = strength.bestE1rmKg[exerciseName.trim().lowercase()] ?: return null
        return loadFromE1rm(exerciseName, muscleGroup, e1rm, reps, rir, worn) to
            "from your ${maxE1rmLabel(e1rm)} kg estimated 1-rep max"
    }

    private fun maxE1rmLabel(e1rm: Double): String =
        if (e1rm % 1.0 == 0.0) e1rm.toInt().toString() else "%.1f".fmt(e1rm)

    private fun loadFromE1rm(
        exerciseName: String,
        muscleGroup: String,
        e1rm: Double,
        reps: Int,
        rir: Int,
        worn: LoadKit? = null,
    ): Double {
        val raw = e1rm / (1.0 + (reps + rir) / 30.0)
        // The vest's own blocks, and never past its top when no belt carries the load on.
        if (worn != null) {
            val top = worn.vestMaxKg?.takeIf { !worn.dipBelt }
            return worn.floorKg(if (top != null) minOf(raw, top) else raw)
        }
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
        worn: LoadKit? = null,
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
            val load = loadFromE1rm(exerciseName, muscleGroup, marked, reps, rir, worn)
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

    // Rest per movement lives in RestRules (Schoenfeld 2016: long rest for heavy
    // loaded work; Singer 2024: growth benefit above ~60 s, flat near 90 s).

    /**
     * Reps in reserve the LOAD is computed for: compounds stop about 2 short
     * of failure (Zourdos 2016: trust the RIR report near failure; Robinson
     * 2024, Refalo 2023: failure adds little and costs recovery on big
     * lifts), single-joint work is cheap to take to failure (Refalo 2023).
     * The lifter sees the same split as an [EffortCue].
     */
    fun loadRir(compound: Boolean): Int = if (compound) 2 else 0

    /** The lifter's own movements-per-workout ceiling: 5 by default, 3 to 8 allowed. */
    const val DEFAULT_MAX_EXERCISES = 5
    val MAX_EXERCISES_RANGE = 3..8

    /** Hard safety cap on movements in one workout, whatever any setting says. */
    const val MOVEMENT_SAFETY_CAP = 10

    /**
     * Movements one session may hold: the lifter's own [maxExercises]
     * (clamped to [MAX_EXERCISES_RANGE]), under the safety cap. The time
     * budget ([sessionBudgetSeconds]) is what limits a session's work; this
     * is only the count the lifter asked for. Every generator and template
     * path reads this.
     */
    fun movementCap(maxExercises: Int): Int =
        maxExercises.coerceIn(MAX_EXERCISES_RANGE).coerceAtMost(MOVEMENT_SAFETY_CAP)

    /**
     * Sets no entry may carry whatever its range says: a sanity ceiling for
     * hand-edited or scaled prescriptions, not a dose. The per-movement
     * ceilings are gone; [setRange] is where the dose lives.
     */
    const val ENTRY_SANITY_MAX_SETS = 6

    /**
     * Most sets one entry may carry: [COMPOUND_MAX_SETS] for bilateral
     * compound and trunk work, [SMALL_MAX_SETS] for single-joint and
     * unilateral work (a set of single-leg calf raises is a set per leg).
     * Trunk work is not single-joint, the same line
     * [MovementDifficulty.isIsolation] draws: bracing the whole chain is not
     * a limb moving about one joint. Unprofiled movements count as compounds. Every generator, improve and template
     * path clamps and grows through this, so no path can stack a sixth set
     * of calf raises to chase a weekly target.
     *
     * PRACTICAL HEURISTIC, not a measured dose: per-session volume shows
     * diminishing returns well before a muscle's whole weekly dose
     * (Remmert 2025: ~11 fractional sets per session for hypertrophy), and
     * the position stand doses multiple sets per exercise, 1-3 for novices
     * and 3-6 for advanced lifters (ACSM 2009). Five keeps the compounds
     * inside that; small muscles and one-side-at-a-time work fatigue
     * fastest and cost twice the clock, so they stop a set earlier and the
     * rest of the weekly dose moves to another movement or day, or the plan
     * says the muscle is short.
     */
    fun maxSetsPerEntry(exerciseName: String): Int {
        val profile = MuscleMap.profile(exerciseName) ?: return COMPOUND_MAX_SETS
        val singleJoint = !profile.compound && profile.pattern != MovementPattern.CORE
        return if (singleJoint || profile.unilateral) SMALL_MAX_SETS else COMPOUND_MAX_SETS
    }

    const val COMPOUND_MAX_SETS = 5
    const val SMALL_MAX_SETS = 4

    /**
     * Fractional sets one session may credit a tracked muscle before growth
     * stops adding to it there: one under the ~11-set point past which more
     * per-session volume showed no detectable extra growth (Remmert 2025).
     * A gate on GROWTH only - the backbone and the lifter's own entries are
     * never cut for it - so a deficit the gate refuses moves to another
     * session, or lands in the plan note as short.
     */
    const val SESSION_MUSCLE_CEILING = 10.0 // same figure as HEAVY_SESSION

    /**
     * Whether [sets] more sets of [exerciseName] keep every muscle it trains
     * (the coverage map's heavy flag looks at all of them) at or under
     * [SESSION_MUSCLE_CEILING] in the session [entries].
     */
    fun sessionMuscleRoom(entries: List<PlannedEntry>, exerciseName: String, sets: Int): Boolean {
        val profile = MuscleMap.profile(exerciseName) ?: return true
        return Muscle.entries.all { muscle ->
            val share = profile.muscles[muscle] ?: 0.0
            if (share <= 0.0) return@all true
            val now = entries.sumOf { (MuscleMap.profile(it)?.muscles?.get(muscle) ?: 0.0) * it.sets }
            now + sets * share <= SESSION_MUSCLE_CEILING + 1e-9
        }
    }

    /**
     * Time under load for one working set, added to the prescribed rest to
     * give the clock time a set costs. 8-12 reps at a controlled tempo.
     */
    const val SET_WORK_SECONDS = 40

    /**
     * Clock time one working set of a named movement costs: its work plus
     * the READY end of its rest window ([RestRules]), the time the lifter
     * actually rests. A unilateral set works each side in turn, so it pays
     * the work twice and the rest once. Unprofiled movements rest as loaded
     * compounds, bodyweight ones when [weighted] says they carry no load;
     * [metric] is the catalogue metric when known.
     */
    fun setSeconds(
        focus: TrainingFocus,
        exerciseName: String,
        metric: ExerciseMetric? = null,
        weighted: Boolean? = null,
    ): Int {
        val sides = if (MuscleMap.profile(exerciseName)?.unilateral == true) 2 else 1
        return RestRules.readySeconds(exerciseName, focus, metric, weighted) + sides * SET_WORK_SECONDS
    }

    /**
     * Session time ceiling, warm-up excluded. PRACTICAL HEURISTIC: the brief
     * lists no verified per-session ceiling; only the weekly dose (rule 6) is
     * evidence-backed. The limit is TIME, not a set count, because a set
     * costs its rest: a 45 s lateral-raise rest is a quarter of a 180 s squat
     * rest ([RestRules]), and a flat 24-set cap charged both the same - which
     * left a 4-day intermediate week unable to reach 12 sets on its upper
     * muscles while the sessions still finished early. When the week's dose
     * still does not fit the chosen days, the generator lands muscles at the
     * reachable level and says so in the plan note instead of cramming.
     */
    const val SESSION_BUDGET_SECONDS = 75 * 60

    /** Estimated clock time of a session; unprofiled movements count as compounds. */
    fun sessionSeconds(entries: List<PlannedEntry>, focus: TrainingFocus): Int =
        entries.sumOf { it.sets * setSeconds(focus, it.exerciseName, weighted = it.targetWeightKg != null) }

    // ------------------------------------------------------- effort-first model

    /**
     * Hard sets a muscle needs in a week before the plan counts it as covered.
     * A convention anchored on the low end of the dose-response curve
     * (Pelland 2026), not a trial result: fewer than this is a maintenance dose.
     */
    const val COVERED_MIN = 4.0

    /** Fractional hard sets in a week past which a muscle is flagged heavy. Soft: coverage wins a conflict. */
    const val HEAVY_WEEK = 20.0

    /** Fractional hard sets in one session past which a muscle is flagged heavy (Remmert 2025). Soft. */
    const val HEAVY_SESSION = 10.0

    /** A muscle's weekly hard sets are covered from [COVERED_MIN] and heavy past [HEAVY_WEEK]. */
    val COVERAGE_BAND: ClosedFloatingPointRange<Double> = COVERED_MIN..HEAVY_WEEK

    /**
     * Sets one movement may carry at a volume level: compounds LOW 2-3,
     * STANDARD 2-4, HIGH 3-5; single-joint work tops out one set lower. A
     * practical heuristic (ACSM 2009: 1-3 sets for novices, 3-6 advanced),
     * not a measured dose.
     */
    fun setRange(volume: VolumeLevel, compound: Boolean): IntRange {
        val compoundRange = when (volume) {
            VolumeLevel.LOW -> 2..3
            VolumeLevel.STANDARD -> 2..4
            VolumeLevel.HIGH -> 3..5
        }
        if (compound) return compoundRange
        val top = compoundRange.last - 1
        return minOf(compoundRange.first, top)..top
    }

    /**
     * Where a new movement starts inside its [setRange]: LOW at the floor,
     * STANDARD at the midpoint (rounded up), HIGH at the top. A [priority]
     * muscle always starts at the top.
     */
    fun startingSets(volume: VolumeLevel, compound: Boolean, priority: Boolean): Int {
        val range = setRange(volume, compound)
        if (priority) return range.last
        return when (volume) {
            VolumeLevel.LOW -> range.first
            VolumeLevel.STANDARD -> (range.first + range.last + 1) / 2
            VolumeLevel.HIGH -> range.last
        }
    }

    /**
     * Per-muscle weekly and busiest-session fractional hard sets of [presets],
     * for every [Muscle] (untouched ones read NOT_TRAINED).
     */
    fun coverage(presets: List<PlannedPreset>): Map<Muscle, MuscleLoad> {
        val weekly = HardSets.planned(presets)
        val busiest = mutableMapOf<Muscle, Double>()
        for (preset in presets) {
            for ((muscle, sets) in HardSets.planned(listOf(preset))) {
                if (sets > (busiest[muscle] ?: 0.0)) busiest[muscle] = sets
            }
        }
        return Muscle.entries.associateWith { MuscleLoad(it, weekly[it] ?: 0.0, busiest[it] ?: 0.0) }
    }

    /** The lifter's per-workout time choices, in minutes. */
    val SESSION_MINUTES_OPTIONS = listOf(45, 60, 75, 90)
    const val DEFAULT_SESSION_MINUTES = 75

    /** The clock a session of [minutes] may use; 75 is the old [SESSION_BUDGET_SECONDS]. */
    fun sessionBudgetSeconds(minutes: Int): Int = minutes.coerceIn(30, 120) * 60

    /** Clock time of one prescribed movement: its sets at the set clock of [focus]. */
    fun movementSeconds(entry: PlannedEntry, focus: TrainingFocus = TrainingFocus.GENERAL): Int =
        entry.sets * setSeconds(focus, entry.exerciseName, weighted = entry.targetWeightKg != null)

    /** Whole minutes (rounded up) a preset takes, warm-up excluded. */
    fun dayMinutes(preset: PlannedPreset, focus: TrainingFocus = TrainingFocus.GENERAL): Int =
        (preset.entries.sumOf { movementSeconds(it, focus) } + 59) / 60

    /**
     * The rep window shown per focus and kind of movement: muscle 6-10 /
     * 10-15, strength 3-6 / 8-12, general (and skill) 5-8 / 10-15 - heavier
     * for compounds, lighter for single-joint work, which is cheap to take
     * near failure (Schoenfeld 2017: growth holds across 5-30 reps).
     */
    fun repWindow(focus: TrainingFocus, compound: Boolean): IntRange = when (focus) {
        TrainingFocus.MUSCLE -> if (compound) 6..10 else 10..15
        TrainingFocus.STRENGTH -> if (compound) 3..6 else 8..12
        TrainingFocus.GENERAL, TrainingFocus.SKILL -> if (compound) 5..8 else 10..15
    }

    /**
     * [entry] with the planning fields the lifter sees filled from the level
     * and focus: the set range (widened to hold the planned [PlannedEntry.sets]),
     * the effort cue (compounds 0-2 reps in reserve, single-joint work to
     * failure) and the rep window (widened to hold the anchor [PlannedEntry.reps]).
     * Unprofiled movements count as compounds, as everywhere else. The one
     * place the generator and the templates write these fields.
     */
    fun withPlan(entry: PlannedEntry, volume: VolumeLevel, focus: TrainingFocus): PlannedEntry {
        val compound = MuscleMap.profile(entry)?.compound ?: true
        val sets = setRange(volume, compound)
        val reps = repWindow(focus, compound)
        return entry.copy(
            setsMin = minOf(sets.first, entry.sets),
            setsMax = maxOf(sets.last, entry.sets),
            effort = if (compound) EffortCue.RIR_0_2 else EffortCue.TO_FAILURE,
            repLow = minOf(reps.first, entry.reps),
            repHigh = maxOf(reps.last, entry.reps),
        )
    }
}

/** The plain default way a prescription reads; the UI owns its final copy. */
object Prescription {
    fun line(entry: PlannedEntry): String {
        val sets = if (entry.setsMin == entry.setsMax) "${entry.sets} sets" else "${entry.setsMin}-${entry.setsMax} sets"
        val reps = if (entry.repLow == entry.repHigh) "${entry.repLow} reps" else "${entry.repLow}-${entry.repHigh} reps"
        val effort = when (entry.effort) {
            EffortCue.RIR_0_2 -> "0-2 reps in reserve"
            EffortCue.TO_FAILURE -> "last set to failure"
        }
        return "$sets x $reps, $effort"
    }
}
