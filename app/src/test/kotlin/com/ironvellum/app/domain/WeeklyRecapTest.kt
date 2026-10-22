package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WeeklyRecapTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
    private val squat = Exercise(id = 2, name = "Back Squat", muscleGroup = MuscleGroup.LEGS, isWeighted = true)
    private val catalogue = listOf(bench, squat).associateBy { it.id }
    private var nextId = 1L
    private val monday = LocalDate.of(2026, 10, 12)
    private val sunday = monday.plusDays(6)

    private fun workout(day: LocalDate, ex: Exercise, sets: Int, kg: Double = 80.0, reps: Int = 5, label: String = "Push A"): Pair<WorkoutSession, List<SessionSet>> {
        val start = day.atTime(LocalTime.of(18, 0)).atZone(zone).toInstant().toEpochMilli()
        val session = WorkoutSession(id = nextId++, label = label, startedAtMs = start, completedAtMs = start + 3_600_000L)
        return session to List(sets) { SessionSet(exerciseId = ex.id, exerciseName = ex.name, setIndex = it, reps = reps, weightKg = kg, done = true) }
    }

    private fun recap(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        plan: List<PlannedPreset> = emptyList(),
        today: LocalDate = sunday,
    ) = WeeklyRecap.build(history, catalogue, { 0.0 }, plan, monday, today, zone)

    private fun preset(name: String, day: Int?, vararg entries: PlannedEntry) = PlannedPreset(name, "", day, entries.toList())

    private fun entry(name: String, sets: Int) = PlannedEntry(name, sets, 5, null)

    @Test
    fun `weeks start on Monday`() {
        assertEquals(monday, WeeklyRecap.weekStartOf(sunday))
        assertEquals(monday, WeeklyRecap.weekStartOf(monday))
        assertEquals(monday, WeeklyRecap.weekStartOf(LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun `an empty week, a light one among busier ones, and an ordinary one`() {
        assertEquals(RecapKind.EMPTY, recap(emptyList()).kind)
        val busyBefore = listOf(
            workout(monday.minusDays(6), bench, 3), workout(monday.minusDays(4), bench, 3), workout(monday.plusDays(1), bench, 3),
        )
        assertEquals(RecapKind.LIGHT, recap(busyBefore).kind)
        // One workout after a week of one is just the habit.
        val steady = listOf(workout(monday.minusDays(4), bench, 3), workout(monday.plusDays(1), bench, 3))
        assertEquals(RecapKind.FULL, recap(steady).kind)
        // Several workouts are an ordinary week.
        assertEquals(RecapKind.FULL, recap(listOf(workout(monday, bench, 3), workout(monday.plusDays(2), bench, 3))).kind)
    }

    @Test
    fun `volume change is against the week before, null when it had none`() {
        val history = listOf(workout(monday.minusDays(3), bench, 2, kg = 100.0), workout(monday, bench, 2, kg = 115.0))
        assertEquals(15, recap(history).volumeChangePercent)
        assertNull(recap(listOf(workout(monday, bench, 2))).volumeChangePercent)
    }

    @Test
    fun `muscles are ordered by hard sets, most first`() {
        val history = listOf(workout(monday, squat, 2), workout(monday.plusDays(1), bench, 5))
        val rows = recap(history).muscles
        assertEquals(rows.sortedByDescending { it.sets }, rows)
        assertEquals(Muscle.MID_CHEST, rows.first().muscle)
        assertEquals(ProgramRules.TRACKED.size, rows.size)
    }

    @Test
    fun `the aim is the plan's weekly sets give or take a fifth`() {
        assertEquals(4..6, WeeklyRecap.aimFor(5.0))
        assertEquals(6..8, WeeklyRecap.aimFor(7.0))
        assertEquals(8..12, WeeklyRecap.aimFor(10.0))
        assertNull(WeeklyRecap.aimFor(0.0))
        assertEquals(1..1, WeeklyRecap.aimFor(1.0))
    }

    @Test
    fun `a muscle is not trained, below the minimum, under the plan or on aim`() {
        assertEquals(AimState.NOT_TRAINED, WeeklyRecap.stateOf(0.0, 4..6))
        assertEquals(AimState.BELOW_MINIMUM, WeeklyRecap.stateOf(3.5, 4..6))
        assertEquals(AimState.UNDER_PLAN, WeeklyRecap.stateOf(7.0, 8..12))
        assertEquals(AimState.ON_AIM, WeeklyRecap.stateOf(8.0, 8..12))
        assertEquals(AimState.ON_AIM, WeeklyRecap.stateOf(15.0, 8..12))
        // No plan: past the 4-set minimum is enough.
        assertEquals(AimState.ON_AIM, WeeklyRecap.stateOf(4.0, null))
        assertEquals(AimState.BELOW_MINIMUM, WeeklyRecap.stateOf(2.0, null))
    }

    @Test
    fun `rows carry the plan's aim and the verdict against it`() {
        val plan = listOf(preset("Push A", 1, entry("Bench Press", 10)))
        val rows = recap(listOf(workout(monday, bench, 7)), plan).muscles.associateBy { it.muscle }
        val chest = rows.getValue(Muscle.MID_CHEST)
        assertEquals(8..12, chest.aim)
        assertEquals(AimState.UNDER_PLAN, chest.state)
        assertNull(rows.getValue(Muscle.QUADS).aim)
        assertEquals(AimState.NOT_TRAINED, rows.getValue(Muscle.QUADS).state)
        assertTrue(recap(listOf(workout(monday, bench, 7)), plan).onAim.none { it.muscle == Muscle.MID_CHEST })
    }

    @Test
    fun `the streak is read as of the end of a past week, and of today in the current one`() {
        val history = listOf(workout(monday.minusDays(2), bench, 3), workout(monday.plusDays(1), bench, 3))
        // Read on the Sunday itself: Sat 10 Oct to Sun 18 Oct is 9 days.
        assertEquals(9, recap(history, today = sunday).streak.days)
        // Read a month later the week's own streak is still its own, not a dead one.
        assertEquals(9, recap(history, today = sunday.plusDays(30)).streak.days)
        assertEquals(monday.plusDays(1).plusDays(7), recap(history).streak.trainBy)
    }

    @Test
    fun `the streak says when it is the longest and when tomorrow closes it`() {
        val longRun = (0 until 60 step 5).map { workout(LocalDate.of(2026, 7, 1).plusDays(it.toLong()), bench, 1) }
        val shorter = recap(longRun + workout(monday.plusDays(1), bench, 3))
        assertFalse(shorter.streak.longest)
        val only = recap(listOf(workout(monday.plusDays(1), bench, 3)))
        assertTrue(only.streak.longest)
        // Last workout exactly 7 days before Sunday: today is the last day it survives.
        val atRisk = WeeklyRecap.build(listOf(workout(monday.minusDays(1), bench, 3)), catalogue, { 0.0 }, emptyList(), monday, monday.plusDays(6), zone)
        assertTrue(atRisk.streak.atRisk)
        assertEquals(monday.plusDays(6), atRisk.streak.trainBy)
    }

    @Test
    fun `next week names the plan's first workout and what this week left short`() {
        val plan = listOf(
            preset("Push A", 1, entry("Bench Press", 4)),
            preset("Legs", 4, entry("Back Squat", 4)),
            preset("Unscheduled", null, entry("Back Squat", 4)),
        )
        val next = recap(listOf(workout(monday, bench, 6)), plan).next!!
        assertEquals(2, next.planned)
        assertEquals("Push A", next.firstName)
        assertEquals(1, next.firstDay)
        assertTrue(Muscle.QUADS in next.short)
        assertEquals("Legs", next.addName)
        assertEquals(4, next.addDay)
    }

    @Test
    fun `no scheduled plan means no next-week card, and an old week has none either`() {
        assertNull(recap(listOf(workout(monday, bench, 6))).next)
        val plan = listOf(preset("Push A", 1, entry("Bench Press", 4)))
        assertTrue(recap(listOf(workout(monday, bench, 6)), plan, today = sunday).next != null)
        assertNull(recap(listOf(workout(monday, bench, 6)), plan, today = monday.plusDays(14)).next)
    }

    @Test
    fun `longest streak spans the closed chains and never trails the live one`() {
        val today = LocalDate.of(2026, 10, 18)
        // A chain of 21 days, a gap, then a live chain of 5.
        val dates = setOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 21), today.minusDays(4), today)
        assertEquals(5, Streak.current(dates, today))
        assertEquals(21, Streak.longest(dates, today))
        assertEquals(5, Streak.longest(setOf(today.minusDays(4), today), today))
        assertEquals(0, Streak.longest(emptySet(), today))
        assertEquals(1, Streak.longest(setOf(today.minusDays(30)), today))
    }
}
