package com.ironvellum.app.data

import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.domain.ActivityScore
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SessionXp
import com.ironvellum.app.domain.WorkoutSession
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
    init {
        catalogue.syncCustomExercises()
    }

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

    /** The catalogue as the domain pricer reads it. */
    val exercises: Map<Long, Exercise> = catalogue.associate { row ->
        row.id to Exercise(
            id = row.id,
            name = row.name,
            muscleGroup = groups.getValue(row.id),
            isWeighted = row.isWeighted,
            metric = metricOf(row.id),
            category = row.category,
        )
    }

    /** A trial's stored sets in the domain's own terms. */
    fun domainSets(sets: List<SetLogEntity>): List<SessionSet> = sets.map { s ->
        SessionSet(
            id = s.id,
            exerciseId = s.exerciseId,
            exerciseName = names[s.exerciseId] ?: "",
            exercisePosition = s.exercisePosition,
            setIndex = s.setIndex,
            reps = s.reps,
            weightKg = s.weightKg,
            modifiers = s.modifiers,
            done = s.done,
            durationSec = s.durationSec,
            distanceM = s.distanceM,
            grade = s.grade,
            kind = s.setKind(),
            supersetGroup = s.supersetGroup,
            rpe = s.rpe,
        )
    }

    /** The pricer over the lifter's completed trials, [excludeSessionId] left out of its history. */
    fun book(
        completed: List<Pair<WorkoutSession, List<SessionSet>>>,
        bodyweightAt: (Long) -> Double,
        excludeSessionId: Long? = null,
        beforeMs: Long? = null,
    ): SessionXp = SessionXp(exercises, completed, bodyweightAt, excludeSessionId, beforeMs = beforeMs)

    /** No history at all: every lift is a first time. */
    fun emptyBook(): SessionXp = book(emptyList(), { 0.0 })

    /**
     * The segments the TICKED sets among [sets] earn. Strength work (reps and
     * static holds) earns effort-judged XP; activities earn from ActivityScore,
     * one segment per movement. [trial] carries the trial-level bonuses.
     */
    fun breakdown(
        sets: List<SetLogEntity>,
        bodyweightKg: Double?,
        book: SessionXp = emptyBook(),
        trial: Xp.TrialFacts = Xp.TrialFacts(earnsBonuses = false),
    ): Xp.Breakdown {
        // A warm-up is stored unticked; the kind check keeps one from ever paying even if a stale write ticked it.
        val doneSets = sets.filter { it.done && !it.isWarmup }
        return book.breakdown(
            sets = domainSets(doneSets),
            bodyweightKg = bodyweightKg,
            trial = trial,
            doneSetCount = doneSets.size,
        )
    }

    /** What [breakdown] pays, in whole. */
    fun xp(
        sets: List<SetLogEntity>,
        bodyweightKg: Double?,
        book: SessionXp = emptyBook(),
        trial: Xp.TrialFacts = Xp.TrialFacts(earnsBonuses = false),
    ): Int = breakdown(sets, bodyweightKg, book, trial).total

    /**
     * Body-scaled strength score of the ticked strength sets; 0 when there is
     * no bodyweight to scale by or no strength work at all. Activities
     * contribute nothing.
     */
    fun strength(sets: List<SetLogEntity>, bodyweightKg: Double?, sex: Sex): Int =
        StrengthIndex.sessionScore(
            sets.filter { it.done && !it.isWarmup && metricOf(it.exerciseId).isStrength }.map { set ->
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
