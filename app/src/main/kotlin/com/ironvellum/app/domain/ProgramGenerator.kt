package com.ironvellum.app.domain

import com.ironvellum.app.data.Words
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Proposes evidence-backed training weeks, single sessions, and improvements
 * to existing presets. Pure and dependency-free: it reads only the [Exercise]
 * list it is given plus the shared tables ([MuscleMap], [MovementDifficulty],
 * [Progression], [ProgramRules]); every number names its [Evidence].
 *
 * STRUCTURE - multi-joint backbone per pattern, then volume by deficit
 * (Gentil 2015, Paoli 2017: single-joint work adds little on its own, so it
 * is used only to close a tracked muscle's weekly deficit - Pelland 2026
 * fractional counting). EFFORT-FIRST: every tracked muscle aims at
 * [ProgramRules.COVERED_MIN] hard sets, a prioritised one (above LOW) at
 * twice that. Each movement starts at [ProgramRules.startingSets] inside its
 * [ProgramRules.setRange] and grows only to the top of that range; past it
 * the fill adds a movement or stops. Heavy ([ProgramRules.HEAVY_WEEK],
 * [ProgramRules.HEAVY_SESSION]) is avoided, except that reaching four wins.
 * Compounds are taken 0-2 reps from failure, single-joint work to failure.
 * The session clock is the lifter's own ([ProgramRequest.sessionMinutes]).
 *
 * EQUIPMENT PREDICATE, MILESTONE RULE, REDUNDANCY RULE, DEGRADE RULE -
 * ported unchanged from the deleted RoutineBuilder: claim-standard rows
 * ([MovementDifficulty.isLoadPriced]) and holds/activities are never
 * prescribed; within a session no muscle group mixes movement versions of
 * different equipment fit; a slot the catalogue cannot fill is dropped and
 * the plan shrinks honestly.
 *
 * SPLIT RULE - the same ISO days as before: 1-3 full-body days, 4 upper/lower
 * twice, 5 push/pull/legs plus an upper/lower pair, 6 PPL x2. Frequency: each
 * tracked muscle is spread over >=2 sessions when the days allow (Pelland
 * 2026 - frequency serves load quality, not extra hypertrophy).
 *
 * STRENGTH - the tested lift itself is practised, never only its muscles
 * (Buckner 2017, TaskSpec 2025): Back Squat, Bench Press, Deadlift, Overhead
 * Press, or the closest allowed equivalent under the equipment. Squat and
 * bench land >=2x/week (Grgic 2018), 3-5 reps at ~2 RIR with 3-5 min rest
 * (Lopez 2021, Schoenfeld 2016); accessories are hypertrophy-style. A
 * priority's main movement (a BACK priority's pull-up) is practised at least
 * twice a week from three days up, before any fill (Grgic 2018).
 *
 * MUSCLE - 6-15 reps, compounds 6-10 at 0-2 RIR and single-joint work 10-15
 * to failure (Lopez 2021, Robinson 2024, Refalo 2023). GENERAL - strength-style compounds
 * plus hypertrophy accessories. SKILL - skill-tree progressions at an
 * accessible tier act as the compounds, strength-style, with the same volume
 * accounting as every other focus.
 *
 * Loads come from the lifter's own e1RM via [ProgramRules.workingLoadKg],
 * from a related logged lift via [ProgramRules.estimatedLoadKg] (labelled as
 * an estimate in the loadNote), or not at all for bodyweight work. Every
 * entry carries a one-line `why` naming its citation.
 *
 * Deterministic throughout: same inputs, same plan. No randomness anywhere.
 */
object ProgramGenerator {

    // ---------------------------------------------------------------- roles

    internal enum class Role { FULL_BODY, UPPER, LOWER, PUSH_DAY, PULL_DAY, LEGS_DAY }

    private fun roleLabel(role: Role): String = when (role) {
        Role.FULL_BODY -> "Full Body"
        Role.UPPER -> "Upper"
        Role.LOWER -> "Lower"
        Role.PUSH_DAY -> "Push"
        Role.PULL_DAY -> "Pull"
        Role.LEGS_DAY -> "Legs"
    }

    /** ISO weekday plus role for a split and day count the split fits. */
    private fun layout(split: TrainingSplit, days: Int): List<Pair<Int, Role>> = when (split) {
        TrainingSplit.FULL_BODY -> when (days) {
            1 -> listOf(1 to Role.FULL_BODY)
            2 -> listOf(1 to Role.FULL_BODY, 4 to Role.FULL_BODY)
            else -> listOf(1 to Role.FULL_BODY, 3 to Role.FULL_BODY, 5 to Role.FULL_BODY)
        }
        TrainingSplit.UPPER_LOWER -> listOf(1 to Role.UPPER, 2 to Role.LOWER, 4 to Role.UPPER, 5 to Role.LOWER)
        TrainingSplit.PUSH_PULL_LEGS -> if (days == 3) {
            listOf(1 to Role.PUSH_DAY, 3 to Role.PULL_DAY, 5 to Role.LEGS_DAY)
        } else {
            listOf(
                1 to Role.PUSH_DAY, 2 to Role.PULL_DAY, 3 to Role.LEGS_DAY,
                5 to Role.PUSH_DAY, 6 to Role.PULL_DAY, 7 to Role.LEGS_DAY,
            )
        }
        TrainingSplit.UPPER_LOWER_PPL -> listOf(
            1 to Role.PUSH_DAY, 2 to Role.PULL_DAY, 4 to Role.LEGS_DAY,
            5 to Role.UPPER, 6 to Role.LOWER,
        )
    }

    /**
     * Backbone patterns per session role, in prescription order. Full-body
     * days alternate A/B (fullBodyIndex even = A, odd = B; null = a lone
     * session) so every week trains both pulls and both pushes: the old single
     * list carried a vertical pull and no row, so a 3-day beginner week never
     * rowed and rhomboids and rear delts sat at 6 sets.
     */
    private fun slotsFor(role: Role, focus: TrainingFocus, fullBodyIndex: Int?): List<MovementPattern> = when (role) {
        Role.FULL_BODY -> fullBodySlots(focus, fullBodyIndex)
        Role.UPPER -> listOf(
            MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PULL,
            MovementPattern.VERTICAL_PUSH, MovementPattern.VERTICAL_PULL,
        )
        Role.LOWER, Role.LEGS_DAY -> listOf(
            MovementPattern.SQUAT, MovementPattern.HINGE,
        )
        Role.PUSH_DAY -> listOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH)
        Role.PULL_DAY -> listOf(MovementPattern.VERTICAL_PULL, MovementPattern.HORIZONTAL_PULL)
    }

    /**
     * STRENGTH/GENERAL/SKILL squat every full-body day (practice frequency of
     * the tested lift - Grgic 2018) and hinge on B days; MUSCLE alternates
     * squat and hinge, because a squat plus a hinge every day buries the
     * glutes past the top of their range before any other muscle is served.
     */
    private fun fullBodySlots(focus: TrainingFocus, index: Int?): List<MovementPattern> {
        val muscle = focus == TrainingFocus.MUSCLE
        return when {
            index == null -> listOf(
                MovementPattern.SQUAT, MovementPattern.HINGE,
                MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PULL,
            )
            index % 2 == 0 -> listOf(
                MovementPattern.SQUAT, MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PULL,
            )
            muscle -> listOf(
                MovementPattern.HINGE, MovementPattern.VERTICAL_PUSH, MovementPattern.VERTICAL_PULL,
            )
            else -> listOf(
                MovementPattern.SQUAT, MovementPattern.VERTICAL_PUSH,
                MovementPattern.VERTICAL_PULL, MovementPattern.HINGE,
            )
        }
    }

    // ------------------------------------------------------ specificity set

    private val MAIN_LIFTS: Map<MovementPattern, String> = mapOf(
        MovementPattern.SQUAT to "Back Squat",
        MovementPattern.HORIZONTAL_PUSH to "Bench Press",
        MovementPattern.HINGE to "Deadlift",
        MovementPattern.VERTICAL_PUSH to "Overhead Press",
    )

    private val MAIN_LIFT_NAMES: Set<String> = MAIN_LIFTS.values.map { it.lowercase() }.toSet()

    private fun isMainLift(name: String): Boolean = name.trim().lowercase() in MAIN_LIFT_NAMES

    // ---------------------------------------------------------- eligibility

    /**
     * Machine detection, identical to the picker's rule ([GearRequirements.isMachine]):
     * loadFactor below the free-weight reference means machine, cable, sled or
     * Smith, and the "assisted" prefix names the assisted machines loadFactor
     * does not cover.
     */
    private fun isMachine(exercise: Exercise): Boolean = GearRequirements.isMachine(exercise.name)

    private fun equipmentAllows(exercise: Exercise, equipment: Equipment): Boolean =
        GearRequirements.allows(exercise.name, exercise.isWeighted, equipment)

    /**
     * Whether the generator may ever dose this movement, whatever the gear:
     * a lifting row (no activity category) with a REPS metric - holds are
     * measured in seconds, and a rep target on a plank is nonsense - that is
     * neither a load-priced milestone nor a skill measured in metres (Seed
     * stamps Handstand Walk REPS, but "10 metres" is not a rep target).
     * Every catalogue row has a [MuscleMap] profile, so this, not the
     * profile, is what keeps the rest out of the pool and out of improve().
     */
    private fun dosable(exercise: Exercise): Boolean =
        exercise.category.isBlank() &&
            exercise.metric == ExerciseMetric.REPS &&
            !MovementDifficulty.isLoadPriced(exercise.name) &&
            Skills.ALL.firstOrNull { skill -> skill.name.equals(exercise.name, ignoreCase = true) }
                ?.metric != Skills.Metric.METRES

    /** How well the implement matches the gear: free weight 0, machine 1,
     * bodyweight 2, assisted machines last of all (3) - they are regressions
     * for lifters who cannot yet do the bodyweight movement. Machines only
     * reach the pool with a full gym. With no loading gear (no full gym,
     * dumbbells or barbell) everything is bodyweight and scores 0. */
    internal fun fitScore(exercise: Exercise, equipment: Equipment): Int {
        val canLoad = equipment.fullGym || Gear.DUMBBELLS in equipment.gear || Gear.BARBELL in equipment.gear
        return when {
            !canLoad -> 0
            !exercise.isWeighted -> 2
            exercise.name.trim().lowercase().startsWith("assisted") -> 3
            isMachine(exercise) -> 1
            else -> 0
        }
    }

    /** A movement the skill tree owns, detectable without importing Skills. */
    private fun isSkillTree(exercise: Exercise): Boolean =
        MovementDifficulty.isClassified(exercise.name) &&
            exercise.name.trim().lowercase() !in MovementDifficulty.catalogueOnlyKeys

    /**
     * Compound & skill only is the calisthenics lifter's switch, so among
     * candidates that already tie on stretch, share and compound it ranks
     * bodyweight skill-tree movements (2), then other bodyweight movements
     * (1), ahead of loaded ones (0): a Door Sheet Row over a Dumbbell Row, a
     * Pull-up over a Chin-up. A bodyweight movement more than one tier under
     * the lifter's training age is not a comparable stand-in (a bodyweight
     * squat against a loaded one), so it scores 0 and the loaded movement
     * keeps the slot. Off unless [ProgramRequest.compoundOnly].
     */
    private fun calisthenicsRank(ctx: Ctx, exercise: Exercise): Int {
        if (!ctx.request.compoundOnly || exercise.isWeighted) return 0
        if (MovementDifficulty.tier(exercise.name) < desiredBodyweightTier(ctx.volume) - 1) return 0
        return if (isSkillTree(exercise)) 2 else 1
    }

    /**
     * The pool the generator may prescribe from: lifting rows with a REPS
     * metric under the equipment, never the milestone rows, and only
     * movements a [MuscleMap] profile exists for - an unprofiled movement
     * cannot be volume-counted or explained, so it degrades out. Skills
     * measured in metres (Handstand Walk) are also out: Seed stamps their
     * catalogue rows REPS, but "10 metres" is not a rep target a program
     * can dose. [compoundOnly] also drops every isolation movement: every
     * slot - backbone, deficit fill, a priority's second practice, an
     * improve addition, a dumbbell-cap harder variant - selects from this
     * pool, so filtering here is the one place the preference has to hold.
     * A slot with no compound left for it is dropped (DEGRADE RULE).
     */
    internal fun eligible(
        catalogue: List<Exercise>,
        equipment: Equipment,
        focus: TrainingFocus,
        compoundOnly: Boolean = false,
    ): List<Exercise> =
        catalogue.filter {
            dosable(it) &&
                !ExerciseLibrary.isLibraryOnly(it.name) &&
                // A custom row that shares a catalogue movement's name IS that
                // movement (every table reads the catalogue entry first), which
                // happens when a seed row ships after the lifter made their own.
                (!CustomExercises.isCustom(it.name) || it.name.trim().lowercase() in MuscleMap.keys) &&
                equipmentAllows(it, equipment) &&
                MuscleMap.profile(it.name) != null &&
                (focus == TrainingFocus.SKILL || !MuscleMap.isTechnique(it.name)) &&
                !(compoundOnly && MovementDifficulty.isIsolation(it.name))
        }

    /**
     * [eligible] narrowed to what this lifter may be prescribed: a bodyweight
     * progression more than a tier past the training age is out (a first
     * year never gets a tier IV Dragon Flag). A hard filter, not a sort key:
     * the deficit fill re-sorts by waste, and a slot with nothing easier
     * degrades into the shortfall note instead (DEGRADE RULE). SKILL keeps
     * the whole tree - its lifter asked for the progressions. Every week,
     * session and improve selection reads this pool.
     */
    internal fun prescribable(catalogue: List<Exercise>, request: ProgramRequest): List<Exercise> {
        val pool = eligible(catalogue, request.equipment, request.focus, request.compoundOnly)
        if (request.focus == TrainingFocus.SKILL) return pool
        val ceiling = desiredBodyweightTier(request.volume) + 1
        return pool.filter { it.isWeighted || MovementDifficulty.tier(it.name) <= ceiling }
    }

    // ----------------------------------------------------------- session draft

    internal class Draft(val day: Int?, val role: Role?) {
        val entries = mutableListOf<PlannedEntry>()
        val names = mutableSetOf<String>()

        /** Equipment fit per prescribed movement, for the pattern-fit rule. */
        val fits = mutableMapOf<String, Int>()
    }

    internal class Ctx(
        val request: ProgramRequest,
        val pool: List<Exercise>,
        val strength: StrengthProfile,
        /** Per-movement familiarity from logged sets, 0..1; empty until the history is wired in. */
        val familiarity: Map<String, Double> = emptyMap(),
        /** Movements the lifter keeps removing: ranked lower, never excluded. */
        val demoted: Set<String> = emptySet(),
    ) {
        /** The session clock this request allows; roomFor and session() read it from chunk 2 on. */
        val budgetSeconds: Int = ProgramRules.sessionBudgetSeconds(request.sessionMinutes)

        /** The most movements one session may hold: the lifter's own count, under the safety cap. */
        val cap: Int = ProgramRules.movementCap(request.maxExercises)

        val volume get() = request.volume
        val focus get() = request.focus
        val priorityMuscles: Set<Muscle> = request.priorities.flatMap { it.muscles }.toSet()

        /**
         * The internal aim, never shown as a range: every muscle to the
         * covered minimum, a priority to twice that once the level is past
         * LOW (a LOW lifter's priority is the same four, started high).
         */
        fun targetFor(muscle: Muscle): Double =
            if (muscle in priorityMuscles && volume != VolumeLevel.LOW) PRIORITY_TARGET else ProgramRules.COVERED_MIN
    }

    /** Weekly hard sets a priority muscle is filled toward at STANDARD and HIGH. */
    private const val PRIORITY_TARGET = 8.0

    // ------------------------------------------------------------- selection

    internal fun profileOf(exercise: Exercise): ExerciseProfile =
        MuscleMap.profile(exercise.name) ?: error("unprofiled ${exercise.name} reached selection")

    /**
     * Deterministic preference order for one slot: the practised main lift
     * first (specificity), then the accessible skill progression (SKILL),
     * then the long-length movement (Wolf 2025 and the lengthened-position
     * trials), the bigger contribution to the requested muscle, the
     * compound, bodyweight and skill-tree work under compound & skill only
     * ([calisthenicsRank]), the better equipment fit, the lower tier, then the name.
     * [exclude] names movements a fallback slot must not reuse (mains whose
     * practice target for the week is already met).
     *
     * Fit rule (ported, refined to its real target): the same movement
     * pattern loading the same muscles must not appear at two different
     * implement fits in one session - "Bodyweight Squat beside Back Squat"
     * cannot happen. Different patterns may use different implements freely
     * (a barbell hinge beside a machine leg curl is normal programming).
     */
    internal fun rankCandidates(
        ctx: Ctx,
        session: Draft,
        pattern: MovementPattern?,
        muscle: Muscle?,
        exclude: Set<String> = emptySet(),
    ): List<Exercise> = ctx.pool
        .filter { pattern == null || profileOf(it).pattern == pattern }
        .filter { muscle == null || (profileOf(it).muscles[muscle] ?: 0.0) >= 0.5 }
        .filter { it.name !in session.names }
        .filter { it.name !in exclude }
        .filter { candidate ->
            val fit = fitScore(candidate, ctx.request.equipment)
            val candidateProfile = profileOf(candidate)
            session.entries.all { entry ->
                val existing = MuscleMap.profile(entry.exerciseName) ?: return@all true
                val existingFit = session.fits[entry.exerciseName] ?: fit
                !(
                    existing.pattern == candidateProfile.pattern &&
                        existingFit != fit &&
                        candidateProfile.muscles.keys.any {
                            (existing.muscles[it] ?: 0.0) >= 0.5
                        }
                    )
            }
        }
        .sortedWith(
            // Specificity: the practised main lift leads its own pattern.
            compareByDescending<Exercise> {
                if (pattern != null && wantsMainLift(ctx, pattern) && it.name.equals(MAIN_LIFTS[pattern], ignoreCase = true)) 1 else 0
            }.thenByDescending {
                // SKILL: the skill tree's own progressions first, the
                // accessible tiers (I-II) above all.
                when {
                    ctx.focus != TrainingFocus.SKILL || !isSkillTree(it) -> 0
                    MovementDifficulty.tier(it.name) <= 2 -> 2
                    else -> 1
                }
            }.thenByDescending { if (stretchesFor(it, muscle)) 1 else 0 }
                // For a muscle fill, how much the movement trains THE muscle
                // outranks whether it is compound: a 1.0 isolation closes the
                // deficit where a 0.5 compound would double-count everyone.
                .thenByDescending {
                    if (muscle != null) (profileOf(it).muscles[muscle] ?: 0.0) else 0.0
                }
                .thenByDescending { if (profileOf(it).compound) 1 else 0 }
                .thenByDescending { calisthenicsRank(ctx, it) }
                .thenBy { fitScore(it, ctx.request.equipment) }
                // Between equal candidates, the one worked both sides at once:
                // a one-arm row pays its work twice on the clock for the same
                // set (ProgramRules.setSeconds), time another muscle needs.
                .thenBy { if (profileOf(it).unilateral) 1 else 0 }
                // Between equal candidates, prefer the one that does not pile
                // lower-back fatigue on top (RDL over Good Morning): a
                // recovery heuristic, not a growth claim.
                .thenBy { profileOf(it).muscles[Muscle.LOWER_BACK] ?: 0.0 }
                // Bodyweight difficulty lives in the movement, not a plate:
                // pick the progression that matches the lifter's training
                // age (an intermediate on incline push-ups never reaches the
                // 6-15 rep, 1-3 RIR zone). Loaded movements scale by load, so
                // the easiest-to-learn variant still wins for them.
                .thenBy {
                    val tier = MovementDifficulty.tier(it.name)
                    if (it.isWeighted) tier else kotlin.math.abs(tier - desiredBodyweightTier(ctx.volume))
                }
                .thenBy { it.name },
        )

    /**
     * The slot's pick: option 0 of [suggest] for the same slot (the ranking
     * is shared, so the tags are not computed on the generator's hot path).
     */
    private fun pick(
        ctx: Ctx,
        session: Draft,
        pattern: MovementPattern?,
        muscle: Muscle?,
        exclude: Set<String> = emptySet(),
    ): Exercise? = rankCandidates(ctx, session, pattern, muscle, exclude).firstOrNull()

    /** A draft holding [session]'s movements, so a ranking sees what the session already has. */
    internal fun draftOf(ctx: Ctx, session: PlannedPreset?): Draft {
        val draft = Draft(session?.scheduledDay, null)
        for (entry in session?.entries.orEmpty()) {
            draft.entries += entry
            draft.names += entry.exerciseName
            ctx.pool.firstOrNull { it.name.equals(entry.exerciseName, ignoreCase = true) }
                ?.let { draft.fits[entry.exerciseName] = fitScore(it, ctx.request.equipment) }
        }
        return draft
    }

    /** The full ranking behind a [Slot.Pick], best first: what the generator itself would walk down. */
    internal fun rankPool(
        request: ProgramRequest,
        catalogue: List<Exercise>,
        strength: StrengthProfile,
        session: PlannedPreset? = null,
        pattern: MovementPattern? = null,
        muscle: Muscle? = null,
        exclude: Set<String> = emptySet(),
    ): List<Exercise> {
        val ctx = Ctx(request, prescribable(catalogue, request), strength)
        return rankCandidates(ctx, draftOf(ctx, session), pattern, muscle, exclude)
    }

    /**
     * Up to three ranked options for [slot], best first, each with at most
     * two reasons. Option 0 is what [week] and [session] would pick; the
     * surfaces that offer a movement (a slot pick, a swap, a stand-in, a
     * short muscle, a replacement for a removed one) all read this.
     * [week] is the lifter's current presets: the session a slot belongs to
     * and the coverage its reasons are judged against.
     */
    fun suggest(
        request: ProgramRequest,
        slot: Slot,
        week: List<PlannedPreset>,
        catalogue: List<Exercise>,
        strength: StrengthProfile,
    ): Ranked = ProgramSuggestions.suggest(request, slot, week, catalogue, strength)

    /** STRENGTH and GENERAL lead their main-lift patterns with the lift itself. */
    private fun wantsMainLift(ctx: Ctx, pattern: MovementPattern): Boolean =
        (ctx.focus == TrainingFocus.STRENGTH || ctx.focus == TrainingFocus.GENERAL) &&
            pattern in MAIN_LIFTS

    /**
     * Adds a movement to the session with its full prescription, updating the
     * session's fit and name registers. Callers drop the slot when selection
     * found nothing (DEGRADE RULE).
     */
    private fun add(
        ctx: Ctx,
        session: Draft,
        exercise: Exercise,
        why: String,
        sets: Int? = null,
        forMuscle: Muscle? = null,
    ): PlannedEntry {
        val profile = profileOf(exercise)
        val reps = prescriptionReps(ctx, profile)
        val rir = ProgramRules.loadRir(profile.compound)
        // A harder variant the session already holds would list one
        // movement twice: fillWithCap keeps the capped original and its honest note.
        val (chosen, fill) = fillWithCap(
            exercise, ctx.pool, ctx.strength, reps, rir, ctx.request.equipment, taken = session.names,
        )
        val fit = fitScore(chosen, ctx.request.equipment)
        session.fits[chosen.name] = fit
        session.names += chosen.name
        if (chosen !== exercise) {
            session.fits[exercise.name] = fit
            session.names += exercise.name
        }
        val entry = ProgramRules.withPlan(
            PlannedEntry(
                exerciseName = chosen.name,
                sets = startSets(ctx, exercise, forMuscle, sets),
                reps = fill?.reps ?: reps,
                targetWeightKg = fill?.kg,
                why = why,
                loadNote = fill?.note,
            ),
            ctx.volume, ctx.focus,
        )
        session.entries += entry
        return entry
    }

    /**
     * Where a new movement starts: the level's starting sets inside its set
     * range ([ProgramRules.startingSets]; a priority muscle's movement starts
     * at the top), or [requested] clamped into that range. Never past the
     * per-entry sanity ceiling.
     */
    private fun startSets(ctx: Ctx, exercise: Exercise, forMuscle: Muscle?, requested: Int? = null): Int {
        val profile = profileOf(exercise)
        val range = ProgramRules.setRange(ctx.volume, profile.compound)
        // Tracked muscles only: an area may name helpers (front delts under
        // Shoulders), and those keep their floor whatever is prioritised.
        val priorities = ctx.priorityMuscles.filter { it in ProgramRules.TRACKED }
        val priority = (forMuscle != null && forMuscle in priorities) || dominantMuscle(profile) in priorities
        val start = requested ?: ProgramRules.startingSets(ctx.volume, profile.compound, priority)
        return start.coerceIn(range).coerceAtMost(ProgramRules.ENTRY_SANITY_MAX_SETS)
    }

    /**
     * Isolation and core stay at 10-12 reps in every focus: a 5-rep calf
     * raise or leg extension is poor practice, and heavy loads only matter
     * for strength on the tested lift (Lopez 2021).
     */
    private fun prescriptionReps(ctx: Ctx, profile: ExerciseProfile): Int = when (ctx.focus) {
        TrainingFocus.MUSCLE -> if (profile.compound) 8 else 12
        TrainingFocus.STRENGTH, TrainingFocus.SKILL, TrainingFocus.GENERAL -> if (profile.compound) 5 else 10
    }

    /** Skill-tree tier a bodyweight movement should sit at for each training age. */
    private fun desiredBodyweightTier(tier: VolumeLevel): Int = when (tier) {
        VolumeLevel.LOW -> 2
        VolumeLevel.STANDARD -> 3
        VolumeLevel.HIGH -> 4
    }

    /**
     * One filled prescription: the load, the reps to do it for (raised when
     * the dumbbell cap forces lighter weight), the loadNote, and whether the
     * movement needs a harder variant (the capped load cannot reach failure
     * inside 20 reps). Callers that can substitute MUST act on [overCap];
     * when it is set the pair already carries the honest fallback - the cap
     * at 20 reps with a loadNote that says so.
     */
    internal class LoadFill(
        val kg: Double,
        val reps: Int,
        val note: String?,
        val overCap: Boolean,
    )

    /**
     * The per-dumbbell ceiling for this movement under this equipment, or
     * null when no cap applies: full gym has no cap, movements that need no
     * dumbbells have no cap (a barbell lift, or a shared-implement lift the
     * lifter's other gear covers, is never held to the dumbbell), and an
     * unset or non-positive max is treated as no cap. A two-dumbbell
     * movement's load numbers are totals, so a pair doubles the ceiling.
     */
    internal fun dumbbellCapKg(exerciseName: String, equipment: Equipment): Double? {
        if (equipment.fullGym) return null
        if (Gear.DUMBBELLS !in equipment.gear) return null
        val ways = GearRequirements.needs(exerciseName)
        if (ways.none { Gear.DUMBBELLS in it }) return null
        if (ways.any { Gear.DUMBBELLS !in it && equipment.gear.containsAll(it) }) return null
        val max = equipment.dumbbellMaxKg?.takeIf { it > 0.0 } ?: return null
        if (GearRequirements.needsPair(exerciseName)) {
            if (!equipment.dumbbellPair) return null
            return 2 * max
        }
        return max
    }

    /** Whether [loadKg] sits at the dumbbell cap for this movement, so its reps were raised to compensate. */
    private fun atDumbbellCap(exerciseName: String, loadKg: Double?, equipment: Equipment): Boolean =
        loadKg != null && dumbbellCapKg(exerciseName, equipment)?.let { loadKg >= it - 1e-9 } == true

    /** The movement's e1RM on file, or the one the prescribed load implies
     * (Epley inverted) when the lifter never logged it directly. */
    private fun impliedE1rm(
        exerciseName: String,
        strength: StrengthProfile,
        load: Double,
        reps: Int,
        rir: Int,
    ): Double {
        val direct = strength.bestE1rmKg[exerciseName.trim().lowercase()]
        if (direct != null) return direct
        return load * (1.0 + (reps + rir) / 30.0)
    }

    /**
     * Loads from the lifter's own PRs; a related logged lift estimates the
     * rest (labelled); bodyweight work carries no load. Prescriptions past
     * ~10 reps stop trusting the Epley inversion, so the estimate path caps
     * its reps (LeSuer 1997).
     *
     * DUMBBELL CAP - the single place both the generator and the templates
     * go through: for a movement that needs dumbbells (and is not full gym)
     * with a max set, a prescription above the cap is cut to the cap and the
     * reps are raised to what the lifter's e1RM supports at the target RIR
     * (Epley inverted), clamped to at most 20 (Lopez 2021: lighter loads
     * still reach near failure with more reps). Past 20 reps the load can no
     * longer be rescued, so [LoadFill.overCap] asks the caller for a harder
     * variant; without one the cap stays at 20 reps and the loadNote says so.
     */
    internal fun fillLoad(
        exercise: Exercise,
        strength: StrengthProfile,
        reps: Int,
        rir: Int,
        equipment: Equipment,
    ): LoadFill? {
        if (!exercise.isWeighted) return null
        val group = exercise.muscleGroup.name
        val worn = WornLoad.forLift(equipment.kit, exercise.name, exercise.isWeighted, exercise.metric)
        var base = ProgramRules.workingLoadKg(exercise.name, group, strength, reps, rir, worn)
        if (base == null) {
            base = ProgramRules.estimatedLoadKg(
                exercise.name, group, strength, reps.coerceAtMost(ProgramRules.MAX_WORKING_REPS), rir, worn,
            )
        }
        if (base == null) {
            base = StarterLoads.loadKg(exercise, strength.starter, reps.coerceAtMost(ProgramRules.MAX_WORKING_REPS), rir, worn)
        }
        base ?: return null
        val cap = dumbbellCapKg(exercise.name, equipment)
        if (cap == null || base.first <= cap) return LoadFill(base.first, reps, base.second, overCap = false)

        val e1rm = impliedE1rm(exercise.name, strength, base.first, reps, rir)
        val rawReps = floor(30.0 * (e1rm / cap - 1.0)).toInt() - rir
        // The cap is a total for two-dumbbell lifts; the note names the
        // dumbbell the lifter owns, not "48 kg per dumbbells".
        val each = equipment.dumbbellMaxKg ?: cap
        val eachLabel = if (each % 1.0 == 0.0) each.toInt().toString() else "%.1f".fmt(each)
        val kit = if (GearRequirements.needsPair(exercise.name)) {
            "your $eachLabel kg dumbbells"
        } else {
            "your $eachLabel kg dumbbell"
        }
        return if (rawReps > MAX_DUMBBELL_REPS) {
            LoadFill(
                cap, MAX_DUMBBELL_REPS,
                "capped at $kit for $MAX_DUMBBELL_REPS reps, still short of failure - " +
                    "move to a harder variant of this when you can",
                overCap = true,
            )
        } else {
            val raised = rawReps.coerceAtLeast(reps).coerceAtMost(MAX_DUMBBELL_REPS)
            val note = if (raised > reps) {
                "capped at $kit; reps raised to stay near failure - Lopez 2021"
            } else {
                "capped at $kit"
            }
            LoadFill(cap, raised, note, overCap = false)
        }
    }

    /** Ceiling where a lighter load stops rescuing the set with reps (Lopez 2021). */
    internal const val MAX_DUMBBELL_REPS = 20

    /**
     * [fillLoad] plus the harder-variant swap it calls for: when the capped
     * load cannot reach failure inside 20 reps, the movement becomes the
     * one-step harder variant from [GearRequirements.harderVariant] - if that
     * variant is in the pool under this equipment and not already [taken]
     * (one movement appears once per session). Returns the movement that
     * was actually filled. Used by the generator's [add] and the templates'
     * build, so the cap can never be routed around.
     */
    internal fun fillWithCap(
        exercise: Exercise,
        pool: List<Exercise>,
        strength: StrengthProfile,
        reps: Int,
        rir: Int,
        equipment: Equipment,
        taken: Set<String> = emptySet(),
    ): Pair<Exercise, LoadFill?> {
        val fill = fillLoad(exercise, strength, reps, rir, equipment)
        if (fill?.overCap != true) return exercise to fill
        val variantName = GearRequirements.harderVariant[exercise.name.trim().lowercase()]
        val variant = variantName
            ?.let { name -> pool.firstOrNull { it.name.equals(name, ignoreCase = true) && it.name !in taken } }
            ?: return exercise to fill
        val variantFill = fillLoad(variant, strength, reps, rir, equipment)
        return variant to variantFill
    }

    // ------------------------------------------------------------------ week

    fun week(request: ProgramRequest, catalogue: List<Exercise>, strength: StrengthProfile): RoutinePlan {
        val days = request.daysPerWeek.coerceIn(1, 6)
        val split = request.split.takeIf { days in it.dayOptions } ?: TrainingSplit.forDays(days)
        val pool = prescribable(catalogue, request)
        if (pool.isEmpty()) return RoutinePlan(emptyList(), note = NOTHING_TO_PLAN_NOTE)
        val ctx = Ctx(request, pool, strength)
        val sessions = layout(split, days).map { (day, role) -> Draft(day, role) }

        // Phase 1: multi-joint backbone per pattern slot. STRENGTH and
        // GENERAL lead their main-lift patterns with the lift itself
        // (specificity - Buckner 2017), up to the practice-frequency target.
        val mainTargets = mapOf(
            "back squat" to 2, "bench press" to 2,
            "deadlift" to mainTargetCount(days), "overhead press" to mainTargetCount(days),
        )
        val mainCount = mutableMapOf<String, Int>()
        val exhausted = mutableSetOf<String>()
        // Sessions each pattern has been placed in so far this week.
        val patternCount = mutableMapOf<MovementPattern, Int>()
        for (session in sessions) {
            val role = session.role ?: continue
            val fullBodyIndex = sessions.filter { it.role == Role.FULL_BODY }.indexOf(session)
            val slots = slotsFor(role, ctx.focus, fullBodyIndex.takeIf { it >= 0 && sessions.size > 1 })
            // A cap under the slot count drops slots: the patterns the week
            // has placed least go first, so no backbone pattern is lost by
            // sitting last in every session's list. Entries keep slot order.
            val order = slots.indices.sortedBy { if (slots.size > ctx.cap) patternCount[slots[it]] ?: 0 else 0 }
            val slotOf = mutableListOf<Int>()
            for (slotIndex in order) {
                val pattern = slots[slotIndex]
                if (session.entries.size >= ctx.cap) break
                val placed = session.entries.size
                val mainName = MAIN_LIFTS[pattern]
                var handled = false
                if (mainName != null && wantsMainLift(ctx, pattern)) {
                    val key = mainName.lowercase()
                    if (mainCount.getOrDefault(key, 0) < (mainTargets[key] ?: 1)) {
                        val main = pool.firstOrNull { it.name.equals(mainName, ignoreCase = true) }
                            ?: closestEquivalent(ctx, pool, pattern, session, mainName)
                        if (main != null) {
                            mainCount.merge(key, 1, Int::plus)
                            if ((mainTargets[key] ?: 1) <= mainCount.getValue(key)) exhausted += mainName
                            add(
                                ctx, session, main,
                                why = if (main.name.equals(mainName, true)) {
                                    "Main lift: strength is specific to the lift trained - Buckner 2017; TaskSpec 2025"
                                } else {
                                    "Your ${Words.armoury.s}'s stand-in for the ${mainName}: good for size, less for max strength - " +
                                        "Kikuchi 2017; Buckner 2017"
                                },
                            )
                            handled = true
                        }
                    }
                }
                if (!handled) {
                    val fallback = pick(ctx, session, pattern, null, exclude = exhausted) ?: continue
                    add(ctx, session, fallback, why = backboneWhy(fallback, ctx.focus))
                }
                if (session.entries.size > placed) {
                    slotOf += slotIndex
                    patternCount.merge(pattern, 1, Int::plus)
                }
            }
            if (slotOf != slotOf.sorted()) {
                val inSlotOrder = session.entries.zip(slotOf).sortedBy { it.second }.map { it.first }
                session.entries.clear()
                session.entries += inSlotOrder
            }
        }

        // The backbone is never cut for volume, but the clock is the lifter's
        // own: a HIGH level starts its compounds at the top of their range,
        // which three or four slots could not finish in 45 minutes.
        sessions.forEach { fitToBudget(ctx, it) }

        // Phase 1b: a STRENGTH priority's main movement is practised twice.
        // Before any fill, so no deficit or helper filler can crowd it out.
        if (ctx.focus == TrainingFocus.STRENGTH && days >= 3) addSecondPractice(ctx, sessions)

        // Phase 2: close each tracked muscle's weekly fractional deficit.
        // Every muscle aims at the covered minimum, priorities at twice it,
        // and this whole phase ends before helpers get any time: a helper
        // can never take a set a prioritised muscle needed.
        val capacityLimited = fillDeficits(ctx, sessions) { muscle -> ctx.targetFor(muscle) }
        // Phase 3: lift each helper muscle to the covered minimum with the
        // time left. No frequency rule: a floor asks for some work, not a
        // second session. A helper the time cannot reach stays light, never
        // a shortfall.
        fillDeficits(ctx, sessions, ProgramRules.HELPERS, frequency = false) { muscle ->
            floorOrTarget(ctx, muscle, sessionsShare = 1.0)
        }

        // Honest capacity line: when the chosen days cannot fit four hard
        // sets per muscle inside the session time budget, name the muscles
        // that land short. When time was not the limit, whatever is still
        // short is short because the kit has no movement for it (no
        // brachialis work with no bar and no weights, say), and the line says so.
        val volume = weeklyVolumeOf(sessions)
        val capacityNote = if (capacityLimited) {
            shortfallNote(volume, capacityAdvice(days))
        } else {
            shortfallNote(volume, "a fuller ${Words.armoury.s} would cover them.")
        }
        val frequencyNote = if (split == TrainingSplit.PUSH_PULL_LEGS && days == 3) ONCE_A_WEEK_NOTE else ""
        val built = sessions.filter { it.entries.isNotEmpty() }
        val fewerDaysNote = if (built.size < sessions.size) {
            "Only ${built.size} of ${sessions.size} days could be planned — your ${Words.armoury.s} had nothing for the rest."
        } else {
            ""
        }

        val roleSeen = mutableMapOf<Role, Int>()

        return RoutinePlan(
            Recovery.spaceDays(built
                .map { session ->
                    // Ordinal letters count per ROLE: Upper A, Lower A,
                    // Upper B, Lower B - not one alphabet across the week.
                    val ordinal = 'A' + roleSeen.merge(session.role!!, 1, Int::plus)!! - 1
                    PlannedPreset(
                        name = "${roleLabel(session.role)} $ordinal",
                        note = presetNote(ctx.volume, ctx.focus),
                        scheduledDay = session.day,
                        entries = spacePullUps(compoundsFirst(session.entries)),
                    )
                }),
            note = joinNotes(capacityNote, frequencyNote, fewerDaysNote),
        )
    }

    /** The routine note when the gear and settings leave no movement to build a week from. */
    internal val NOTHING_TO_PLAN_NOTE: String get() =
        "No days could be planned — your ${Words.armoury.s} and settings left nothing to build a week from."

    /**
     * What to do about muscles the session time could not reach. A sixth day
     * is the most the Forge builds, so a full six-day week is told the one
     * lever left: a priority claims time before the other muscles fill.
     */
    internal fun capacityAdvice(days: Int): String =
        if (days < 6) "add a day to cover them." else "every ${Words.rite.s} is full; prioritising them would move time their way."

    /** Routine-level note parts, blank ones dropped, one space between. */
    internal fun joinNotes(vararg parts: String): String = parts.filter { it.isNotBlank() }.joinToString(" ")

    /**
     * Pull-up family for the no-back-to-back rule: every dynamic vertical
     * pull (pull-ups, chin-ups, archer, L-sit, assisted, pulldowns,
     * muscle-ups). Lever holds share the pattern but are not pull-ups.
     */
    internal fun isPullUpVariant(entry: PlannedEntry): Boolean {
        if (MovementDifficulty.isHoldSet(null, entry.exerciseName, entry.modifiers)) return false
        // The lifter's own names go by name; a bodyweight row is never one.
        val profile = MuscleMap.profile(entry) ?: return entry.exerciseName.lowercase().let {
            ("pull-up" in it || "chin-up" in it) && "australian" !in it
        }
        return profile.pattern == MovementPattern.VERTICAL_PULL && profile.compound
    }

    /**
     * Owner rule: no two pull-up variants back to back - another exercise
     * sits between them. The first entry, the day's primary lift, never
     * moves. After it, relative order inside each group is kept: a non-pull
     * moves up only when a pull would otherwise follow a pull, and a pull
     * moves up only when leaving it later would run out of separators. A
     * workout with more pulls than separators keeps the surplus adjacent at
     * the end (a pull-up-only day stays as written). A separator is a
     * compound where one is left, so single-joint work is never moved up
     * ahead of the compounds to do the job.
     */
    internal fun spacePullUps(entries: List<PlannedEntry>): List<PlannedEntry> {
        if (entries.isEmpty()) return entries
        val remaining = entries.drop(1).toMutableList()
        val result = mutableListOf(entries.first())
        while (remaining.isNotEmpty()) {
            val pulls = remaining.count { isPullUpVariant(it) }
            val separators = remaining.size - pulls
            val lastIsPull = isPullUpVariant(result.last())
            val next = remaining.first()
            val index = when {
                isPullUpVariant(next) && lastIsPull ->
                    remaining.indexOfFirst { !isPullUpVariant(it) && isCompoundEntry(it) }.takeIf { it >= 0 }
                        ?: remaining.indexOfFirst { !isPullUpVariant(it) }.takeIf { it >= 0 } ?: 0
                // Placing this separator now would leave too few for the
                // pulls still to come: a pull goes first instead.
                !isPullUpVariant(next) && !lastIsPull && pulls > separators ->
                    remaining.indexOfFirst { isPullUpVariant(it) }
                else -> 0
            }
            result += remaining.removeAt(index)
        }
        return result
    }

    /** Single-joint work is the exception: an unprofiled movement counts as a compound, as everywhere else. */
    private fun isCompoundEntry(entry: PlannedEntry): Boolean = MuscleMap.profile(entry)?.compound != false

    /**
     * Compounds ahead of single-joint work, relative order kept inside each
     * group: the deficit and helper fills append in the order the muscles ran
     * short, which could put a curl before the row it backs up. Run before
     * [spacePullUps], which then keeps the pull-ups apart from there.
     */
    internal fun compoundsFirst(entries: List<PlannedEntry>): List<PlannedEntry> =
        entries.partition { isCompoundEntry(it) }.let { (compounds, isolation) -> compounds + isolation }

    /**
     * "N muscles below 4 hard sets (X and Y) — [advice]" naming every tracked
     * muscle under [ProgramRules.COVERED_MIN] - the same test the coverage
     * map uses to call a muscle BELOW_MINIMUM - or "" when none is. Helpers
     * are never named: under their floor they are light, not a problem.
     */
    internal fun shortfallNote(
        volume: Map<Muscle, Double>,
        advice: String,
    ): String {
        val short = ProgramRules.TRACKED.filter { (volume[it] ?: 0.0) < ProgramRules.COVERED_MIN - 1e-9 }
        if (short.isEmpty()) return ""
        val count = if (short.size == 1) "1 muscle" else "${short.size} muscles"
        return "$count below ${ProgramRules.COVERED_MIN.toInt()} hard sets (${joinWithAnd(short.map { it.label.lowercase() })}) — $advice"
    }

    /**
     * The main compound pattern of each priority area's dominant tracked
     * muscle. Areas without one (arms, calves, core) are absent, and so are
     * the hamstrings: their compound is the hinge, whose practice is held
     * to once a week under five days for recovery ([mainTargetCount]).
     * Shoulders are absent too: their tracked muscles are the side and rear
     * delts, which an overhead press barely loads (its lead is the front
     * delt, a helper), so a second press would spend another day's time on
     * the wrong muscle. A shoulder priority is served by lateral raises.
     */
    private val PRIORITY_PATTERNS: Map<MuscleArea, MovementPattern> = mapOf(
        MuscleArea.BACK to MovementPattern.VERTICAL_PULL,
        MuscleArea.CHEST to MovementPattern.HORIZONTAL_PUSH,
        MuscleArea.QUADS to MovementPattern.SQUAT,
        MuscleArea.GLUTES to MovementPattern.SQUAT,
    )

    /**
     * STRENGTH: a priority's main movement practised only once in the week
     * gets a second, lighter practice (3 sets of the same movement) at the
     * front of the shortest session that lacks it and whose role does not
     * train the opposing push or pull muscles - on a 3-day push/pull/legs
     * week a BACK priority's pull-up lands on the legs day. Strength comes
     * from practising the lift itself, more often (Grgic 2018; Buckner 2017).
     * Skipped when the kit never practises the pattern or no session has
     * room for it.
     */
    private fun addSecondPractice(ctx: Ctx, sessions: List<Draft>) {
        val leading = mutableMapOf<Draft, Int>()
        val patterns = MuscleArea.entries
            .filter { it in ctx.request.priorities }
            .mapNotNull { PRIORITY_PATTERNS[it] }
            .distinct()
        for (pattern in patterns) {
            val practised = sessions.filter { s -> s.entries.any { MuscleMap.profile(it)?.pattern == pattern } }
            if (practised.size != 1) continue
            val source = practised.single().entries.first { MuscleMap.profile(it)?.pattern == pattern }
            val exercise = ctx.pool.firstOrNull { it.name == source.exerciseName } ?: continue
            val host = bySpaceOf(ctx, sessions).firstOrNull { s ->
                s !in practised && hostsPractice(s.role, exercise.muscleGroup) &&
                    s.entries.size < ctx.cap && exercise.name !in s.names &&
                    ProgramRules.sessionSeconds(s.entries, ctx.focus) +
                    3 * ProgramRules.setSeconds(ctx.focus, exercise.name) <=
                    ctx.budgetSeconds
            } ?: continue
            val entry = add(
                ctx, host, exercise, sets = 3,
                why = "Second ${exercise.name.lowercase()} practice: strength comes from practising " +
                    "the lift - Grgic 2018; Buckner 2017",
            )
            host.entries.removeAt(host.entries.lastIndex)
            val at = leading.getOrDefault(host, 0)
            host.entries.add(at, entry)
            leading[host] = at + 1
        }
    }

    /** A push day never hosts a pull and a pull day never hosts a press: the split stays distinct. */
    private fun hostsPractice(role: Role?, group: MuscleGroup): Boolean = when (role) {
        Role.PUSH_DAY -> group != MuscleGroup.PULL
        Role.PULL_DAY -> group != MuscleGroup.PUSH
        else -> true
    }

    /** Push/pull/legs on three days: allowed, and honest about what it trades. */
    internal const val ONCE_A_WEEK_NOTE =
        "Each muscle once a week — fine, but each exercise gets less practice (Pelland 2026; Grgic 2018)."

    /** Deadlift and press practice scales with the room the week has. */
    private fun mainTargetCount(days: Int): Int = if (days >= 5) 2 else 1

    /**
     * Why a compound anchors the session, in the goal's own terms: a strength
     * week keeps its non-tested compounds for the muscle behind the lifts,
     * not for "long-length growth", and a hypertrophy week names the long
     * length only when the movement really loads its target there.
     */
    private fun backboneWhy(exercise: Exercise, focus: TrainingFocus): String {
        val profile = profileOf(exercise)
        val muscles = muscleListOf(exercise)
        val lead = "${patternLabel(profile.pattern).replaceFirstChar { it.uppercase() }} compound: $muscles"
        return when {
            focus == TrainingFocus.STRENGTH || focus == TrainingFocus.GENERAL -> "$lead - Gentil 2015"
            profile.stretchBias ->
                "$lead, loaded stretched - Gentil 2015; ${longLengthEvidence(dominantMuscle(profile))}"
            else -> "$lead - Gentil 2015"
        }
    }

    private fun patternLabel(pattern: MovementPattern): String = when (pattern) {
        MovementPattern.SQUAT -> "squat"
        MovementPattern.HINGE -> "hip-hinge"
        MovementPattern.LUNGE -> "single-leg"
        MovementPattern.HORIZONTAL_PUSH -> "horizontal press"
        MovementPattern.VERTICAL_PUSH -> "overhead press"
        MovementPattern.HORIZONTAL_PULL -> "row"
        MovementPattern.VERTICAL_PULL -> "vertical pull"
        MovementPattern.ISOLATION -> "isolation"
        MovementPattern.CORE -> "core"
    }

    /** Main muscles of a movement, plain-labelled and deterministic: "mid chest, front delts and triceps". */
    internal fun muscleListOf(exercise: Exercise, include: Muscle? = null): String {
        val profile = profileOf(exercise)
        val shares = profile.muscles.filterValues { it >= 0.5 }
        val source = if (shares.isEmpty()) profile.muscles else shares
        val names = source.entries.sortedByDescending { it.value }.take(3).map { it.key }
        val ordered = if (include != null && include !in names) listOf(include) + names else names
        return joinWithAnd(ordered.map { it.label.lowercase() })
    }

    internal fun joinWithAnd(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    private fun closestEquivalent(
        ctx: Ctx,
        pool: List<Exercise>,
        pattern: MovementPattern,
        session: Draft,
        mainName: String,
    ): Exercise? {
        // Same pattern, closest MuscleMap profile: prefer a compound the
        // equipment fits, deterministic down to the name. Between equals the
        // one crediting fewer muscles the main lift does not train wins, not
        // the later name: a dumbbell kit's deadlift stand-in is the two-leg
        // RDL, not its single-leg twin with the abductors on top.
        // Ahead of all of that, the stand-in must credit the main lift's
        // lead muscle as a prime mover: a triceps-led bench dip shares the
        // bench press's pattern but is no chest press, so it never takes the
        // slot from a push-up while one fits.
        val mainProfile = MuscleMap.profile(mainName)
        val lead = mainProfile?.let { dominantMuscle(it) }
        val trained = mainProfile?.muscles.orEmpty().filterValues { it > 0.0 }.keys
        return pool
            .filter { profileOf(it).pattern == pattern && it.name !in session.names }
            .maxWithOrNull(
                compareBy(
                    { if (lead != null && (profileOf(it).muscles[lead] ?: 0.0) >= 1.0) 1 else 0 },
                    { if (profileOf(it).compound) 1 else 0 },
                    { calisthenicsRank(ctx, it) },
                    { -fitScore(it, ctx.request.equipment) },
                    { -MovementDifficulty.tier(it.name) },
                    { -profileOf(it).muscles.count { (m, share) -> share > 0.0 && m !in trained } },
                    { it.name },
                ),
            )
    }

    // -------------------------------------------------------------- deficits

    private fun weeklyVolumeOf(sessions: List<Draft>): Map<Muscle, Double> =
        ProgramRules.weeklyVolume(sessions.map { PlannedPreset("", "", null, it.entries) })

    /**
     * Trims a session to the clock, one set at a time from the entry with the
     * most sets above its [PlannedEntry.setsMin]; only when every entry is at
     * its minimum does the last entry go (DEGRADE RULE), never the first, the
     * day's primary lift.
     */
    private fun fitToBudget(ctx: Ctx, draft: Draft) {
        while (ProgramRules.sessionSeconds(draft.entries, ctx.focus) > ctx.budgetSeconds) {
            val cut = draft.entries.indices
                .filter { draft.entries[it].sets > draft.entries[it].setsMin }
                .maxWithOrNull(compareBy<Int>({ draft.entries[it].sets }, { it }))
            if (cut != null) {
                draft.entries[cut] = draft.entries[cut].copy(sets = draft.entries[cut].sets - 1)
                continue
            }
            if (draft.entries.size <= 1) return
            val gone = draft.entries.removeAt(draft.entries.lastIndex)
            draft.names -= gone.exerciseName
            draft.fits.remove(gone.exerciseName)
        }
    }

    /**
     * Closes each tracked muscle's weekly fractional deficit ONE SET AT A TIME,
     * always serving the muscle with the largest RELATIVE shortfall next.
     * Growing in single sets is what keeps a capacity-limited week fair: the
     * old version handed the first muscle it served a whole 5-set block, so a
     * 4-day intermediate week filled glutes to 18 and left biceps and rear
     * delts at 6 once the upper days hit their set ceiling.
     *
     * Each step, in order of preference:
     *  1. frequency - a muscle directly trained in fewer than two sessions
     *     gets a new movement in a region-fitting session that lacks it
     *     (>=2 sessions per muscle, Pelland 2026: load quality);
     *  2. one more set on the session's existing DIRECT movement for it,
     *     up to that movement's own [PlannedEntry.setsMax] (fewer, fuller
     *     movements beat a pile of 2-set ones);
     *  3. a new direct movement at its starting sets where the session has room;
     *  4. one more set on an indirect (0.5-share) movement.
     * A step is refused when it would push the session past its exercise cap
     * or its time budget ([Ctx.budgetSeconds]), or any muscle past Heavy (see
     * [heavyWeek] and [ProgramRules.sessionMuscleRoom]). Heavy is soft
     * though: a muscle still under [ProgramRules.COVERED_MIN] that no
     * lighter step can serve gets the heavy step anyway - reaching four wins
     * over avoiding Heavy. A muscle no step can serve is dropped - the plan
     * degrades honestly - and so is one a step did not move (no progress).
     * Returns true when the session ceilings, not the targets, ended the fill.
     */
    private fun fillDeficits(
        ctx: Ctx,
        sessions: List<Draft>,
        muscles: List<Muscle> = ProgramRules.TRACKED,
        frequency: Boolean = true,
        targetOf: (Muscle) -> Double,
    ): Boolean {
        val skipped = mutableSetOf<Muscle>()
        var capacityBlocked = false
        var grown = 0
        var lastStep: Pair<Muscle, Double>? = null
        while (grown < MAX_FILL_STEPS) {
            // One volume per step: every candidate in growOnce reads this map,
            // and only the single step that grows the week changes it.
            val volume = weeklyVolumeOf(sessions)
            // No-progress exit: a step that left its muscle where it was
            // (a collateral-only set) must not be asked for again.
            lastStep?.let { (was, before) -> if ((volume[was] ?: 0.0) <= before + 1e-9) skipped += was }
            val muscle = muscles
                .filter { it !in skipped && targetOf(it) > 0.0 }
                .map { it to (targetOf(it) - (volume[it] ?: 0.0)) }
                // Filled to the floor itself: the coverage map calls 3.9 of
                // 4 below the minimum, so stopping half a set short (once
                // treated as fractional noise) showed a fresh plan as short.
                .filter { it.second > 1e-9 }
                .maxWithOrNull(
                    compareBy<Pair<Muscle, Double>>({ it.second / targetOf(it.first) })
                        .thenBy { -muscles.indexOf(it.first) },
                )
                ?.first ?: return capacityBlocked
            val before = volume[muscle] ?: 0.0
            var step = growOnce(ctx, sessions, volume, muscle, frequency, targetOf, allowHeavy = false)
            if (step != Grow.GREW && before < ProgramRules.COVERED_MIN - 1e-9) {
                step = growOnce(ctx, sessions, volume, muscle, frequency, targetOf, allowHeavy = true)
            }
            when (step) {
                Grow.GREW -> { grown++; lastStep = muscle to before }
                Grow.NO_ROOM -> { capacityBlocked = true; skipped += muscle }
                Grow.NO_MOVEMENT -> skipped += muscle
            }
        }
        return capacityBlocked
    }

    /**
     * Fill target for the helper phase: helpers aim at the covered minimum,
     * every major keeps its own target so the waste ordering still judges
     * collateral on the majors correctly. [sessionsShare] splits the weekly
     * figure across the sessions assumed to carry it.
     */
    private fun floorOrTarget(ctx: Ctx, muscle: Muscle, sessionsShare: Double): Double =
        (if (muscle in ProgramRules.HELPERS) ProgramRules.COVERED_MIN else ctx.targetFor(muscle)) / sessionsShare

    private enum class Grow { GREW, NO_ROOM, NO_MOVEMENT }

    /** Upper bound on fill steps: every step adds a set, so this caps a week at ~200 extra sets; a refused step adds none and is not counted. */
    private const val MAX_FILL_STEPS = 200

    private fun share(name: String, muscle: Muscle): Double =
        MuscleMap.profile(name)?.muscles?.get(muscle) ?: 0.0

    /**
     * Whether [sets] more sets of [exercise] would take any muscle past
     * [ProgramRules.HEAVY_WEEK] given the week's [volume] - the coverage
     * map's flag, which looks at every muscle, not only the tracked ones.
     */
    private fun heavyWeek(volume: Map<Muscle, Double>, exercise: Exercise, sets: Int): Boolean =
        profileOf(exercise).muscles.any { (muscle, share) ->
            share > 0.0 && (volume[muscle] ?: 0.0) + sets * share > ProgramRules.HEAVY_WEEK + 1e-9
        }

    private fun growOnce(
        ctx: Ctx,
        sessions: List<Draft>,
        volume: Map<Muscle, Double>,
        muscle: Muscle,
        frequency: Boolean,
        targetOf: (Muscle) -> Double,
        allowHeavy: Boolean,
    ): Grow {
        val fitting = sessions.filter { s ->
            ctx.pool.any { groupFitsSession(s.role, it.muscleGroup) && share(it.name, muscle) >= 0.5 }
        }
        if (fitting.isEmpty()) return Grow.NO_MOVEMENT
        var sawMovement = false

        // Time, and the per-session Heavy flag: a deficit one session cannot
        // take moves to another, or ends the fill as NO_ROOM.
        fun roomFor(s: Draft, exercise: Exercise, sets: Int) =
            ProgramRules.sessionSeconds(s.entries, ctx.focus) +
                sets * ProgramRules.setSeconds(ctx.focus, exercise.name) <=
                ctx.budgetSeconds &&
                (allowHeavy || ProgramRules.sessionMuscleRoom(s.entries, exercise.name, sets))

        fun heavy(exercise: Exercise, sets: Int) = !allowHeavy && heavyWeek(volume, exercise, sets)

        /**
         * Collateral a set spends on tracked muscles already at their target.
         * Zero-waste growth goes first: a quad set from a leg extension beats
         * one from a squat once the glutes are served.
         */
        fun waste(name: String): Double =
            ProgramRules.TRACKED.filter { it != muscle }.sumOf { other ->
                val s = share(name, other)
                if (s > 0.0 && (volume[other] ?: 0.0) >= targetOf(other)) s else 0.0
            }

        fun bump(s: Draft, index: Int): Boolean {
            val entry = s.entries[index]
            val exercise = ctx.pool.firstOrNull { it.name == entry.exerciseName } ?: return false
            if (entry.sets >= entry.setsMax || entry.sets >= ProgramRules.ENTRY_SANITY_MAX_SETS) return false
            if (!roomFor(s, exercise, 1) || heavy(exercise, 1)) return false
            s.entries[index] = entry.copy(sets = entry.sets + 1)
            return true
        }

        fun addNew(s: Draft, maxWaste: Double): Boolean {
            if (s.entries.size >= ctx.cap) return false
            val candidate = rankCandidates(ctx, s, null, muscle)
                .filter { groupFitsSession(s.role, it.muscleGroup) && !redundantIn(s, it) }
                .filter { waste(it.name) <= maxWaste && roomFor(s, it, startSets(ctx, it, muscle)) }
                .sortedBy { waste(it.name) }
                .firstOrNull { !heavy(it, startSets(ctx, it, muscle)) }
                ?: return false
            sawMovement = true
            val deficit = targetOf(muscle) - (volume[muscle] ?: 0.0)
            add(ctx, s, candidate, why = muscleWhy(candidate, muscle, ctx, deficit), forMuscle = muscle)
            return true
        }

        /** Bump candidates across the week: least wasteful first, then fewest sets. */
        fun bumpables(minShare: Double, wasteFree: Boolean?): List<Pair<Draft, Int>> =
            bySpaceOf(ctx, fitting).flatMap { s ->
                s.entries.indices
                    .filter { share(s.entries[it].exerciseName, muscle) >= minShare }
                    .map { s to it }
            }.filter { (s, i) ->
                val w = waste(s.entries[i].exerciseName)
                wasteFree == null || (w == 0.0) == wasteFree
            }.sortedWith(
                compareBy<Pair<Draft, Int>> { (s, i) -> waste(s.entries[i].exerciseName) }
                    .thenBy { (s, i) -> s.entries[i].sets },
            )

        // 1. Frequency: a second session for the muscle before more sets in one.
        val trainedIn = fitting.count { s -> s.entries.any { share(it.exerciseName, muscle) >= 0.5 } }
        if (frequency && trainedIn < minOf(2, fitting.size)) {
            for (s in bySpaceOf(ctx, fitting)) {
                if (s.entries.any { share(it.exerciseName, muscle) >= 0.5 }) continue
                if (addNew(s, Double.MAX_VALUE)) return Grow.GREW
            }
        }
        // 2. A set on a direct movement that spends nothing on served muscles.
        for ((s, i) in bumpables(1.0, wasteFree = true)) {
            sawMovement = true
            if (bump(s, i)) return Grow.GREW
        }
        // 3. A new movement that spends nothing on served muscles.
        for (s in bySpaceOf(ctx, fitting)) if (addNew(s, 0.0)) return Grow.GREW
        // 4. Any direct set, then any new movement, then indirect sets.
        for ((s, i) in bumpables(1.0, wasteFree = null)) {
            sawMovement = true
            if (bump(s, i)) return Grow.GREW
        }
        for (s in bySpaceOf(ctx, fitting)) if (addNew(s, Double.MAX_VALUE)) return Grow.GREW
        for ((s, i) in bumpables(0.5, wasteFree = null)) {
            sawMovement = true
            if (bump(s, i)) return Grow.GREW
        }
        val anyCandidate = fitting.any { s ->
            rankCandidates(ctx, s, null, muscle).any { groupFitsSession(s.role, it.muscleGroup) && !redundantIn(s, it) }
        }
        return if (sawMovement || anyCandidate) Grow.NO_ROOM else Grow.NO_MOVEMENT
    }

    /**
     * Shortest sessions first, by estimated clock time rather than set count,
     * so work lands where there is time for it: counting sets put a 5-set
     * knee raise on a 74-minute upper day beside two lower days under an hour.
     */
    private fun bySpaceOf(ctx: Ctx, sessions: List<Draft>): List<Draft> =
        sessions.withIndex().sortedWith(
            compareBy({ ProgramRules.sessionSeconds(it.value.entries, ctx.focus) }, { it.index }),
        ).map { it.value }

    /**
     * A second movement with the same pattern and the same main muscle as one
     * already in the session adds nothing the first could not do with another
     * set (Gentil 2015). Seen on device: barbell, Pendlay and dumbbell rows in
     * one upper day; seated and lying leg curls side by side. Once the first
     * sits at the top of its set range ([PlannedEntry.setsMax]) a second variant is the only way to add
     * volume, so it stops being redundant - that is how a leg day carries
     * both a standing and a seated calf raise (Kinoshita 2023: they load
     * different heads). A profile with no lead by design (a mobility drill,
     * the half-credit seated calf raise) has no main muscle to share, so it
     * never makes a real calf raise redundant.
     */
    private fun redundantIn(session: Draft, candidate: Exercise): Boolean {
        val profile = profileOf(candidate)
        return session.entries.any { entry ->
            val existing = MuscleMap.profile(entry.exerciseName) ?: return@any false
            sharesJob(existing, profile) && entry.sets < entry.setsMax
        }
    }

    /**
     * Whether two movements do one job: the same pattern leading with the
     * same muscle, neither without a lead by design. The predicate behind
     * [redundantIn] and [Overlap.detect].
     */
    internal fun sharesJob(a: ExerciseProfile, b: ExerciseProfile): Boolean =
        !a.noLeadByDesign && !b.noLeadByDesign && a.pattern == b.pattern && dominantMuscle(a) == dominantMuscle(b)

    /**
     * Weekly sets under the floor of [range] that losing [entry] would cost
     * the tracked muscles, given the week's volume with it still in
     * ([weekVolume]). Zero when every muscle it trains stays at its floor
     * without it. [absorbers] are the entries that could regrow the loss:
     * spare sets (up to [ProgramRules.maxSetsPerEntry]) on a direct movement for the muscle count against
     * the loss unless the movement would push another tracked muscle past
     * the top of [range] - the same limit the repair passes grow within.
     */
    internal fun capTrimCost(
        entry: PlannedEntry,
        weekVolume: Map<Muscle, Double>,
        range: ClosedFloatingPointRange<Double>,
        absorbers: List<PlannedEntry> = emptyList(),
    ): Double {
        val profile = MuscleMap.profile(entry) ?: return 0.0
        fun without(muscle: Muscle) = (weekVolume[muscle] ?: 0.0) - entry.sets * (profile.muscles[muscle] ?: 0.0)
        return ProgramRules.TRACKED.sumOf { muscle ->
            val share = profile.muscles[muscle] ?: 0.0
            val lost = maxOf(0.0, range.start - without(muscle)) - maxOf(0.0, range.start - (weekVolume[muscle] ?: 0.0))
            val spare = if (lost <= 0.0) 0.0 else absorbers.sumOf { other ->
                val direct = MuscleMap.profile(other)?.muscles ?: emptyMap()
                val clear = direct.all { (m, s) ->
                    m == muscle || m !in ProgramRules.TRACKED || without(m) + s <= range.endInclusive + 1e-9
                }
                if ((direct[muscle] ?: 0.0) >= 0.5 && clear) {
                    (ProgramRules.maxSetsPerEntry(other.exerciseName) - other.sets).coerceAtLeast(0) * direct.getValue(muscle)
                } else {
                    0.0
                }
            }
            if (share <= 0.0) 0.0 else maxOf(0.0, lost - spare)
        }
    }

    /**
     * The entry a session over its exercise cap gives up first: the one whose
     * loss leaves the week's tracked muscles least further under the floor
     * of [range] once [capTrimCost] lets the rest of the session regrow it
     * (weekly sets drive growth - Pelland 2026). Never index 0, the
     * session's primary compound or skill. Ties drop single-joint and core
     * work before compounds, then the later entry. Null when only the first
     * entry is left.
     */
    internal fun capTrimIndex(
        entries: List<PlannedEntry>,
        weekVolume: Map<Muscle, Double>,
        range: ClosedFloatingPointRange<Double>,
    ): Int? =
        (1 until entries.size).minWithOrNull(
            compareBy<Int> { i ->
                capTrimCost(entries[i], weekVolume, range, entries.filterIndexed { j, _ -> j != i })
            }
                .thenBy { if (MuscleMap.profile(entries[it])?.compound == false) 0 else 1 }
                .thenByDescending { it },
        )

    /**
     * A movement lands on a day whose role trains its region: leg work on
     * lower/leg days, presses on upper/push days, pulls on upper/pull days,
     * core anywhere. FULL_BODY trains everything; AUTO (null role) accepts
     * all. A push day carrying rows (or a pull day carrying diamond push-ups,
     * as a 6-day week once did) breaks the split the lifter chose.
     */
    internal fun groupFitsSession(role: Role?, group: MuscleGroup): Boolean {
        if (role == null || role == Role.FULL_BODY) return true
        return when (group) {
            MuscleGroup.LEGS -> role == Role.LOWER || role == Role.LEGS_DAY
            MuscleGroup.PUSH -> role == Role.UPPER || role == Role.PUSH_DAY
            MuscleGroup.PULL -> role == Role.UPPER || role == Role.PULL_DAY
            else -> true
        }
    }

    /** One decimal at most, integers plain: 5 not 5.0. */
    private fun fmtSets(sets: Double): String =
        if (kotlin.math.abs(sets - kotlin.math.round(sets)) < 0.05) {
            kotlin.math.round(sets).toInt().toString()
        } else {
            "%.1f".fmt(sets)
        }

    private fun setsPhrase(sets: Double): String =
        "${fmtSets(sets)} ${if (fmtSets(sets) == "1") "set" else "sets"}"

    /**
     * The trial that measured long-length growth for THIS muscle where one
     * exists; the general long-length meta otherwise. A lateral raise must
     * never end up citing the hamstring trial.
     */
    internal fun longLengthEvidence(muscle: Muscle?): String = when (muscle) {
        Muscle.HAMSTRINGS -> "Maeo 2021"
        Muscle.TRICEPS -> "Maeo 2022"
        Muscle.CALVES -> "Kinoshita 2023; Kassiano 2023"
        Muscle.QUADS -> "Pedrosa 2022"
        else -> "Wolf 2025"
    }

    /**
     * Plain-language reason for a deficit fill: which muscle it fills,
     * whether THIS movement trains it stretched, how short the week WAS
     * before it (past tense: the volume panel shows the filled week, so a
     * present-tense "5 sets short" contradicted its IN RANGE), and the
     * citation. The filled muscle is always named, even at a 0.5 share.
     */
    private fun muscleWhy(exercise: Exercise, muscle: Muscle, ctx: Ctx, deficit: Double): String {
        // A sub-set shortfall is indirect-share noise; say so instead of
        // printing "0.1 sets". The target itself is on the volume panel.
        val name = muscle.label.lowercase()
        val was = if (deficit < 0.5) "just under target" else "${setsPhrase(deficit)} short"
        val target = if (muscle in ctx.priorityMuscles) "priority $name" else name
        return if (stretchesFor(exercise, muscle)) {
            "Fills $target at full stretch: the week was $was - ${longLengthEvidence(muscle)}"
        } else {
            "Fills $target: the week was $was - Pelland 2026"
        }
    }

    /**
     * Whether the long-length advantage applies to [muscle] in this movement.
     * stretchBias describes the movement's DOMINANT muscle: a row stretches
     * the rhomboids, not the rear delts it also trains at a 0.5 share, so a
     * rear-delt fill must neither rank the row as long-length work nor cite
     * that evidence for it. A null [muscle] (a pattern slot) takes the flag
     * as it stands.
     */
    internal fun stretchesFor(exercise: Exercise, muscle: Muscle?): Boolean {
        val profile = profileOf(exercise)
        return profile.stretchBias && (muscle == null || dominantMuscle(profile) == muscle)
    }

    // --------------------------------------------------------------- session

    /**
     * One session at a per-session dose: the weekly target divided by the
     * assumed two sessions per muscle per week (Pelland 2026 - frequency is
     * a dose-distribution tool). AUTO builds around the tracked muscles the
     * existing week leaves furthest under target, each capped at that
     * one-session share; it returns null when the week already meets every
     * muscle's target, since there is nothing left to build around.
     */
    fun session(
        request: ProgramRequest,
        kind: SessionKind,
        scheduledDay: Int?,
        existingWeek: List<PlannedPreset>,
        catalogue: List<Exercise>,
        strength: StrengthProfile,
    ): PlannedPreset? {
        val pool = prescribable(catalogue, request)
        if (pool.isEmpty()) return null
        val ctx = Ctx(request, pool, strength)
        val existing = ProgramRules.weeklyVolume(existingWeek)
        val draft = Draft(scheduledDay, roleOf(kind))

        if (kind == SessionKind.AUTO) {
            // The shortfall is WEEKLY (target minus what the week already
            // does); one session closes at most its share of it. Comparing
            // the per-session share against the weekly volume, as this once
            // did, called any real week complete and built nothing.
            val dose = 2.0 // assumed sessions per muscle per week
            val under = ProgramRules.TRACKED
                .map { it to ctx.targetFor(it) - (existing[it] ?: 0.0) }
                .filter { it.second > 0.01 }
                .sortedWith(
                    compareByDescending<Pair<Muscle, Double>> { it.second }
                        .thenBy { ProgramRules.TRACKED.indexOf(it.first) },
                ) +
                // Helpers after every major: their whole floor fits one session.
                ProgramRules.HELPERS
                    .map { it to ProgramRules.COVERED_MIN - (existing[it] ?: 0.0) }
                    .filter { it.second > 0.01 }
            for ((muscle, weeklyDeficit) in under) {
                val deficit = if (muscle in ProgramRules.HELPERS) {
                    weeklyDeficit - (weeklyVolumeOf(listOf(draft))[muscle] ?: 0.0)
                } else {
                    minOf(weeklyDeficit, ctx.targetFor(muscle) / dose)
                }
                if (deficit <= 0.01) continue
                if (draft.entries.size >= ctx.cap) break
                // Prefer a movement whose other main muscles still want sets:
                // a dip for triceps also loads a chest the week already
                // serves, where an overhead extension does not.
                val served = ProgramRules.TRACKED.filter { other ->
                    val planned = (existing[other] ?: 0.0) + (weeklyVolumeOf(listOf(draft))[other] ?: 0.0)
                    planned >= ctx.targetFor(other)
                }.toSet()
                val exercise = rankCandidates(ctx, draft, null, muscle).firstOrNull { candidate ->
                    profileOf(candidate).muscles.none { (other, share) -> other in served && share >= 0.5 }
                } ?: pick(ctx, draft, null, muscle) ?: continue
                var sets = startSets(ctx, exercise, muscle)
                val least = ProgramRules.setRange(ctx.volume, profileOf(exercise).compound).first
                while (sets > least && !ProgramRules.sessionMuscleRoom(draft.entries, exercise.name, sets)) sets--
                if (!ProgramRules.sessionMuscleRoom(draft.entries, exercise.name, sets)) continue
                val seconds = ProgramRules.sessionSeconds(draft.entries, ctx.focus) +
                    sets * ProgramRules.setSeconds(ctx.focus, exercise.name)
                if (seconds > ctx.budgetSeconds) continue
                add(ctx, draft, exercise, why = muscleWhy(exercise, muscle, ctx, deficit), sets = sets, forMuscle = muscle)
            }
            // A session of single-joint work only has no anchor (Gentil 2015:
            // it adds little on its own): the closest compound for the
            // biggest shortfall it can cover takes the first slot, the last
            // fill giving way when the cap is full.
            if (draft.entries.isNotEmpty() && draft.entries.none { isCompoundEntry(it) }) {
                for ((muscle, weeklyDeficit) in under) {
                    val anchor = rankCandidates(ctx, draft, null, muscle).firstOrNull { profileOf(it).compound } ?: continue
                    val full = draft.entries.size >= ctx.cap
                    val rest = if (full) draft.entries.dropLast(1) else draft.entries
                    if (ProgramRules.sessionSeconds(rest, ctx.focus) + 2 * ProgramRules.setSeconds(ctx.focus, anchor.name) >
                        ctx.budgetSeconds ||
                        !ProgramRules.sessionMuscleRoom(rest, anchor.name, 2)
                    ) {
                        continue
                    }
                    if (full) draft.entries.removeAt(draft.entries.lastIndex)
                    val entry = add(ctx, draft, anchor, sets = 2, why = muscleWhy(anchor, muscle, ctx, weeklyDeficit))
                    draft.entries.removeAt(draft.entries.lastIndex)
                    draft.entries.add(0, entry)
                    break
                }
            }
        } else {
            val role = draft.role ?: return null
            for (pattern in slotsFor(role, ctx.focus, null)) {
                if (draft.entries.size >= ctx.cap) break
                val exercise = pick(ctx, draft, pattern, null) ?: continue
                add(ctx, draft, exercise, why = backboneWhy(exercise, ctx.focus))
            }
            fitToBudget(ctx, draft)
            // One session's share of the weekly dose: the target over the
            // assumed two sessions per muscle. The fill counts only this
            // draft, so adding the existing week's volume to the target (as
            // this once did) overshot every muscle the week already trained.
            fillDeficits(ctx, listOf(draft)) { muscle -> ctx.targetFor(muscle) / 2.0 }
            fillDeficits(ctx, listOf(draft), ProgramRules.HELPERS, frequency = false) { muscle ->
                floorOrTarget(ctx, muscle, sessionsShare = 2.0)
            }
        }
        if (draft.entries.isEmpty()) return null
        return PlannedPreset(
            name = draft.role?.let { roleLabel(it) } ?: Words.rite.c,
            note = presetNote(ctx.volume, ctx.focus),
            scheduledDay = scheduledDay,
            entries = spacePullUps(compoundsFirst(draft.entries)),
        )
    }

    private fun roleOf(kind: SessionKind): Role? = when (kind) {
        SessionKind.AUTO -> null
        SessionKind.FULL_BODY -> Role.FULL_BODY
        SessionKind.UPPER -> Role.UPPER
        SessionKind.LOWER -> Role.LOWER
        SessionKind.PUSH -> Role.PUSH_DAY
        SessionKind.PULL -> Role.PULL_DAY
        SessionKind.LEGS -> Role.LEGS_DAY
    }

    // --------------------------------------------------------------- improve

    /**
     * The rep range a focus asks an entry to sit inside. Isolation and core
     * stay hypertrophy-style in every focus: 3-5-rep leg extensions and calf
     * raises are poor practice even in a strength block (Lopez 2021 - heavy
     * loads drive strength on the TESTED lift, not on accessories).
     */
    private fun repRange(focus: TrainingFocus, compound: Boolean): IntRange = when (focus) {
        TrainingFocus.MUSCLE -> if (compound) 6..10 else 10..15
        TrainingFocus.STRENGTH, TrainingFocus.SKILL -> if (compound) 3..5 else 8..12
        TrainingFocus.GENERAL -> if (compound) 3..8 else 6..12
    }

    /** Why the reps moved, in the goal's own language. */
    private fun adjustReason(focus: TrainingFocus, compound: Boolean, reps: Int, range: IntRange): String {
        val lead = "Reps set to $reps (${range.first}-${range.last})"
        return when {
            focus == TrainingFocus.MUSCLE ->
                "$lead: builds muscle without grinding - Lopez 2021; Robinson 2024"
            focus == TrainingFocus.STRENGTH || focus == TrainingFocus.SKILL ->
                if (compound) {
                    "$lead: heavy reps build strength on compound exercises - Lopez 2021; Buckner 2017"
                } else {
                    "$lead: extra exercises build muscle at higher reps - Lopez 2021"
                }
            compound ->
                "$lead: compound exercises stay heavy - Lopez 2021"
            else ->
                "$lead: extra exercises carry volume at higher reps - Lopez 2021"
        }
    }

    internal fun dominantMuscle(profile: ExerciseProfile): Muscle? =
        profile.muscles.filterValues { it > 0.0 }
            .entries
            .maxWithOrNull(compareBy { it.value })?.key

    /**
     * Tracked muscles a session's existing movements put in scope, by the
     * region of each movement's DOMINANT muscle. Classifying by pattern put
     * a leg extension or calf raise (pattern ISOLATION) in the upper scope,
     * so improving a lower day added a bench press to it. Tib raises and
     * hip abductions are lower-body work; Pallof presses and leg raises are
     * trunk work, which fits any day.
     */
    internal fun trainedScope(entries: List<PlannedEntry>): List<Muscle> {
        val lowerMuscles = setOf(
            Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.ADDUCTORS,
            Muscle.ABDUCTORS, Muscle.TIBIALIS,
        )
        val trunkMuscles = setOf(Muscle.ABS, Muscle.OBLIQUES, Muscle.HIP_FLEXORS)
        val dominants = entries.mapNotNull { entry ->
            MuscleMap.profile(entry)?.let { dominantMuscle(it) }
        }.toSet()
        val scope = mutableSetOf<Muscle>()
        if (dominants.any { it in lowerMuscles }) {
            scope += listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.ABS)
        }
        if (dominants.any { it !in lowerMuscles && it !in trunkMuscles }) {
            scope += listOf(
                Muscle.MID_CHEST, Muscle.LATS, Muscle.RHOMBOIDS, Muscle.SIDE_DELTS,
                Muscle.REAR_DELTS, Muscle.BICEPS, Muscle.TRICEPS,
            )
        }
        if (dominants.any { it in trunkMuscles }) scope += Muscle.ABS
        return ProgramRules.TRACKED.filter { it in scope }
    }

    /**
     * The push or pull day a preset's own movements make it, or null when it
     * is neither (full body, upper, lower, legs, or empty): only a lone push
     * or lone pull split is policed.
     */
    internal fun dayRoleOf(groups: Set<MuscleGroup>): Role? = when {
        MuscleGroup.LEGS in groups -> null
        MuscleGroup.PUSH in groups && MuscleGroup.PULL !in groups -> Role.PUSH_DAY
        MuscleGroup.PULL in groups && MuscleGroup.PUSH !in groups -> Role.PULL_DAY
        else -> null
    }

    /** Cosine floor a compound or skill movement must clear to stand in for isolation work. */
    internal const val COMPOUND_ONLY_MIN_SIMILARITY = 0.5

    /** Cosine similarity of two profiles' muscle-contribution vectors, 0..1. */
    internal fun similarity(a: ExerciseProfile, b: ExerciseProfile): Double {
        val keys = a.muscles.keys + b.muscles.keys
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (k in keys) {
            val x = a.muscles[k] ?: 0.0
            val y = b.muscles[k] ?: 0.0
            dot += x * y
            na += x * x
            nb += y * y
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / kotlin.math.sqrt(na * nb)
    }

    /**
     * The compound or skill movement closest to an isolation entry's
     * [profile] - any pattern, since an isolation pattern has no compound
     * twin - or none when nothing clears [COMPOUND_ONLY_MIN_SIMILARITY]. Never
     * one already in the session ([taken], lowercase), and never a press on
     * a pull session or a pull on a press session ([dayGroups]): the split
     * stays distinct. Every such movement, closest first: the first is the
     * stand-in, the next two are the alternatives the lifter is offered.
     */
    internal fun compoundStandIns(
        ctx: Ctx,
        profile: ExerciseProfile,
        taken: Set<String>,
        dayGroups: Set<MuscleGroup>,
    ): List<Exercise> = ctx.pool
        .filter { it.name.trim().lowercase() !in taken }
        .filter { candidate ->
            val group = candidate.muscleGroup
            !(group == MuscleGroup.PUSH && MuscleGroup.PULL in dayGroups && MuscleGroup.PUSH !in dayGroups) &&
                !(group == MuscleGroup.PULL && MuscleGroup.PUSH in dayGroups && MuscleGroup.PULL !in dayGroups)
        }
        .filter { similarity(profile, profileOf(it)) >= COMPOUND_ONLY_MIN_SIMILARITY }
        .sortedWith(
            compareByDescending<Exercise> { similarity(profile, profileOf(it)) }
                .thenByDescending { if (profileOf(it).compound) 1 else 0 }
                .thenByDescending { -fitScore(it, ctx.request.equipment) }
                .thenByDescending { -MovementDifficulty.tier(it.name) }
                .thenBy { it.name },
        )

    /**
     * Whether improve() may re-dose [entry]: not a hold, and its catalogue
     * row [dosable]. A name with no row (a user's own movement) goes by
     * name: only a load-priced milestone or a metre skill is left alone.
     */
    private fun redosable(entry: PlannedEntry, catalogue: List<Exercise>): Boolean {
        if (MovementDifficulty.isHoldSet(null, entry.exerciseName, entry.modifiers)) return false
        val row = catalogue.firstOrNull { it.name.equals(entry.exerciseName, ignoreCase = true) }
            ?: Exercise(name = entry.exerciseName, muscleGroup = MuscleGroup.CORE, isWeighted = false)
        return dosable(row)
    }

    /** The top of the level's set range for [name]: where the generator stops growing an entry. */
    private fun rangeTop(request: ProgramRequest, entry: PlannedEntry, name: String): Int =
        ProgramRules.setRange(request.volume, MuscleMap.profile(name, entry.modifiers)?.compound ?: true).last

    /**
     * Most sets improve() leaves on [entry]: the old per-movement ceiling,
     * widened to the level's range top, so a set count the generator wrote
     * is never "fixed" back down while an over-long hand-made entry still is.
     */
    private fun ceilingOf(request: ProgramRequest, entry: PlannedEntry, name: String): Int =
        maxOf(ProgramRules.maxSetsPerEntry(name), rangeTop(request, entry, name))

    /**
     * Improves one preset toward the request's goal, keeping name, day and
     * every entry's modifiers:
     *  - reps and sets move into the goal's ranges (Lopez 2021);
     *  - short-length movements swap for their stretch-biased pattern
     *    equivalent where one exists (Maeo 2021, Maeo 2022, Kassiano 2023,
     *    Wolf 2025) - never a STRENGTH main lift, which must stay itself
     *    (Buckner 2017);
     *  - redundant duplicates beyond need (same dominant muscle and pattern)
     *    are removed;
     *  - tracked muscles this session's role trains, which the whole week
     *    leaves under the tier minimum, gain one movement each;
     *  - missing loads are filled from the strength profile;
     *  - a preset over [ProgramRules.movementCap] loses the entries the week
     *    misses least ([capTrimIndex]), each reported as REMOVED.
     * Movements without a MuscleMap profile (user-created, CSV imports),
     * holds, activities, load-priced milestones and metre skills are passed
     * through untouched ([redosable]): their figure is seconds, a session or
     * a stated load, so no rep range, swap or set top-up applies to them,
     * but their profile still counts toward the week's coverage. With [ProgramRequest.compoundOnly] each of
     * the lifter's own isolation entries becomes its closest compound or
     * skill stand-in ([compoundStandIns]) or is removed - unless removing
     * would empty the session, in which case they stay. Idempotent by
     * construction: run it on its own output and nothing changes.
     *
     * The result is a list of independent [PlanChange]s, not one all-or-nothing
     * preset: each is one edit (a removal, a swap, a set count, a rep target, a
     * load, an addition, a pull-up move) with its tags, coverage effect and
     * ranked options, and [Improvement.apply] builds the preset for any ticked
     * subset. The walk above runs once over the whole preset, so additions and
     * top-ups are proposed against the preset with every other change in;
     * a change that builds on another says so in [PlanChange.requires].
     */
    fun improve(
        target: PlannedPreset,
        restOfWeek: List<PlannedPreset>,
        request: ProgramRequest,
        catalogue: List<Exercise>,
        strength: StrengthProfile,
    ): Improvement {
        val pool = prescribable(catalogue, request)
        val ctx = Ctx(request, pool, strength)
        val weekBefore = ProgramRules.weeklyVolume(restOfWeek + listOf(target))
        // One record per entry as given, filled as the walk decides what its
        // changes are; each becomes its own PlanChange at the end.
        val records = target.entries.map { EntryRecord() }
        val result = mutableListOf<PlannedEntry>()
        // Parallel to result: the index of the given entry it came from, null for an addition.
        val origin = mutableListOf<Int?>()
        val replan = mutableSetOf<String>()
        val trimOrder = mutableListOf<String>()
        val capRemovals = mutableListOf<Pair<Int, String>>()
        val additions = mutableListOf<AddRecord>()
        // First entry per (dominant muscle, pattern) -> its prescribed sets.
        val dominantSeen = mutableMapOf<Pair<Muscle, MovementPattern>, Int>()
        val signatureCeiling = mutableMapOf<Pair<Muscle, MovementPattern>, Int>()

        // Compound & skill only: every isolation entry's stand-in, decided
        // before the walk so two entries never claim the same movement.
        val standIns = mutableMapOf<Int, Exercise?>()
        val standInOptions = mutableMapOf<Int, Ranked>()
        if (request.compoundOnly) {
            val taken = target.entries.map { it.exerciseName.trim().lowercase() }.toMutableSet()
            val dayGroups = target.entries
                .filterNot { MovementDifficulty.isIsolation(it.exerciseName) }
                .mapNotNull { e -> catalogue.firstOrNull { it.name.equals(e.exerciseName, ignoreCase = true) }?.muscleGroup }
                .toSet()
            target.entries.forEachIndexed { index, entry ->
                if (!MovementDifficulty.isIsolation(entry.exerciseName)) return@forEachIndexed
                val profile = MuscleMap.profile(entry.exerciseName) ?: return@forEachIndexed
                val candidates = compoundStandIns(ctx, profile, taken, dayGroups)
                val standIn = candidates.firstOrNull()
                standIn?.let { taken += it.name.trim().lowercase() }
                standIns[index] = standIn
                standInOptions[index] = ProgramSuggestions.rank(
                    ctx, candidates, dominantMuscle(profile), profile, weekBefore, standInFor = entry.exerciseName,
                )
            }
        }
        // Never an empty session: when nothing but isolation without a
        // stand-in is left, it stays.
        val dropAllowed = target.entries.indices.any { it !in standIns || standIns[it] != null }

        for ((index, entry) in target.entries.withIndex()) {
            val record = records[index]
            // Modifiers count: a deficit push-up is already long-length work.
            val profile = MuscleMap.profile(entry)
            if (profile == null || !redosable(entry, catalogue)) {
                // Unknown movement, a hold, an activity or a milestone: kept,
                // untouched, modifiers intact.
                result += entry
                origin += index
                continue
            }
            var name = entry.exerciseName
            var sets = entry.sets
            var reps = entry.reps
            var load = entry.targetWeightKg
            var loadNote = entry.loadNote

            if (index in standIns) {
                val standIn = standIns[index]
                if (standIn != null) {
                    record.swap = SwapRecord(
                        standIn,
                        "Swapped for ${standIn.name}: compound & ${Words.technique.s} only, and it trains the same " +
                            "muscles - Gentil 2015",
                        standInOptions.getValue(index),
                        clearsLoad = true,
                    )
                    name = standIn.name
                    // The isolation load says nothing about the stand-in.
                    load = null
                    loadNote = null
                } else if (dropAllowed) {
                    record.removal = "Removed: with Compound & ${Words.technique.s} only on, nothing replaces its muscles - Gentil 2015"
                    continue
                }
            }

            // Specificity guard: a STRENGTH main lift is never swapped away.
            val swapAllowed = !(request.focus == TrainingFocus.STRENGTH && isMainLift(name))
            if (record.swap == null && swapAllowed && !profile.stretchBias) {
                // Never onto a movement the preset already holds: one
                // exercise appears once per session (nor onto one a
                // compound-only stand-in has already claimed).
                val twins = ProgramSuggestions.stretchTwins(
                    ctx, name, profile,
                    target.entries.map { it.exerciseName } + result.map { it.exerciseName } +
                        standIns.values.mapNotNull { it?.name },
                )
                val replacement = twins.firstOrNull()
                if (replacement != null) {
                    record.swap = SwapRecord(
                        replacement,
                        "Swapped for ${replacement.name}: loads the " +
                            "${dominantMuscle(profile)?.label?.lowercase() ?: "target muscle"} stretched, " +
                            "where it grows more - ${longLengthEvidence(dominantMuscle(profile))}",
                        ProgramSuggestions.rank(ctx, twins, dominantMuscle(profile), profile, weekBefore),
                        clearsLoad = false,
                    )
                    name = replacement.name
                }
            }
            val newProfile = MuscleMap.profile(name, entry.modifiers) ?: profile

            val range = repRange(request.focus, newProfile.compound)
            var repsAdjusted = false
            // A dumbbell-capped entry sits above the range on purpose: its
            // load cannot go up, so the extra reps keep the set near failure.
            if (reps !in range && !(atDumbbellCap(name, load, request.equipment) && reps > range.last)) {
                reps = if (reps < range.first) range.first else range.last
                repsAdjusted = true
                record.reps = reps to adjustReason(request.focus, newProfile.compound, reps, range)
            }
            if (sets < 2) {
                sets = 2
                record.sets = sets to if (repsAdjusted) {
                    "Sets ${entry.sets} → 2: one set is too little work to progress on"
                } else {
                    adjustReason(request.focus, newProfile.compound, reps, range)
                }
            }
            val ceiling = ceilingOf(request, entry, name)
            if (sets > ceiling) {
                record.sets = ceiling to "Sets $sets → $ceiling: past $ceiling sets one exercise adds little in a single ${Words.trial.s}; " +
                    "the rest belongs on another exercise or day - Remmert 2025"
                sets = ceiling
            }

            if (load == null) {
                val exercise = pool.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: catalogue.firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (exercise != null) {
                    val filled = fillLoad(exercise, strength, reps, ProgramRules.loadRir(newProfile.compound), request.equipment)
                    if (filled != null) {
                        load = filled.kg
                        reps = filled.reps
                        loadNote = filled.note
                        record.load = LoadRecord(
                            filled.kg, filled.reps, filled.note,
                            "Load set from your logged sets (${filled.note}) - Zourdos 2016",
                        )
                    }
                }
            }

            val dominant = dominantMuscle(newProfile)
            // No lead by design, no main muscle: a knee-to-wall drill's 0.25
            // calf credit once made the lifter's calf raise "redundant", so
            // every pass removed and re-added it.
            val signature = dominant?.takeIf { !newProfile.noLeadByDesign }?.let { it to newProfile.pattern }
            val firstSets = signature?.let { dominantSeen[it] }
            if (firstSets != null && firstSets < signatureCeiling.getValue(signature!!)) {
                // Redundant beyond need: the session already owns this
                // muscle through this pattern and the first copy has sets to
                // spare (Gentil 2015). At its set ceiling a second variant
                // is the only way to add volume, as in the week generator.
                record.removal =
                    "Removed: another exercise already trains ${dominant.label.lowercase()} the same way - Gentil 2015"
                record.swap = null
                continue
            }
            if (signature != null && firstSets == null) {
                dominantSeen[signature] = sets
                signatureCeiling[signature] = rangeTop(request, entry, name)
            }
            val planned = ProgramRules.withPlan(
                entry.copy(exerciseName = name, sets = sets, reps = reps, targetWeightKg = load, loadNote = loadNote),
                request.volume, request.focus,
            )
            result += planned
            origin += index
            replan += planned.exerciseName.trim().lowercase()
        }

        // The cap the lifter asked for: a preset over it gives up, one at a
        // time, the entry the week misses least, each with its reason line.
        while (result.size > ctx.cap) {
            val week = ProgramRules.weeklyVolume(restOfWeek + listOf(target.copy(entries = result)))
            val drop = capTrimIndex(result, week, ProgramRules.COVERAGE_BAND) ?: break
            val cost = capTrimCost(result[drop], week, ProgramRules.COVERAGE_BAND)
            result.removeAt(drop)
            val gone = origin.removeAt(drop)!!
            // Its swap, rep or load lines described an entry that is gone.
            records[gone].dropped = true
            trimOrder += target.entries[gone].exerciseName
            capRemovals += gone to if (cost < 1e-9) {
                "Removed: over your ${ctx.cap}-exercise cap, and the ${Words.cycle.s} already covers its muscles - Pelland 2026"
            } else {
                "Removed: over your ${ctx.cap}-exercise cap, and it helped short muscles least - Pelland 2026"
            }
        }

        // Additions: tracked muscles this session's role trains that the
        // whole week (this preset + the rest) leaves under the tier minimum.
        val scope = trainedScope(result)
        val dayRole = dayRoleOf(
            result.mapNotNull { e -> catalogue.firstOrNull { it.name.equals(e.exerciseName, ignoreCase = true) }?.muscleGroup }
                .toSet(),
        )
        val weekVolume = ProgramRules.weeklyVolume(
            restOfWeek + listOf(target.copy(entries = result)),
        )
        for (muscle in scope) {
            if (result.size >= ctx.cap) break
            val covered = result.any {
                (MuscleMap.profile(it)?.muscles?.get(muscle) ?: 0.0) >= 0.5
            }
            val minimum = ProgramRules.COVERED_MIN
            if (covered || (weekVolume[muscle] ?: 0.0) >= minimum) continue
            val draft = Draft(target.scheduledDay, null)
            // Deliberately no fit bound: the lifter's own preset defines the
            // session, and refusing calf machines because a barbell squat is
            // present would leave the muscle untrained.
            draft.names += result.map { it.exerciseName }
            // Never a pull on a push preset or a press on a pull preset: the
            // split the lifter built stays distinct, as in week().
            val fitting = rankCandidates(ctx, draft, null, muscle).filter { groupFitsSession(dayRole, it.muscleGroup) }
            val exercise = fitting.firstOrNull() ?: continue
            val seconds = ProgramRules.sessionSeconds(result, request.focus) +
                3 * ProgramRules.setSeconds(request.focus, exercise.name)
            if (seconds > ctx.budgetSeconds) continue
            if (!ProgramRules.sessionMuscleRoom(result, exercise.name, 3)) continue
            val deficit = ProgramRules.COVERED_MIN - (weekVolume[muscle] ?: 0.0)
            val added = add(ctx, draft, exercise, why = muscleWhy(exercise, muscle, ctx, deficit), sets = 3, forMuscle = muscle)
            // add() prescribes the focus anchor; keep it inside the same
            // rep range improve holds every other entry to, or the second
            // pass would "fix" what this pass just added - except the reps a
            // dumbbell cap raised, which the second pass keeps too.
            val range = repRange(ctx.focus, profileOf(exercise).compound)
            val inRange = if (atDumbbellCap(added.exerciseName, added.targetWeightKg, request.equipment) && added.reps > range.last) {
                added
            } else {
                added.copy(reps = added.reps.coerceIn(range))
            }
            additions += AddRecord(
                resultIndex = result.size,
                exerciseName = exercise.name,
                detail = "Added ${exercise.name}: the week was ${setsPhrase(deficit)} short on " +
                    "${muscle.label.lowercase()} - Pelland 2026",
                options = ProgramSuggestions.rank(ctx, fitting, muscle, null, weekBefore),
            )
            result += inRange
            origin += null
        }

        // Top-ups: a muscle this session already trains that the week still
        // leaves under the minimum gains sets on its most direct movement
        // here - up to the movement's set ceiling and the session's muscle
        // ceiling, inside the session time budget, and never pushing another
        // tracked muscle past the top of its range.
        // Without this, improve answered "no changes needed" for a day whose
        // calves sat half a set under target with a calf raise at 2 sets.
        val originalSets = result.map { it.sets }
        val raisedFor = result.indices.associateWith { mutableSetOf<Muscle>() }
        // The scope after the additions: a movement just added brings its own
        // region in, and a second pass must find nothing left to raise.
        for (muscle in trainedScope(result)) {
            while (true) {
                val week = ProgramRules.weeklyVolume(restOfWeek + listOf(target.copy(entries = result)))
                if ((week[muscle] ?: 0.0) >= ProgramRules.COVERED_MIN) break
                val index = result.indices.filter { i ->
                    val profile = MuscleMap.profile(result[i]) ?: return@filter false
                    redosable(result[i], catalogue) &&
                        (profile.muscles[muscle] ?: 0.0) >= 0.5 &&
                        result[i].sets < rangeTop(request, result[i], result[i].exerciseName) &&
                        ProgramRules.sessionSeconds(result, request.focus) +
                        ProgramRules.setSeconds(request.focus, result[i].exerciseName) <=
                        ctx.budgetSeconds &&
                        ProgramRules.sessionMuscleRoom(result, result[i].exerciseName, 1) &&
                        profile.muscles.none { (other, share) ->
                            other in ProgramRules.TRACKED &&
                                (week[other] ?: 0.0) + share > ProgramRules.HEAVY_WEEK + 1e-9
                        }
                }.sortedWith(
                    compareByDescending<Int> { MuscleMap.profile(result[it].exerciseName)!!.muscles[muscle] ?: 0.0 }
                        .thenBy { result[it].sets },
                ).firstOrNull() ?: break
                result[index] = ProgramRules.withPlan(
                    result[index].copy(sets = result[index].sets + 1), request.volume, request.focus,
                )
                raisedFor.getValue(index) += muscle
            }
        }
        // A raise on an entry from the preset is one SETS change from its given sets; on an
        // addition it is already in the added entry.
        val topUps = mutableListOf<Int>()
        result.indices.filter { result[it].sets > originalSets[it] }.forEach { i ->
            val from = origin[i] ?: return@forEach
            val muscles = raisedFor.getValue(i).joinToString(" and ") { it.label.lowercase() }
            records[from].sets = result[i].sets to
                "Sets ${target.entries[from].sets} → ${result[i].sets}: the week was short on $muscles - Pelland 2026"
            records[from].topUp = raisedFor.getValue(i).toList()
            topUps += from
        }

        // Owner rule: no two pull-up variants back to back. Each exercise
        // moved up to split them says so.
        val spaced = spacePullUps(result)
        val spacing = mutableListOf<PlannedEntry>()
        spaced.forEachIndexed { at, entry ->
            if (at < result.indexOfFirst { it === entry }) spacing += entry
        }

        // ---- the independent changes, one per edit.
        val changes = mutableListOf<PlanChange>()
        val swapIds = mutableMapOf<Int, String?>()

        fun change(kind: PlanChange.Kind, name: String, detail: String, op: ChangeOp, tags: List<ReasonTag> = emptyList(),
                   requires: String? = null, options: Ranked = Ranked(emptyList())): PlanChange {
            val made = PlanChange(
                id = PlanChange.idOf(name, kind, op), kind = kind, exerciseName = name, detail = detail,
                tags = tags.take(2), op = op, requires = requires, options = options,
            )
            changes += made
            return made
        }

        target.entries.forEachIndexed { index, entry ->
            val record = records[index]
            if (record.dropped) return@forEachIndexed
            record.removal?.let {
                change(
                    PlanChange.Kind.REMOVED, entry.exerciseName, it, ChangeOp.RemoveEntry(entry.exerciseName),
                    tags = if (it.contains("already trains")) listOf(ReasonTag(Reason.SAME_PATTERN_AS_MAIN)) else emptyList(),
                )
                return@forEachIndexed
            }
            var name = entry.exerciseName
            var swapId: String? = null
            record.swap?.let { swap ->
                name = swap.to.name
                val to = entry.copy(
                    exerciseName = swap.to.name,
                    targetWeightKg = if (swap.clearsLoad) null else entry.targetWeightKg,
                    loadNote = if (swap.clearsLoad) null else entry.loadNote,
                )
                swapId = change(
                    PlanChange.Kind.SWAPPED, name, swap.detail, ChangeOp.SwapEntry(entry.exerciseName, to),
                    tags = swap.options.options.firstOrNull()?.tags.orEmpty(), options = swap.options,
                ).id
                swapIds[index] = swapId
            }
            record.reps?.let { (value, detail) ->
                change(PlanChange.Kind.ADJUSTED, name, detail, ChangeOp.SetReps(name, value), requires = swapId)
            }
            if (record.topUp == null) {
                record.sets?.let { (value, detail) ->
                    change(PlanChange.Kind.ADJUSTED, name, detail, ChangeOp.SetSets(name, value), requires = swapId)
                }
            }
            record.load?.let { load ->
                change(
                    PlanChange.Kind.LOAD_SET, name, load.detail, ChangeOp.SetLoad(name, load.kg, load.reps, load.note),
                    tags = if (name.trim().lowercase() in strength.bestE1rmKg) listOf(ReasonTag(Reason.YOU_LOG_THIS)) else emptyList(),
                    requires = swapId,
                )
            }
        }
        for ((gone, detail) in capRemovals) {
            val name = target.entries[gone].exerciseName
            change(PlanChange.Kind.REMOVED, name, detail, ChangeOp.RemoveEntry(name))
        }
        for (addition in additions) {
            val entry = result[addition.resultIndex]
            change(
                PlanChange.Kind.ADDED, addition.exerciseName, addition.detail, ChangeOp.AddEntry(entry),
                tags = addition.options.options.firstOrNull()?.tags.orEmpty(), options = addition.options,
            )
        }
        for (index in topUps) {
            val record = records[index]
            val name = record.swap?.to?.name ?: target.entries[index].exerciseName
            val (value, detail) = record.sets!!
            change(
                PlanChange.Kind.ADJUSTED, name, detail, ChangeOp.SetSets(name, value),
                tags = record.topUp!!.map { ReasonTag(Reason.FILLS_SHORT_MUSCLE, it) }, requires = swapIds[index],
            )
        }
        for (entry in spacing) {
            change(
                PlanChange.Kind.ADJUSTED, entry.exerciseName, "Moved up so no two pull-up variants run back to back",
                ChangeOp.SpaceEntry(entry.exerciseName),
            )
        }

        val plan = ApplyPlan(request.volume, request.focus, ctx.cap, trimOrder, replan)
        // Each change's own effect on the week: itself, and what it requires, applied alone.
        val withDeltas = changes.map { made ->
            val alone = ProgramChanges.apply(target, changes, plan, ProgramChanges.closure(changes, made.id))
            val delta = ProgramRules.weeklyVolume(listOf(alone)).let { now ->
                val was = ProgramRules.weeklyVolume(listOf(target))
                (now.keys + was.keys).associateWith { (now[it] ?: 0.0) - (was[it] ?: 0.0) }
                    .filterValues { kotlin.math.abs(it) > 1e-9 }
            }
            made.copy(coverageDelta = delta)
        }
        return Improvement(before = target, changes = withDeltas, plan = plan)
    }

    /** What improve decided for one entry as given, before it becomes independent changes. */
    private class EntryRecord {
        var swap: SwapRecord? = null
        var reps: Pair<Int, String>? = null
        var sets: Pair<Int, String>? = null
        var load: LoadRecord? = null
        var removal: String? = null

        /** Muscles a top-up raised the sets for, when one did (it owns [sets]). */
        var topUp: List<Muscle>? = null

        /** Trimmed by the cap: nothing else about it is reported. */
        var dropped = false
    }

    private class SwapRecord(val to: Exercise, val detail: String, val options: Ranked, val clearsLoad: Boolean)

    private class LoadRecord(val kg: Double?, val reps: Int, val note: String?, val detail: String)

    private class AddRecord(val resultIndex: Int, val exerciseName: String, val detail: String, val options: Ranked)

    // ------------------------------------------------------------------ note

    /**
     * The one-line rest and effort guidance every generated preset carries
     * in its note - the UI shows it under the workout name. The effort half
     * is the [EffortCue] split: compounds stop 0-2 reps short of failure,
     * single-joint work goes to failure on its last set (Robinson 2024;
     * Refalo 2023).
     */
    internal fun presetNote(tier: VolumeLevel, focus: TrainingFocus): String {
        val effort = "Compound exercises: stop 0-2 reps short of failure; single-joint: last set to failure"
        return if (focus == TrainingFocus.STRENGTH || focus == TrainingFocus.GENERAL) {
            "Rest 3-5 min on compound exercises, 90 s on the rest. $effort " +
                "(Schoenfeld 2016; Singer 2024; Refalo 2023)."
        } else {
            "Rest 2-3 min, at least 90 s. $effort (Singer 2024; Refalo 2023)."
        }
    }

    // ------------------------------------------------- overlap, easing, days

    /** Rows of [preset] that do one job; replacements come with a [request] and [catalogue]. */
    fun overlaps(
        preset: PlannedPreset,
        request: ProgramRequest? = null,
        catalogue: List<Exercise> = emptyList(),
    ): List<OverlapFinding> = Overlap.detect(preset, request, catalogue)

    /** Per heavy muscle, the ranked ways to ease it; empty when nothing is heavy. */
    fun easing(
        week: List<PlannedPreset>,
        request: ProgramRequest,
        catalogue: List<Exercise>,
        strength: StrengthProfile = StrengthProfile(),
    ): List<EasingSuggestion> = Easing.suggest(week, request, catalogue, strength)

    /** The three best sessions of [week] to put [entry] in, best first. */
    fun bestFitDays(entry: PlannedEntry, week: List<PlannedPreset>, request: ProgramRequest): RankedDays =
        Recovery.bestFitDays(entry, week, request)
}
