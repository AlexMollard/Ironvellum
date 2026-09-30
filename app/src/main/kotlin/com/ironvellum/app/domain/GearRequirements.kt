package com.ironvellum.app.domain

/**
 * What each movement NEEDS, as an explicit per-movement table: a lowercase
 * catalogue name maps to a list of alternatives, and each alternative is a
 * set of [Gear] that must ALL be present. The coarse access levels this
 * replaces (BODYWEIGHT / HOME_WEIGHTS / FULL_GYM) mis-sold reality: a lifter
 * with a pull-up bar, parallettes and a 24 kg dumbbell still has no rings
 * and nowhere to do an inverted row.
 *
 * Rules, in the order [allows] applies them:
 * - Machines, cables, sleds, Smith and assisted machines (the existing
 *   [isMachine] rule: an "assisted" prefix or a loadFactor below
 *   [MovementDifficulty.FREE_WEIGHT_LOAD]) need a full gym - they are never
 *   toggles. The table therefore lists no machine rows; the rule covers them.
 * - Bar-hanging moves need a PULL_UP_BAR or RINGS, except where the
 *   implement is fixed (muscle-up: bar; skin the cat / ring muscle-up: rings).
 * - Inverted row / australian pull-up: RINGS, or a BARBELL set low in a rack.
 * - Dip / parallel bar dip: DIP_BARS or RINGS. Bench dip needs nothing
 *   (a chair). Ab wheel rollout: AB_WHEEL. L-sit: parallettes, dip bars or
 *   the floor (holds are excluded from the generator anyway).
 * - Barbell lifts: BARBELL; bench variants need BARBELL + BENCH, but the
 *   hip thrust is fine with the bar alone (a couch works).
 * - Dumbbell moves: DUMBBELLS; the two-dumbbell presses and fly also need a
 *   BENCH and a PAIR (see [needsPair]). Preacher curl and chest-supported
 *   row were judged from the seed catalogue: preacher curl sits in its
 *   dumbbell free-weight band (loadFactor default 1.0), so DUMBBELLS;
 *   chest-supported row sits in the plate-loaded lever band (loadFactor
 *   0.70), so it is a machine and the full-gym rule covers it.
 * - Shared-implement lifts with isWeighted = true take the implement they
 *   are actually done with: romanian deadlift and bulgarian split squat
 *   DUMBBELLS or BARBELL.
 * - Unlisted NON-weighted movements are allowed with nothing. Unlisted
 *   WEIGHTED movements are NOT allowed - every weighted, generator-eligible
 *   catalogue movement must have an explicit row (a test enforces this), so
 *   nothing falls through silently.
 */
object GearRequirements {

    /** Alternatives per movement; each set must be fully owned. */
    private val requirements: Map<String, List<Set<Gear>>> = mapOf(
        // Bar-hanging: bar or rings.
        "pull-up" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "chin-up" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "archer pull-up" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "l-sit pull-up" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "one-arm pull-up" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "one-arm negative" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "weighted pull-up" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "scapular pull" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "dead hang" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "active bar hang" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "one-arm hang" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "hanging leg raise" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "hanging knee raise" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "toes-to-bar" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        "inverted muscle-up" to listOf(setOf(Gear.PULL_UP_BAR), setOf(Gear.RINGS)),
        // Fixed implements.
        "muscle-up" to listOf(setOf(Gear.PULL_UP_BAR)),
        "strict muscle-up" to listOf(setOf(Gear.PULL_UP_BAR)),
        "skin the cat" to listOf(setOf(Gear.RINGS)),
        "ring muscle-up" to listOf(setOf(Gear.RINGS)),
        "ring row" to listOf(setOf(Gear.RINGS)),
        "ring dip" to listOf(setOf(Gear.RINGS)),
        // Horizontal pulling under the bar: rings, or a bar set low in a rack.
        "inverted row" to listOf(setOf(Gear.RINGS), setOf(Gear.BARBELL)),
        "australian pull-up" to listOf(setOf(Gear.RINGS), setOf(Gear.BARBELL)),
        // Dips: bars or rings. Bench dip needs nothing, so it is unlisted.
        "dip" to listOf(setOf(Gear.DIP_BARS), setOf(Gear.RINGS)),
        "parallel bar dip" to listOf(setOf(Gear.DIP_BARS), setOf(Gear.RINGS)),
        "weighted dip" to listOf(setOf(Gear.DIP_BARS), setOf(Gear.RINGS)),
        // Core implements.
        "ab wheel rollout" to listOf(setOf(Gear.AB_WHEEL)),
        // Floor works too, hence the empty-set alternative.
        "l-sit" to listOf(setOf(Gear.PARALLETTES), setOf(Gear.DIP_BARS), emptySet()),
        // Barbell free weights.
        "back squat" to listOf(setOf(Gear.BARBELL)),
        "front squat" to listOf(setOf(Gear.BARBELL)),
        "deadlift" to listOf(setOf(Gear.BARBELL)),
        "sumo deadlift" to listOf(setOf(Gear.BARBELL)),
        "rack pull" to listOf(setOf(Gear.BARBELL)),
        "barbell row" to listOf(setOf(Gear.BARBELL)),
        "pendlay row" to listOf(setOf(Gear.BARBELL)),
        "t-bar row" to listOf(setOf(Gear.BARBELL)),
        "overhead press" to listOf(setOf(Gear.BARBELL)),
        "push press" to listOf(setOf(Gear.BARBELL)),
        "good morning" to listOf(setOf(Gear.BARBELL)),
        "barbell lunge" to listOf(setOf(Gear.BARBELL)),
        "barbell step-up" to listOf(setOf(Gear.BARBELL)),
        "barbell shrug" to listOf(setOf(Gear.BARBELL)),
        // Bench variants: bar plus bench...
        "bench press" to listOf(setOf(Gear.BARBELL, Gear.BENCH)),
        "incline bench press" to listOf(setOf(Gear.BARBELL, Gear.BENCH)),
        "close-grip bench press" to listOf(setOf(Gear.BARBELL, Gear.BENCH)),
        // ...but a couch works for the hip thrust.
        "hip thrust" to listOf(setOf(Gear.BARBELL)),
        // Dumbbell free weights. Preacher curl is seeded in the dumbbell
        // free-weight band, so it stays a dumbbell move (see class doc).
        "dumbbell row" to listOf(setOf(Gear.DUMBBELLS)),
        "dumbbell shoulder press" to listOf(setOf(Gear.DUMBBELLS)),
        "arnold press" to listOf(setOf(Gear.DUMBBELLS)),
        "lateral raise" to listOf(setOf(Gear.DUMBBELLS)),
        "front raise" to listOf(setOf(Gear.DUMBBELLS)),
        "reverse fly" to listOf(setOf(Gear.DUMBBELLS)),
        "hammer curl" to listOf(setOf(Gear.DUMBBELLS)),
        "preacher curl" to listOf(setOf(Gear.DUMBBELLS)),
        "dumbbell external rotation" to listOf(setOf(Gear.DUMBBELLS)),
        "dumbbell shrug" to listOf(setOf(Gear.DUMBBELLS)),
        "goblet squat" to listOf(setOf(Gear.DUMBBELLS)),
        "walking lunge" to listOf(setOf(Gear.DUMBBELLS)),
        "dumbbell step-up" to listOf(setOf(Gear.DUMBBELLS)),
        "triceps kickback" to listOf(setOf(Gear.DUMBBELLS)),
        "dumbbell pullover" to listOf(setOf(Gear.DUMBBELLS)),
        "wrist curl" to listOf(setOf(Gear.DUMBBELLS)),
        "bicep curl" to listOf(setOf(Gear.DUMBBELLS), setOf(Gear.BARBELL)),
        "reverse curl" to listOf(setOf(Gear.DUMBBELLS), setOf(Gear.BARBELL)),
        // Shared-implement lifts: either implement as applicable.
        "romanian deadlift" to listOf(setOf(Gear.DUMBBELLS), setOf(Gear.BARBELL)),
        "bulgarian split squat" to listOf(setOf(Gear.DUMBBELLS), setOf(Gear.BARBELL)),
        // Two-dumbbell moves: dumbbells, a bench, and a PAIR.
        "dumbbell bench press" to listOf(setOf(Gear.DUMBBELLS, Gear.BENCH)),
        "incline dumbbell press" to listOf(setOf(Gear.DUMBBELLS, Gear.BENCH)),
        "dumbbell fly" to listOf(setOf(Gear.DUMBBELLS, Gear.BENCH)),
        // Cable movements the machine rule cannot see (no loadFactor entry):
        // an empty alternative list means no toggle expresses them - full gym only.
        "face pull" to emptyList(),
    )

    /** Movements that physically need TWO dumbbells; a single one rules them out. */
    private val pairOnly: Set<String> = setOf(
        "dumbbell bench press",
        "incline dumbbell press",
        "dumbbell fly",
    )

    /**
     * One-step harder variant when the capped dumbbell cannot reach failure
     * inside 20 reps (Lopez 2021): same pattern, more bodyweight share.
     */
    internal val harderVariant: Map<String, String> = mapOf(
        "goblet squat" to "Bulgarian Split Squat",
        "walking lunge" to "Bulgarian Split Squat",
        "dumbbell step-up" to "Bulgarian Split Squat",
    )

    /** Whether the movement's catalogue row is machine, cable, sled, Smith or assisted. */
    private fun isMachine(name: String): Boolean =
        name.trim().lowercase().startsWith("assisted") ||
            MovementDifficulty.loadFactor(name) < MovementDifficulty.FREE_WEIGHT_LOAD

    /** True when the movement is generator-eligible under this equipment. */
    fun allows(exerciseName: String, isWeighted: Boolean, equipment: Equipment): Boolean {
        val name = exerciseName.trim().lowercase()
        if (equipment.fullGym) return true
        if (isMachine(name)) return false
        if (needsPair(name) && !equipment.dumbbellPair) return false
        val alternatives = requirements[name]
        if (alternatives == null) return !isWeighted
        return alternatives.any { alternative -> equipment.gear.containsAll(alternative) }
    }

    /**
     * Whether a browsing lifter with [equipment] can do the row, for the
     * exercise picker's "my gear" filter. It reuses [allows], differing in
     * two places where the generator is strict and browsing must not be:
     * activities (a non-blank [category]) are never gear-gated, and a
     * weighted, non-machine movement with no requirements row has UNKNOWN
     * needs, so it stays visible instead of being treated as impossible.
     */
    fun fits(exerciseName: String, isWeighted: Boolean, category: String, equipment: Equipment): Boolean {
        if (category.isNotBlank()) return true
        val name = exerciseName.trim().lowercase()
        if (isWeighted && !isMachine(name) && name !in requirements) return true
        return allows(exerciseName, isWeighted, equipment)
    }

    /** True when the movement cannot be done with a single dumbbell. */
    fun needsPair(name: String): Boolean = name.trim().lowercase() in pairOnly

    /** The alternative gear sets the movement needs (each set must be fully owned); empty when unlisted or nothing is needed. */
    fun needs(exerciseName: String): List<Set<Gear>> =
        requirements[exerciseName.trim().lowercase()].orEmpty().filter { it.isNotEmpty() }

    /** Whether the movement has an explicit row - the silent-fallthrough guard. */
    internal fun hasEntry(name: String): Boolean = name.trim().lowercase() in requirements
}
