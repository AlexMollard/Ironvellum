package com.ironvellum.app.domain

/**
 * The share of the lifter's body a bodyweight movement actually moves, and the estimated one-rep max that follows
 * from it. The ONE table: [MovementDifficulty.bodyShare] (set schemes), the History tab, the Stats records board,
 * the recaps and reviews all read it, so a push-up cannot be "two thirds of the body" in one place and "nothing"
 * in another.
 *
 * Why it exists. A bodyweight lift with a vest or a belt moves the body AND the plates. Epley on the plates alone
 * (+18 kg x 12 -> 25 kg) ignores 50-ish kilos of the load and ranks +55 kg x 3 below +18 kg x 12. The estimate is
 * taken on the TOTAL load (share x body weight + added kg), then expressed back as the ADDED-load equivalent
 * (total e1RM minus the share): "+46 kg" reads on the same scale as the plates the lifter loads.
 *
 * Which movements. [addsToBody]: the catalogue's bodyweight movements (isWeighted false) and the named "Weighted ..."
 * variants. Barbell, dumbbell and machine lifts are external load: share 0, the marked weight is the whole load.
 *
 * Where the numbers come from, honestly:
 *  - PUBLISHED measurements (ground-reaction-force studies, recalled from the literature and NOT re-checked
 *    against the papers in this session): a standard floor push-up carries about 64-66 % of body weight on the
 *    hands (Ebben et al. 2011, J Strength Cond Res; Suprak et al. 2011; Cogley et al. 2005) and a push-up from the
 *    knees about 49 %. A hands-and-feet-elevated variation shifts it by roughly +-10 to 30 %.
 *  - SEGMENT ANTHROPOMETRY (Dempster 1955 as tabulated by Winter 2009): a leg is about 16 % of body mass, shank +
 *    foot about 6 %, head + trunk + arms about 60 %. Squats, single-leg squats, calf raises, bridges, back
 *    extensions, hyperextensions and leg raises are derived from these: the mass that is lifted, not the mass that
 *    rests on the floor.
 *  - ESTIMATES (geometry or judgement, no measurement behind them): incline and decline multipliers, pike
 *    push-ups, inverted rows, lunges and split squats, hanging and floor core work, the plank family. Treat
 *    them as +-0.1.
 *  - DELIBERATE v1 SIMPLIFICATION: pull-ups, chin-ups, dips and the skill-tree rungs move the whole body (1.0), the
 *    same assumption [StrengthIndex] and [LiftBoards] make, so the records board and the Rank cannot disagree.
 *    The truth is a little under, from the hands and forearms.
 *
 * Modifiers adjust the share only where they change how much body is on the working limbs: an incline or an
 * elevation changes the angle of a push-up or a row; a deficit lengthens the range and moves nothing, so it is
 * x1.0. Assisted sets are not adjusted: their marked kilos SUBTRACT load, which the plain-load rule here does not
 * model (shortcut: they keep the old marked-load estimate; add a signed load if assisted records matter).
 */
object BodyShare {

    const val MIN_SHARE = 0.1
    const val MAX_SHARE = 1.0

    /** Where a number came from. */
    enum class Basis {
        /** A named movement with a published or deliberately chosen value. */
        OVERRIDE,

        /** A family of movements by name (every lunge, every plank...). */
        FAMILY,

        /** A technique rung of the skill tree: balance or transition work that moves the whole body. */
        TECHNIQUE,

        /** The movement pattern's default ([MuscleMap] profile). */
        PATTERN,

        /** Nothing in the profile: the muscle group's default. */
        GROUP,
    }

    class Derived(val share: Double, val basis: Basis)

    /**
     * True when the set's marked load is ADDED to the body (a pull-up's plates), so the body is part of the load.
     * Free-standing single-leg work (split squats, lunges, step-ups) counts the body even when the catalogue marks it
     * weighted: the dumbbells or vest ride on a body the working leg also moves. Bar, Smith and machine versions keep
     * the gym convention of the marked load only.
     */
    fun addsToBody(exercise: Exercise): Boolean {
        val name = exercise.name.trim().lowercase()
        if (!exercise.isWeighted || name.startsWith("weighted")) return true
        return singleLegFamilies.any { it in name } && externalRigs.none { it in name }
    }

    private val singleLegFamilies = listOf("split squat", "lunge", "step-up")
    private val externalRigs = listOf("barbell", "smith", "machine", "landmine", "cable")

    private class Rule(val share: Double, val matches: (String) -> Boolean)

    private fun named(vararg names: String): (String) -> Boolean = { it in names }

    private fun has(vararg parts: String): (String) -> Boolean = { n -> parts.any { it in n } }

    /** Named movements, first match wins. Sources in the class comment. */
    private val overrides: List<Rule> = listOf(
        // Push-ups: floor push-up ~64-66 % (published GRF); knees ~49 % (published); the rest are estimates.
        Rule(0.65, named("push-up", "archer push-up", "diamond push-up", "wide push-up", "typewriter push-up", "explosive push-up", "push-up shoulder tap", "scapular push-up", "bosu ball push-up", "exercise ball push-up", "one-arm biased push-up")),
        Rule(0.49, named("knee push-up")),
        Rule(0.72, named("decline push-up", "hindu push-up")),
        Rule(0.15, named("wall push-up")),
        Rule(0.45, named("incline push-up")),
        Rule(0.75, named("pike push-up")),
        Rule(0.85, named("feet-elevated pike push-up", "elevated pike push-up", "wall walk")),
        Rule(1.0, named("handstand push-up")),
        // Whole-body hangs and supports: the v1 simplification.
        Rule(1.0, named("pull-up", "chin-up", "dip", "chest dip", "l-sit")),
        // A bench dip keeps the feet on the floor: roughly half the body (estimate).
        Rule(0.55, named("bench dip")),
        // Inverted row at roughly 45 degrees: an estimate (a horizontal body would equal the push-up's ~0.65).
        // An Australian pull-up is the same lift under another name.
        Rule(0.5, named("inverted row", "australian pull-up")),
        // The knees travel forward under the whole upper body: an estimate.
        Rule(0.7, named("sissy squat", "weighted sissy squat")),
        Rule(0.9, named("jumping jack")),
        // The band, the cable or the neck's own few kilos is the load, not the body: the floor of the band.
        Rule(MIN_SHARE, named("abdominal draw-in", "banded lat pulldown", "banded upright row", "banded crossover", "banded incline bench press", "banded pallof press", "banded woodchop", "banded reverse fly", "banded face pull", "band pull-apart", "banded alternating curl", "cable pallof hold", "standing towel triceps extension", "front split", "half split", "pancake")),
        // Single-leg squat: body minus the support shank and foot (anthropometry): 1 - 0.06.
        Rule(0.94, named("pistol squat", "skater squat", "single-leg box squat", "shrimp squat")),
        Rule(0.8, named("split squat")),
        Rule(0.85, named("bulgarian split squat")),
    )

    /** Families by name, first match wins; each is an estimate unless the class comment says otherwise. */
    private val families: List<Rule> = listOf(
        Rule(1.0, has("pull-up", "chin-up", "muscle-up")),
        Rule(1.0) { n -> n.endsWith(" dip") && !n.endsWith("hip dip") },
        Rule(0.65, has("push-up")),
        Rule(0.9, named("tuck front lever row")),
        // Arms hold the whole body: levers, planches, handstands, supports, the L-sit family, the flag.
        Rule(1.0, has("lever", "planche", "support hold", "handstand", "hspu", "l-sit", "v-sit", "manna", "human flag", "crow pose", "frog stand")),
        Rule(0.5, has(" row")),
        Rule(0.6, has("rollout", "dragon flag")),
        // Hanging core: legs (and some pelvis) swing, straight legs on a long lever.
        Rule(0.4, has("hanging leg raise")),
        Rule(0.25, has("hanging knee raise", "hanging oblique knee raise", "captain's chair knee raise")),
        Rule(0.65, has("plank")),
        Rule(0.5, has("sit-up", "v-up", "hollow", "mountain climber", "crab walk", "bear crawl", "inchworm")),
        Rule(0.7, has("burpee", "sprawl")),
        Rule(0.3, has("crunch", "toe touch", "leg lift", "leg raise", "bird dog")),
        Rule(0.25, has("bicycle", "knee tuck")),
        Rule(0.35, has("russian twist")),
        Rule(0.2, has("dead bug", "flutter kick")),
        // Lower body from segment anthropometry: lifted mass, not floor-supported mass.
        Rule(0.7, has("nordic", "single-leg glute bridge")),
        Rule(0.65, has("glute bridge", "hip thrust", "frog pump")),
        Rule(0.6, has("back extension", "hamstring curl", "hamstring walkout")),
        Rule(0.3, has("hyperextension", "superman")),
        Rule(0.95, has("calf raise")),
        Rule(0.2, has("tib raise")),
        Rule(0.94, has("pistol squat", "shrimp squat")),
        Rule(0.8, has("lunge", "step-down", "step-up", "cossack")),
        Rule(0.15, has("clamshell", "fire hydrant", "donkey kick", "kickback", "hip abduction", "side-lying", "lateral walk", "monster walk")),
    )

    /** Pattern defaults for what no rule names. */
    private fun patternShare(pattern: MovementPattern): Double = when (pattern) {
        MovementPattern.HORIZONTAL_PUSH -> 0.65
        MovementPattern.VERTICAL_PUSH -> 0.75
        MovementPattern.HORIZONTAL_PULL -> 0.5
        MovementPattern.VERTICAL_PULL -> 1.0
        MovementPattern.SQUAT -> 0.88
        MovementPattern.LUNGE -> 0.8
        MovementPattern.HINGE -> 0.65
        MovementPattern.CORE -> 0.4
        MovementPattern.ISOLATION -> MIN_SHARE + 0.05
    }

    private fun groupShare(group: MuscleGroup?): Double = when (group) {
        MuscleGroup.PUSH -> 0.65
        MuscleGroup.PULL -> 0.5
        MuscleGroup.LEGS -> 0.85
        MuscleGroup.CORE -> 0.4
        else -> 0.5
    }

    private fun key(name: String) = name.trim().lowercase()

    /** The movement's unmodified share and where it came from. [group] is the fallback for a movement with no profile. */
    fun derive(exerciseName: String, group: MuscleGroup? = null): Derived {
        val n = key(exerciseName)
        overrides.firstOrNull { it.matches(n) }?.let { return Derived(it.share, Basis.OVERRIDE) }
        if (MuscleMap.isTechnique(n)) return Derived(1.0, Basis.TECHNIQUE)
        families.firstOrNull { it.matches(n) }?.let { return Derived(it.share, Basis.FAMILY) }
        MuscleMap.profile(n)?.let { return Derived(patternShare(it.pattern), Basis.PATTERN) }
        return Derived(groupShare(group), Basis.GROUP)
    }

    /**
     * What the modifiers do to a share, as a multiplier. A push-up body is a lever: feet up ("elevated", "decline")
     * puts more on the hands, hands up ("incline") less, both cancel. An inverted row is the same lever read the
     * other way. A rear foot up loads the front leg a little more. Everything else, deficit included, leaves it.
     */
    private fun modifierFactor(exerciseName: String, modifiers: String): Double {
        val words = Regex("[a-z]+").findAll(modifiers.lowercase()).map { it.value }.toSet()
        if (words.isEmpty()) return 1.0
        val n = key(exerciseName)
        val up = "elevated" in words || "decline" in words
        val down = "incline" in words
        val pattern = MuscleMap.profile(n)?.pattern
        return when {
            "push-up" in n -> when {
                up && !down -> 1.10
                down && !up -> 0.70
                else -> 1.0
            }
            pattern == MovementPattern.HORIZONTAL_PULL && " row" in n -> when {
                up && !down -> 1.30
                down && !up -> 0.80
                else -> 1.0
            }
            pattern == MovementPattern.LUNGE && "elevated" in words -> 1.07
            else -> 1.0
        }
    }

    /** The share of [exerciseName] done with [modifiers], treated as a bodyweight movement; always within [MIN_SHARE]..[MAX_SHARE]. */
    fun of(exerciseName: String, modifiers: String = "", group: MuscleGroup? = null): Double =
        (derive(exerciseName, group).share * modifierFactor(exerciseName, modifiers)).coerceIn(MIN_SHARE, MAX_SHARE)

    /**
     * The share for a set of [exercise]: 0 for an external-load lift (its marked weight is the whole load) and for an
     * assisted set (see the class comment), else [of].
     */
    fun of(exercise: Exercise, modifiers: String = ""): Double {
        if (!addsToBody(exercise)) return 0.0
        if ("assisted" in modifiers.lowercase()) return 0.0
        return of(exercise.name, modifiers, exercise.muscleGroup)
    }

    /**
     * The estimated one-rep max of a set as an ADDED-load equivalent: Epley on the total load, minus the body part of
     * it. With no body ([share] 0 or [bodyweightKg] unknown) this is plain Epley on [addedKg], the old figure.
     */
    fun e1rmAddedKg(addedKg: Double, reps: Int, share: Double, bodyweightKg: Double?): Double {
        val body = if (share > 0.0 && bodyweightKg != null && bodyweightKg > 0.0) share * bodyweightKg else 0.0
        return ProgramRules.epley(body + addedKg, reps) - body
    }

    /** True when [e1rmAddedKg] had a body to work with, so a figure on a bodyweight lift can be trusted. */
    fun knowsBody(share: Double, bodyweightKg: Double?): Boolean = share <= 0.0 || (bodyweightKg != null && bodyweightKg > 0.0)
}
