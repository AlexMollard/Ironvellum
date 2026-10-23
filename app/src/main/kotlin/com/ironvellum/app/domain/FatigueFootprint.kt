package com.ironvellum.app.domain

/** How much of a footprint a muscle takes: Strong from 2 hard-set equivalents, Medium from 1, Light below. */
enum class FootprintBand { STRONG, MEDIUM, LIGHT }

/** What makes one lift leave more behind than another: it loads the muscle long, or it is a heavy whole-body lift. */
enum class FootprintTrait { LOADS_LONG, HEAVY_WHOLE_BODY }

data class FootprintMuscle(val muscle: Muscle, val dose: Double, val band: FootprintBand)

/** A lift of the same lead muscle without the trait, named by its catalogue key (lowercase). */
data class Comparison(val alternative: String, val trait: FootprintTrait)

/**
 * Where one movement's fatigue lands, derived and never hand-written: the dose formula applied to its profile for
 * [FatigueTuning.FOOTPRINT_SETS] hard sets at [FatigueTuning.FOOTPRINT_RIR] reps left, on a fresh lifter who has done it before.
 */
data class FatigueFootprint(
    /** Muscles taking at least [FatigueTuning.BAND_OMIT_BELOW], most first. */
    val muscles: List<FootprintMuscle>,
    val stretchHeavy: Boolean,
    val heavyWholeBody: Boolean,
    /** The muscle the movement is for: the first with a full share, else the one that takes most. */
    val lead: Muscle,
    /** The lead muscle's level right after those sets, alone and fresh. */
    val typicalLevel: FatigueLevel,
    /** Hours until the lead muscle is back below the light-by threshold; null when it never rises above it. */
    val lightInHours: Int?,
    val comparison: Comparison?,
)

object FatigueFootprints {

    private const val MS_PER_HOUR = 3_600_000.0

    /** Null when [exerciseName] has no muscle profile. [pool] holds the lowercase catalogue names a comparison may come from. */
    fun of(exerciseName: String, modifiers: String, pool: Collection<String> = MuscleMap.keys): FatigueFootprint? {
        val profile = MuscleMap.profile(exerciseName, modifiers) ?: return null
        val doses = perMuscle(exerciseName, modifiers)
        if (doses.isEmpty()) return null
        val muscles = doses.entries
            .filter { it.value >= FatigueTuning.BAND_OMIT_BELOW - 1e-9 }
            .sortedByDescending { it.value }
            .map { FootprintMuscle(it.key, it.value, band(it.value)) }
        val stretchHeavy = FatigueTraits.stretchedMuscles(exerciseName, modifiers, profile).isNotEmpty()
        val heavy = FatigueTraits.isHeavyWholeBody(exerciseName)
        val lead = leadOf(profile) ?: doses.maxBy { it.value }.key
        val leadDose = doses[lead] ?: 0.0
        return FatigueFootprint(
            muscles = muscles,
            stretchHeavy = stretchHeavy,
            heavyWholeBody = heavy,
            lead = lead,
            typicalLevel = FatigueLevel.of(leadDose),
            lightInHours = lightInHours(lead, leadDose),
            comparison = comparison(exerciseName, profile, leadOf(profile), stretchHeavy, heavy, pool),
        )
    }

    fun band(dose: Double): FootprintBand = when {
        dose >= FatigueTuning.BAND_STRONG - 1e-9 -> FootprintBand.STRONG
        dose >= FatigueTuning.BAND_MEDIUM - 1e-9 -> FootprintBand.MEDIUM
        else -> FootprintBand.LIGHT
    }

    private fun leadOf(profile: ExerciseProfile): Muscle? =
        profile.muscles.entries.firstOrNull { it.value >= 1.0 - 1e-9 }?.key

    /** The footprint's sets, summed per muscle; the one place the footprint reads [FatigueDose]. */
    private fun perMuscle(exerciseName: String, modifiers: String): Map<Muscle, Double> {
        val set = LoggedSet(
            exerciseName = exerciseName, modifiers = modifiers, weightKg = 100.0, reps = 5,
            rpe = Effort.rpeOfRir(FatigueTuning.FOOTPRINT_RIR), kind = SetKind.WORKING, done = true, atMs = 0L, bodyweight = true,
        )
        val out = LinkedHashMap<Muscle, Double>()
        for (part in FatigueDose.perSet(set, novelty = 1.0)) out[part.muscle] = (out[part.muscle] ?: 0.0) + part.dose * FatigueTuning.FOOTPRINT_SETS
        return out
    }

    /** The residual rule itself: one contribution at time 0, solved for the light-by threshold. */
    private fun lightInHours(lead: Muscle, dose: Double): Int? {
        val contribution = FatigueContribution(
            muscle = lead, sessionId = 0L, sessionAtMs = 0L, exerciseName = "", modifiers = "", liftKey = "",
            dose = dose, qualifyingDose = 0.0, stretched = false, novel = false, halfLifeHours = lead.recoveryClass().halfLifeHours,
        )
        val report = FatigueReport(nowMs = 0L, newLifter = false, contributions = mapOf(lead to listOf(contribution)))
        val ms = report.firstBelowMs(lead, FatigueTuning.LIGHT_BELOW) ?: return null
        return Math.round(ms / MS_PER_HOUR).toInt()
    }

    private fun comparison(
        exerciseName: String, profile: ExerciseProfile, lead: Muscle?,
        stretchHeavy: Boolean, heavy: Boolean, pool: Collection<String>,
    ): Comparison? {
        if (lead == null) return null
        val trait = when {
            stretchHeavy -> FootprintTrait.LOADS_LONG
            heavy -> FootprintTrait.HEAVY_WHOLE_BODY
            else -> return null
        }
        val self = exerciseName.trim().lowercase()
        val best = pool.asSequence()
            .map { it.trim().lowercase() }
            .distinct()
            .filter { it != self }
            .mapNotNull { key -> MuscleMap.profile(key)?.let { key to it } }
            // A fair comparison lacks every trait the lift has: a deadlift is no foil for a Romanian deadlift's long load.
            .filter { (key, other) -> leadOf(other) == lead && FootprintTrait.entries.none { it.of(exerciseName, profile) && it.of(key, other) } }
            .sortedWith(
                compareByDescending<Pair<String, ExerciseProfile>> { it.second.muscles[lead] ?: 0.0 }
                    .thenByDescending { it.second.pattern == profile.pattern }
                    // Then the simpler movement: the one that credits fewest muscles is the cleanest foil.
                    .thenBy { it.second.muscles.count { m -> m.value > 0.0 } }
                    .thenBy { it.first },
            )
            .firstOrNull() ?: return null
        return Comparison(best.first, trait)
    }

    private fun FootprintTrait.of(key: String, profile: ExerciseProfile): Boolean = when (this) {
        FootprintTrait.LOADS_LONG -> FatigueTraits.stretchedMuscles(key, "", profile).isNotEmpty()
        FootprintTrait.HEAVY_WHOLE_BODY -> FatigueTraits.isHeavyWholeBody(key)
    }
}
