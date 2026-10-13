package com.ironvellum.app.domain

/**
 * Where a movement is being suggested. [Pick] is a slot in a session (a
 * movement pattern, a muscle, or both), [Swap] the stretch-biased twins of an
 * entry, [StandIn] the compound movements closest to an isolation entry,
 * [ShortMuscle] a movement for a muscle the week leaves short, and
 * [ReplaceRemoved] what could take the place of an entry the lifter dropped.
 */
sealed class Slot {
    data class Pick(
        val pattern: MovementPattern? = null,
        val muscle: Muscle? = null,
        /** The session the movement would join; its movements are never offered again. */
        val session: PlannedPreset? = null,
        val exclude: Set<String> = emptySet(),
    ) : Slot()

    data class Swap(val entry: PlannedEntry) : Slot()
    data class StandIn(val entry: PlannedEntry) : Slot()
    data class ShortMuscle(val muscle: Muscle, val session: PlannedPreset? = null) : Slot()
    data class ReplaceRemoved(val entry: PlannedEntry) : Slot()
}

/**
 * The ranked-suggestion surface behind [ProgramGenerator.suggest]. The order
 * of every list is the generator's own ([ProgramGenerator.rankCandidates] and
 * friends); this file only cuts it to three and explains each option.
 */
internal object ProgramSuggestions {

    /** Options offered per suggestion. */
    const val OPTIONS = 3

    fun suggest(
        request: ProgramRequest,
        slot: Slot,
        week: List<PlannedPreset>,
        catalogue: List<Exercise>,
        strength: StrengthProfile,
    ): Ranked {
        val pool = ProgramGenerator.prescribable(catalogue, request)
        if (pool.isEmpty()) return Ranked(emptyList())
        val ctx = ProgramGenerator.Ctx(request, pool, strength)
        val weekly = ProgramRules.weeklyVolume(week)

        fun sessionOf(entry: PlannedEntry): PlannedPreset? =
            week.firstOrNull { preset -> preset.entries.any { it.exerciseName.equals(entry.exerciseName, ignoreCase = true) } }

        return when (slot) {
            is Slot.Pick -> {
                val draft = ProgramGenerator.draftOf(ctx, slot.session)
                rank(
                    ctx, ProgramGenerator.rankCandidates(ctx, draft, slot.pattern, slot.muscle, slot.exclude),
                    slot.muscle, null, weekly,
                )
            }

            is Slot.ShortMuscle -> {
                val draft = ProgramGenerator.draftOf(ctx, slot.session)
                val dayRole = ProgramGenerator.dayRoleOf(groupsOf(draft.entries, catalogue))
                val fitting = ProgramGenerator.rankCandidates(ctx, draft, null, slot.muscle)
                    .filter { ProgramGenerator.groupFitsSession(dayRole, it.muscleGroup) }
                rank(ctx, fitting, slot.muscle, null, weekly)
            }

            is Slot.Swap -> {
                val profile = MuscleMap.profile(slot.entry) ?: return Ranked(emptyList())
                val taken = sessionOf(slot.entry)?.entries?.map { it.exerciseName } ?: listOf(slot.entry.exerciseName)
                rank(
                    ctx, stretchTwins(ctx, slot.entry.exerciseName, profile, taken),
                    ProgramGenerator.dominantMuscle(profile), profile, weekly,
                )
            }

            is Slot.StandIn -> {
                val profile = MuscleMap.profile(slot.entry) ?: return Ranked(emptyList())
                val session = sessionOf(slot.entry)
                val taken = session?.entries?.map { it.exerciseName.trim().lowercase() }?.toSet()
                    ?: setOf(slot.entry.exerciseName.trim().lowercase())
                val dayGroups = session?.entries.orEmpty()
                    .filterNot { MovementDifficulty.isIsolation(it.exerciseName) }
                    .mapNotNull { e -> catalogue.firstOrNull { it.name.equals(e.exerciseName, ignoreCase = true) }?.muscleGroup }
                    .toSet()
                rank(
                    ctx, ProgramGenerator.compoundStandIns(ctx, profile, taken, dayGroups),
                    ProgramGenerator.dominantMuscle(profile), profile, weekly, standInFor = slot.entry.exerciseName,
                )
            }

            is Slot.ReplaceRemoved -> {
                val profile = MuscleMap.profile(slot.entry) ?: return Ranked(emptyList())
                val session = sessionOf(slot.entry)
                // The session as it stands without the removed movement.
                val without = session?.copy(
                    entries = session.entries.filterNot { it.exerciseName.equals(slot.entry.exerciseName, ignoreCase = true) },
                )
                val draft = ProgramGenerator.draftOf(ctx, without)
                val muscle = ProgramGenerator.dominantMuscle(profile)
                val exclude = setOf(slot.entry.exerciseName)
                val samePattern = ProgramGenerator.rankCandidates(ctx, draft, profile.pattern, muscle, exclude)
                // Same pattern first; the rest of the muscle's movements fill the remaining places.
                val rest = ProgramGenerator.rankCandidates(ctx, draft, null, muscle, exclude)
                rank(ctx, (samePattern + rest).distinctBy { it.name }, muscle, profile, weekly)
            }
        }
    }

    /**
     * The stretch-biased movements of the same pattern an entry could swap
     * for (Maeo 2021; Kassiano 2023), best first: compound, then the better
     * implement fit, the lower tier, and the name - the comparator the improve
     * pass has always used, so the first is the swap it makes. Never one in
     * [taken], and never one that trains the entry's main muscle less.
     */
    fun stretchTwins(
        ctx: ProgramGenerator.Ctx,
        name: String,
        profile: ExerciseProfile,
        taken: Collection<String>,
    ): List<Exercise> {
        val primary = ProgramGenerator.dominantMuscle(profile)
        return ctx.pool
            .filter { ProgramGenerator.profileOf(it).pattern == profile.pattern && ProgramGenerator.profileOf(it).stretchBias }
            .filter { it.name != name }
            .filter { candidate -> taken.none { it.equals(candidate.name, ignoreCase = true) } }
            .filter {
                primary == null || (ProgramGenerator.profileOf(it).muscles[primary] ?: 0.0) >=
                    (profile.muscles[primary] ?: 0.0)
            }
            .sortedWith(
                compareByDescending<Exercise> { if (ProgramGenerator.profileOf(it).compound) 1 else 0 }
                    .thenByDescending { -ProgramGenerator.fitScore(it, ctx.request.equipment) }
                    .thenByDescending { -MovementDifficulty.tier(it.name) }
                    .thenByDescending { it.name },
            )
    }

    /**
     * The first three of an already ordered [ordered] list as [Candidate]s,
     * best first, each with at most two reasons in the [Reason] order.
     * [muscle] is the one being filled (null for a pattern slot), [reference]
     * the movement being replaced or stood in for.
     */
    fun rank(
        ctx: ProgramGenerator.Ctx,
        ordered: List<Exercise>,
        muscle: Muscle?,
        reference: ExerciseProfile?,
        weekly: Map<Muscle, Double>,
        standInFor: String? = null,
    ): Ranked {
        val top = ordered.take(OPTIONS)
        val bestFit = top.minOfOrNull { ProgramGenerator.fitScore(it, ctx.request.equipment) }
        val worstFit = top.maxOfOrNull { ProgramGenerator.fitScore(it, ctx.request.equipment) }
        return Ranked(
            top.mapIndexed { index, exercise ->
                val profile = ProgramGenerator.profileOf(exercise)
                val tags = mutableListOf<ReasonTag>()
                if (muscle != null) {
                    if ((profile.muscles[muscle] ?: 0.0) >= DIRECT_SHARE) tags += ReasonTag(Reason.DIRECT_MUSCLE, muscle)
                    if ((weekly[muscle] ?: 0.0) < ProgramRules.COVERED_MIN) tags += ReasonTag(Reason.FILLS_SHORT_MUSCLE, muscle)
                }
                // Only a real difference in implement fit is worth saying.
                if (bestFit != worstFit && ProgramGenerator.fitScore(exercise, ctx.request.equipment) == bestFit) {
                    tags += ReasonTag(Reason.FITS_GEAR)
                }
                if (exercise.name.trim().lowercase() in ctx.strength.bestE1rmKg || (ctx.familiarity[exercise.name] ?: 0.0) > 0.0) {
                    tags += ReasonTag(Reason.YOU_LOG_THIS)
                }
                if (reference != null && ProgramGenerator.similarity(reference, profile) < LOW_OVERLAP_BELOW) {
                    tags += ReasonTag(Reason.LOW_OVERLAP)
                }
                if (ProgramGenerator.stretchesFor(exercise, muscle)) tags += ReasonTag(Reason.STRETCH_BIASED, muscle)
                if (exercise.name in ctx.demoted) tags += ReasonTag(Reason.OFTEN_SKIPPED)
                // A stand-in always says what it stands in for; the best other reason keeps it company.
                val standIn = listOfNotNull(standInFor?.let { ReasonTag(Reason.STAND_IN_FOR, other = it) })
                Candidate(
                    exercise = exercise,
                    tags = standIn + tags.sortedBy { it.reason.ordinal }.take(MAX_TAGS - standIn.size),
                    score = (ordered.size - index).toDouble(),
                    coverageDelta = coverageOf(ctx, exercise, profile),
                )
            },
        )
    }

    /** Fractional hard sets the movement would add at its starting dose, per muscle it trains. */
    private fun coverageOf(ctx: ProgramGenerator.Ctx, exercise: Exercise, profile: ExerciseProfile): Map<Muscle, Double> {
        val sets = ProgramRules.startingSets(ctx.volume, profile.compound, priority = false)
        return profile.muscles.filterValues { it > 0.0 }.mapValues { it.value * sets }
    }

    private fun groupsOf(entries: List<PlannedEntry>, catalogue: List<Exercise>): Set<MuscleGroup> =
        entries.mapNotNull { e -> catalogue.firstOrNull { it.name.equals(e.exerciseName, ignoreCase = true) }?.muscleGroup }.toSet()

    /** A movement trains a muscle directly from this share of its work. */
    private const val DIRECT_SHARE = 0.9

    /** Below this similarity to the movement it replaces, an option loads the muscles differently. */
    private const val LOW_OVERLAP_BELOW = 0.5

    private const val MAX_TAGS = 2
}
