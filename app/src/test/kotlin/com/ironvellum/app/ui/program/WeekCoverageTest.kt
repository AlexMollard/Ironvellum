package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One week on one map: done and planned sets combined, the five states and the rows' order. */
class WeekCoverageTest {

    private val chest = Muscle.MID_CHEST
    private val utc = ZoneId.of("UTC")
    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
    private val exercises = mapOf(1L to bench)

    private fun benchPlan(sets: Int, day: Int = 5) =
        PlannedPreset("Push", "", day, listOf(PlannedEntry("Bench Press", sets, 8, null)))

    private fun state(done: Double, remaining: List<PlannedPreset> = emptyList()): WeekState =
        weekCoverage(mapOf(chest to done), mapOf(chest to done), remaining).stateOf(chest)

    @Test
    fun `done planned short heavy and none are told apart`() {
        assertEquals(WeekState.DONE, state(4.0))
        assertEquals(WeekState.NONE, state(0.0))
        assertEquals(WeekState.SHORT, state(1.0))
        // Done is under 4, but the plan's remaining day takes it over.
        assertEquals(WeekState.PLANNED, state(2.0, listOf(benchPlan(8))))
        // And a plan that still leaves it under 4 stays short.
        assertEquals(WeekState.SHORT, state(1.0, listOf(benchPlan(1))))
        // 16 done and a day to come is past the weekly line.
        assertEquals(WeekState.HEAVY, state(16.0, listOf(benchPlan(8))))
    }

    @Test
    fun `the planned half is the planned-sets rule and the week adds the done half to it`() {
        val plan = listOf(benchPlan(8))
        val week = weekCoverage(mapOf(chest to 3.0), mapOf(chest to 3.0), plan)
        assertEquals(ProgramRules.coverage(plan).getValue(chest).weekly, week.of(chest).planned, 1e-9)
        assertEquals(3.0, week.of(chest).done, 1e-9)
        assertEquals(week.of(chest).done + week.of(chest).planned, week.of(chest).total, 1e-9)
    }

    @Test
    fun `a dismissed heavy flag reads as the plain state and with nothing done a plan never reads done`() {
        val week = weekCoverage(mapOf(chest to 30.0), mapOf(chest to 8.0), emptyList())
        assertEquals(WeekState.HEAVY, week.stateOf(chest))
        assertEquals(WeekState.DONE, week.stateOf(chest, dismissedHeavy = true))
        val preview = plannedOnlyWeek(listOf(benchPlan(8)))
        assertTrue(preview.states().values.none { it == WeekState.DONE })
        assertEquals(WeekState.PLANNED, preview.stateOf(chest))
    }

    @Test
    fun `rows put heavy first then short with nothing at all after, then on track, and fold the done`() {
        val week = weekCoverage(
            done = mapOf(Muscle.FRONT_DELTS to 22.0, Muscle.CALVES to 1.0, Muscle.FOREARMS to 3.0, Muscle.BICEPS to 5.0, Muscle.LATS to 2.0),
            doneMaxSession = emptyMap(),
            remaining = listOf(PlannedPreset("Pull", "", 5, listOf(PlannedEntry("Pull-up", 8, 8, null)))),
        )
        val rows = weekRows(week, week.states())
        assertEquals(Muscle.FRONT_DELTS, rows.needing.first())
        assertTrue(Muscle.BICEPS in rows.covered)
        val short = rows.needing.filter { week.stateOf(it) == WeekState.SHORT }
        assertEquals("nearest to the floor first", short.sortedByDescending { week.of(it).total }, short)
        val none = rows.needing.indexOfFirst { week.stateOf(it) == WeekState.NONE }
        assertTrue(none > rows.needing.indexOf(short.last()))
        assertFalse(Muscle.BICEPS in rows.needing)
    }

    @Test
    fun `the week is Monday to Monday in local time, 169 hours when the clocks go back`() {
        // Wednesday 21 October 2026 in UTC.
        val wednesday = ZonedDateTime.of(2026, 10, 21, 15, 0, 0, 0, utc).toInstant().toEpochMilli()
        val (from, to) = weekBounds(wednesday, utc)
        assertEquals(ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, utc).toInstant().toEpochMilli(), from)
        assertEquals(7 * 24 * 3_600_000L, to - from)
        val ny = ZoneId.of("America/New_York")
        val (nyFrom, nyTo) = weekBounds(ZonedDateTime.of(2026, 10, 28, 12, 0, 0, 0, ny).toInstant().toEpochMilli(), ny)
        assertEquals(169 * 3_600_000L, nyTo - nyFrom)
    }

    private fun rite(id: Long, day: Int?) = WorkoutPreset(
        id = id, name = "Rite $id", scheduledDay = day,
        entries = listOf(PresetEntry(exerciseId = 1, exerciseName = "Bench Press", targetSets = 4, targetReps = 8)),
    )

    private fun sealed(presetId: Long, atMs: Long) =
        WorkoutSession(id = presetId, presetId = presetId, label = "x", startedAtMs = atMs - 3_600_000, completedAtMs = atMs) to emptyList<SessionSet>()

    @Test
    fun `remaining rites are today and later and not yet sealed this week`() {
        val presets = listOf(rite(1, 1), rite(2, 3), rite(3, 5))
        val weekStart = 1_000_000L
        assertEquals(listOf("Rite 2", "Rite 3"), remainingRites(presets, emptyList(), today = 3, weekStartMs = weekStart).map { it.name })
        assertEquals(listOf("Rite 3"), remainingRites(presets, listOf(sealed(2, weekStart + 5)), 3, weekStart).map { it.name })
        // Sealed last week says nothing about this one.
        assertEquals(listOf("Rite 2", "Rite 3"), remainingRites(presets, listOf(sealed(2, weekStart - 5)), 3, weekStart).map { it.name })
    }

    @Test
    fun `with no day on any rite the whole board is the plan unless sealed, and an unscheduled one is left out once another has a day`() {
        val loose = listOf(rite(1, null), rite(2, null))
        assertEquals(2, remainingRites(loose, emptyList(), today = 4, weekStartMs = 0).size)
        assertEquals(1, remainingRites(loose, listOf(sealed(1, 10)), today = 4, weekStartMs = 0).size)
        assertEquals(listOf("Rite 3"), remainingRites(listOf(rite(3, 6), rite(4, null)), emptyList(), 4, 0).map { it.name })
    }

    private fun set(index: Int, kind: SetKind = SetKind.WORKING, rpe: Int? = 9) = SessionSet(
        exerciseId = 1, exerciseName = "Bench Press", setIndex = index, reps = 5, weightKg = 100.0, done = true, kind = kind, rpe = rpe,
    )

    @Test
    fun `done sets are the hard sets inside the week only, and the busiest session is kept`() {
        val day = 24 * 3_600_000L
        val from = 100 * day
        val to = from + 7 * day
        fun session(id: Long, at: Long, vararg sets: SessionSet) =
            WorkoutSession(id = id, label = "x", startedAtMs = at, completedAtMs = at + 1) to sets.toList()
        val history = listOf(
            session(1, from + day, set(0), set(1), set(2, kind = SetKind.WARMUP)),
            session(2, from + 3 * day, set(0), set(1), set(2), set(3, rpe = 5)),
            session(3, from - day, set(0), set(1), set(2), set(3)),
        )
        val done = doneInWeek(history, exercises, from, to)
        val one = com.ironvellum.app.domain.MuscleMap.profile("Bench Press", "")!!.muscles.getValue(chest)
        assertEquals(5 * one, done.sets.getValue(chest), 1e-9)
        assertEquals(3 * one, done.maxSession.getValue(chest), 1e-9)
        val rite = doneRite(history, exercises, from, to).single().entries.single()
        assertEquals(5, rite.sets)
    }

    @Test
    fun `train's line counts the muscles short and heavy and says on track when neither`() {
        val heavyAndShort = weekCoverage(mapOf(chest to 30.0), emptyMap(), emptyList()).tally()
        assertEquals(1, heavyAndShort.heavy.size)
        assertTrue(weekLine(heavyAndShort).endsWith("1 heavy"))
        assertTrue(weekLine(heavyAndShort).contains("short"))
        val clear = CoverageTally(JUDGED_MUSCLES.associateWith { CoverageState.COVERED })
        assertEquals("on track", weekLine(clear))
    }
}
