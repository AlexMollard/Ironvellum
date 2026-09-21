package com.monarch.app.domain

/**
 * Lifting groups first, then the activity groups the seeded sports use.
 * Activity work has no single muscle group, so it gets its own bucket rather
 * than being mislabelled as PULL or LEGS.
 */
enum class MuscleGroup { PULL, PUSH, LEGS, CORE, CARDIO, SPORT, CLIMBING, WATER, MOBILITY }

/**
 * How a movement is measured. REPS is the lifting/calisthenics case.
 *
 * HOLD is a static hold — an L-sit, a plank, a front lever. Its figure is
 * SECONDS and lives in [SessionSet.durationSec], never in `reps`. Before it
 * existed, holds were catalogued as REPS and a 60-second hold was typed into
 * the reps box, so every rep-counting consumer in the app — XP, strength
 * score, personal records, rep-count titles, progressive overload, volume —
 * read it as sixty repetitions.
 *
 * HOLD is strength work, not an activity: code that separates lifting from
 * cardio must treat it with REPS, not with DURATION.
 */
enum class ExerciseMetric { REPS, HOLD, DURATION, DISTANCE_TIME, ATTEMPTS_GRADE }

/** True for the metrics that are strength training rather than activity. */
val ExerciseMetric.isStrength: Boolean
    get() = this == ExerciseMetric.REPS || this == ExerciseMetric.HOLD

data class Exercise(
    val id: Long = 0,
    val name: String,
    val muscleGroup: MuscleGroup,
    val isWeighted: Boolean,
    val metric: ExerciseMetric = ExerciseMetric.REPS,
    /** "" for lifting; otherwise e.g. "Cardio", "Sport", "Climbing", "Water", "Mobility". */
    val category: String = "",
)

data class PresetEntry(
    val id: Long = 0,
    val exerciseId: Long,
    val exerciseName: String = "",
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double? = null,
    val modifiers: String = "",
    val position: Int = 0,
)

data class WorkoutPreset(
    val id: Long = 0,
    val name: String,
    val note: String = "",
    val entries: List<PresetEntry> = emptyList(),
    /** ISO day-of-week (1 = Monday .. 7 = Sunday) or null for unscheduled. */
    val scheduledDay: Int? = null,
)

data class SessionSet(
    val id: Long = 0,
    val exerciseId: Long,
    val exerciseName: String = "",
    val exercisePosition: Int = 0,
    val setIndex: Int,
    /** 0 for a HOLD set: its figure is seconds, in [durationSec]. */
    val reps: Int,
    val weightKg: Double? = null,
    val modifiers: String = "",
    val done: Boolean = false,
    /** Seconds held for HOLD; elapsed time for activities; null for REPS. */
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val grade: String? = null,
)

data class WorkoutSession(
    val id: Long = 0,
    val presetId: Long? = null,
    val label: String,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,
    val xpAwarded: Int = 0,
    val strengthScore: Int = 0,
    /** User-authored title; syncs. "" when unset. */
    val title: String = "",
    /** PUBLIC note, visible in the feed; syncs. "" when unset. */
    val note: String = "",
    /** Device-only note, never leaves the app. "" when unset. */
    val privateNote: String = "",
)

data class StatEntry(
    val id: Long = 0,
    val takenAtMs: Long,
    val weightKg: Double,
    val heightCm: Double,
    val bodyFatPct: Double? = null,
)

data class PlayerProfile(
    val name: String = "Hunter",
    val totalXp: Long = 0,
    val currentTitleId: String? = null,
    val trainingMode: TrainingMode = TrainingMode.STRENGTH,
    /** CLEAN is the default look; ink is the opt-in hand-drawn treatment. */
    val inkStyle: Boolean = false,
)

data class SkillPractice(
    val skillName: String,
    val practicedAtMs: Long,
    val claimed: Boolean = false,
    /** Seconds held, reps completed, or metres travelled. */
    val value: Int = 0,
    val weightKg: Double? = null,
)

data class SkillClaimResult(
    val skill: Skills.SkillDef,
    val xpAwarded: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val totalXp: Long,
    val newTitles: List<TitleDef>,
    val unlockedNext: List<Skills.SkillDef>,
)

data class UnlockedTitle(
    val titleId: String,
    val unlockedAtMs: Long,
)

/** Room projection: one logged set of one exercise with its session timestamp. */
data class ExerciseSetRow(
    val sessionId: Long,
    val atMs: Long,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double?,
    val modifiers: String,
    val done: Boolean,
    /** Seconds held; null unless the movement is [ExerciseMetric.HOLD]. */
    val durationSec: Int? = null,
)

/** One session's contribution to an exercise's record. */
data class ExerciseSessionPoint(
    val atMs: Long,
    val bestSetScore: Double,
    val totalReps: Int,
)

/**
 * One logged set, ready for the history list. For a [ExerciseMetric.HOLD]
 * movement [reps] carries SECONDS — the figure the hunter entered — and the
 * screen labels it as such.
 */
data class ExerciseSetEntry(
    val sessionId: Long,
    val atMs: Long,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double?,
    val modifiers: String,
    val done: Boolean,
    val score: Double,
)

data class ExerciseHistory(
    val exercise: Exercise,
    val sessions: Int,
    val completedSets: Int,
    val totalReps: Int,
    /** Kilos lifted across completed sets; null when a set's load is unknown (no added weight and no weigh-in). */
    val totalVolumeKg: Double?,
    val heaviestWeightKg: Double?,
    val heaviestReps: Int,
    val heaviestAtMs: Long?,
    val bestSetReps: Int,
    /** Added weight on the best set — bodyweight only when null, NOT a resolved load. */
    val bestSetLoadKg: Double?,
    val bestSetAtMs: Long?,
    val bestScore: Double,
    val bestScoreAtMs: Long?,
    val firstLoggedAtMs: Long?,
    val lastLoggedAtMs: Long?,
    val daysSinceLast: Long?,
    val series: List<ExerciseSessionPoint>,
    val entries: List<ExerciseSetEntry>,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

object ExerciseHistoryCalculator {

    /** Pure summary over one exercise's logged sets; bodyweight scores body-scaled load. */
    fun build(
        exercise: Exercise,
        rows: List<ExerciseSetRow>,
        bodyweightKg: Double?,
        nowMs: Long = System.currentTimeMillis(),
    ): ExerciseHistory {
        if (rows.isEmpty()) {
            return ExerciseHistory(
                exercise = exercise,
                sessions = 0,
                completedSets = 0,
                totalReps = 0,
                totalVolumeKg = null,
                heaviestWeightKg = null,
                heaviestReps = 0,
                heaviestAtMs = null,
                bestSetReps = 0,
                bestSetLoadKg = null,
                bestSetAtMs = null,
                bestScore = 0.0,
                bestScoreAtMs = null,
                firstLoggedAtMs = null,
                lastLoggedAtMs = null,
                daysSinceLast = null,
                series = emptyList(),
                entries = emptyList(),
            )
        }

        // A hold's figure is seconds. It is carried in `reps` from here on
        // because that is the column every list row and chart already reads —
        // but it is SCORED as rep-equivalents, never as repetitions, and the
        // screens label it "s".
        val isHold = exercise.metric == ExerciseMetric.HOLD
        fun figureOf(r: ExerciseSetRow): Int = if (isHold) (r.durationSec ?: r.reps) else r.reps

        val entries = rows.map { r ->
            val figure = figureOf(r)
            ExerciseSetEntry(
                sessionId = r.sessionId,
                atMs = r.atMs,
                setIndex = r.setIndex,
                reps = figure,
                weightKg = r.weightKg,
                modifiers = r.modifiers,
                done = r.done,
                // Same suppression as hold tonnage below: an activity metric's
                // figure is an attempt count or a distance, and repScore would
                // invent a bodyweight-rep number from it. 0.0 is the value the
                // calculator already uses for "no honest strength score".
                score = when {
                    isHold -> StrengthIndex.holdScore(exercise.name, figure, r.weightKg, bodyweightKg ?: 0.0)
                    exercise.metric.isStrength -> StrengthIndex.repScore(exercise.name, figure, r.weightKg, bodyweightKg ?: 0.0)
                    else -> 0.0
                },
            )
        }

        fun loadOf(e: ExerciseSetEntry): Double = e.weightKg ?: bodyweightKg ?: 0.0
        val done = entries.filter { it.done }
        val heaviest = done.maxByOrNull { loadOf(it) * 1000 + it.reps }
        val bestSet = done.maxByOrNull { it.reps * loadOf(it) }
        val bestScored = done.maxByOrNull { it.score }
        // loadOf resolves to 0.0 when there is no weigh-in; a bodyweight set then
        // carries a load we do not know, and summing it claims "0 kg" — a confident
        // lie. Volume is only reported when every completed set has a known load.
        val unknownLoad = done.any { it.weightKg == null && bodyweightKg == null }

        val series = entries
            .groupBy { it.sessionId }
            .map { (_, sets) ->
                ExerciseSessionPoint(
                    atMs = sets.minOf { it.atMs },
                    bestSetScore = sets.maxOf { it.score },
                    totalReps = sets.filter { it.done }.sumOf { it.reps },
                )
            }
            .sortedBy { it.atMs }

        val first = entries.minOf { it.atMs }
        val last = entries.maxOf { it.atMs }
        return ExerciseHistory(
            exercise = exercise,
            sessions = entries.groupBy { it.sessionId }.size,
            completedSets = done.size,
            totalReps = done.sumOf { it.reps },
            // reps x load is rep-volume. Seconds x kilos is not a tonnage,
            // so a hold reports none rather than a fabricated figure.
            totalVolumeKg = when {
                isHold -> 0.0
                unknownLoad -> null
                else -> done.sumOf { it.reps * loadOf(it) }
            },
            heaviestWeightKg = heaviest?.weightKg,
            heaviestReps = heaviest?.reps ?: 0,
            heaviestAtMs = heaviest?.atMs,
            bestSetReps = bestSet?.reps ?: 0,
            // The set's added weight, not loadOf's bodyweight fallback — the
            // grid decides how to label a bodyweight-only set.
            bestSetLoadKg = bestSet?.weightKg,
            bestSetAtMs = bestSet?.atMs,
            bestScore = bestScored?.score ?: 0.0,
            bestScoreAtMs = bestScored?.atMs,
            firstLoggedAtMs = first,
            lastLoggedAtMs = last,
            daysSinceLast = (nowMs - last) / 86_400_000L,
            series = series,
            entries = entries,
        )
    }
}

data class HealthDay(
    val date: java.time.LocalDate,
    val steps: Int = 0,
    val distanceKm: Double = 0.0,
    val activeKcal: Int = 0,
    val sleepMinutes: Int = 0,
    val restingHr: Int? = null,
)
