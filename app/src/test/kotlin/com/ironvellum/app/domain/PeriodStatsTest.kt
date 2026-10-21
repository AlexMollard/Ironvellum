package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PeriodStatsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
    private val squat = Exercise(id = 2, name = "Back Squat", muscleGroup = MuscleGroup.LEGS, isWeighted = true)
    private val pullUp = Exercise(id = 3, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
    private val run = Exercise(
        id = 4, name = "Run", muscleGroup = MuscleGroup.CARDIO, isWeighted = false,
        metric = ExerciseMetric.DISTANCE_TIME, category = "Cardio",
    )
    private val catalogue = listOf(bench, squat, pullUp, run).associateBy { it.id }

    private var nextId = 1L

    private fun set(ex: Exercise, kg: Double?, reps: Int, index: Int = 0, done: Boolean = true) =
        SessionSet(exerciseId = ex.id, exerciseName = ex.name, setIndex = index, reps = reps, weightKg = kg, done = done)

    /** A workout on [day] at [time], [minutes] long. */
    private fun workout(day: LocalDate, minutes: Long, vararg sets: SessionSet, time: LocalTime = LocalTime.of(18, 0)): Pair<WorkoutSession, List<SessionSet>> {
        val start = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
        val session = WorkoutSession(
            id = nextId++, label = "Workout", startedAtMs = start, completedAtMs = start + minutes * 60_000L,
        )
        return session to sets.toList()
    }

    private val monday = LocalDate.of(2026, 10, 12)

    private fun week(history: List<Pair<WorkoutSession, List<SessionSet>>>, start: LocalDate = monday) =
        PeriodStats.of(history, catalogue, start, start.plusDays(7), zone)

    @Test
    fun `counts workouts, done sets, volume and minutes of the range only`() {
        val history = listOf(
            workout(monday.minusDays(1), 60, set(bench, 100.0, 5)), // Sunday before: out
            workout(monday, 50, set(bench, 80.0, 5, 0), set(bench, 80.0, 5, 1), set(bench, 80.0, 5, 2, done = false)),
            workout(monday.plusDays(6), 40, set(squat, 100.0, 5)), // Sunday: in
            workout(monday.plusDays(7), 40, set(squat, 100.0, 5)), // next Monday: out
        )
        val stats = week(history)
        assertEquals(2, stats.workouts)
        assertEquals(3, stats.sets)
        assertEquals(80.0 * 5 * 2 + 100.0 * 5, stats.volumeKg, 1e-9)
        assertEquals(90L, stats.minutes)
        assertEquals(setOf(monday, monday.plusDays(6)), stats.days)
    }

    @Test
    fun `an empty range is empty`() {
        val stats = week(emptyList())
        assertTrue(stats.isEmpty)
        assertEquals(0, stats.sets)
        assertNull(stats.bestSet)
        assertTrue(stats.records.isEmpty())
    }

    @Test
    fun `a workout belongs to the day it completed, in the zone of the reader`() {
        // Starts Sunday 23:30 and finishes Monday 00:30 Berlin time: Monday's workout.
        val late = workout(monday.minusDays(1), 60, set(bench, 80.0, 5), time = LocalTime.of(23, 30))
        assertEquals(1, week(listOf(late)).workouts)
        assertEquals(0, week(listOf(late), monday.minusDays(7)).workouts)
    }

    @Test
    fun `a week holding the clock change is still seven days`() {
        val sunday = LocalDate.of(2026, 10, 25) // clocks go back at 03:00
        val start = LocalDate.of(2026, 10, 19)
        val history = listOf(
            workout(start, 30, set(bench, 60.0, 5), time = LocalTime.of(0, 30)),
            workout(sunday, 30, set(bench, 60.0, 5), time = LocalTime.of(23, 0)),
            workout(sunday.plusDays(1), 30, set(bench, 60.0, 5), time = LocalTime.of(0, 10)),
        )
        assertEquals(2, week(history, start).workouts)
    }

    @Test
    fun `time skips a trial left open overnight and an import with no length`() {
        val open = workout(monday, 25 * 60, set(bench, 60.0, 5))
        val imported = workout(monday.plusDays(1), 0, set(bench, 60.0, 5))
        val normal = workout(monday.plusDays(2), 45, set(bench, 60.0, 5))
        assertEquals(45L, week(listOf(open, imported, normal)).minutes)
    }

    @Test
    fun `a record is a lift whose best estimated 1RM beat everything before the range`() {
        val history = listOf(
            workout(monday.minusDays(20), 60, set(bench, 80.0, 5), set(squat, 100.0, 5)),
            workout(monday.plusDays(1), 60, set(bench, 85.0, 5), set(squat, 90.0, 5)),
        )
        val records = week(history).records
        assertEquals(listOf("Bench Press"), records.map { it.label })
        val bench = records.single()
        assertEquals(ProgramRules.epley(85.0, 5) - ProgramRules.epley(80.0, 5), bench.gainKg, 1e-9)
        assertEquals(ProgramRules.epley(85.0, 5), bench.e1rmKg, 1e-9)
        assertEquals(85.0, bench.set.weightKg!!, 0.0)
    }

    @Test
    fun `a lift never done before the range is history starting, not a record`() {
        val history = listOf(workout(monday.plusDays(1), 60, set(bench, 85.0, 5)))
        assertTrue(week(history).records.isEmpty())
    }

    @Test
    fun `a heavier set within the range counts once per lift, at its best`() {
        val history = listOf(
            workout(monday.minusDays(10), 60, set(bench, 80.0, 5)),
            workout(monday.plusDays(1), 60, set(bench, 82.5, 5)),
            workout(monday.plusDays(3), 60, set(bench, 85.0, 5)),
        )
        val records = week(history).records
        assertEquals(1, records.size)
        assertEquals(ProgramRules.epley(85.0, 5) - ProgramRules.epley(80.0, 5), records.single().gainKg, 1e-9)
        assertEquals(monday.plusDays(3), java.time.Instant.ofEpochMilli(records.single().atMs).atZone(zone).toLocalDate())
    }

    @Test
    fun `a matching or lighter week is not a record, and later weeks never lend a benchmark`() {
        val history = listOf(
            workout(monday.minusDays(10), 60, set(bench, 80.0, 5)),
            workout(monday.plusDays(1), 60, set(bench, 80.0, 5)),
            workout(monday.plusDays(9), 60, set(bench, 120.0, 5)), // after the range: ignored
        )
        assertTrue(week(history).records.isEmpty())
    }

    @Test
    fun `an added-load lift reports its load as added to the body`() {
        val history = listOf(
            workout(monday.minusDays(10), 60, set(pullUp, 10.0, 5)),
            workout(monday.plusDays(1), 60, set(pullUp, 12.5, 7)),
        )
        val record = week(history).records.single()
        assertTrue(record.addsToBody)
        assertEquals(12.5, record.set.weightKg!!, 0.0)
    }

    @Test
    fun `best set is the highest estimated 1RM of the range`() {
        val history = listOf(
            workout(monday, 60, set(bench, 82.5, 6)),
            workout(monday.plusDays(3), 60, set(squat, 105.0, 5)),
        )
        val best = week(history).bestSet!!
        assertEquals("Back Squat", best.label)
        assertEquals(105.0, best.set.weightKg!!, 0.0)
    }

    @Test
    fun `muscles are credited hard sets only inside the range`() {
        val history = listOf(
            workout(monday.minusDays(1), 60, set(squat, 100.0, 5), set(squat, 100.0, 5)),
            workout(monday, 60, set(bench, 80.0, 5), set(bench, 80.0, 5), set(bench, 80.0, 5)),
        )
        val muscles = week(history).muscleSets
        assertEquals(3.0, muscles[Muscle.MID_CHEST]!!, 1e-9)
        assertNull(muscles[Muscle.QUADS])
    }

    @Test
    fun `lifts list the done working sets, with their common reps and top load`() {
        val history = listOf(
            workout(monday, 60, set(squat, 100.0, 5, 0), set(squat, 100.0, 5, 1), set(squat, 90.0, 5, 2)),
            workout(monday.plusDays(1), 60, set(bench, 80.0, 8, 0), set(bench, 80.0, 6, 1)),
        )
        val lifts = week(history).lifts
        assertEquals(listOf("Back Squat", "Bench Press"), lifts.map { it.label })
        assertEquals(LiftWork("Back Squat", 3, 5, 100.0, false), lifts[0])
        assertNull(lifts[1].reps)
    }

    @Test
    fun `activities add sets but no lift row, no record and no muscle credit`() {
        val history = listOf(
            workout(monday.minusDays(9), 30, SessionSet(exerciseId = 4, exerciseName = "Run", setIndex = 0, reps = 1, done = true, distanceM = 5000.0)),
            workout(monday, 30, SessionSet(exerciseId = 4, exerciseName = "Run", setIndex = 0, reps = 1, done = true, distanceM = 6000.0)),
        )
        val stats = week(history)
        assertEquals(1, stats.sets)
        assertTrue(stats.lifts.isEmpty())
        assertTrue(stats.records.isEmpty())
        assertTrue(stats.muscleSets.isEmpty())
    }
}
