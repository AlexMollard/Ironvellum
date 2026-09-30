package com.ironvellum.app.data

import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.domain.ActivityScore
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.StrengthIndex
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.isStrength

/**
 * The one place a trial's stored sets become XP and a strength score.
 * Sealing, the CSV merge and a sealed-trial edit all price sets here, so the
 * three can never disagree about what the same work is worth. The quest bonus
 * is deliberately NOT included: it rewards the day, not the sets.
 *
 * Built once per catalogue read; an unknown stored metric reads as REPS and an
 * unknown muscle group as CORE rather than failing the caller.
 */
internal class SessionScoring(catalogue: List<ExerciseEntity>) {
    private val metrics = catalogue.associate { row ->
        row.id to runCatching { ExerciseMetric.valueOf(row.metric) }.getOrDefault(ExerciseMetric.REPS)
    }
    private val names = catalogue.associate { it.id to it.name }
    private val categories = catalogue.associate { it.id to it.category }

    // The sex normalisation differs upper vs lower body, so the score needs
    // the catalogue row's own group.
    private val groups = catalogue.associate { row ->
        row.id to (runCatching { MuscleGroup.valueOf(row.muscleGroup) }.getOrNull() ?: MuscleGroup.CORE)
    }

    fun metricOf(exerciseId: Long): ExerciseMetric = metrics[exerciseId] ?: ExerciseMetric.REPS

    /** Seconds when the set is a hold, null when it is counted in reps. */
    private fun holdSecondsOf(set: SetLogEntity): Int? =
        if (metricOf(set.exerciseId) == ExerciseMetric.HOLD) set.durationSec else null

    /**
     * XP for the TICKED sets among [sets]. Strength work (reps and static
     * holds) earns difficulty-weighted XP; activities earn from ActivityScore.
     */
    fun xp(sets: List<SetLogEntity>, bodyweightKg: Double?): Int {
        val doneSets = sets.filter { it.done }
        val liftingSets = doneSets.filter { metricOf(it.exerciseId).isStrength }
        val activitySets = doneSets.filterNot { metricOf(it.exerciseId).isStrength }
        val liftingXp = Xp.award(
            liftingSets.map { set ->
                Xp.SetEffort(
                    exerciseName = names[set.exerciseId] ?: "",
                    reps = set.reps,
                    holdSeconds = holdSecondsOf(set),
                    weightKg = set.weightKg,
                    modifiers = set.modifiers,
                    metric = metricOf(set.exerciseId),
                )
            },
            bodyweightKg,
            doneSetCount = doneSets.size,
        )
        val activityXp = activitySets.sumOf { set ->
            val metric = metricOf(set.exerciseId)
            val per = ActivityScore.xp(
                exerciseName = names[set.exerciseId] ?: "",
                category = categories[set.exerciseId] ?: "",
                metric = metric,
                durationSec = set.durationSec,
                distanceM = set.distanceM,
                addedKg = set.weightKg,
                bodyweightKg = bodyweightKg ?: 0.0,
            )
            if (metric == ExerciseMetric.ATTEMPTS_GRADE) per * set.reps else per
        }
        return liftingXp + activityXp
    }

    /**
     * Body-scaled strength score of the ticked strength sets; 0 when there is
     * no bodyweight to scale by or no strength work at all. Activities
     * contribute nothing.
     */
    fun strength(sets: List<SetLogEntity>, bodyweightKg: Double?, sex: Sex): Int =
        StrengthIndex.sessionScore(
            sets.filter { it.done && metricOf(it.exerciseId).isStrength }.map { set ->
                StrengthIndex.Effort(
                    exerciseName = names[set.exerciseId] ?: "",
                    reps = set.reps,
                    holdSeconds = holdSecondsOf(set),
                    addedKg = set.weightKg,
                    muscleGroup = groups[set.exerciseId] ?: MuscleGroup.CORE,
                )
            },
            bodyweightKg,
            sex,
        ) ?: 0
}
