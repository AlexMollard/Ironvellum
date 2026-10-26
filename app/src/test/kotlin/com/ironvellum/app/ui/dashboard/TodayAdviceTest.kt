package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.domain.FatigueAdvice
import com.ironvellum.app.domain.FatigueContribution
import com.ironvellum.app.domain.FatigueLevel
import com.ironvellum.app.domain.FatigueReport
import com.ironvellum.app.domain.EntryAdjust
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.PlanFatigue
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/** Which advice Today's day card wears: the kinds of day it appears on, the setting, "not today", and what rides the rows. */
class TodayAdviceTest {

    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-26T09:00:00Z").toEpochMilli()
    private val monday = 1

    private fun report(vararg residuals: Pair<Muscle, Double>) = FatigueReport(
        nowMs = now,
        newLifter = false,
        contributions = residuals.associate { (muscle, residual) ->
            muscle to listOf(FatigueContribution(muscle, 1, now, "Test lift", "", "test lift", residual, 0.0, false, false, 24.0))
        },
    )

    private fun rite(id: Long, name: String, day: Int?, vararg lifts: String) = WorkoutPreset(
        id = id, name = name, scheduledDay = day,
        entries = lifts.mapIndexed { i, l -> PresetEntry(id = id * 10 + i, exerciseId = id * 10 + i, exerciseName = l, targetSets = 3, targetReps = 8, position = i) },
    )

    private val push = rite(1, "Push Strength", monday, "Bench Press", "Overhead Press")
    private val pull = rite(2, "Legs", 3, "Back Squat")

    /** Chest tired, not the shoulders: one exercise leans, so it is the adjust line. */
    private val chestTired = report(Muscle.MID_CHEST to 4.0)

    private fun advice(
        kind: DayKind = DayKind.BEGIN,
        report: FatigueReport? = chestTired,
        enabled: Boolean = true,
        notToday: Boolean = false,
        selectedDay: Int = monday,
        rite: WorkoutPreset? = push,
        weekDone: List<WorkoutSession> = emptyList(),
    ) = todayAdvice(kind, rite, listOf(push, pull), weekDone, report, selectedDay, zone, enabled, notToday)

    @Test
    fun `a tired muscle on a begin day is the adjust line, tagging the exercise that leans on it`() {
        val a = advice()!!
        assertTrue(a.advice is FatigueAdvice.Today.Adjust)
        assertFalse(a.projected)
        assertEquals(now, a.atMs)
        assertEquals(setOf(push.entries[0].id), a.tags.keys)
        assertEquals(FatigueTag(Muscle.MID_CHEST, FatigueLevel.HIGH), a.tags.getValue(push.entries[0].id))
        assertNull(a.other)
    }

    @Test
    fun `nothing at all when the setting is off, the report is not in, or it was said not today`() {
        assertNull(advice(enabled = false))
        assertNull(advice(report = null))
        assertNull(advice(notToday = true))
        assertNull(advice(report = report(Muscle.MID_CHEST to 3.5)))
    }

    @Test
    fun `only a begin or planned day wears advice`() {
        listOf(DayKind.LIVE, DayKind.SEALED, DayKind.RESPITE, DayKind.NO_CYCLE).forEach { assertNull("$it", advice(kind = it)) }
        assertNull(advice(rite = null))
        assertNotNull(advice(kind = DayKind.PLANNED, report = report(Muscle.MID_CHEST to 12.0), selectedDay = 2))
    }

    @Test
    fun `another day reads the levels at 08 00 on it, says projected, and never offers to rest`() {
        // Tuesday 08:00 is 23 hours on: a chest this worn (24 hour half-life) has halved and is still High.
        val worn = report(Muscle.MID_CHEST to 12.0)
        val planned = advice(kind = DayKind.PLANNED, report = worn, selectedDay = 2, rite = push.copy(scheduledDay = 2))!!
        assertTrue(planned.projected)
        assertEquals(PlanFatigue.nextEightAm(2, now, zone), planned.atMs)
        assertTrue(planned.advice is FatigueAdvice.Today.Adjust)

        // A smaller dose is High now but back under High by then: nothing to say about that day.
        assertNull(advice(kind = DayKind.PLANNED, report = report(Muscle.MID_CHEST to 4.0), selectedDay = 2, rite = push.copy(scheduledDay = 2)))
        assertNotNull(advice(kind = DayKind.BEGIN, report = report(Muscle.MID_CHEST to 4.0)))
    }

    @Test
    fun `a worn-out begin day is the rest panel, which offers the freshest routine not done this week`() {
        fun worn(dose: Double) = report(*(FatigueAdvice.mainMuscles("Bench Press") + FatigueAdvice.mainMuscles("Overhead Press")).map { it to dose }.toTypedArray())
        val a = advice(report = worn(5.0))!!
        assertTrue(a.advice is FatigueAdvice.Today.Rest)
        assertEquals(pull, a.other)

        val done = WorkoutSession(id = 5, presetId = pull.id, label = "Legs", startedAtMs = now - 1_000)
        assertNull("done this week, so only rest or begin anyway", advice(report = worn(5.0), weekDone = listOf(done))!!.other)
        // On another day the routine only reads; there is nothing to rest from or begin instead.
        val planned = advice(kind = DayKind.PLANNED, report = worn(12.0), selectedDay = 2)!!
        assertTrue(planned.advice is FatigueAdvice.Today.Adjust)
        assertNull(planned.other)
    }

    @Test
    fun `a tagged exercise takes a line of its own, and the tag rides its row`() {
        val tags = mapOf(push.entries[0].id to FatigueTag(Muscle.MID_CHEST, FatigueLevel.HIGH))
        val rows = plannedRows(push.entries, tags)
        assertEquals(FatigueTag(Muscle.MID_CHEST, FatigueLevel.HIGH), rows[0].fatigue)
        assertNull(rows[1].fatigue)
        assertFalse(fitsHalfLine(DayRow("Dip", "3 × 5", 0, 3, false, fatigue = rows[0].fatigue)))
        assertTrue(fitsHalfLine(DayRow("Dip", "3 × 5", 0, 3, false)))
        assertEquals("no tags is the card as it was", plannedRows(push.entries), plannedRows(push.entries, emptyMap()))
    }

    @Test
    fun `adjust choices make the override, a keep changes nothing and a swap with no stand-in is a keep`() {
        val swap = EntryAdjust.Swap(9, "Pike Push-up", "", 3, 6, null)
        val choices = mapOf(1L to AdjustChoice.KEEP, 2L to AdjustChoice.LIGHTEN, 3L to AdjustChoice.SWAP, 4L to AdjustChoice.SWAP)
        val override = adjustOverride(choices, mapOf(3L to swap))
        assertEquals(setOf(2L, 3L), override.entries.keys)
        assertEquals(2, changeCount(override))
        assertEquals(0, changeCount(adjustOverride(mapOf(1L to AdjustChoice.KEEP), emptyMap())))
    }
}
