package com.ironvellum.app.domain

/** The best reps one set reached carrying exactly [tokens] (modifiers and name words) on [name], a normalised movement. */
data class VariationSetBest(val name: String, val tokens: Set<String>, val reps: Int)

/**
 * What the variation deeds read out of the logged history, computed once by [Titles.ledgerOf]: the variation
 * rules of [Variation] (what is easier, harder or standard) applied to the done sets in the order they happened.
 *
 * Only done sets of a counted strength movement (reps or a hold) are read, so a warm-up never counts, as for
 * every other deed.
 */
object VariationDeeds {

    data class Facts(
        /**
         * Movements (normalised) moved off the easier version: a standard set was logged after an easier (assisted,
         * incline) set of the same movement, or, for a movement whose easier version was never logged and exists, a
         * standard-or-harder set was logged at all.
         */
        val movedOff: Set<String> = emptySet(),
        /** When each of [movedOff] was first moved off: the start of the session of the set that did it. */
        val movedOffAtMs: Map<String, Long> = emptyMap(),
        /**
         * A standard set with more reps than the best assisted set of the same movement, or, for a movement never
         * logged assisted where assisted exists, a standard-or-harder set at all.
         */
        val beatAssisted: Boolean = false,
        /** When [beatAssisted] was first true: the start of the session of the earliest set that did it. */
        val beatAssistedAtMs: Long? = null,
        /** Best reps per movement and per set of splitting modifiers it carried; sets carrying none are not listed. */
        val setBests: List<VariationSetBest> = emptyList(),
        /** A deficit variation's best estimated 1RM beats the standard lift's. */
        val deficitBeatsStandard: Boolean = false,
        /** How many different variations ([Variation.liftKey] with a variation) were ever logged. */
        val distinctVariations: Int = 0,
        /** Per movement (normalised), the single-modifier variations logged: "push-up" to {"archer", "deficit"}. */
        val facesLogged: Map<String, Set<String>> = emptyMap(),
        /** The most harder modifiers ([Variation.HARDER]) any one set carried. */
        val mostHarder: Int = 0,
    )

    /**
     * The splitting modifiers a set carries: the ones picked for it plus the ones its movement's own name says
     * (an "Archer Push-up" is an archer set whatever is ticked, and the picker never offers the word twice).
     */
    fun carried(exerciseName: String, modifiers: String): Set<String> {
        val name = exerciseName.lowercase().replace(" arm", "-arm")
        val fromName = Variation.TOKENS.filterTo(HashSet()) { it in name }
        return fromName + Variation.key(modifiers).split(",").filter { it.isNotEmpty() }
    }

    fun facts(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        exercises: Map<Long, Exercise>,
        bodyweightAt: ((Long) -> Double)?,
    ): Facts {
        val easierSeen = HashSet<String>()
        val movedOff = LinkedHashMap<String, Long>()
        val assistedReps = HashMap<String, Int>()
        val standardReps = HashMap<String, Int>()
        // Sets that are standard-or-harder, first and every plain one, for the movements whose easier version exists.
        val firstUpwards = HashMap<String, Long>()
        val firstUpwardsEasing = HashMap<String, Long>()
        val plainSets = HashMap<String, MutableList<Pair<Long, Int>>>()
        val bests = LinkedHashMap<Pair<String, Set<String>>, Int>()
        val variations = HashSet<String>()
        val faces = HashMap<String, MutableSet<String>>()
        val e1rmByLift = HashMap<String, Double>()
        var mostHarder = 0

        for ((session, sets) in history.sortedBy { it.first.startedAtMs }) {
            val bodyweight = bodyweightAt?.invoke(session.startedAtMs)?.takeIf { it > 0.0 }
            for ((key, best) in LiftRecords.sessionBests(sets, exercises, bodyweight)) {
                if (best.e1rmKg > (e1rmByLift[key] ?: 0.0)) e1rmByLift[key] = best.e1rmKg
            }
            val ordered = sets.filter { it.done }.sortedWith(compareBy({ it.exercisePosition }, { it.setIndex }))
            for (set in ordered) {
                val exercise = exercises[set.exerciseId] ?: continue
                if (!exercise.metric.isStrength) continue
                if (set.reps <= 0 && (set.durationSec ?: 0) <= 0) continue
                val name = Titles.normaliseName(exercise.name)
                val key = Variation.key(set.modifiers)
                if (key.isNotEmpty()) {
                    variations += Variation.liftKey(exercise.name, set.modifiers)
                    if (',' !in key) faces.getOrPut(name) { HashSet() } += key
                }
                val carried = carried(exercise.name, set.modifiers)
                val upwards = !Variation.eases(set.modifiers) && carried.none { it in EASIER }
                if (upwards) {
                    // Standard or harder (weighted and the harder modifiers too), on a movement with an easier version.
                    val applicable = applicableModifiers(exercise)
                    if ("assisted" in applicable) firstUpwards.putIfAbsent(name, session.startedAtMs)
                    if ("assisted" in applicable || "incline" in applicable) firstUpwardsEasing.putIfAbsent(name, session.startedAtMs)
                }
                if (carried.isNotEmpty()) {
                    bests.merge(name to carried, set.reps, { a, b -> maxOf(a, b) })
                    mostHarder = maxOf(mostHarder, carried.count { it in Variation.HARDER })
                }
                if (Variation.eases(set.modifiers)) {
                    easierSeen += name
                } else if (key.isEmpty() && name in easierSeen) {
                    movedOff.putIfAbsent(name, session.startedAtMs)
                }
                if (key.isEmpty()) {
                    standardReps.merge(name, set.reps, { a, b -> maxOf(a, b) })
                    plainSets.getOrPut(name) { ArrayList() } += session.startedAtMs to set.reps
                }
                if ("assisted" in key.split(",")) assistedReps.merge(name, set.reps, { a, b -> maxOf(a, b) })
            }
        }

        // An easier version never logged is moved past: the first standard-or-harder set of the movement says so.
        firstUpwardsEasing.forEach { (name, at) -> if (name !in easierSeen) movedOff.putIfAbsent(name, at) }
        // Beating the assisted best: the first standard set above it, or, with no assisted set ever, the first set up.
        val beatAt = HashMap<String, Long>()
        assistedReps.forEach { (name, reps) ->
            if (reps > 0) plainSets[name]?.firstOrNull { it.second > reps }?.let { beatAt[name] = it.first }
        }
        firstUpwards.forEach { (name, at) -> if (name !in assistedReps) beatAt.putIfAbsent(name, at) }

        return Facts(
            movedOff = movedOff.keys,
            movedOffAtMs = movedOff,
            beatAssisted = beatAt.isNotEmpty(),
            beatAssistedAtMs = beatAt.values.minOrNull(),
            setBests = bests.map { (k, reps) -> VariationSetBest(k.first, k.second, reps) },
            deficitBeatsStandard = deficitBeatsStandard(e1rmByLift),
            distinctVariations = variations.size,
            facesLogged = faces,
            mostHarder = mostHarder,
        )
    }

    /**
     * A deficit variation (deficit and nothing easier) whose best e1RM tops the same movement's standard best, which
     * must exist: the standard lift has to have been loaded and lifted before it can be beaten. [e1rmByLift] is
     * [LiftRecords.sessionBests], by [Variation.liftKey]: the one rule every record reads.
     */
    private fun deficitBeatsStandard(e1rmByLift: Map<String, Double>): Boolean =
        e1rmByLift.any { (liftKey, e1rm) ->
            val at = liftKey.indexOf(Variation.SEPARATOR)
            if (at < 0) return@any false
            val tokens = liftKey.substring(at + Variation.SEPARATOR.length).split(",")
            val standard = e1rmByLift[liftKey.substring(0, at)] ?: return@any false
            "deficit" in tokens && tokens.none { it in EASIER } && standard > 0.0 && e1rm > standard
        }

    private val EASIER: Set<String> = Variation.TOKENS - Variation.HARDER
}
