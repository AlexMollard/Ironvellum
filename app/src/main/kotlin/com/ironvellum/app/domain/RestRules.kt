package com.ironvellum.app.domain

/**
 * How long to rest after a set of a given movement: a READY time (the
 * earliest sensible restart, which the live timer counts down to and the time
 * estimates price) and a MAX (past it more rest buys nothing; shown as a
 * soft ceiling, +15 still extends past it).
 *
 * The numbers follow the two findings the repo cites: Schoenfeld 2016 (3 min
 * beat 1 min for strength and for quad and triceps growth in trained men, so
 * heavy loaded work earns the long end) and Singer 2024 (growth benefit
 * above ~60 s, flattening near 90 s, so small-muscle and light work is done
 * sooner). Within those findings the per-category figures are judgement
 * calls, not measured per exercise.
 *
 * The category is DERIVED from fields the catalogue already holds (pattern,
 * compound, unilateral, hold, technique, gear, load factor, skill line); a
 * short [overrides] map covers the names that derivation gets wrong.
 */
object RestRules {

    /** [ready] seconds to the earliest restart; [max] seconds past which more rest adds nothing. 0/0 means no timer. */
    data class Window(val ready: Int, val max: Int) {
        val isNone: Boolean get() = ready <= 0
    }

    /** A category's two columns. [skill] marks the skill categories a SKILL focus reads from the strength column. */
    class Windows(val muscle: Window, val strength: Window, val skill: Boolean = false) {
        constructor(ready: Int, max: Int) : this(Window(ready, max), Window(ready, max))
    }

    private fun w(mr: Int, mm: Int, sr: Int, sm: Int, skill: Boolean = false) =
        Windows(Window(mr, mm), Window(sr, sm), skill)

    // shortcut: category values, not per-exercise evidence; promote a category to a per-exercise number when one proves wrong.
    /** A: heavy barbell lower (squat, hinge). */
    val HEAVY_LOWER = w(120, 180, 180, 300)
    /** B: heavy barbell upper (press, row, weighted pull-up and dip). */
    val HEAVY_UPPER = w(90, 150, 150, 240)
    /** C: dumbbell, Smith, machine, cable and plate-loaded compounds; also the unprofiled default. */
    val LOADED_COMPOUND = w(75, 120, 120, 180)
    /** D: bodyweight compounds. */
    val BODYWEIGHT_COMPOUND = w(60, 90, 90, 150)
    /** E: unilateral compounds, after both sides. */
    val UNILATERAL_COMPOUND = w(90, 120, 120, 180)
    /** F: upper-body and small-muscle isolation. */
    val ISOLATION_SMALL = w(45, 75, 60, 90)
    /** G: lower-body and high-fatigue single-joint work. */
    val ISOLATION_LOWER = w(60, 90, 75, 120)
    /** H: dynamic core. */
    val CORE_DYNAMIC = w(45, 75, 45, 75)
    /** I: loaded anti-extension core. */
    val CORE_LOADED = w(60, 90, 75, 120)
    /** J: static holds that are not skills (plank, hang). */
    val HOLD_STATIC = w(45, 75, 60, 90)
    /** K: skill statics and levers. */
    val SKILL_STATIC = w(90, 150, 120, 240, skill = true)
    /** L: skill dynamics and technique. */
    val SKILL_DYNAMIC = w(120, 180, 150, 300, skill = true)
    /** M: mobility and stretching drills. */
    val MOBILITY = Windows(15, 30)
    /** N: activities (a run, a sport): one logged session, no rest between sets. */
    val NONE = Windows(0, 0)

    /** The scapular drill: pulling skill by name, but light work. */
    private val SCAPULAR = Windows(45, 75)

    /** Big sled machines: the muscle column is longer than the other machines. */
    private val SLED = Windows(Window(90, 150), LOADED_COMPOUND.strength)

    /** Names derivation gets wrong, lowercase. */
    private val overrides: Map<String, Windows> = mapOf(
        // Bodyweight by gear, but loaded or heavy in effect.
        "weighted pull-up" to HEAVY_UPPER,
        "weighted dip" to HEAVY_UPPER,
        // Dumbbell or barbell by gear; hinges heavy either way.
        "romanian deadlift" to HEAVY_LOWER,
        "leg press" to SLED,
        "hack squat" to SLED,
        // Single-joint by flag, but very high fatigue.
        "nordic curl" to Windows(90, 150),
        "nordic negative" to Windows(90, 150),
        // Core: anti-extension work and a quick explosive technique move.
        "ab wheel rollout" to CORE_LOADED,
        "dragon flag" to CORE_LOADED,
        "kip-up" to Windows(60, 90),
        // Static holds the tree files under Core and Pull.
        "hollow hold" to HOLD_STATIC,
        "dead hang" to HOLD_STATIC,
        // Metres, not seconds, so not a hold by name.
        "handstand walk" to SKILL_STATIC,
        // Drills.
        "scapular pull" to SCAPULAR,
        "knee-to-wall dorsiflexion" to MOBILITY,
    )

    /** The overridden names, lowercase - exposed for the coverage test. */
    val overrideNames: Set<String> get() = overrides.keys

    private val lowerMuscles = setOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.ADDUCTORS, Muscle.ABDUCTORS)

    private fun key(name: String) = name.trim().lowercase()

    /**
     * The category windows for [name]; [metric] is the catalogue metric and
     * [weighted] the exercise's weighted flag, each when known. A movement
     * the catalogue does not profile (a custom one) rests as a loaded
     * compound, or a bodyweight one when it is known to be unweighted.
     */
    fun windowsFor(name: String, metric: ExerciseMetric? = null, weighted: Boolean? = null): Windows {
        overrides[key(name)]?.let { return it }
        if (metric == ExerciseMetric.DURATION || metric == ExerciseMetric.DISTANCE_TIME ||
            metric == ExerciseMetric.ATTEMPTS_GRADE
        ) return NONE
        val profile = MuscleMap.profile(name)
            ?: return when {
                metric == ExerciseMetric.HOLD -> HOLD_STATIC
                weighted == false -> BODYWEIGHT_COMPOUND
                else -> LOADED_COMPOUND
            }
        // Holds before the compound check: the hold() helper marks most holds compound.
        if (MovementDifficulty.isHoldSet(metric, name)) {
            val line = Skills.ALL.firstOrNull { key(it.name) == key(name) }?.line
            return when (line) {
                "Mobility" -> MOBILITY
                null -> HOLD_STATIC
                else -> SKILL_STATIC
            }
        }
        if (profile.noLeadByDesign && profile.compound) return NONE
        if (MuscleMap.isTechnique(name)) return SKILL_DYNAMIC
        if (profile.pattern == MovementPattern.CORE) return CORE_DYNAMIC
        if (!profile.compound) {
            val lead = profile.muscles.entries.firstOrNull { it.value == 1.0 }?.key
            return if (profile.pattern == MovementPattern.HINGE || lead in lowerMuscles) ISOLATION_LOWER
            else ISOLATION_SMALL
        }
        if (profile.unilateral) return UNILATERAL_COMPOUND
        if (MovementDifficulty.loadFactor(name) < MovementDifficulty.FREE_WEIGHT_LOAD || key(name).startsWith("assisted")) {
            return LOADED_COMPOUND
        }
        val needs = GearRequirements.needs(name)
        if (needs.isNotEmpty() && needs.all { Gear.BARBELL in it }) {
            val lower = profile.pattern == MovementPattern.SQUAT || profile.pattern == MovementPattern.HINGE
            return if (lower) HEAVY_LOWER else HEAVY_UPPER
        }
        if (needs.any { Gear.DUMBBELLS in it }) return LOADED_COMPOUND
        return BODYWEIGHT_COMPOUND
    }

    /**
     * The window for [name] under [focus]. STRENGTH reads the strength column,
     * MUSCLE the muscle column, GENERAL the midpoint of the two, SKILL the
     * strength column for the skill categories and the muscle column otherwise.
     * A live trial or estimate resolves its focus from the lifter's training
     * mode first ([SessionClock.focusFor]), so these four only decide when no
     * mode is known.
     */
    fun window(name: String, focus: TrainingFocus, metric: ExerciseMetric? = null, weighted: Boolean? = null): Window {
        val c = windowsFor(name, metric, weighted)
        return when (focus) {
            TrainingFocus.STRENGTH -> c.strength
            TrainingFocus.MUSCLE -> c.muscle
            TrainingFocus.GENERAL -> Window((c.muscle.ready + c.strength.ready) / 2, (c.muscle.max + c.strength.max) / 2)
            TrainingFocus.SKILL -> if (c.skill) c.strength else c.muscle
        }
    }

    /** Ready seconds only: what the clock estimates are priced at. */
    fun readySeconds(name: String, focus: TrainingFocus, metric: ExerciseMetric? = null, weighted: Boolean? = null): Int =
        window(name, focus, metric, weighted).ready
}
