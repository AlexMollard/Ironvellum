package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanFatigueTest {

    private val H = 3_600_000L
    private val UTC = ZoneId.of("UTC")
    private val SLOW = 24.0

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, min: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    private fun contribution(
        muscle: Muscle,
        dose: Double,
        name: String = "Back Squat",
        ageHours: Long = 0,
        now: Long,
        id: Long = 1,
    ) = FatigueContribution(muscle, id, now - ageHours * H, name, "", name.lowercase(), dose, 0.0, false, false, SLOW)

    private fun report(now: Long, vararg contributions: FatigueContribution) =
        FatigueReport(now, false, Muscle.entries.associateWith { m -> contributions.filter { it.muscle == m } })

    private fun day(scheduledDay: Int?, vararg entries: PlannedEntry) = PlannedPreset("Day", "", scheduledDay, entries.toList())

    private fun squats() = PlannedEntry("Back Squat", 3, 5, null)

    // Thursday 2026-10-22, 12:00 UTC.
    private val now = at(UTC, 2026, 10, 22, 12)

    @Test
    fun `rightNow is null when nothing is moderate, and for an empty report`() {
        assertNull(PlanFatigue.rightNow(report(now), UTC))
        assertNull(PlanFatigue.rightNow(report(now, contribution(Muscle.QUADS, 1.0, now = now)), UTC))
    }

    @Test
    fun `rightNow names the worst muscles with their top cause, at most three`() {
        val r = report(
            now,
            contribution(Muscle.QUADS, 5.5, now = now, ageHours = 2),
            contribution(Muscle.GLUTES, 3.0, now = now, ageHours = 2),
            contribution(Muscle.HAMSTRINGS, 2.5, now = now, ageHours = 2, name = "Romanian Deadlift"),
            contribution(Muscle.CALVES, 2.4, now = now, ageHours = 2),
        )
        val row = PlanFatigue.rightNow(r, UTC)!!
        assertEquals(3, row.items.size)
        assertEquals(listOf(Muscle.QUADS, Muscle.GLUTES, Muscle.HAMSTRINGS), row.items.map { it.muscle })
        assertEquals("Back Squat", row.items.first().cause.exerciseName)
        assertTrue(row.items.first().level >= row.items.last().level)
    }

    @Test
    fun `nextEightAm is strictly after now`() {
        val monMorning = at(UTC, 2026, 10, 19, 7)
        assertEquals(at(UTC, 2026, 10, 19, 8), PlanFatigue.nextEightAm(1, monMorning, UTC))
        assertEquals(at(UTC, 2026, 10, 26, 8), PlanFatigue.nextEightAm(1, at(UTC, 2026, 10, 19, 8), UTC))
        assertEquals(at(UTC, 2026, 10, 26, 8), PlanFatigue.nextEightAm(1, at(UTC, 2026, 10, 19, 14), UTC))
        // Wednesday 21st, asking for Sunday: that week's Sunday.
        assertEquals(at(UTC, 2026, 10, 25, 8), PlanFatigue.nextEightAm(7, at(UTC, 2026, 10, 21, 9), UTC))
    }

    @Test
    fun `eightAmOnDstChangeDay`() {
        val ny = ZoneId.of("America/New_York")
        // Spring forward Sunday 2026-03-08 and fall back Sunday 2026-11-01: 08:00 exists on both.
        val spring = PlanFatigue.nextEightAm(7, at(ny, 2026, 3, 6, 20), ny)
        assertEquals(ZonedDateTime.of(2026, 3, 8, 8, 0, 0, 0, ny).toInstant().toEpochMilli(), spring)
        val fall = PlanFatigue.nextEightAm(7, at(ny, 2026, 10, 30, 20), ny)
        assertEquals(ZonedDateTime.of(2026, 11, 1, 8, 0, 0, 0, ny).toInstant().toEpochMilli(), fall)
        // The offset differs on the two days, the wall-clock hour does not.
        assertEquals(8, java.time.Instant.ofEpochMilli(spring).atZone(ny).hour)
        assertEquals(8, java.time.Instant.ofEpochMilli(fall).atZone(ny).hour)
    }

    @Test
    fun `a day note appears when the muscle is still moderate at 08 00`() {
        // Quads at 6 residual, 24 h half-life: Friday 08:00 is 20 h away, so about 3.4 left: Moderate.
        val r = report(now, contribution(Muscle.QUADS, 6.0, now = now))
        val notes = PlanFatigue.dayNotes(day(5, squats()), r, UTC)
        assertEquals(1, notes.count { it.muscle == Muscle.QUADS })
        assertEquals(DayOfWeek.FRIDAY, notes.first().weekday)
        assertTrue(notes.first().level >= FatigueLevel.MODERATE)
    }

    @Test
    fun `a muscle that is light by then gets no note`() {
        val r = report(now, contribution(Muscle.QUADS, 3.0, now = now, ageHours = 60))
        assertTrue(PlanFatigue.dayNotes(day(5, squats()), r, UTC).isEmpty())
    }

    @Test
    fun `a muscle the day only touches lightly gets no note`() {
        // A calf raise barely assists the quads (share well under 0.5).
        val r = report(now, contribution(Muscle.QUADS, 6.0, now = now))
        assertTrue(PlanFatigue.dayNotes(day(5, PlannedEntry("Standing Calf Raise", 3, 10, null)), r, UTC).none { it.muscle == Muscle.QUADS })
    }

    @Test
    fun `an unscheduled day has no notes and at most two are given`() {
        val r = report(
            now,
            contribution(Muscle.QUADS, 6.0, now = now), contribution(Muscle.GLUTES, 6.0, now = now),
            contribution(Muscle.HAMSTRINGS, 6.0, now = now), contribution(Muscle.CALVES, 6.0, now = now),
        )
        assertTrue(PlanFatigue.dayNotes(day(null, squats()), r, UTC).isEmpty())
        assertTrue(PlanFatigue.dayNotes(day(5, squats(), PlannedEntry("Romanian Deadlift", 3, 8, null)), r, UTC).size <= 2)
    }

    @Test
    fun `reading the plan never changes it`() {
        val plan = listOf(day(1, squats()), day(4, PlannedEntry("Bench Press", 3, 8, null)))
        val before = plan.toList()
        val r = report(now, contribution(Muscle.QUADS, 6.0, now = now))
        plan.forEach { PlanFatigue.dayNotes(it, r, UTC) }
        assertEquals(before, plan)
        assertNotNull(PlanFatigue.rightNow(r, UTC))
    }
}
