package com.ironvellum.app.domain

/**
 * Energy expenditure estimates from data the app already holds.
 *
 * Sources:
 * - Resting: Katch-McArdle (1975), BMR = 370 + 21.6 x leanMassKg. Chosen because it
 *   needs NO age or sex, which the app does not store and we will not invent.
 * - Activity: MET equation, kcal = MET x 3.5 x kg / 200 x minutes
 *   (Ainsworth et al., Compendium of Physical Activities, 2011 update).
 * - Stride: strideM = 0.415 x heightCm / 100 (fitted from gait literature, e.g.
 *   School of Sport/Exercise gait analyses reporting stride ~= 0.41-0.43 x height).
 *
 * Everything here is an ESTIMATE, never a measurement; [EnergyEstimate] says so.
 */
/** Why an estimate is coarse, so the UI can say so instead of implying precision. */
enum class EnergyConfidence { MEASURED, ESTIMATED, COARSE }

data class EnergyEstimate(
    val kcal: Int,
    val confidence: EnergyConfidence,
    /** Human-readable basis, e.g. "MET 8.3 x 72.5 kg x 31 min" — shown on tap. */
    val basis: String,
    /** Inputs that were missing, e.g. "body fat" — shown so the user can improve it. */
    val missing: List<String> = emptyList(),
)

object Energy {
    /**
     * MET values anchored to the Compendium of Physical Activities (2011) and the
     * product decision anchors (running 8 km/h 8.3, 12 km/h 11.8, cycling moderate
     * 7.5, freestyle swim 7.0, skipping 11.0, bouldering 7.5, football 7.0,
     * basketball 6.5, tennis 7.3, boxing sparring 7.8, BJJ/wrestling 10.3, yoga 2.5,
     * stretching 2.3, walking 3.5, hiking 6.0, rowing 7.0, resistance vigorous 6.0 /
     * moderate 3.5). Matched by lowercase substring on the exercise name so
     * "5km Run" and "run" both hit; unknown names fall back to a category MET.
     */
    private val nameMets: List<Pair<String, Double>> = listOf(
        "run" to 8.3,
        "sprint" to 11.8,
        "jog" to 7.0,
        "cycle" to 7.5,
        "bike" to 7.5,
        "cycl" to 7.5,
        "swim" to 7.0,
        "skip" to 11.0,
        "rope" to 11.0,
        "boulder" to 7.5,
        "climb" to 7.5,
        "football" to 7.0,
        "soccer" to 7.0,
        "basketball" to 6.5,
        "tennis" to 7.3,
        "badminton" to 5.5,
        "rugby" to 7.3,
        "boxing" to 7.8,
        "bjj" to 10.3,
        "wrestl" to 10.3,
        "judo" to 10.3,
        "martial" to 10.3,
        "yoga" to 2.5,
        "stretch" to 2.3,
        "pilates" to 3.0,
        "walk" to 3.5,
        "hike" to 6.0,
        "row" to 7.0,
        "jump" to 11.0,
        "hiit" to 8.0,
        "cardio" to 8.0,
    )

    /** Category fallbacks (METs) when the name is unknown: Sport/Cardio/Water/Climbing/Mobility. */
    private val categoryMets: Map<String, Double> = mapOf(
        "sport" to 7.0,
        "cardio" to 8.0,
        "water" to 7.0,
        "climbing" to 7.5,
        "mobility" to 2.5,
    )

    /** MET for a plain name+category lookup, no speed banding. */
    private fun flatMet(exerciseName: String, category: String): Double {
        val n = exerciseName.lowercase()
        nameMets.firstOrNull { (key, met) -> n.contains(key) }?.let { return it.second }
        return categoryMets[category.lowercase()] ?: 3.5 // unknown name AND category: moderate resistance training, the safest sane default
    }

    /**
     * MET for an exercise, speed-banded for distance work: a 12 km/h run is not a
     * 7 km/h jog, so when a set carries both distance and duration we derive km/h
     * and pick a band instead of using the flat value. Missing duration (or
     * distance) falls back to the flat MET.
     */
    fun metFor(exerciseName: String, category: String, metric: ExerciseMetric, distanceM: Double?, durationSec: Int?): Double {
        val flat = flatMet(exerciseName, category)
        if (metric != ExerciseMetric.DISTANCE_TIME) return flat
        val dist = distanceM ?: return flat
        val dur = durationSec ?: return flat
        if (dur <= 0 || dist <= 0.0) return flat
        val kmh = (dist / 1000.0) / (dur / 3600.0)
        val n = exerciseName.lowercase()
        return when {
            n.contains("run") || n.contains("jog") || n.contains("sprint") -> when {
                kmh >= 11.0 -> 11.8 // Compendium 12.1 km/h
                kmh >= 7.5 -> 8.3 // Compendium ~8 km/h
                else -> 6.0 // slow jog / walk-run
            }
            n.contains("cycl") || n.contains("bike") -> when {
                kmh >= 24.0 -> 10.0 // vigorous 20-25 km/h+
                kmh >= 16.0 -> 7.5 // moderate
                else -> 5.8 // leisure
            }
            n.contains("swim") -> when {
                kmh >= 3.0 -> 10.0 // vigorous freestyle
                else -> 7.0 // moderate freestyle
            }
            else -> flat
        }
    }

    fun leanMassKg(weightKg: Double, bodyFatPct: Double?): Double? {
        bodyFatPct ?: return null
        return weightKg * (1.0 - bodyFatPct / 100.0)
    }

    /** Katch-McArdle resting burn per day; null when body fat is unknown. */
    fun restingKcalPerDay(weightKg: Double?, bodyFatPct: Double?): EnergyEstimate? {
        if (bodyFatPct == null || weightKg == null) return null
        val lean = leanMassKg(weightKg, bodyFatPct)!!
        val bmr = 370.0 + 21.6 * lean
        return EnergyEstimate(
            kcal = bmr.toInt(),
            confidence = EnergyConfidence.ESTIMATED,
            basis = "Katch-McArdle: 370 + 21.6 x ${"%.1f".format(lean)} kg lean (${weightKg} kg, ${bodyFatPct}% body fat)",
        )
    }

    private fun metKcal(met: Double, kg: Double, minutes: Double): Int =
        (met * 3.5 * kg / 200.0 * minutes).toInt()

    /**
     * One activity set (real duration or distance+duration). Returns null rather
     * than 0 when body weight is unknown (0 kcal is a lie; null lets the UI say
     * "log your weight") and for REPS sets, which are estimated per-session by the
     * coarse set-count model instead — a single set has no meaningful duration of
     * its own.
     */
    fun setKcal(
        metric: ExerciseMetric,
        exerciseName: String,
        category: String,
        durationSec: Int?,
        distanceM: Double?,
        reps: Int,
        weightKg: Double?,
        bodyKg: Double?,
    ): EnergyEstimate? {
        if (bodyKg == null || bodyKg <= 0.0) return null
        if (metric == ExerciseMetric.REPS || metric == ExerciseMetric.ATTEMPTS_GRADE) return null
        val dur = durationSec ?: return null
        if (dur <= 0) return null
        val met = metFor(exerciseName, category, metric, distanceM, dur)
        val minutes = dur / 60.0
        return EnergyEstimate(
            kcal = metKcal(met, bodyKg, minutes),
            confidence = EnergyConfidence.ESTIMATED,
            basis = "MET $met x $bodyKg kg x ${"%.0f".format(minutes)} min",
        )
    }

    /** Per-set work+rest allowance (minutes) for lifting/REPS sets: ~45 s working plus ~45 s rest. */
    private const val MINUTES_PER_SET = 1.5

    /**
     * Whole session: activity sets by MET x real minutes; lifting/REPS sets by the
     * coarse set-count model ([MINUTES_PER_SET] per set), unless the session's real
     * elapsed time is known, in which case the remaining time after timed sets goes
     * to the lifting work. Confidence is COARSE whenever any part relied on the
     * set-count model, ESTIMATED otherwise.
     */
    fun sessionKcal(
        sets: List<SessionSet>,
        exercises: Map<Long, Exercise>,
        bodyKg: Double?,
        sessionMinutes: Int?,
    ): EnergyEstimate? {
        if (bodyKg == null || bodyKg <= 0.0) return null
        if (sets.isEmpty()) return null
        var timedKcal = 0
        var timedMinutes = 0.0
        val untimedSets = mutableListOf<SessionSet>()
        for (set in sets) {
            val ex = exercises[set.exerciseId]
            val name = set.exerciseName.ifEmpty { ex?.name ?: "" }
            val category = ex?.category ?: ""
            val metric = ex?.metric ?: (if (set.durationSec != null || set.distanceM != null) ExerciseMetric.DISTANCE_TIME else ExerciseMetric.REPS)
            if (metric == ExerciseMetric.REPS || metric == ExerciseMetric.ATTEMPTS_GRADE || set.durationSec == null || set.durationSec <= 0) {
                untimedSets += set
            } else {
                val met = metFor(name, category, metric, set.distanceM, set.durationSec)
                val minutes = set.durationSec / 60.0
                timedKcal += metKcal(met, bodyKg, minutes)
                timedMinutes += minutes
            }
        }
        var coarseKcal = 0
        var coarse = false
        var basis = StringBuilder()
        if (untimedSets.isNotEmpty()) {
            // Prefer the session's real elapsed time over the synthetic per-set model.
            val coarseMinutes: Double = sessionMinutes?.let { (it - timedMinutes).coerceAtLeast(0.0) }
                ?: (untimedSets.size * MINUTES_PER_SET)
            val met = if (sessionMinutes != null) 6.0 else 3.5
            // With real elapsed time the whole lifting block is one vigourous-resistance block;
            // without it we can only assume moderate resistance per set.
            coarseKcal = metKcal(met, bodyKg, coarseMinutes)
            coarse = true
            basis = StringBuilder("lift ${untimedSets.size} sets x MET $met x $bodyKg kg x ${"%.0f".format(coarseMinutes)} min")
        }
        val total = timedKcal + coarseKcal
        if (total <= 0) return null
        return EnergyEstimate(
            kcal = total,
            confidence = if (coarse) EnergyConfidence.COARSE else EnergyConfidence.ESTIMATED,
            basis = listOfNotNull(
                basis.toString().ifEmpty { null },
                "timed sets MET x $bodyKg kg x ${"%.0f".format(timedMinutes)} min".takeIf { timedMinutes > 0.0 },
            ).joinToString("; "),
        )
    }

    /**
     * Steps for a day. Health Connect's measured distance wins; otherwise stride is
     * estimated from height (strideM = 0.415 x heightCm / 100). Walking uses the
     * MET equation at a walking MET (3.5, ~5 km/h). Zero steps yields zero, not null.
     */
    fun stepsKcal(steps: Int, distanceKm: Double?, bodyKg: Double?, heightCm: Double?): EnergyEstimate? {
        if (steps <= 0) {
            return EnergyEstimate(0, EnergyConfidence.ESTIMATED, "no steps recorded")
        }
        if (bodyKg == null || bodyKg <= 0.0) return null
        // Measured distance wins; otherwise the walk is reconstructed from stride.
        // Nothing is reported as "missing" here: the old code named `height` even
        // when height was present and used, so the UI told the lifter to log
        // something they had already logged. What is actually absent in that
        // branch is Health Connect's measured distance, which nobody can log by
        // hand — so the basis string says the distance was derived instead.
        var strideDerived = false
        val distanceKm = distanceKm ?: run {
            strideDerived = true
            val height = heightCm ?: return null
            steps * (0.415 * height / 100.0) / 1000.0
        }
        // Walking at ~5 km/h; duration follows from the distance walked.
        val minutes = distanceKm / 5.0 * 60.0
        return EnergyEstimate(
            kcal = metKcal(3.5, bodyKg, minutes),
            confidence = EnergyConfidence.ESTIMATED,
            basis = "MET 3.5 x $bodyKg kg x ${"%.0f".format(minutes)} min (${"%.2f".format(distanceKm)} km walked" +
                if (strideDerived) ", stride from height)" else ", measured)",
        )
    }

    /**
     * Day total that respects the no-double-count rule: when a measured active kcal
     * value exists it WINS — estimates are ignored, not summed — and the estimate
     * basis says so.
     */
    fun dayKcal(measuredActiveKcal: Int?, stepsEstimate: EnergyEstimate?, sessionEstimates: List<EnergyEstimate>): EnergyEstimate? {
        if (measuredActiveKcal != null) {
            return EnergyEstimate(
                kcal = measuredActiveKcal,
                confidence = EnergyConfidence.MEASURED,
                basis = "measured active kcal (Health Connect); estimates not added",
            )
        }
        val all = listOfNotNull(stepsEstimate) + sessionEstimates
        if (all.isEmpty()) return null
        val kcal = all.sumOf { it.kcal }
        val conf = if (all.any { it.confidence == EnergyConfidence.COARSE }) EnergyConfidence.COARSE else EnergyConfidence.ESTIMATED
        return EnergyEstimate(
            kcal = kcal,
            confidence = conf,
            basis = all.joinToString("; ") { it.basis },
            missing = all.flatMap { it.missing }.distinct(),
        )
    }
}
