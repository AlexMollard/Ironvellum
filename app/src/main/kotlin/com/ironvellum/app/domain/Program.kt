package com.ironvellum.app.domain

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words

/**
 * Shared vocabulary of the program generator, the tiered templates and the
 * "improve a preset" pass. Pure data: the rules live in ProgramGenerator,
 * ProgramTemplates, MuscleMap and ProgramRules, each citing the evidence
 * ([Evidence]) behind every number it uses.
 */

/**
 * One piece of gear the lifter owns, one toggle each: coarse levels hid the
 * real answer ("pull-up bar but no rings, parallettes, a 24 kg dumbbell").
 * Machines and cables are never toggles - they only come with a full gym.
 * Persisted by constant name (ProgramAnswers), so never rename a constant.
 */
enum class Gear(@StringRes private val labelRes: Int) {
    PULL_UP_BAR(R.string.program_gear_pull_up_bar),
    DIP_BARS(R.string.program_gear_dip_bars),
    PARALLETTES(R.string.program_gear_parallettes),
    RINGS(R.string.program_gear_rings),
    DUMBBELLS(R.string.program_gear_dumbbells),
    BARBELL(R.string.program_gear_barbell),
    BENCH(R.string.program_gear_bench),
    AB_WHEEL(R.string.program_gear_ab_wheel),
    ;

    /** The gear's name as shown. */
    val label: String get() = Words.text(UiText.res(labelRes))
}

/**
 * What the lifter can train with. [fullGym] means everything, machines and
 * cables included, with no dumbbell cap. [dumbbellMaxKg] is the per-dumbbell
 * ceiling; null means no cap (full gym, or never answered). [dumbbellPair]
 * is false when the lifter owns a single dumbbell, which rules out the
 * two-dumbbell presses and flys. [kit] is the vest and dip belt that set the
 * increments of loaded bodyweight lifts; the default owns neither.
 */
data class Equipment(
    val fullGym: Boolean,
    val gear: Set<Gear> = emptySet(),
    val dumbbellMaxKg: Double? = null,
    val dumbbellPair: Boolean = true,
    val kit: LoadKit = LoadKit.NONE,
) {
    companion object {
        val FULL_GYM = Equipment(fullGym = true, gear = Gear.entries.toSet())
        val NOTHING = Equipment(fullGym = false)
    }
}

/**
 * What the lifter is training for; decides rep ranges, rest and slot mix.
 * Persisted by constant name (ProgramAnswers), so never rename a constant.
 */
enum class TrainingFocus {
    STRENGTH, MUSCLE, SKILL, GENERAL;

    /** The goal's name on a chip: Power, Muscle, the skills word, Mixed. */
    val label: String
        get() = when (this) {
            STRENGTH -> Words.text(UiText.res(R.string.onboarding_focus_power))
            MUSCLE -> Words.text(UiText.res(R.string.onboarding_focus_muscle))
            SKILL -> Words.technique.cpl
            GENERAL -> Words.text(UiText.res(R.string.onboarding_focus_mixed))
        }
}

/**
 * How much weekly work the plan prescribes. The volume evidence is tiered on
 * training age (see ProgramRules.weeklySetTarget: under a year LOW, one to
 * three years STANDARD, beyond that HIGH), so the app suggests the level from
 * logged history - but it is the lifter's dose to pick, not a rank. It also
 * sets reps in reserve, movements per session and bodyweight progressions.
 * Persisted by constant name (ProgramAnswers), so never rename a constant.
 */
enum class VolumeLevel(@StringRes private val labelRes: Int) {
    LOW(R.string.program_volume_low),
    STANDARD(R.string.program_volume_standard),
    HIGH(R.string.program_volume_high),
    ;

    /** The level's name as shown. */
    val label: String get() = Words.text(UiText.res(labelRes))
}

/**
 * How the week is divided - the headline choice of the builder. With weekly
 * volume equated the split barely changes growth (Pelland 2026; Schoenfeld
 * 2019), so it is a scheduling preference, and each split offers only the
 * day counts it fits. Persisted by constant name (ProgramAnswers), so never
 * rename a constant.
 */
enum class TrainingSplit(
    @StringRes private val labelRes: Int,
    val dayOptions: List<Int>,
    @StringRes private val shortLabelRes: Int? = null,
) {
    FULL_BODY(R.string.program_split_full_body, listOf(1, 2, 3)),
    UPPER_LOWER(R.string.program_split_upper_lower, listOf(4)),
    PUSH_PULL_LEGS(R.string.program_split_push_pull_legs, listOf(3, 6), shortLabelRes = R.string.program_split_push_pull_legs_short),
    UPPER_LOWER_PPL(R.string.program_split_hybrid, listOf(5), shortLabelRes = R.string.program_split_hybrid_short),
    ;

    /** The split's name as shown. */
    val label: String get() = Words.text(UiText.res(labelRes))

    /** The label for a one-line tab, so a four-way picker never wraps a word onto a second line. */
    val pickerLabel: String get() = shortLabelRes?.let { Words.text(UiText.res(it)) } ?: label

    companion object {
        /** The conventional split for a day count: what a request without a split gets. */
        fun forDays(days: Int): TrainingSplit = when (days.coerceIn(1, 6)) {
            1, 2, 3 -> FULL_BODY
            4 -> UPPER_LOWER
            5 -> UPPER_LOWER_PPL
            else -> PUSH_PULL_LEGS
        }

        /** Every split and day count the pickers offer, in display order. */
        val OPTIONS: List<Pair<TrainingSplit, Int>> = listOf(
            FULL_BODY to 2, FULL_BODY to 3, PUSH_PULL_LEGS to 3,
            UPPER_LOWER to 4, UPPER_LOWER_PPL to 5, PUSH_PULL_LEGS to 6,
        )
    }
}

/**
 * The muscles weekly volume is counted against. Finer than [MuscleGroup]
 * (which files the catalogue into PUSH/PULL/LEGS/CORE) because the evidence
 * counts sets per muscle, and a push day can starve the side delts while the
 * chest is over-served. Declared in display order. The chest is split into
 * its clavicular (upper), sternal (mid) and costal (lower) regions, and the
 * upper back into the rhomboids that rows aim at and the whole trapezius,
 * which every pull, row, shrug and overhead lockout works in part. NECK is
 * on the figure but in neither [ProgramRules.TRACKED] nor [ProgramRules.HELPERS]:
 * it has no weekly target, so nothing is ever added to chase it. Never
 * persisted, so renames are data-safe.
 */
enum class Muscle(@StringRes private val labelRes: Int) {
    UPPER_CHEST(R.string.muscle_upper_chest),
    MID_CHEST(R.string.muscle_mid_chest),
    LOWER_CHEST(R.string.muscle_lower_chest),
    LATS(R.string.muscle_lats),
    RHOMBOIDS(R.string.muscle_rhomboids),
    TRAPS(R.string.muscle_traps),
    NECK(R.string.muscle_neck),
    FRONT_DELTS(R.string.muscle_front_delts),
    SIDE_DELTS(R.string.muscle_side_delts),
    REAR_DELTS(R.string.muscle_rear_delts),
    ROTATOR_CUFF(R.string.muscle_rotator_cuff),
    SERRATUS(R.string.muscle_serratus),
    BICEPS(R.string.muscle_biceps),
    BRACHIALIS(R.string.muscle_brachialis),
    TRICEPS(R.string.muscle_triceps),
    FOREARMS(R.string.muscle_forearms),
    ABS(R.string.muscle_abs),
    OBLIQUES(R.string.muscle_obliques),
    LOWER_BACK(R.string.muscle_lower_back),
    HIP_FLEXORS(R.string.muscle_hip_flexors),
    QUADS(R.string.muscle_quads),
    HAMSTRINGS(R.string.muscle_hamstrings),
    GLUTES(R.string.muscle_glutes),
    ADDUCTORS(R.string.muscle_adductors),
    ABDUCTORS(R.string.muscle_abductors),
    CALVES(R.string.muscle_calves),
    TIBIALIS(R.string.muscle_tibialis),
    ;

    /** The muscle's name as shown. */
    val label: String get() = Words.text(UiText.res(labelRes))
}

/**
 * What a lifter can ask to prioritise; each area names the muscles it raises.
 * Persisted by constant name (ProgramAnswers), so never rename a constant.
 * An area may name helper muscles: prioritising only raises the TRACKED
 * muscles to the top of their range - helpers keep their floor either way
 * (see ProgramGenerator.floorOrTarget) and only gain the "Priority" wording.
 */
enum class MuscleArea(@StringRes private val labelRes: Int, val muscles: Set<Muscle>) {
    CHEST(R.string.musclearea_chest, setOf(Muscle.UPPER_CHEST, Muscle.MID_CHEST, Muscle.LOWER_CHEST)),
    BACK(R.string.musclearea_back, setOf(Muscle.LATS, Muscle.RHOMBOIDS, Muscle.TRAPS)),
    SHOULDERS(R.string.musclearea_shoulders, setOf(Muscle.SIDE_DELTS, Muscle.REAR_DELTS, Muscle.FRONT_DELTS)),
    ARMS(R.string.musclearea_arms, setOf(Muscle.BICEPS, Muscle.TRICEPS)),
    QUADS(R.string.musclearea_quads, setOf(Muscle.QUADS)),
    HAMSTRINGS(R.string.musclearea_hamstrings, setOf(Muscle.HAMSTRINGS)),
    GLUTES(R.string.musclearea_glutes, setOf(Muscle.GLUTES)),
    CALVES(R.string.musclearea_calves, setOf(Muscle.CALVES)),
    CORE(R.string.musclearea_core, setOf(Muscle.ABS)),
    ;

    /** The area's name as shown. */
    val label: String get() = Words.text(UiText.res(labelRes))
}

/** Movement pattern, the unit strength specificity and substitution work in. */
enum class MovementPattern {
    SQUAT,
    HINGE,
    LUNGE,
    HORIZONTAL_PUSH,
    VERTICAL_PUSH,
    HORIZONTAL_PULL,
    VERTICAL_PULL,
    ISOLATION,
    CORE,
}

/** What one generated session is built around. AUTO fills what the rest of the week is missing. */
enum class SessionKind(@StringRes private val labelRes: Int) {
    AUTO(R.string.program_session_auto),
    FULL_BODY(R.string.program_split_full_body),
    UPPER(R.string.program_session_upper),
    LOWER(R.string.program_session_lower),
    PUSH(R.string.program_session_push),
    PULL(R.string.program_session_pull),
    LEGS(R.string.program_session_legs),
    ;

    /** The session kind's name as shown. */
    val label: String get() = Words.text(UiText.res(labelRes))
}

/** One prescribed movement inside a proposed session. */
data class PlannedEntry(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val targetWeightKg: Double?,
    /** Free-text preset modifiers ("weighted, deficit"); carried through improve untouched. */
    val modifiers: String = "",
    /** Why this movement and dose, in one line with its citation ("Loads the hamstrings long - Maeo 2021"). */
    val why: String = "",
    /** Where [targetWeightKg] came from, or null when there is no load to state. */
    val loadNote: String? = null,
    /** The set range this movement may be dosed within; [sets] is the planned midpoint. */
    val setsMin: Int = sets,
    val setsMax: Int = sets,
    /** How hard the sets are taken: compounds leave 0-2 reps in reserve, isolation goes to failure. */
    val effort: EffortCue = EffortCue.RIR_0_2,
    /** The rep window shown to the lifter; [reps] stays the anchor the load is computed from. */
    val repLow: Int = reps,
    val repHigh: Int = reps,
)

/** One proposed session, scheduled on an ISO weekday (1 = Monday .. 7 = Sunday) or unscheduled. */
data class PlannedPreset(
    val name: String,
    val note: String,
    val scheduledDay: Int?,
    val entries: List<PlannedEntry>,
)

/**
 * A proposed routine. [note] is routine-wide advice (muscles the week leaves
 * short, what a once-a-week split trades) shown once on review; it is never
 * saved into a workout's note, which carries only that day's rest and effort line.
 */
data class RoutinePlan(val presets: List<PlannedPreset>, val note: String = "")

/** The answers a generated program is built from. */
data class ProgramRequest(
    val focus: TrainingFocus,
    val volume: VolumeLevel,
    val equipment: Equipment,
    /** Week mode only; clamped to 1..6. */
    val daysPerWeek: Int = 3,
    val priorities: Set<MuscleArea> = emptySet(),
    val sex: Sex = Sex.MALE,
    /** Week mode only. A day count the split does not fit falls back to [TrainingSplit.forDays]. */
    val split: TrainingSplit = TrainingSplit.forDays(daysPerWeek),
    /** Compound & skill only: no [MovementDifficulty.isIsolation] movement is ever selected. */
    val compoundOnly: Boolean = false,
    /**
     * Most movements one workout may hold (3..8, clamped). Every session,
     * improve pass and template day stays at or under
     * [ProgramRules.exerciseCap] of this and the volume level.
     */
    val maxExercises: Int = ProgramRules.DEFAULT_MAX_EXERCISES,
    /** Minutes one workout may take, warm-up excluded (45, 60, 75 or 90). */
    val sessionMinutes: Int = ProgramRules.DEFAULT_SESSION_MINUTES,
)

/**
 * Best estimated one-rep max per movement, in the MARKED kilos the lifter
 * logs (not force), keyed by lowercase exercise name. Empty for a new lifter.
 */
data class StrengthProfile(
    val bestE1rmKg: Map<String, Double> = emptyMap(),
    /** Where a loaded lift with no logged relative starts; see [StarterLoads]. Null leaves such a lift unloaded. */
    val starter: StarterBasis? = null,
)

/** A logged working set, the raw material of a [StrengthProfile]. */
data class LoggedLift(val exerciseName: String, val weightKg: Double, val reps: Int)

/**
 * One independent change of an improve pass: what changes on which movement,
 * and why. Every change can be ticked on its own ([Improvement.apply]):
 *  - [id] is stable for the same preset and request (movement name, kind, and
 *    for ADJUSTED the sort of adjustment), so a screen can keep its ticks;
 *  - [exerciseName] is the movement as it reads once the change is in;
 *  - [tags] are at most two reasons, [coverageDelta] the weekly hard sets per
 *    muscle this change alone adds or removes;
 *  - [op] is the edit itself as data;
 *  - [requires] is the id of the change this one builds on (a load set for a
 *    swapped-in movement needs the swap): it stands only while that one does;
 *  - [options] are the ranked movements for a swap or an addition, best first.
 *    Option 0 is the movement [op] uses.
 */
data class PlanChange(
    val id: String,
    val kind: Kind,
    val exerciseName: String,
    val detail: String,
    val tags: List<ReasonTag> = emptyList(),
    val coverageDelta: Map<Muscle, Double> = emptyMap(),
    val op: ChangeOp,
    val requires: String? = null,
    val options: Ranked = Ranked(emptyList()),
) {
    enum class Kind { ADDED, REMOVED, SWAPPED, ADJUSTED, LOAD_SET }

    companion object {
        /** The id every change of this movement, kind and sort of edit gets. */
        fun idOf(exerciseName: String, kind: Kind, op: ChangeOp): String {
            val sort = when (op) {
                is ChangeOp.SetSets -> "|sets"
                is ChangeOp.SetReps -> "|reps"
                is ChangeOp.SpaceEntry -> "|space"
                else -> ""
            }
            return "$exerciseName|$kind$sort"
        }
    }
}

/**
 * What [Improvement.apply] needs besides the changes: how entries are
 * re-planned, the entry cap, which entries give way to it first, and which
 * entries the improve pass re-planned.
 */
data class ApplyPlan(
    val volume: VolumeLevel = VolumeLevel.STANDARD,
    val focus: TrainingFocus = TrainingFocus.MUSCLE,
    val cap: Int = Int.MAX_VALUE,
    /** Movements (as the preset gave them) that give way first when a subset leaves the preset over [cap]. */
    val trimOrder: List<String> = emptyList(),
    /** Lowercase names of the movements whose set range and effort cue are re-derived. */
    val replan: Set<String> = emptySet(),
)

/**
 * A preset and the independent changes proposed for it. [before] is what to
 * restore on undo, [after] is every change applied, and [apply] gives any
 * ticked subset, consistent whatever was left out. [changes] is empty when
 * nothing needed changing.
 */
data class Improvement(
    val before: PlannedPreset,
    val changes: List<PlanChange>,
    val plan: ApplyPlan = ApplyPlan(),
) {
    val after: PlannedPreset = apply(changes.map { it.id }.toSet())

    /**
     * [before] with the changes in [selectedIds] applied, in the canonical
     * order (remove, swap, sets, reps, load, add), then tidied: no movement
     * twice, the entry cap held, pull-ups kept apart. A change whose
     * [PlanChange.requires] is not in the set is left out too; unknown ids
     * are ignored; nothing selected gives [before].
     */
    fun apply(selectedIds: Set<String>): PlannedPreset = ProgramChanges.apply(before, changes, plan, selectedIds)
}

/**
 * A hand-authored program, written for a full gym at [authoredVolume] and
 * scaled to the lifter's chosen volume on build.
 */
data class ProgramTemplate(
    val id: String,
    val split: TrainingSplit,
    val authoredVolume: VolumeLevel,
    val focus: TrainingFocus,
    val name: String,
    val summary: String,
    val days: List<PlannedPreset>,
)
