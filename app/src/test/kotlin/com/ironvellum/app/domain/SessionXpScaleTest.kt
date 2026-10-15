package com.ironvellum.app.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/** A long synthetic history (made up, not the owner's): the rescore stays exact while it stops rebuilding history per trial. */
class SessionXpScaleTest {

    private val zone = ZoneId.of("Europe/London")

    private val exercises = listOf(
        Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
        Exercise(id = 2, name = "Back Squat", muscleGroup = MuscleGroup.LEGS, isWeighted = true),
        Exercise(id = 3, name = "Deadlift", muscleGroup = MuscleGroup.LEGS, isWeighted = true),
        Exercise(id = 4, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false),
        Exercise(id = 5, name = "Push-up", muscleGroup = MuscleGroup.PUSH, isWeighted = false),
        Exercise(id = 6, name = "Barbell Row", muscleGroup = MuscleGroup.PULL, isWeighted = true),
        Exercise(id = 7, name = "Overhead Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true),
        Exercise(id = 8, name = "Plank", muscleGroup = MuscleGroup.CORE, isWeighted = false, metric = ExerciseMetric.HOLD),
        Exercise(id = 9, name = "Running", muscleGroup = MuscleGroup.CARDIO, isWeighted = false, metric = ExerciseMetric.DISTANCE_TIME, category = "Cardio"),
    ).associateBy { it.id }

    /** About three trials a week from 2023, some imported, some sharing a start, each four to six movements. */
    private fun history(trials: Int, seed: Int = 7): List<Pair<WorkoutSession, List<SessionSet>>> {
        val random = Random(seed)
        val start = LocalDate.of(2023, 1, 2)
        var setId = 1L
        return (1..trials).map { n ->
            val date = start.plusDays((n * 7L) / 3)
            val startedAt = date.atStartOfDay(zone).toInstant().toEpochMilli() + (if (n % 17 == 0) 0 else random.nextInt(6, 20)) * 3_600_000L
            val session = WorkoutSession(
                id = n.toLong(),
                presetId = (n % 4).toLong() + 1,
                label = "Trial $n",
                startedAtMs = startedAt,
                completedAtMs = startedAt + 3_600_000L,
                imported = n % 23 == 0,
            )
            val movements = exercises.keys.shuffled(random).take(random.nextInt(4, 7))
            val sets = movements.flatMapIndexed { position, id ->
                val exercise = exercises.getValue(id)
                (0 until random.nextInt(2, 6)).map { index ->
                    SessionSet(
                        id = setId++,
                        exerciseId = id,
                        exerciseName = exercise.name,
                        exercisePosition = position,
                        setIndex = index,
                        reps = random.nextInt(3, 13),
                        weightKg = if (exercise.isWeighted) 20.0 + n / 4 + random.nextInt(0, 20) * 2.5 else null,
                        done = random.nextInt(10) > 0,
                        durationSec = when (exercise.metric) {
                            ExerciseMetric.HOLD -> random.nextInt(20, 90)
                            ExerciseMetric.DISTANCE_TIME -> random.nextInt(600, 2400)
                            else -> null
                        },
                        distanceM = if (exercise.metric == ExerciseMetric.DISTANCE_TIME) random.nextDouble(2000.0, 8000.0) else null,
                        rpe = if (random.nextBoolean()) random.nextInt(6, 11) else null,
                        kind = if (index == 0 && random.nextInt(5) == 0) SetKind.WARMUP else SetKind.WORKING,
                    )
                }
            }
            session to sets
        }.shuffled(random)
    }

    private val bodyweightAt: (Long) -> Double = { at -> 78.0 + (at / 86_400_000L % 30) / 10.0 }

    /** The rescore before it learned to keep history running: one fresh [SessionXp] per trial. */
    private fun naive(history: List<Pair<WorkoutSession, List<SessionSet>>>): List<XpRescore.Change> = history
        .filter { (session, _) -> session.completedAtMs != null && !session.imported }
        .sortedWith(compareBy({ it.first.startedAtMs }, { it.first.id }))
        .map { (session, sets) ->
            val book = SessionXp(exercises, history, bodyweightAt, excludeSessionId = session.id, beforeMs = session.startedAtMs)
            val done = sets.filter { it.done && !it.warmup }
            val quest = XpRescore.questEarned(session, sets, history, { (it % 7 + 1).toInt() }, zone)
            val facts = book.factsFor(session, done, mapOf(1L to 3), quest, zone)
            XpRescore.Change(
                sessionId = session.id,
                label = session.title.ifBlank { session.label },
                startedAtMs = session.startedAtMs,
                oldXp = session.xpAwarded,
                newXp = book.breakdown(done, bodyweightAt(session.startedAtMs).takeIf { it > 0.0 }, facts, doneSetCount = done.size).total,
                amended = session.editedAtMs != null || session.sealedXp != null,
                sealedXp = session.sealedXp,
            )
        }

    private fun reprice(history: List<Pair<WorkoutSession, List<SessionSet>>>) =
        XpRescore.reprice(history, exercises, bodyweightAt, { mapOf(1L to 3) }, { (it % 7 + 1).toInt() }, zone)

    @Test
    fun `the rescore prices every trial exactly as a fresh book per trial would`() {
        val history = history(160)
        assertEquals(naive(history), reprice(history))
    }
}
