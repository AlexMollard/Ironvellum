package com.ironvellum.app.domain

import com.ironvellum.app.ui.program.WebFatigueText
import com.ironvellum.app.ui.theme.AccentPalette
import com.ironvellum.app.ui.theme.ThemeMode
import com.ironvellum.app.ui.theme.WebAppearance
import java.time.Instant
import java.time.ZoneId

internal object WebSnapshotFixtures {
    private const val DAY = 86_400_000L
    val now = Instant.parse("2026-10-27T12:00:00Z").toEpochMilli()
    private val exercises = listOf(
        Exercise(1, "Pull-up", MuscleGroup.PULL, false),
        Exercise(2, "Push-up", MuscleGroup.PUSH, false),
        Exercise(3, "Barbell Squat", MuscleGroup.LEGS, true),
        Exercise(4, "Plank", MuscleGroup.CORE, false, ExerciseMetric.HOLD),
        Exercise(5, "Run", MuscleGroup.LEGS, false, ExerciseMetric.DISTANCE_TIME),
        Exercise(6, "Fictional lift", MuscleGroup.PULL, true, custom = true),
        Exercise(7, "Weighted Dip", MuscleGroup.PUSH, true),
    ).associateBy { it.id }
    fun input(): SnapshotInput {
        val history = (0 until 14).map { n ->
            val at = now - listOf(69, 62, 55, 48, 41, 26, 23, 20, 17, 14, 11, 8, 5, 2)[n] * DAY
            val session = WorkoutSession(id = n + 1L, label = "Fictional $n", startedAtMs = at,
                completedAtMs = at + 40 * 60_000, xpAwarded = if (n >= 12) 0 else 100, imported = n >= 12)
            val modifier = when (n % 4) { 0 -> "Weighted"; 1 -> "Archer"; 2 -> "Assisted"; else -> "Negative" }
            val sets = listOf(
                SessionSet(exerciseId = 1, exerciseName = "Pull-up", setIndex = 0, reps = 5, weightKg = 5.0, kind = SetKind.WARMUP),
                SessionSet(exerciseId = 1, exerciseName = "Pull-up", setIndex = 1, reps = 6 + n % 3,
                    weightKg = if (n % 4 == 0) 12.5 else if (n % 4 == 2) -20.0 else null,
                    modifiers = modifier, done = true, kind = SetKind.TOP, rpe = 8, durationSec = if (n % 4 == 3) 5 else null),
                SessionSet(exerciseId = 1, exerciseName = "Pull-up", setIndex = 2, reps = 8, modifiers = modifier, done = true, kind = SetKind.BACKOFF),
                SessionSet(exerciseId = 2, exerciseName = "Push-up", exercisePosition = 1, setIndex = 0, reps = 5, kind = SetKind.WARMUP),
                SessionSet(exerciseId = 2, exerciseName = "Push-up", exercisePosition = 1, setIndex = 1, reps = 10, modifiers = "Deficit", done = true),
                SessionSet(exerciseId = 2, exerciseName = "Push-up", exercisePosition = 2, setIndex = 0, reps = 10, done = true),
                SessionSet(exerciseId = 3, exerciseName = "Barbell Squat", exercisePosition = 3, setIndex = 0, reps = 5, weightKg = 20.0, kind = SetKind.WARMUP),
                SessionSet(exerciseId = 3, exerciseName = "Barbell Squat", exercisePosition = 3, setIndex = 1, reps = 8, weightKg = 60.0, done = true),
                SessionSet(exerciseId = 4, exerciseName = "Plank", exercisePosition = 4, setIndex = 0, reps = 0, durationSec = 10, kind = SetKind.WARMUP),
                SessionSet(exerciseId = 4, exerciseName = "Plank", exercisePosition = 4, setIndex = 1, reps = 0, durationSec = 40, done = true),
                SessionSet(exerciseId = 5, exerciseName = "Run", exercisePosition = 5, setIndex = 0, reps = 0, durationSec = 900, distanceM = 2000.0, done = true),
                SessionSet(exerciseId = 6, exerciseName = "Fictional lift", exercisePosition = 6, setIndex = 0, reps = 5, weightKg = 10.0, kind = SetKind.WARMUP),
                SessionSet(exerciseId = 6, exerciseName = "Fictional lift", exercisePosition = 6, setIndex = 1, reps = 8, weightKg = 20.0, done = true),
                SessionSet(exerciseId = 7, exerciseName = "Weighted Dip", exercisePosition = 7, setIndex = 0, reps = 5, weightKg = 10.0, kind = SetKind.WARMUP),
                SessionSet(exerciseId = 7, exerciseName = "Weighted Dip", exercisePosition = 7, setIndex = 1, reps = 8, weightKg = 30.0, done = true),
                SessionSet(exerciseId = 7, exerciseName = "Weighted Dip", exercisePosition = 7, setIndex = 2, reps = 8, weightKg = 30.0, done = true),
                SessionSet(exerciseId = 7, exerciseName = "Weighted Dip", exercisePosition = 7, setIndex = 3, reps = 8, weightKg = 30.0, done = true),
                SessionSet(exerciseId = 7, exerciseName = "Weighted Dip", exercisePosition = 7, setIndex = 4, reps = 8, weightKg = 30.0, done = true),
            ).filterNot {
                (n >= 11 && it.exerciseId in 1L..4L) ||
                    (n == 10 && it.exerciseId in 3L..4L) ||
                    (n in 5..8 && it.exerciseId == 6L) || (n != 13 && it.exerciseId == 7L)
            }
            session to sets
        }
        return SnapshotInput(history, exercises, listOf(StatEntry(takenAtMs = now - 100 * DAY, weightKg = 80.0, heightCm = 180.0)),
            PlayerProfile(totalXp = 126420), emptyList(), emptyList(), LoadUnit.KG, EffortScale.RPE, false,
            ZoneId.of("Australia/Sydney"), now,
            WebAppearance.of(ThemeMode.SYSTEM, AccentPalette.Default, AccentPalette.LightDefault, themed = false),
            WebFatigueText, "Fictional lifter", "female", null)
    }
}
