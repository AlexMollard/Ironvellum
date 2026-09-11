package com.monarch.app.domain

enum class MuscleGroup { PULL, PUSH, LEGS, CORE }

data class Exercise(
    val id: Long = 0,
    val name: String,
    val muscleGroup: MuscleGroup,
    val isWeighted: Boolean,
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
    val reps: Int,
    val weightKg: Double? = null,
    val modifiers: String = "",
    val done: Boolean = false,
)

data class WorkoutSession(
    val id: Long = 0,
    val presetId: Long? = null,
    val label: String,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,
    val xpAwarded: Int = 0,
    val strengthScore: Int = 0,
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
)

/** One session's contribution to an exercise's record. */
data class ExerciseSessionPoint(
    val atMs: Long,
    val bestSetScore: Double,
    val totalReps: Int,
)

/** One logged set, ready for the history list. */
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
    val totalVolumeKg: Double,
    val heaviestWeightKg: Double?,
    val heaviestReps: Int,
    val heaviestAtMs: Long?,
    val bestSetReps: Int,
    val bestSetLoadKg: Double,
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
                totalVolumeKg = 0.0,
                heaviestWeightKg = null,
                heaviestReps = 0,
                heaviestAtMs = null,
                bestSetReps = 0,
                bestSetLoadKg = 0.0,
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

        val entries = rows.map { r ->
            val load = r.weightKg ?: bodyweightKg ?: 0.0
            ExerciseSetEntry(
                sessionId = r.sessionId,
                atMs = r.atMs,
                setIndex = r.setIndex,
                reps = r.reps,
                weightKg = r.weightKg,
                modifiers = r.modifiers,
                done = r.done,
                score = StrengthIndex.repScore(r.reps, r.weightKg, bodyweightKg ?: 0.0),
            )
        }

        fun loadOf(e: ExerciseSetEntry): Double = e.weightKg ?: bodyweightKg ?: 0.0
        val done = entries.filter { it.done }
        val heaviest = done.maxByOrNull { loadOf(it) * 1000 + it.reps }
        val bestSet = done.maxByOrNull { it.reps * loadOf(it) }
        val bestScored = done.maxByOrNull { it.score }

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
            totalVolumeKg = done.sumOf { it.reps * loadOf(it) },
            heaviestWeightKg = heaviest?.weightKg,
            heaviestReps = heaviest?.reps ?: 0,
            heaviestAtMs = heaviest?.atMs,
            bestSetReps = bestSet?.reps ?: 0,
            bestSetLoadKg = bestSet?.let { loadOf(it) } ?: 0.0,
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
