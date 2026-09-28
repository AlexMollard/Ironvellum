package com.ironvellum.app.domain

/**
 * Shared vocabulary of the program generator, the tiered templates and the
 * "improve a preset" pass. Pure data: the rules live in ProgramGenerator,
 * ProgramTemplates, MuscleMap and ProgramRules, each citing the evidence
 * ([Evidence]) behind every number it uses.
 */

/** Where the load comes from. Mirrors the exercise picker's facets. */
enum class EquipmentAccess { BODYWEIGHT, HOME_WEIGHTS, FULL_GYM }

/** What the lifter is training for; decides rep ranges, rest and slot mix. */
enum class TrainingFocus { STRENGTH, MUSCLE, SKILL, GENERAL }

/**
 * How much weekly work the plan prescribes. The volume evidence is tiered on
 * training age (see ProgramRules.weeklySetTarget: under a year LEAN, one to
 * three years STANDARD, beyond that HIGH), so the app suggests the level from
 * logged history - but it is the lifter's dose to pick, not a rank. It also
 * sets reps in reserve, movements per session and bodyweight progressions.
 */
enum class VolumeLevel(val label: String) {
    LEAN("Lean"),
    STANDARD("Standard"),
    HIGH("High"),
}

/**
 * How the week is divided - the headline choice of the builder. With weekly
 * volume equated the split barely changes growth (Pelland 2026; Schoenfeld
 * 2019), so it is a scheduling preference, and each split offers only the
 * day counts it fits.
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
 * chest is over-served.
 */
enum class Muscle(val label: String) {
    CHEST("Chest"),
    LATS("Lats"),
    UPPER_BACK("Upper back"),
    FRONT_DELTS("Front delts"),
    SIDE_DELTS("Side delts"),
    REAR_DELTS("Rear delts"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    ADDUCTORS("Adductors"),
    CALVES("Calves"),
    ABS("Abs"),
    LOWER_BACK("Lower back"),
}

/** What a lifter can ask to prioritise; each area names the muscles it raises. */
enum class MuscleArea(val label: String, val muscles: Set<Muscle>) {
    CHEST("Chest", setOf(Muscle.CHEST)),
    BACK("Back", setOf(Muscle.LATS, Muscle.UPPER_BACK)),
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
)

/** One proposed session, scheduled on an ISO weekday (1 = Monday .. 7 = Sunday) or unscheduled. */
data class PlannedPreset(
    val name: String,
    val note: String,
    val scheduledDay: Int?,
    val entries: List<PlannedEntry>,
)

data class RoutinePlan(val presets: List<PlannedPreset>)

/** The answers a generated program is built from. */
data class ProgramRequest(
    val focus: TrainingFocus,
    val volume: VolumeLevel,
    val equipment: EquipmentAccess,
    /** Week mode only; clamped to 1..6. */
    val daysPerWeek: Int = 3,
    val priorities: Set<MuscleArea> = emptySet(),
    val sex: Sex = Sex.MALE,
    /** Week mode only. A day count the split does not fit falls back to [TrainingSplit.forDays]. */
    val split: TrainingSplit = TrainingSplit.forDays(daysPerWeek),
)

/**
 * Best estimated one-rep max per movement, in the MARKED kilos the lifter
 * logs (not force), keyed by lowercase exercise name. Empty for a new lifter.
 */
data class StrengthProfile(val bestE1rmKg: Map<String, Double> = emptyMap())

/** A logged working set, the raw material of a [StrengthProfile]. */
data class LoggedLift(val exerciseName: String, val weightKg: Double, val reps: Int)

/** One line of an improve pass: what changed on which movement, and why. */
data class PlanChange(val kind: Kind, val exerciseName: String, val detail: String) {
    enum class Kind { ADDED, REMOVED, SWAPPED, ADJUSTED, LOAD_SET }
}

/** A preset before and after an improve pass. [changes] is empty when nothing needed changing. */
data class Improvement(
    val before: PlannedPreset,
    val after: PlannedPreset,
    val changes: List<PlanChange>,
)

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
