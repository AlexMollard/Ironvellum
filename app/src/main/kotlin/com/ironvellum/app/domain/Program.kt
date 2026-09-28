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
 * Training age, the axis the volume evidence is tiered on (see
 * ProgramRules): beginner under a year, intermediate one to three years,
 * advanced beyond that.
 */
enum class ExperienceTier(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
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
    val tier: ExperienceTier,
    val equipment: EquipmentAccess,
    /** Week mode only; clamped to 1..6. */
    val daysPerWeek: Int = 3,
    val priorities: Set<MuscleArea> = emptySet(),
    val sex: Sex = Sex.MALE,
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

/** A hand-authored program, one per tier and goal, written for a full gym. */
data class ProgramTemplate(
    val id: String,
    val tier: ExperienceTier,
    val focus: TrainingFocus,
    val name: String,
    val summary: String,
    val days: List<PlannedPreset>,
)
