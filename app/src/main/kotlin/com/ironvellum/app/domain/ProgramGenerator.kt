package com.ironvellum.app.domain

import com.ironvellum.app.domain.MovementDifficulty.FREE_WEIGHT_LOAD
import kotlin.math.ceil

/**
 * Proposes evidence-backed training weeks, single sessions, and improvements
 * to existing presets. Pure and dependency-free: it reads only the [Exercise]
 * list it is given plus the shared tables ([MuscleMap], [MovementDifficulty],
 * [Progression], [ProgramRules]); every number names its [Evidence].
 *
 * STRUCTURE - multi-joint backbone per pattern, then volume by deficit
 * (Gentil 2015, Paoli 2017: single-joint work adds little on its own, so it
 * is used only to close a tracked muscle's weekly deficit toward its tier
 * target - Pelland 2026 fractional counting). Prioritised muscles are pushed
 * to the TOP of the tier range, everything else to the bottom.
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
 * (Lopez 2021, Schoenfeld 2016); accessories are hypertrophy-style.
 *
 * MUSCLE - 6-15 reps at 1-3 RIR (Lopez 2021, Robinson 2024, Refalo 2023);
 * beginners sit at the conservative 3 RIR. GENERAL - strength-style compounds
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

    private enum class Role { FULL_BODY, UPPER, LOWER, PUSH_DAY, PULL_DAY, LEGS_DAY }

    private fun roleLabel(role: Role): String = when (role) {
        Role.FULL_BODY -> "Full Body"
        Role.UPPER -> "Upper"
        Role.LOWER -> "Lower"
        Role.PUSH_DAY -> "Push"
        Role.PULL_DAY -> "Pull"
        Role.LEGS_DAY -> "Legs"
    }

    /** ISO weekday plus role, per requested day count - the RoutineBuilder table. */
    private val SPLITS: Map<Int, List<Pair<Int, Role>>> = mapOf(
        1 to listOf(1 to Role.FULL_BODY),
        2 to listOf(1 to Role.FULL_BODY, 4 to Role.FULL_BODY),
        3 to listOf(1 to Role.FULL_BODY, 3 to Role.FULL_BODY, 5 to Role.FULL_BODY),
        4 to listOf(1 to Role.UPPER, 2 to Role.LOWER, 4 to Role.UPPER, 5 to Role.LOWER),
        5 to listOf(
            1 to Role.PUSH_DAY, 2 to Role.PULL_DAY, 4 to Role.LEGS_DAY,
            5 to Role.UPPER, 6 to Role.LOWER,
        ),
        6 to listOf(
            1 to Role.PUSH_DAY, 2 to Role.PULL_DAY, 3 to Role.LEGS_DAY,
            5 to Role.PUSH_DAY, 6 to Role.PULL_DAY, 7 to Role.LEGS_DAY,
        ),
    )

    /**
     * Backbone patterns per session role, in prescription order. Full-body
     * days alternate A/B (fullBodyIndex even = A, odd = B; null = a lone
     * session) so every week trains both pulls and both pushes: the old single
     * list carried a vertical pull and no row, so a 3-day beginner week never
     * rowed and upper back and rear delts sat at 6 sets.
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
     * Machine detection, identical to the picker's rule: loadFactor below the
     * free-weight reference means machine, cable, sled or Smith, and the
     * "assisted" prefix names the assisted machines loadFactor does not cover.
     */
    private fun isMachine(exercise: Exercise): Boolean =
        exercise.name.trim().lowercase().startsWith("assisted") ||
            MovementDifficulty.loadFactor(exercise.name) < FREE_WEIGHT_LOAD

    private fun equipmentAllows(exercise: Exercise, equipment: EquipmentAccess): Boolean {
        if (exercise.category.isNotBlank()) return false
        // Holds are measured in seconds; a rep target on a plank is nonsense.
        if (exercise.metric != ExerciseMetric.REPS) return false
        if (!exercise.isWeighted) return true
        if (equipment == EquipmentAccess.BODYWEIGHT) return false
        if (equipment == EquipmentAccess.HOME_WEIGHTS) return !isMachine(exercise)
        return true
    }

    /** How well the implement matches the access level: free weight 0, machine 1,
     * bodyweight 2 - and assisted machines last of all (3): they are regressions
     * for lifters who cannot yet do the bodyweight movement, never choices for
     * people who can. */
    private fun fitScore(exercise: Exercise, equipment: EquipmentAccess): Int = when {
        equipment == EquipmentAccess.BODYWEIGHT -> 0
        !exercise.isWeighted -> 2
        exercise.name.trim().lowercase().startsWith("assisted") -> 3
        isMachine(exercise) -> 1
        else -> 0
    }

    /** A movement the skill tree owns, detectable without importing Skills. */
    private fun isSkillTree(exercise: Exercise): Boolean =
        MovementDifficulty.isClassified(exercise.name) &&
            exercise.name.trim().lowercase() !in MovementDifficulty.catalogueOnlyKeys

    /**
     * The pool the generator may prescribe from: lifting rows with a REPS
     * metric under the equipment, never the milestone rows, and only
     * movements a [MuscleMap] profile exists for - an unprofiled movement
     * cannot be volume-counted or explained, so it degrades out. Skills
     * measured in metres (Handstand Walk) are also out: Seed stamps their
     * catalogue rows REPS, but "10 metres" is not a rep target a program
     * can dose.
     */
    internal fun eligible(
        catalogue: List<Exercise>,
        equipment: EquipmentAccess,
        focus: TrainingFocus,
    ): List<Exercise> =
        catalogue.filter {
            equipmentAllows(it, equipment) &&
                !MovementDifficulty.isLoadPriced(it.name) &&
                MuscleMap.profile(it.name) != null &&
                (focus == TrainingFocus.SKILL || !MuscleMap.isTechnique(it.name)) &&
                Skills.ALL.firstOrNull { skill -> skill.name.equals(it.name, ignoreCase = true) }
                    ?.metric != Skills.Metric.METRES
        }

    // ----------------------------------------------------------- session draft

    private class Draft(val day: Int?, val role: Role?) {
        val entries = mutableListOf<PlannedEntry>()
        val names = mutableSetOf<String>()

        /** Equipment fit per prescribed movement, for the pattern-fit rule. */
        val fits = mutableMapOf<String, Int>()
    }

    private class Ctx(
        val request: ProgramRequest,
        val pool: List<Exercise>,
        val strength: StrengthProfile,
        val cap: Int,
    ) {
        val tier get() = request.tier
        val focus get() = request.focus
        val priorityMuscles: Set<Muscle> = request.priorities.flatMap { it.muscles }.toSet()
        val targetRange = ProgramRules.weeklySetTarget(request.tier, request.focus)

        /** Priorities are pushed to the TOP of the tier range, the rest to the bottom. */
        fun targetFor(muscle: Muscle): Double =
            if (muscle in priorityMuscles) targetRange.endInclusive else targetRange.start
    }

    // ------------------------------------------------------------- selection

    private fun profileOf(exercise: Exercise): ExerciseProfile =
        MuscleMap.profile(exercise.name) ?: error("unprofiled ${exercise.name} reached selection")

    /**
     * Deterministic preference order for one slot: the practised main lift
     * first (specificity), then the accessible skill progression (SKILL),
     * then the long-length movement (Wolf 2025 and the lengthened-position
     * trials), the bigger contribution to the requested muscle, the
     * compound, the better equipment fit, the lower tier, then the name.
     * [exclude] names movements a fallback slot must not reuse (mains whose
     * practice target for the week is already met).
     *
     * Fit rule (ported, refined to its real target): the same movement
     * pattern loading the same muscles must not appear at two different
     * implement fits in one session - "Bodyweight Squat beside Back Squat"
     * cannot happen. Different patterns may use different implements freely
     * (a barbell hinge beside a machine leg curl is normal programming).
     */
    private fun ranked(
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
            compareByDescending<Exercise> {
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
                .thenBy { fitScore(it, ctx.request.equipment) }
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
                    if (it.isWeighted) tier else kotlin.math.abs(tier - desiredBodyweightTier(ctx.tier))
                }
                .thenBy { it.name },
        )

    private fun pick(
        ctx: Ctx,
        session: Draft,
        pattern: MovementPattern?,
        muscle: Muscle?,
        exclude: Set<String> = emptySet(),
    ): Exercise? = ranked(ctx, session, pattern, muscle, exclude).firstOrNull()

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
        sets: Int,
        why: String,
    ): PlannedEntry {
        val profile = profileOf(exercise)
        val reps = prescriptionReps(ctx, profile)
        val load = fillLoad(exercise, ctx.strength, reps, ProgramRules.targetRir(ctx.tier, ctx.focus))
        val fit = fitScore(exercise, ctx.request.equipment)
        session.fits[exercise.name] = fit
        session.names += exercise.name
        val entry = PlannedEntry(
            exerciseName = exercise.name,
            sets = sets.coerceIn(2, 5),
            reps = reps,
            targetWeightKg = load?.first,
            why = why,
            loadNote = load?.second,
        )
        session.entries += entry
        return entry
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
    private fun desiredBodyweightTier(tier: ExperienceTier): Int = when (tier) {
        ExperienceTier.BEGINNER -> 2
        ExperienceTier.INTERMEDIATE -> 3
        ExperienceTier.ADVANCED -> 4
    }

    /** Sets for a deficit fill: cover the remaining deficit, clamped to 2-5. */
    private fun setsForDeficit(deficit: Double, contribution: Double): Int {
        if (contribution <= 0.0) return 2
        return ceil(deficit / contribution).toInt().coerceIn(2, 5)
    }

    /**
     * Loads from the lifter's own PRs; a related logged lift estimates the
     * rest (labelled); bodyweight work carries no load. Prescriptions past
     * ~10 reps stop trusting the Epley inversion, so the estimate path caps
     * its reps (LeSuer 1997).
     */
    internal fun fillLoad(
        exercise: Exercise,
        strength: StrengthProfile,
        reps: Int,
        rir: Int,
    ): Pair<Double, String>? {
        if (!exercise.isWeighted) return null
        val group = exercise.muscleGroup.name
        val direct = ProgramRules.workingLoadKg(exercise.name, group, strength, reps, rir)
        if (direct != null) return direct
        return ProgramRules.estimatedLoadKg(
            exercise.name, group, strength, reps.coerceAtMost(ProgramRules.MAX_WORKING_REPS), rir,
        )
    }

    // ------------------------------------------------------------------ week

    fun week(request: ProgramRequest, catalogue: List<Exercise>, strength: StrengthProfile): RoutinePlan {
        val days = request.daysPerWeek.coerceIn(1, 6)
        val pool = eligible(catalogue, request.equipment, request.focus)
        if (pool.isEmpty()) return RoutinePlan(emptyList())
        val ctx = Ctx(request, pool, strength, ProgramRules.sessionCap(request.tier))
        val sessions = (SPLITS[days] ?: SPLITS.getValue(2)).map { (day, role) -> Draft(day, role) }

        // Phase 1: multi-joint backbone per pattern slot. STRENGTH and
        // GENERAL lead their main-lift patterns with the lift itself
        // (specificity - Buckner 2017), up to the practice-frequency target.
        val mainTargets = mapOf(
            "back squat" to 2, "bench press" to 2,
            "deadlift" to mainTargetCount(days), "overhead press" to mainTargetCount(days),
        )
        val mainCount = mutableMapOf<String, Int>()
        val exhausted = mutableSetOf<String>()
        for (session in sessions) {
            val role = session.role ?: continue
            val fullBodyIndex = sessions.filter { it.role == Role.FULL_BODY }.indexOf(session)
            for (pattern in backboneSlots(ctx, role, fullBodyIndex.takeIf { it >= 0 && sessions.size > 1 })) {
                val mainName = MAIN_LIFTS[pattern]
                var handled = false
                if (mainName != null && wantsMainLift(ctx, pattern)) {
                    val key = mainName.lowercase()
                    if (mainCount.getOrDefault(key, 0) < (mainTargets[key] ?: 1)) {
                        val main = pool.firstOrNull { it.name.equals(mainName, ignoreCase = true) }
                            ?: closestEquivalent(ctx, pool, pattern, session)
                        if (main != null) {
                            mainCount.merge(key, 1, Int::plus)
                            if ((mainTargets[key] ?: 1) <= mainCount.getValue(key)) exhausted += mainName
                            add(
                                ctx, session, main, sets = 3,
                                why = if (main.name.equals(mainName, true)) {
                                    "Practises the ${main.name} itself: strength is specific to the " +
                                        "lift you train, and it loads your ${muscleListOf(main)} - " +
                                        "Buckner 2017; TaskSpec 2025"
                                } else {
                                    "No access to the ${mainName} here, so the ${main.name} stands in: " +
                                        "closest match in pattern and muscles - a fair swap for muscle " +
                                        "growth, never for max strength - Kikuchi 2017; Buckner 2017"
                                },
                            )
                            handled = true
                        }
                    }
                }
                if (!handled) {
                    val fallback = pick(ctx, session, pattern, null, exclude = exhausted) ?: continue
                    add(ctx, session, fallback, sets = 3, why = backboneWhy(fallback, ctx.focus))
                }
            }
        }

        // Phase 2: close each tracked muscle's weekly fractional deficit.
        val capacityLimited = fillDeficits(ctx, sessions) { muscle -> ctx.targetFor(muscle) }

        // Honest capacity line: when the chosen days cannot fit the tier's
        // weekly range under the per-session ceiling, name the muscles that
        // land short and by how much. The old line quoted the single lowest
        // muscle as "about N sets per muscle", which read as if the whole week
        // were that thin.
        val volume = weeklyVolumeOf(sessions)
        val short = ProgramRules.TRACKED.filter { (volume[it] ?: 0.0) < ctx.targetRange.start - 0.5 }
        val capacityNote = if (capacityLimited && short.isNotEmpty()) {
            val low = short.minOf { volume[it] ?: 0.0 }
            " Heads-up: $days days leave ${joinWithAnd(short.map { it.label.lowercase() })} short of the " +
                "${ctx.tier.label.lowercase()} range (${ctx.targetRange.start.toInt()}-" +
                "${ctx.targetRange.endInclusive.toInt()} sets a week; the lowest sits at ${setsPhrase(low)}). " +
                "Add a day to reach it."
        } else {
            ""
        }

        val roleSeen = mutableMapOf<Role, Int>()

        return RoutinePlan(
            sessions
                .filter { it.entries.isNotEmpty() }
                .map { session ->
                    // Ordinal letters count per ROLE: Upper A, Lower A,
                    // Upper B, Lower B - not one alphabet across the week.
                    val ordinal = 'A' + roleSeen.merge(session.role!!, 1, Int::plus)!! - 1
                    PlannedPreset(
                        name = "${roleLabel(session.role)} $ordinal",
                        note = presetNote(ctx.tier, ctx.focus).let { base ->
                            base + (if (session.day == sessions.mapNotNull { d -> d.day }.minOrNull()) capacityNote else "")
                        },
                        scheduledDay = session.day,
                        entries = session.entries.toList(),
                    )
                },
        )
    }

    /** Deadlift and press practice scales with the room the week has. */
    private fun mainTargetCount(days: Int): Int = if (days >= 5) 2 else 1

    private fun backboneSlots(ctx: Ctx, role: Role, fullBodyIndex: Int?): List<MovementPattern> =
        slotsFor(role, ctx.focus, fullBodyIndex)

    private fun backboneSets(focus: TrainingFocus): Int = 3

    /**
     * Why a compound anchors the session, in the goal's own terms: a strength
     * week keeps its non-tested compounds for the muscle behind the lifts,
     * not for "long-length growth", and a hypertrophy week names the long
     * length only when the movement really loads its target there.
     */
    private fun backboneWhy(exercise: Exercise, focus: TrainingFocus): String {
        val profile = profileOf(exercise)
        val muscles = muscleListOf(exercise)
        return when {
            focus == TrainingFocus.STRENGTH || focus == TrainingFocus.GENERAL ->
                "${exercise.name} is the ${patternLabel(profile.pattern)} compound here: it builds the " +
                    "$muscles behind your main lifts and keeps pushing and pulling balanced - Gentil 2015"
            profile.stretchBias ->
                "${exercise.name} is the ${patternLabel(profile.pattern)} compound here: one movement " +
                    "trains your $muscles together, loaded deep in the stretch where muscle grows best - " +
                    "Gentil 2015; ${longLengthEvidence(dominantMuscle(profile))}"
            else ->
                "${exercise.name} is the ${patternLabel(profile.pattern)} compound here: one movement " +
                    "trains your $muscles together - Gentil 2015"
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

    /** Main muscles of a movement, plain-labelled and deterministic: "chest, front delts and triceps". */
    internal fun muscleListOf(exercise: Exercise, include: Muscle? = null): String {
        val profile = profileOf(exercise)
        val shares = profile.muscles.filterValues { it >= 0.5 }
        val source = if (shares.isEmpty()) profile.muscles else shares
        val names = source.entries.sortedByDescending { it.value }.take(3).map { it.key }
        val ordered = if (include != null && include !in names) listOf(include) + names else names
        return joinWithAnd(ordered.map { it.label.lowercase() })
    }

    private fun joinWithAnd(items: List<String>): String = when (items.size) {
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
    ): Exercise? {
        // Same pattern, closest MuscleMap profile: prefer a compound the
        // equipment fits, deterministic down to the name.
        return pool
            .filter { profileOf(it).pattern == pattern && it.name !in session.names }
            .maxWithOrNull(
                compareBy(
                    { if (profileOf(it).compound) 1 else 0 },
                    { -fitScore(it, ctx.request.equipment) },
                    { -MovementDifficulty.tier(it.name) },
                    { it.name },
                ),
            )
    }

    // -------------------------------------------------------------- deficits

    private fun weeklyVolumeOf(sessions: List<Draft>): Map<Muscle, Double> {
        val volume = mutableMapOf<Muscle, Double>()
        for (session in sessions) for (entry in session.entries) {
            val profile = MuscleMap.profile(entry.exerciseName) ?: continue
            for ((muscle, share) in profile.muscles) {
                if (share > 0.0) volume.merge(muscle, entry.sets * share, Double::plus)
            }
        }
        return volume
    }

    /**
     * Closes each tracked muscle's weekly deficit ONE SET AT A TIME, always
     * serving the muscle with the largest RELATIVE shortfall next. Growing in
     * single sets is what keeps a capacity-limited week fair: the old version
     * handed the first muscle it served a whole 5-set block, so a 4-day
     * intermediate week filled glutes to 18 and left biceps and rear delts at
     * 6 once the upper days hit their set ceiling.
     *
     * Each step, in order of preference:
     *  1. frequency - a muscle directly trained in fewer than two sessions
     *     gets a new movement in a region-fitting session that lacks it
     *     (>=2 sessions per muscle, Pelland 2026: load quality);
     *  2. one more set on the session's existing DIRECT movement for it
     *     (fewer, fuller movements beat a pile of 2-set ones);
     *  3. a new direct movement (2 sets) where the session has room;
     *  4. one more set on an indirect (0.5-share) movement.
     * A step is refused when it would push any tracked muscle past its
     * collateral limit (see [overflows]) or the session past its exercise or
     * hard-set ceiling. A muscle no step can serve is dropped - the plan
     * degrades honestly. Returns true when the set/exercise ceilings, not the
     * targets, ended the fill.
     */
    private fun fillDeficits(ctx: Ctx, sessions: List<Draft>, targetOf: (Muscle) -> Double): Boolean {
        val skipped = mutableSetOf<Muscle>()
        var capacityBlocked = false
        repeat(MAX_FILL_STEPS) {
            val volume = weeklyVolumeOf(sessions)
            val muscle = ProgramRules.TRACKED
                .filter { it !in skipped && targetOf(it) > 0.0 }
                .map { it to (targetOf(it) - (volume[it] ?: 0.0)) }
                // Under half a set short is fractional-counting noise.
                .filter { it.second > 0.5 }
                .maxWithOrNull(
                    compareBy<Pair<Muscle, Double>>({ it.second / targetOf(it.first) })
                        .thenBy { -ProgramRules.TRACKED.indexOf(it.first) },
                )
                ?.first ?: return capacityBlocked
            when (growOnce(ctx, sessions, muscle, targetOf)) {
                Grow.GREW -> Unit
                Grow.NO_ROOM -> { capacityBlocked = true; skipped += muscle }
                Grow.NO_MOVEMENT -> skipped += muscle
            }
        }
        return capacityBlocked
    }

    private enum class Grow { GREW, NO_ROOM, NO_MOVEMENT }

    /** Upper bound on fill steps: every step adds a set, so this caps a week at ~200 extra sets. */
    private const val MAX_FILL_STEPS = 200

    private fun share(name: String, muscle: Muscle): Double =
        MuscleMap.profile(name)?.muscles?.get(muscle) ?: 0.0

    private fun setsIn(session: Draft): Int = session.entries.sumOf { it.sets }

    private fun growOnce(ctx: Ctx, sessions: List<Draft>, muscle: Muscle, targetOf: (Muscle) -> Double): Grow {
        val fitting = sessions.filter { s ->
            ctx.pool.any { groupFitsSession(s.role, it.muscleGroup) && share(it.name, muscle) >= 0.5 }
        }
        if (fitting.isEmpty()) return Grow.NO_MOVEMENT
        var sawMovement = false

        fun roomForSet(s: Draft) = setsIn(s) + 1 <= ProgramRules.SESSION_HARD_SET_CAP
        fun roomForNew(s: Draft) =
            s.entries.size < ctx.cap && setsIn(s) + 2 <= ProgramRules.SESSION_HARD_SET_CAP

        /**
         * Collateral a set spends on tracked muscles already at their target.
         * Zero-waste growth goes first: a quad set from a leg extension beats
         * one from a squat once the glutes are served.
         */
        fun waste(name: String): Double {
            val volume = weeklyVolumeOf(sessions)
            return ProgramRules.TRACKED.filter { it != muscle }.sumOf { other ->
                val s = share(name, other)
                if (s > 0.0 && (volume[other] ?: 0.0) >= targetOf(other)) s else 0.0
            }
        }

        fun bump(s: Draft, index: Int): Boolean {
            val entry = s.entries[index]
            if (entry.sets >= 5 || !roomForSet(s)) return false
            val exercise = ctx.pool.firstOrNull { it.name == entry.exerciseName } ?: return false
            if (overflows(ctx, sessions, exercise, 1, targetOf)) return false
            s.entries[index] = entry.copy(sets = entry.sets + 1)
            return true
        }

        fun addNew(s: Draft, maxWaste: Double): Boolean {
            if (!roomForNew(s)) return false
            val candidate = ranked(ctx, s, null, muscle)
                .filter { groupFitsSession(s.role, it.muscleGroup) && !redundantIn(s, it) }
                .filter { waste(it.name) <= maxWaste }
                .sortedBy { waste(it.name) }
                .firstOrNull { !overflows(ctx, sessions, it, 2, targetOf) }
                ?: return false
            sawMovement = true
            val deficit = targetOf(muscle) - (weeklyVolumeOf(sessions)[muscle] ?: 0.0)
            add(ctx, s, candidate, sets = 2, why = muscleWhy(candidate, muscle, ctx, deficit))
            return true
        }

        /** Bump candidates across the week: least wasteful first, then fewest sets. */
        fun bumpables(minShare: Double, wasteFree: Boolean?): List<Pair<Draft, Int>> =
            bySpaceOf(fitting).flatMap { s ->
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
        if (trainedIn < minOf(2, fitting.size)) {
            for (s in bySpaceOf(fitting)) {
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
        for (s in bySpaceOf(fitting)) if (addNew(s, 0.0)) return Grow.GREW
        // 4. Any direct set, then any new movement, then indirect sets.
        for ((s, i) in bumpables(1.0, wasteFree = null)) {
            sawMovement = true
            if (bump(s, i)) return Grow.GREW
        }
        for (s in bySpaceOf(fitting)) if (addNew(s, Double.MAX_VALUE)) return Grow.GREW
        for ((s, i) in bumpables(0.5, wasteFree = null)) {
            sawMovement = true
            if (bump(s, i)) return Grow.GREW
        }
        val anyCandidate = fitting.any { s ->
            ranked(ctx, s, null, muscle).any { groupFitsSession(s.role, it.muscleGroup) && !redundantIn(s, it) }
        }
        return if (sawMovement || anyCandidate) Grow.NO_ROOM else Grow.NO_MOVEMENT
    }

    /** Least-loaded sessions first, so volume spreads across the week. */
    private fun bySpaceOf(sessions: List<Draft>): List<Draft> =
        sessions.withIndex().sortedWith(compareBy({ setsIn(it.value) }, { it.index })).map { it.value }

    /**
     * A second movement with the same pattern and the same main muscle as one
     * already in the session adds nothing the first could not do with another
     * set (Gentil 2015). Seen on device: barbell, Pendlay and dumbbell rows in
     * one upper day; seated and lying leg curls side by side. Once the first
     * sits at the 5-set ceiling a second variant is the only way to add
     * volume, so it stops being redundant - that is how a leg day carries
     * both a standing and a seated calf raise (Kinoshita 2023: they load
     * different heads).
     */
    private fun redundantIn(session: Draft, candidate: Exercise): Boolean {
        val profile = profileOf(candidate)
        val main = dominantMuscle(profile)
        return session.entries.any { entry ->
            val existing = MuscleMap.profile(entry.exerciseName) ?: return@any false
            existing.pattern == profile.pattern && dominantMuscle(existing) == main && entry.sets < 5
        }
    }

    /**
     * Hard ceiling: no step may push any tracked muscle past the top of its
     * range (or its own target, where a single-session target sits higher).
     * Wasted collateral BELOW that ceiling is steered by [growOnce]'s
     * waste ordering rather than forbidden: forbidding it (the old
     * target+3 bystander cap) blocked every rear-delt isolation once rows
     * had served the upper back, leaving rear delts at 9 with sets to spare.
     */
    private fun overflows(
        ctx: Ctx,
        sessions: List<Draft>,
        exercise: Exercise,
        sets: Int,
        targetOf: (Muscle) -> Double,
    ): Boolean {
        val volume = weeklyVolumeOf(sessions)
        return ProgramRules.TRACKED.any { other ->
            val s = profileOf(exercise).muscles[other] ?: 0.0
            if (s <= 0.0) return@any false
            val top = maxOf(ctx.targetRange.endInclusive, targetOf(other))
            (volume[other] ?: 0.0) + sets * s > top + 1e-9
        }
    }

    /**
     * A movement lands on a day whose role trains its region: leg work on
     * lower/leg/full-body days, presses and pulls on upper/push/pull/
     * full-body days, core anywhere. FULL_BODY trains everything; AUTO
     * (null role) accepts all.
     */
    private fun groupFitsSession(role: Role?, group: MuscleGroup): Boolean {
        if (role == null || role == Role.FULL_BODY) return true
        val upper = setOf(Role.UPPER, Role.PUSH_DAY, Role.PULL_DAY)
        val lower = setOf(Role.LOWER, Role.LEGS_DAY)
        return when (group) {
            MuscleGroup.LEGS -> role in lower
            MuscleGroup.PUSH, MuscleGroup.PULL -> role in upper
            else -> true
        }
    }

    /** One decimal at most, integers plain: 5 not 5.0. */
    private fun fmtSets(sets: Double): String =
        if (kotlin.math.abs(sets - kotlin.math.round(sets)) < 0.05) {
            kotlin.math.round(sets).toInt().toString()
        } else {
            "%.1f".format(sets)
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
     * Plain-language reason for a deficit fill: how short the week was, on
     * which muscle, why THIS movement (long length where applicable), and
     * the citation - deterministic down to the numbers. The filled muscle is
     * always named, even at a 0.5 share.
     */
    private fun muscleWhy(exercise: Exercise, muscle: Muscle, ctx: Ctx, deficit: Double): String {
        val range = ctx.targetRange
        // A sub-set shortfall is indirect-share noise; say so instead of
        // printing "0.1 sets".
        val shortfall = if (deficit < 0.5) {
            "Your week is just under target on ${muscle.label.lowercase()} " +
                "(target ${range.start.toInt()}-${range.endInclusive.toInt()})"
        } else {
            "Your week was ${setsPhrase(deficit)} short on " +
                "${muscle.label.lowercase()} (target ${range.start.toInt()}-${range.endInclusive.toInt()})"
        }
        val priority = if (muscle in ctx.priorityMuscles) {
            "Priority muscle you picked. $shortfall"
        } else {
            shortfall
        }
        return if (stretchesFor(exercise, muscle)) {
            "$priority. The ${exercise.name} trains your ${muscleListOf(exercise, muscle)} at long " +
                "muscle length, where they grow more - ${longLengthEvidence(muscle)}"
        } else {
            "$priority. The ${exercise.name} trains your ${muscleListOf(exercise, muscle)} - Pelland 2026"
        }
    }

    /**
     * Whether the long-length advantage applies to [muscle] in this movement.
     * stretchBias describes the movement's DOMINANT muscle: a row stretches
     * the upper back, not the rear delts it also trains at a 0.5 share, so a
     * rear-delt fill must neither rank the row as long-length work nor cite
     * that evidence for it. A null [muscle] (a pattern slot) takes the flag
     * as it stands.
     */
    private fun stretchesFor(exercise: Exercise, muscle: Muscle?): Boolean {
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
        val pool = eligible(catalogue, request.equipment, request.focus)
        if (pool.isEmpty()) return null
        val ctx = Ctx(request, pool, strength, ProgramRules.sessionCap(request.tier))
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
                )
            for ((muscle, weeklyDeficit) in under) {
                val deficit = minOf(weeklyDeficit, ctx.targetFor(muscle) / dose)
                if (draft.entries.size >= ctx.cap) break
                // Prefer a movement whose other main muscles still want sets:
                // a dip for triceps also loads a chest the week already
                // serves, where an overhead extension does not.
                val served = ProgramRules.TRACKED.filter { other ->
                    val planned = (existing[other] ?: 0.0) + (weeklyVolumeOf(listOf(draft))[other] ?: 0.0)
                    planned >= ctx.targetFor(other)
                }.toSet()
                val exercise = ranked(ctx, draft, null, muscle).firstOrNull { candidate ->
                    profileOf(candidate).muscles.none { (other, share) -> other in served && share >= 0.5 }
                } ?: pick(ctx, draft, null, muscle) ?: continue
                val contribution = profileOf(exercise).muscles[muscle] ?: 0.0
                add(
                    ctx, draft, exercise,
                    sets = setsForDeficit(deficit, contribution),
                    why = muscleWhy(exercise, muscle, ctx, deficit),
                )
            }
        } else {
            val role = draft.role ?: return null
            for (pattern in backboneSlots(ctx, role, null)) {
                if (draft.entries.size >= ctx.cap) break
                val exercise = pick(ctx, draft, pattern, null) ?: continue
                add(ctx, draft, exercise, sets = backboneSets(ctx.focus), why = backboneWhy(exercise, ctx.focus))
            }
            // One session's share of the weekly dose: the target over the
            // assumed two sessions per muscle. The fill counts only this
            // draft, so adding the existing week's volume to the target (as
            // this once did) overshot every muscle the week already trained.
            fillDeficits(ctx, listOf(draft)) { muscle -> ctx.targetFor(muscle) / 2.0 }
        }
        if (draft.entries.isEmpty()) return null
        return PlannedPreset(
            name = draft.role?.let { roleLabel(it) } ?: "Session",
            note = presetNote(ctx.tier, ctx.focus),
            scheduledDay = scheduledDay,
            entries = draft.entries.toList(),
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
        val band = "band ${range.first}-${range.last}"
        return when {
            focus == TrainingFocus.MUSCLE ->
                "Reps moved to $reps ($band): working 1-3 reps short of failure builds muscle " +
                    "without the recovery cost of grinding - Lopez 2021; Robinson 2024"
            focus == TrainingFocus.STRENGTH || focus == TrainingFocus.SKILL ->
                if (compound) {
                    "Reps moved to $reps ($band): heavy loads on the lifts you want stronger " +
                        "drive strength - Lopez 2021; Buckner 2017"
                } else {
                    "Reps moved to $reps ($band): accessories build the muscle behind the lift " +
                        "at higher reps - Lopez 2021"
                }
            compound ->
                "Reps moved to $reps ($band): compounds stay heavy, accessories carry volume - Lopez 2021"
            else ->
                "Reps moved to $reps ($band): accessories carry volume at higher reps - Lopez 2021"
        }
    }

    private fun dominantMuscle(profile: ExerciseProfile): Muscle? =
        profile.muscles.filterValues { it > 0.0 }
            .entries
            .maxWithOrNull(compareBy { it.value })?.key

    /**
     * Tracked muscles a session's existing movements put in scope, by the
     * region of each movement's DOMINANT muscle. Classifying by pattern put
     * a leg extension or calf raise (pattern ISOLATION) in the upper scope,
     * so improving a lower day added a bench press to it.
     */
    private fun trainedScope(entries: List<PlannedEntry>): List<Muscle> {
        val lowerMuscles = setOf(
            Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.ADDUCTORS,
        )
        val dominants = entries.mapNotNull { entry ->
            MuscleMap.profile(entry.exerciseName)?.let { dominantMuscle(it) }
        }.toSet()
        val scope = mutableSetOf<Muscle>()
        if (dominants.any { it in lowerMuscles }) {
            scope += listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.ABS)
        }
        if (dominants.any { it !in lowerMuscles && it != Muscle.ABS }) {
            scope += listOf(
                Muscle.CHEST, Muscle.LATS, Muscle.UPPER_BACK, Muscle.SIDE_DELTS,
                Muscle.REAR_DELTS, Muscle.BICEPS, Muscle.TRICEPS,
            )
        }
        if (Muscle.ABS in dominants) scope += Muscle.ABS
        return ProgramRules.TRACKED.filter { it in scope }
    }

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
     *  - missing loads are filled from the strength profile.
     * Movements without a MuscleMap profile (user-created, CSV imports) are
     * passed through untouched. Idempotent by construction: run it on its
     * own output and nothing changes.
     */
    fun improve(
        target: PlannedPreset,
        restOfWeek: List<PlannedPreset>,
        request: ProgramRequest,
        catalogue: List<Exercise>,
        strength: StrengthProfile,
    ): Improvement {
        val pool = eligible(catalogue, request.equipment, request.focus)
        val ctx = Ctx(request, pool, strength, ProgramRules.sessionCap(request.tier))
        val changes = mutableListOf<PlanChange>()
        val result = mutableListOf<PlannedEntry>()
        // First entry per (dominant muscle, pattern) -> its prescribed sets.
        val dominantSeen = mutableMapOf<Pair<Muscle, MovementPattern>, Int>()
        val rir = ProgramRules.targetRir(request.tier, request.focus)

        for (entry in target.entries) {
            val profile = MuscleMap.profile(entry.exerciseName)
            if (profile == null) {
                // Unknown movement: kept, untouched, modifiers intact.
                result += entry
                continue
            }
            var name = entry.exerciseName
            var sets = entry.sets
            var reps = entry.reps
            var load = entry.targetWeightKg
            var loadNote = entry.loadNote
            var swapNote: String? = null

            // Specificity guard: a STRENGTH main lift is never swapped away.
            val swapAllowed = !(request.focus == TrainingFocus.STRENGTH && isMainLift(name))
            if (swapAllowed && !profile.stretchBias) {
                val primary = dominantMuscle(profile)
                val replacement = pool
                    .filter { profileOf(it).pattern == profile.pattern && profileOf(it).stretchBias }
                    .filter { it.name != name }
                    .filter {
                        primary == null || (profileOf(it).muscles[primary] ?: 0.0) >=
                            (profile.muscles[primary] ?: 0.0)
                    }
                    .maxWithOrNull(
                        compareBy(
                            { if (profileOf(it).compound) 1 else 0 },
                            { -fitScore(it, request.equipment) },
                            { -MovementDifficulty.tier(it.name) },
                            { it.name },
                        ),
                    )
                if (replacement != null) {
                    swapNote = "Swapped the ${entry.exerciseName} for the ${replacement.name}: same " +
                        "movement pattern, but the ${replacement.name} loads the " +
                        "${dominantMuscle(profile)?.label?.lowercase() ?: "target muscle"} at long " +
                        "length, where it grows more - ${longLengthEvidence(dominantMuscle(profile))}"
                    name = replacement.name
                }
            }
            val newProfile = MuscleMap.profile(name) ?: profile

            var adjusted = false
            val range = repRange(request.focus, newProfile.compound)
            if (reps !in range) {
                reps = if (reps < range.first) range.first else range.last
                adjusted = true
            }
            if (sets < 2 || sets > 5) {
                sets = sets.coerceIn(2, 5)
                adjusted = true
            }
            if (adjusted) {
                changes += PlanChange(
                    PlanChange.Kind.ADJUSTED, name,
                    adjustReason(request.focus, newProfile.compound, reps, range),
                )
            }
            swapNote?.let { changes += PlanChange(PlanChange.Kind.SWAPPED, name, it) }

            if (load == null) {
                val exercise = pool.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: catalogue.firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (exercise != null) {
                    val filled = fillLoad(exercise, strength, reps, rir)
                    if (filled != null) {
                        load = filled.first
                        loadNote = filled.second
                        changes += PlanChange(
                            PlanChange.Kind.LOAD_SET, name,
                            "No load was set, so this one comes from your own logged lifts " +
                                "(${filled.second}) - Zourdos 2016",
                        )
                    }
                }
            }

            val dominant = dominantMuscle(newProfile)
            val signature = dominant?.let { it to newProfile.pattern }
            val firstSets = signature?.let { dominantSeen[it] }
            if (firstSets != null && firstSets < 5) {
                // Redundant beyond need: the session already owns this
                // muscle through this pattern and the first copy has sets to
                // spare (Gentil 2015). At the 5-set ceiling a second variant
                // is the only way to add volume, as in the week generator.
                changes += PlanChange(
                    PlanChange.Kind.REMOVED, entry.exerciseName,
                    "Removed: this session already trains your ${dominant.label.lowercase()} through " +
                        "the same movement pattern, and a second version of it adds no new growth - Gentil 2015",
                )
                continue
            }
            if (signature != null && firstSets == null) dominantSeen[signature] = sets
            result += entry.copy(
                exerciseName = name, sets = sets, reps = reps,
                targetWeightKg = load, loadNote = loadNote,
            )
        }

        // Additions: tracked muscles this session's role trains that the
        // whole week (this preset + the rest) leaves under the tier minimum.
        val scope = trainedScope(result)
        val weekVolume = ProgramRules.weeklyVolume(
            restOfWeek + listOf(target.copy(entries = result)),
        )
        for (muscle in scope) {
            if (result.size >= ctx.cap) break
            val covered = result.any {
                (MuscleMap.profile(it.exerciseName)?.muscles?.get(muscle) ?: 0.0) >= 0.5
            }
            val minimum = ctx.targetRange.start
            if (covered || (weekVolume[muscle] ?: 0.0) >= minimum) continue
            val draft = Draft(target.scheduledDay, null)
            // Deliberately no fit bound: the lifter's own preset defines the
            // session, and refusing calf machines because a barbell squat is
            // present would leave the muscle untrained.
            draft.names += result.map { it.exerciseName }
            val exercise = pick(ctx, draft, null, muscle) ?: continue
            val added = add(ctx, draft, exercise, sets = 3, why = muscleWhy(exercise, muscle, ctx, ctx.targetRange.start))
            // add() prescribes the focus anchor; keep it inside the same
            // rep range improve holds every other entry to, or the second
            // pass would "fix" what this pass just added.
            val inRange = added.copy(reps = added.reps.coerceIn(repRange(ctx.focus, profileOf(exercise).compound)))
            result += inRange
            changes += PlanChange(
                PlanChange.Kind.ADDED, exercise.name,
                "Added the ${exercise.name}: your week is ${setsPhrase(ctx.targetRange.start - (weekVolume[muscle] ?: 0.0))} " +
                    "short on ${muscle.label.lowercase()} (minimum ${ctx.targetRange.start.toInt()}) - " +
                    "it covers ${muscleListOf(exercise, muscle)} - Pelland 2026",
            )
        }

        val after = target.copy(entries = result)
        return Improvement(before = target, after = after, changes = changes)
    }

    // ------------------------------------------------------------------ note

    /**
     * The one-line rest and RIR guidance every generated preset carries in
     * its note - the UI shows it under the session name.
     */
    internal fun presetNote(tier: ExperienceTier, focus: TrainingFocus): String {
        val rir = ProgramRules.targetRir(tier, focus)
        return if (focus == TrainingFocus.STRENGTH || focus == TrainingFocus.GENERAL) {
            "Rest 3-5 min on the main lifts (Schoenfeld 2016; Grgic 2018), " +
                "at least 90 s on accessories (Singer 2024). " +
                "Keep $rir reps in reserve - nothing to failure (Refalo 2023)."
        } else {
            "Rest 2-3 min on compounds, never under 90 s (Singer 2024). " +
                "Keep $rir reps in reserve - no set to failure (Refalo 2023; Robinson 2024)."
        }
    }
}
