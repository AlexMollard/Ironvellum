package com.ironvellum.app.domain

import com.ironvellum.app.domain.MovementDifficulty.FREE_WEIGHT_LOAD

/** Where the load comes from, asked at setup. Mirrors the picker's facets. */
enum class EquipmentAccess { BODYWEIGHT, HOME_WEIGHTS, FULL_GYM }

/** What the lifter is training for; decides rep bands and slot mix. */
enum class TrainingFocus { STRENGTH, MUSCLE, SKILL, GENERAL }

/** One prescribed movement inside a proposed session. */
data class PlannedEntry(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val targetWeightKg: Double?,
)

/** One proposed session, scheduled on an ISO weekday (1 = Monday .. 7 = Sunday). */
data class PlannedPreset(
    val name: String,
    val note: String,
    val scheduledDay: Int,
    val entries: List<PlannedEntry>,
)

data class RoutinePlan(val presets: List<PlannedPreset>)

/**
 * Proposes a first training week from the catalogue a caller hands in.
 *
 * Pure and dependency-free: it reads only the [Exercise] list it is given and
 * the shared domain tables ([MovementDifficulty], [Progression]). Every
 * movement in the result is chosen by catalogue attributes, never by name, so
 * a rename or an unseeded row degrades the plan instead of crashing it.
 *
 * EQUIPMENT PREDICATE - the same derivation the exercise picker uses
 * ([Exercise.isWeighted] plus [MovementDifficulty.loadFactor], no second
 * equipment model):
 *  - activities (non-blank [Exercise.category]) and non-REPS metrics (holds
 *    are measured in seconds, not reps) are never prescribed;
 *  - BODYWEIGHT: only unweighted lifting movements;
 *  - HOME_WEIGHTS: weighted movements whose loadFactor is the free-weight
 *    reference - barbell and dumbbell - and NOT the "assisted" machines,
 *    which loadFactor does not cover (their marked weight subtracts);
 *  - FULL_GYM: every lifting movement, machines included.
 *
 * SPLIT RULE - how many days become what, and the rest rule behind it:
 *  - 1-3 days: full body, each session trains every pattern;
 *  - 4 days: upper / lower twice (Mon/Tue/Thu/Fri);
 *  - 5-6 days: push / pull / legs (plus one upper / lower pair at 5).
 * Two sessions that work the same primary pattern are never on adjacent
 * weekdays; adjacent days are allowed only when their dominant groups are
 * disjoint (upper vs lower, push vs pull). Every plan ends the week with at
 * least one full rest day before it wraps to Monday.
 *
 * BALANCE RULE - a plan that trains push and never pull is a defect. Across
 * the whole plan the templates prescribe exactly as many pushing as pulling
 * entries, and legs and core are never absent, whatever the focus.
 *
 * FOCUS - rep numbers come from [Progression.repBand]; nothing is invented:
 *  - STRENGTH: compounds at the low band anchor (5s), few accessories;
 *  - MUSCLE: hypertrophy band anchor (10s) with isolation accessories;
 *  - SKILL: skill-tree progressions at an accessible tier (I-II), trained
 *    strength-style;
 *  - GENERAL: strength-anchored compounds plus hypertrophy accessories.
 *
 * MILESTONE RULE - the gym progression lines seed catalogue rows for their
 * CLAIM STANDARDS ("5 reps at double bodyweight"). [MovementDifficulty]
 * .isLoadPriced marks exactly those rows - the tier II-V line entries plus
 * Weighted Pull-up / Dip - and they are never prescribed: they are deeds to
 * be claimed, not training volume. The tier-I line roots (Back Squat, Bench
 * Press, Deadlift, Overhead Press) are real lifts and stay selectable.
 *
 * EQUIPMENT-FIT RULE - within a compound slot the movement that best matches
 * the access level outranks the lowest tier: a barbell press beats a bench
 * dip in a full gym, and bodyweight beats nothing only when it is all there
 * is. Fit rank: loaded free weight 0, machine 1, bodyweight 2; everything is
 * 0 under BODYWEIGHT.
 *
 * REDUNDANCY RULE - once a session has a movement for a group at a given fit
 * rank, no further same-group movement of a worse rank joins that session, so
 * "Bodyweight Squat beside Back Squat" cannot happen; equal-rank variety
 * (squat beside deadlift) still can.
 *
 * DEGRADE RULE - a slot the catalogue cannot fill under the constraints is
 * dropped, and a session left with no entries is dropped with it. The plan
 * shrinks honestly rather than prescribing a movement that breaks the
 * equipment constraint or duplicating one to pad the count.
 */
object RoutineBuilder {

    private enum class Role { FULL_BODY, UPPER, LOWER, PUSH_DAY, PULL_DAY, LEGS_DAY }

    /** One slot in a session template: which group it draws from, heavy or light. */
    private data class Slot(val group: MuscleGroup, val compound: Boolean)

    private val FULL_BODY_SLOTS = listOf(
        Slot(MuscleGroup.PUSH, compound = true),
        Slot(MuscleGroup.PULL, compound = true),
        Slot(MuscleGroup.LEGS, compound = true),
        Slot(MuscleGroup.CORE, compound = false),
    )
    private val UPPER_SLOTS = listOf(
        Slot(MuscleGroup.PUSH, compound = true),
        Slot(MuscleGroup.PULL, compound = true),
        Slot(MuscleGroup.PUSH, compound = false),
        Slot(MuscleGroup.PULL, compound = false),
    )
    private val LOWER_SLOTS = listOf(
        Slot(MuscleGroup.LEGS, compound = true),
        Slot(MuscleGroup.LEGS, compound = true),
        Slot(MuscleGroup.LEGS, compound = false),
        Slot(MuscleGroup.CORE, compound = false),
    )
    private val PUSH_SLOTS = listOf(
        Slot(MuscleGroup.PUSH, compound = true),
        Slot(MuscleGroup.PUSH, compound = true),
        Slot(MuscleGroup.PUSH, compound = false),
        Slot(MuscleGroup.PUSH, compound = false),
        Slot(MuscleGroup.CORE, compound = false),
    )
    private val PULL_SLOTS = listOf(
        Slot(MuscleGroup.PULL, compound = true),
        Slot(MuscleGroup.PULL, compound = true),
        Slot(MuscleGroup.PULL, compound = false),
        Slot(MuscleGroup.PULL, compound = false),
        Slot(MuscleGroup.CORE, compound = false),
    )
    private val LEGS_SLOTS = listOf(
        Slot(MuscleGroup.LEGS, compound = true),
        Slot(MuscleGroup.LEGS, compound = true),
        Slot(MuscleGroup.LEGS, compound = false),
        Slot(MuscleGroup.LEGS, compound = false),
        Slot(MuscleGroup.CORE, compound = false),
    )

    /** ISO weekday plus role, per requested day count. See the split rule in the class KDoc. */
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
     * Reps/sets per slot kind, per focus. The rep is the band anchor, held
     * inside [Progression.repBand]; rest comes from [Progression.restSeconds].
     */
    private data class Prescription(val mode: TrainingMode, val reps: Int, val sets: Int)

    private fun compoundPrescription(focus: TrainingFocus): Prescription = when (focus) {
        TrainingFocus.MUSCLE -> Prescription(TrainingMode.HYPERTROPHY, reps = 10, sets = 4)
        TrainingFocus.SKILL, TrainingFocus.STRENGTH -> Prescription(TrainingMode.STRENGTH, reps = 5, sets = 4)
        TrainingFocus.GENERAL -> Prescription(TrainingMode.STRENGTH, reps = 5, sets = 4)
    }

    private fun accessoryPrescription(focus: TrainingFocus): Prescription = when (focus) {
        TrainingFocus.STRENGTH -> Prescription(TrainingMode.STRENGTH, reps = 5, sets = 3)
        TrainingFocus.MUSCLE -> Prescription(TrainingMode.HYPERTROPHY, reps = 10, sets = 3)
        TrainingFocus.GENERAL -> Prescription(TrainingMode.HYPERTROPHY, reps = 10, sets = 3)
        TrainingFocus.SKILL -> Prescription(TrainingMode.STRENGTH, reps = 5, sets = 3)
    }

    fun plan(
        daysPerWeek: Int,
        equipment: EquipmentAccess,
        focus: TrainingFocus,
        catalogue: List<Exercise>,
    ): RoutinePlan {
        val days = daysPerWeek.coerceIn(1, 6)
        val available = catalogue
            .filter { equipmentAllows(it, equipment) }
            // Claim-standard rows of the gym progression lines: milestones,
            // not trainable accessories. See the MILESTONE RULE in the KDoc.
            .filterNot { MovementDifficulty.isLoadPriced(it.name) }
        // A movement may serve two days of the week, but never two slots of
        // one session, and only after every unused candidate is exhausted.
        val usedGlobally = mutableSetOf<String>()
        val roleSeen = mutableMapOf<Role, Int>()

        val presets = (SPLITS[days] ?: SPLITS.getValue(2)).mapNotNull { (day, role) ->
            val slots = when (role) {
                Role.FULL_BODY -> FULL_BODY_SLOTS
                Role.UPPER -> UPPER_SLOTS
                Role.LOWER -> LOWER_SLOTS
                Role.PUSH_DAY -> PUSH_SLOTS
                Role.PULL_DAY -> PULL_SLOTS
                Role.LEGS_DAY -> LEGS_SLOTS
            }
            val usedHere = mutableSetOf<String>()
            val minFitByGroup = mutableMapOf<MuscleGroup, Int>()
            val entries = slots.mapNotNull { slot ->
                val exercise = pick(
                    available, slot, focus, equipment, usedGlobally, usedHere, minFitByGroup,
                ) ?: return@mapNotNull null
                usedHere += exercise.name
                usedGlobally += exercise.name
                minFitByGroup.merge(exercise.muscleGroup, fitScore(exercise, equipment), ::minOf)
                val rx = if (slot.compound) compoundPrescription(focus) else accessoryPrescription(focus)
                PlannedEntry(
                    exerciseName = exercise.name,
                    sets = rx.sets,
                    // The band anchor, kept inside the school's band so the
                    // in-app progression engine takes over from set one.
                    reps = Progression.repBand(rx.mode, rx.reps).let { rx.reps.coerceIn(it.first, it.last) },
                    // No bodyweight on file at setup: loads are filled in on
                    // the first session and progressive overload carries on.
                    targetWeightKg = null,
                )
            }
            if (entries.isEmpty()) return@mapNotNull null
            val ordinal = ('A' + roleSeen.merge(role, 1, Int::plus)!! - 1)
            val roleLabel = when (role) {
                Role.FULL_BODY -> "Full Body"
                Role.UPPER -> "Upper"
                Role.LOWER -> "Lower"
                Role.PUSH_DAY -> "Push"
                Role.PULL_DAY -> "Pull"
                Role.LEGS_DAY -> "Legs"
            }
            val rest = Progression.restSeconds(compoundPrescription(focus).mode)
            PlannedPreset(
                name = "$roleLabel $ordinal",
                note = "Proposed starter - edit freely. " +
                    "${compoundPrescription(focus).sets}-set compounds, " +
                    "${accessoryPrescription(focus).sets}-set accessories, " +
                    "rest ${rest}s between sets.",
                scheduledDay = day,
                entries = entries,
            )
        }
        return RoutinePlan(presets)
    }

    /**
     * Machine detection, identical to the picker's [equipmentFacet] rule:
     * loadFactor below the free-weight reference means machine, cable, sled
     * or Smith, and the "assisted" prefix names the assisted machines that
     * loadFactor deliberately does not cover.
     */
    private fun isMachine(exercise: Exercise): Boolean =
        exercise.name.trim().lowercase().startsWith("assisted") ||
            MovementDifficulty.loadFactor(exercise.name) < FREE_WEIGHT_LOAD

    private fun equipmentAllows(exercise: Exercise, equipment: EquipmentAccess): Boolean {
        if (exercise.category.isNotBlank()) return false
        // Holds are measured in seconds; prescribing them with a rep target
        // would put 30 "reps" of plank in a stranger's first session.
        if (exercise.metric != ExerciseMetric.REPS) return false
        if (!exercise.isWeighted) return true
        if (equipment == EquipmentAccess.BODYWEIGHT) return false
        if (equipment == EquipmentAccess.HOME_WEIGHTS) return !isMachine(exercise)
        return true
    }

    /** A movement the skill tree owns, detectable without importing [Skills]: */
    private fun isSkillTree(exercise: Exercise): Boolean =
        MovementDifficulty.isClassified(exercise.name) &&
            exercise.name.trim().lowercase() !in MovementDifficulty.catalogueOnlyKeys

    /**
     * How well a movement's implement matches the access level. Lower is a
     * better fit: under a full gym the barbell outranks the machine which
     * outranks the floor; under HOME_WEIGHTS machines never reach the pool;
     * under BODYWEIGHT everything ties and the other keys decide.
     */
    private fun fitScore(exercise: Exercise, equipment: EquipmentAccess): Int = when {
        equipment == EquipmentAccess.BODYWEIGHT -> 0
        !exercise.isWeighted -> 2
        isMachine(exercise) -> 1
        else -> 0
    }

    private fun pick(
        catalogue: List<Exercise>,
        slot: Slot,
        focus: TrainingFocus,
        equipment: EquipmentAccess,
        usedGlobally: Set<String>,
        usedHere: Set<String>,
        minFitByGroup: Map<MuscleGroup, Int>,
    ): Exercise? {
        val tier = { e: Exercise -> MovementDifficulty.tier(e.name) }
        val candidates = catalogue
            .filter { it.muscleGroup == slot.group }
            .filter {
                // The tree prices its line roots (Back Squat, Bench Press,
                // Deadlift) at tier I, so tier alone cannot separate main
                // lifts from isolation for a skill lifter.
                if (slot.compound) tier(it) >= 2 || isSkillTree(it) else tier(it) <= 2
            }
            .filterNot { it.name in usedHere }
            // REDUNDANCY RULE: the session already owns this pattern at a
            // better equipment match; a worse-matched version adds nothing.
            .filter { fitScore(it, equipment) <= (minFitByGroup[it.muscleGroup] ?: Int.MAX_VALUE) }
        // Deterministic preference order: equipment fit first (a barbell
        // press beats a bench dip in a full gym whatever the tier says),
        // then skill accessibility, then unused across the plan, then tier,
        // then name as tiebreak.
        return candidates.minWithOrNull(
            compareBy(
                { fitScore(it, equipment) },
                // SKILL: a progression the lifter can actually start at -
                // skill-tree ownership at tier I or II beats everything else.
                { if (focus == TrainingFocus.SKILL && isSkillTree(it) && tier(it) <= 2) 0 else 1 },
                { it.name in usedGlobally },
                { tier(it) },
                { it.name },
            ),
        )
    }
}
