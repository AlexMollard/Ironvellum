package com.ironvellum.app.domain

/**
 * The catalogue exercises that are a base exercise plus a modifier or load, kept as exercises of their own
 * (own art, own form guide): "Archer Push-up" is Push-up with archer, "Weighted Chin-up" is Chin-up with load.
 *
 * One table, [ALL], keyed by the dedicated exercise's name. Everything that needs to know a base + modifier
 * is a twin reads it: a skill's evidence ([twinned]), the steering prompt ([steer]) and the one-time convert
 * of past sets ([resolve]). Names are matched case-insensitively against whatever the device's exercises table
 * holds, so a legacy row the seed no longer lists is covered by the same rule, as long as its name is here.
 *
 * Not twins, on purpose:
 * - Assisted Pull-up / Chin-up / Dip: the machine's marked kilos subtract load, a band modifier's do not, so the
 *   sets are not the same measurement ([MovementDifficulty.loadFactor]).
 * - Paused Bench Press, Pause Squat: rungs of a gym progression with their own tier and bar, and "paused" does
 *   not split a lift ([Variation.TOKENS]).
 * - Incline/Decline Bench Press and the like, Elevated Pike Push-up, Elevated Pistol Squat: the base does not
 *   offer the modifier ([applicableModifiers]), so nobody can reach them by adding it.
 * - Deficit Advanced Shrimp Squat (the base does not offer deficit) and Weighted Sissy Squat (the base is filed as
 *   an isolation, the twin as a squat, so fatigue would change).
 * - Banded X and Weighted Skipping / Weighted Ball Side Bend: a band scores nothing, an activity takes no
 *   modifier, and Ball Side Bend has no unweighted base.
 */
object ExerciseTwins {

    /**
     * [dedicated] is [bases] (any one of them) done with [absorbs]: a list of alternatives, each a set of modifier
     * tokens that, all present, make the base this exercise. Decline Push-up has two: decline, and the synonym
     * elevated (feet up).
     */
    data class Twin(val dedicated: String, val bases: List<String>, val absorbs: List<Set<String>>)

    private fun twin(dedicated: String, base: String, vararg absorbs: String) =
        Twin(dedicated, listOf(base), absorbs.map { setOf(it) })

    /** Declaration order is the last tie-break of [resolve]. */
    val ALL: Map<String, Twin> = listOf(
        twin("Archer Pull-up", "Pull-up", "archer"),
        twin("Archer Push-up", "Push-up", "archer"),
        twin("One-Arm Pull-up", "Pull-up", "one-arm"),
        twin("One-Arm Push-up", "Push-up", "one-arm"),
        twin("Incline Push-up", "Push-up", "incline"),
        twin("Decline Push-up", "Push-up", "decline", "elevated"),
        twin("Deficit Reverse Lunge", "Reverse Lunge", "deficit"),
        twin("Weighted Pull-up", "Pull-up", WEIGHTED_MODIFIER),
        twin("Weighted Chin-up", "Chin-up", WEIGHTED_MODIFIER),
        Twin("Weighted Dip", listOf("Dip", "Parallel Bar Dip"), listOf(setOf(WEIGHTED_MODIFIER))),
        twin("Weighted Push-up", "Push-up", WEIGHTED_MODIFIER),
        twin("Weighted Plank", "Plank", WEIGHTED_MODIFIER),
        twin("Weighted Crunch", "Crunch", WEIGHTED_MODIFIER),
        twin("Weighted Russian Twist", "Russian Twist", WEIGHTED_MODIFIER),
    ).associateBy { it.dedicated.lowercase() }

    private val ORDER: Map<String, Int> = ALL.values.mapIndexed { at, twin -> twin.dedicated to at }.toMap()

    private fun key(name: String) = name.trim().lowercase()

    /** The twin whose dedicated exercise is [name], or null. */
    fun forDedicated(name: String): Twin? = ALL[key(name)]

    /** A base done with some modifiers that is, or can be switched to, a dedicated exercise. */
    data class Resolution(
        val twin: Twin,
        /** The tokens the dedicated exercise carries in its name, lower case. */
        val absorbed: Set<String>,
        /** What is left to record on the dedicated exercise: the other tokens, in their order, comma-joined. */
        val modifiers: String,
    ) {
        /** What a "Keep as is" is remembered under: the base and what it was given. */
        fun keepKey(base: String): String = ExerciseTwins.keepKey(base, absorbed)
    }

    fun keepKey(base: String, absorbed: Set<String>): String = key(base) + "|" + absorbed.sorted().joinToString(",")

    /**
     * Whether a [weightKg] on a bodyweight base counts as the "weighted" modifier although it is not tagged: the
     * load is the modifier ([modifiersFollowingLoad]). Never on an assisted set, whose kilos are the assistance.
     */
    fun effectiveModifiers(modifiers: String, weightKg: Double?, bodyweightBase: Boolean): String {
        val tokens = modifierTokens(modifiers)
        val loaded = bodyweightBase && (weightKg ?: 0.0) > 0.0 &&
            tokens.none { it.equals(WEIGHTED_MODIFIER, true) || it.equals("assisted", true) }
        return if (loaded) (tokens + WEIGHTED_MODIFIER).joinToString(", ") else tokens.joinToString(", ")
    }

    /**
     * The dedicated exercise [baseName] with [modifiers] amounts to, or null. [exists] says which names the
     * catalogue holds (a twin missing from it is no twin here). A "weighted" only counts on a bodyweight base
     * ([bodyweightBase]); pass the modifiers through [effectiveModifiers] first to let an untagged load count.
     *
     * When several twins match: one that absorbs a shape-changing token (anything but weighted) beats one that
     * absorbs only weighted (Push-up weighted, incline is Incline Push-up with weighted); then the one that
     * absorbs the most tokens; then declaration order in [ALL].
     */
    fun resolve(
        baseName: String,
        modifiers: String,
        bodyweightBase: Boolean = true,
        exists: (String) -> Boolean = { true },
    ): Resolution? {
        val tokens = modifierTokens(modifiers).filter { bodyweightBase || !it.equals(WEIGHTED_MODIFIER, true) }
        if (tokens.isEmpty()) return null
        val have = tokens.map { it.lowercase() }.toSet()
        val base = key(baseName)
        val candidates = ALL.values.mapNotNull { twin ->
            if (twin.bases.none { key(it) == base } || !exists(twin.dedicated)) return@mapNotNull null
            val absorbed = twin.absorbs.filter { have.containsAll(it) }.flatten().toSet()
            if (absorbed.isEmpty()) null else twin to absorbed
        }
        val (twin, absorbed) = candidates.maxWithOrNull(
            compareBy<Pair<Twin, Set<String>>>(
                { (_, absorbed) -> absorbed.any { it != WEIGHTED_MODIFIER } },
                { (_, absorbed) -> absorbed.size },
            ).thenBy { (twin, _) -> -ORDER.getValue(twin.dedicated) },
        ) ?: return null
        val left = modifierTokens(modifiers).filterNot { it.lowercase() in absorbed }
        return Resolution(twin, absorbed, left.joinToString(", "))
    }

    /** The modifiers [modifiers] gained over [before], the ones a steering prompt looks at. */
    fun added(before: String, modifiers: String): Set<String> =
        modifierTokens(modifiers).map { it.lowercase() }.toSet() - modifierTokens(before).map { it.lowercase() }.toSet()

    /**
     * The prompt owed after the modifiers of [baseName] went from [before] to [modifiers]: a dedicated twin that
     * [modifiers] now make it and that a modifier just added is part of, unless the lifter kept it as is
     * ([kept], the [keepKey]s). Null when nothing was added, no twin matches, or it was kept.
     */
    fun steer(
        baseName: String,
        before: String,
        modifiers: String,
        bodyweightBase: Boolean,
        kept: Set<String>,
        exists: (String) -> Boolean,
    ): Resolution? {
        val gained = added(before, modifiers)
        if (gained.isEmpty()) return null
        val found = resolve(baseName, modifiers, bodyweightBase, exists) ?: return null
        if (found.absorbed.none { it in gained }) return null
        return found.takeUnless { it.keepKey(baseName) in kept }
    }

    /**
     * [history] with every set that is a base with the modifiers or load of a twin renamed to the twin, its
     * absorbed tokens dropped: what a skill's evidence reads beside the sets logged under the twin's own name.
     * A set that is no twin is left as it is.
     */
    fun twinned(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        isBodyweight: (String) -> Boolean,
        exists: (String) -> Boolean,
    ): List<Pair<WorkoutSession, List<SessionSet>>> = history.map { (session, sets) ->
        session to sets.map { set ->
            val bodyweight = isBodyweight(set.exerciseName)
            val found = resolve(set.exerciseName, effectiveModifiers(set.modifiers, set.weightKg, bodyweight), bodyweight, exists)
            if (found == null) set else set.copy(exerciseName = found.twin.dedicated, modifiers = found.modifiers)
        }
    }
}
