package com.ironvellum.app.domain

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
enum class Gear(val label: String) {
    PULL_UP_BAR("Pull-up bar"),
    DIP_BARS("Dip bars"),
    PARALLETTES("Parallettes"),
    RINGS("Rings"),
    DUMBBELLS("Dumbbells"),
    BARBELL("Barbell & rack"),
    BENCH("Bench"),
    AB_WHEEL("Ab wheel"),
}

/**
 * What the lifter can train with. [fullGym] means everything, machines and
 * cables included, with no dumbbell cap. [dumbbellMaxKg] is the per-dumbbell
 * ceiling; null means no cap (full gym, or never answered). [dumbbellPair]
 * is false when the lifter owns a single dumbbell, which rules out the
 * two-dumbbell presses and flys.
 */
data class Equipment(
    val fullGym: Boolean,
    val gear: Set<Gear> = emptySet(),
    val dumbbellMaxKg: Double? = null,
    val dumbbellPair: Boolean = true,
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
enum class TrainingFocus { STRENGTH, MUSCLE, SKILL, GENERAL }

/**
 * How much weekly work the plan prescribes. The volume evidence is tiered on
 * training age (see ProgramRules.weeklySetTarget: under a year LOW, one to
 * three years STANDARD, beyond that HIGH), so the app suggests the level from
 * logged history - but it is the lifter's dose to pick, not a rank. It also
 * sets reps in reserve, movements per session and bodyweight progressions.
 * Persisted by constant name (ProgramAnswers), so never rename a constant.
 */
enum class VolumeLevel(val label: String) {
    LOW("Low"),
    STANDARD("Standard"),
    HIGH("High"),
}

/**
 * How the week is divided - the headline choice of the builder. With weekly
 * volume equated the split barely changes growth (Pelland 2026; Schoenfeld
 * 2019), so it is a scheduling preference, and each split offers only the
 * day counts it fits. Persisted by constant name (ProgramAnswers), so never
 * rename a constant.
 */
enum class TrainingSplit(val label: String, val dayOptions: List<Int>) {
    FULL_BODY("Full body", listOf(1, 2, 3)),
    UPPER_LOWER("Upper / lower", listOf(4)),
    PUSH_PULL_LEGS("Push / pull / legs", listOf(3, 6)),
    UPPER_LOWER_PPL("Upper / lower + PPL", listOf(5)),
    ;

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
enum class Muscle(val label: String) {
    UPPER_CHEST("Upper chest"),
    MID_CHEST("Mid chest"),
    LOWER_CHEST("Lower chest"),
    LATS("Lats"),
    RHOMBOIDS("Rhomboids"),
    TRAPS("Traps"),
    NECK("Neck"),
    FRONT_DELTS("Front delts"),
    SIDE_DELTS("Side delts"),
    REAR_DELTS("Rear delts"),
    ROTATOR_CUFF("Rotator cuff"),
    SERRATUS("Serratus"),
    BICEPS("Biceps"),
    BRACHIALIS("Brachialis"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    ABS("Abs"),
    OBLIQUES("Obliques"),
    LOWER_BACK("Lower back"),
    HIP_FLEXORS("Hip flexors"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    ADDUCTORS("Adductors (inner thigh)"),
    ABDUCTORS("Abductors (outer hip)"),
    CALVES("Calves"),
    TIBIALIS("Tibialis"),
}

/**
 * What a lifter can ask to prioritise; each area names the muscles it raises.
 * Persisted by constant name (ProgramAnswers), so never rename a constant.
 * An area may name helper muscles: prioritising only raises the TRACKED
 * muscles to the top of their range - helpers keep their floor either way
 * (see ProgramGenerator.floorOrTarget) and only gain the "Priority" wording.
 */
enum class MuscleArea(val label: String, val muscles: Set<Muscle>) {
    CHEST("Chest", setOf(Muscle.UPPER_CHEST, Muscle.MID_CHEST, Muscle.LOWER_CHEST)),
    BACK("Back", setOf(Muscle.LATS, Muscle.RHOMBOIDS, Muscle.TRAPS)),
    SHOULDERS("Shoulders", setOf(Muscle.SIDE_DELTS, Muscle.REAR_DELTS, Muscle.FRONT_DELTS)),
    ARMS("Arms", setOf(Muscle.BICEPS, Muscle.TRICEPS)),
    QUADS("Quads", setOf(Muscle.QUADS)),
    HAMSTRINGS("Hamstrings", setOf(Muscle.HAMSTRINGS)),
    GLUTES("Glutes", setOf(Muscle.GLUTES)),
    CALVES("Calves", setOf(Muscle.CALVES)),
    CORE("Core", setOf(Muscle.ABS)),
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
enum class SessionKind(val label: String) {
    AUTO("What my week is missing"),
    FULL_BODY("Full body"),
    UPPER("Upper"),
    LOWER("Lower"),
    PUSH("Push"),
    PULL("Pull"),
    LEGS("Legs"),
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
data class StrengthProfile(val bestE1rmKg: Map<String, Double> = emptyMap())

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
