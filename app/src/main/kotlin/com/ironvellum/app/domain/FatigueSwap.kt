package com.ironvellum.app.domain

/**
 * The stand-in Today offers for an exercise that leans on a tired muscle. It reads the generator's own ranking
 * pieces (the prescribable pool, [ProgramGenerator.similarity], [Easing.entryFor]) and adds the one rule fatigue
 * needs: every main muscle of the stand-in is below High. A catalogue exercise is always a dedicated one where a twin
 * exists ([ExerciseTwins]), so its art and form guide follow; the stand-in starts with no modifiers.
 */
object FatigueSwap {

    /** The lifts weighed for the "recent exercises win a tie" rule: the best this many by similarity. */
    private const val WINDOW = 3

    /**
     * The best stand-in for [lean], or null when none exists (nothing of its pattern is below High, none fits the gear,
     * or the lift is not one the generator knows). Candidates are the prescribable exercises of the same movement
     * pattern and metric that the routine does not already hold, ranked by how closely they load the same muscles. Of
     * the best [WINDOW] that qualify, a lift the lifter did lately ([recent], lower-case names) comes first, else the
     * closest. The sets stay as planned; the reps and load are the stand-in's own, never the old lift's.
     */
    fun choose(
        lean: PresetEntry,
        routine: List<PresetEntry>,
        report: FatigueReport,
        atMs: Long,
        catalogue: List<Exercise>,
        request: ProgramRequest,
        strength: StrengthProfile,
        recent: Set<String>,
    ): EntryAdjust.Swap? {
        val ranked = standIns(lean, routine, report, atMs, catalogue, request)
        val pick = ranked.firstOrNull { it.name.trim().lowercase() in recent } ?: ranked.firstOrNull() ?: return null
        val sets = Lighten.workingSets(lean)
        val planned = Easing.entryFor(
            PlannedEntry(lean.exerciseName, sets, lean.targetReps, lean.targetWeightKg, lean.modifiers), pick, request, strength,
        )
        return EntryAdjust.Swap(pick.id, pick.name, "", sets, planned.reps, planned.targetWeightKg)
    }

    /** The best [WINDOW] stand-ins for [lean], closest first: the ranking [choose] picks from. */
    internal fun standIns(
        lean: PresetEntry,
        routine: List<PresetEntry>,
        report: FatigueReport,
        atMs: Long,
        catalogue: List<Exercise>,
        request: ProgramRequest,
    ): List<Exercise> {
        val from = MuscleMap.profile(lean.exerciseName, lean.modifiers) ?: return emptyList()
        val original = catalogue.firstOrNull { it.name.equals(lean.exerciseName, ignoreCase = true) } ?: return emptyList()
        val taken = routine.map { it.exerciseName.trim().lowercase() }.toSet()
        return ProgramGenerator.prescribable(catalogue, request)
            .filter { it.metric == original.metric && it.name.trim().lowercase() !in taken }
            .mapNotNull { exercise ->
                val profile = MuscleMap.profile(exercise.name) ?: return@mapNotNull null
                if (profile.pattern != from.pattern) return@mapNotNull null
                val main = profile.muscles.filterValues { it >= FatigueTuning.TRAINS_SHARE }.keys
                if (main.any { FatigueAdvice.triggers(report.levelAt(it, atMs)) }) return@mapNotNull null
                exercise to ProgramGenerator.similarity(from, profile)
            }
            .sortedWith(compareByDescending<Pair<Exercise, Double>> { it.second }.thenBy { it.first.name })
            .take(WINDOW)
            .map { it.first }
    }
}
