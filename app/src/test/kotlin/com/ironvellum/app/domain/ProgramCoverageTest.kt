package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The effort-first coverage model: 4 hard sets covers a muscle, 20 a week or 10 a session is heavy. */
class ProgramCoverageTest {

    private fun entry(name: String, sets: Int) = PlannedEntry(name, sets, 8, null)

    private fun day(day: Int, vararg entries: PlannedEntry) =
        PlannedPreset("Day $day", "", day, entries.toList())

    private fun load(muscle: Muscle, vararg presets: PlannedPreset) =
        ProgramRules.coverage(presets.toList()).getValue(muscle)

    // ----------------------------------------------------------- coverage bands

    @Test
    fun `coverage bands are none, below four and covered`() {
        assertEquals(Coverage.NOT_TRAINED, load(Muscle.SIDE_DELTS, day(1, entry("Bench Press", 0))).coverage)
        assertEquals(Coverage.NOT_TRAINED, load(Muscle.CALVES, day(1, entry("Bench Press", 4))).coverage)
        for (sets in 1..3) {
            assertEquals(Coverage.BELOW_MINIMUM, load(Muscle.MID_CHEST, day(1, entry("Bench Press", sets))).coverage)
        }
        for (sets in 4..6) {
            assertEquals(Coverage.COVERED, load(Muscle.MID_CHEST, day(1, entry("Bench Press", sets))).coverage)
        }
    }

    @Test
    fun `the boundary is on fractional credit, 3_9 below and 4_0 covered`() {
        // Bench press credits the side delts 0.3 a set: 13 sets = 3.9.
        val almost = load(Muscle.SIDE_DELTS, day(1, entry("Bench Press", 13)))
        assertEquals(3.9, almost.weekly, 1e-9)
        assertEquals(Coverage.BELOW_MINIMUM, almost.coverage)
        assertEquals(Coverage.COVERED, load(Muscle.SIDE_DELTS, day(1, entry("Lateral Raise", 4))).coverage)
        // Lateral raise 2 sets (2.0) + bench 6 sets (1.8) = 3.8.
        assertEquals(
            Coverage.BELOW_MINIMUM,
            load(Muscle.SIDE_DELTS, day(1, entry("Lateral Raise", 2), entry("Bench Press", 6))).coverage,
        )
        // Floating-point drift must not push an exact 4.0 below the line (1.0 + 0.3 * 10).
        val exact = load(Muscle.SIDE_DELTS, day(1, entry("Lateral Raise", 1), entry("Bench Press", 10)))
        assertEquals(Coverage.COVERED, exact.coverage)
    }

    @Test
    fun `heavy week is strictly over twenty fractional sets`() {
        val at20 = load(Muscle.MID_CHEST, day(1, entry("Bench Press", 10)), day(3, entry("Bench Press", 10)))
        assertEquals(20.0, at20.weekly, 1e-9)
        assertFalse(at20.heavy)
        val at21 = load(
            Muscle.MID_CHEST,
            day(1, entry("Bench Press", 10)), day(3, entry("Bench Press", 10)), day(5, entry("Bench Press", 1)),
        )
        assertTrue(at21.heavy)
    }

    @Test
    fun `heavy session is strictly over ten in one day`() {
        val at10 = load(Muscle.MID_CHEST, day(1, entry("Bench Press", 10)))
        assertEquals(10.0, at10.maxSession, 1e-9)
        assertFalse(at10.heavy)
        val at11 = load(Muscle.MID_CHEST, day(1, entry("Bench Press", 11)))
        assertTrue(at11.heavy)
        // Twelve spread over two days is not a heavy session.
        val spread = load(Muscle.MID_CHEST, day(1, entry("Bench Press", 6)), day(3, entry("Bench Press", 6)))
        assertEquals(6.0, spread.maxSession, 1e-9)
        assertFalse(spread.heavy)
    }

    @Test
    fun `planned hard sets are sets times the muscle share`() {
        val planned = HardSets.planned(listOf(day(1, entry("Bench Press", 4)), day(3, entry("Lateral Raise", 3))))
        assertEquals(4.0, planned.getValue(Muscle.MID_CHEST), 1e-9)
        assertEquals(3.0 + 1.2, planned.getValue(Muscle.SIDE_DELTS), 1e-9)
    }

    // ---------------------------------------------------------------- setRange

    @Test
    fun `set range per volume level, compound and isolation`() {
        assertEquals(2..3, ProgramRules.setRange(VolumeLevel.LOW, compound = true))
        assertEquals(2..4, ProgramRules.setRange(VolumeLevel.STANDARD, compound = true))
        assertEquals(3..5, ProgramRules.setRange(VolumeLevel.HIGH, compound = true))
        assertEquals(2..2, ProgramRules.setRange(VolumeLevel.LOW, compound = false))
        assertEquals(2..3, ProgramRules.setRange(VolumeLevel.STANDARD, compound = false))
        assertEquals(3..4, ProgramRules.setRange(VolumeLevel.HIGH, compound = false))
    }

    @Test
    fun `starting sets are floor, midpoint and top, and a priority starts at the top`() {
        assertEquals(2, ProgramRules.startingSets(VolumeLevel.LOW, true, priority = false))
        assertEquals(3, ProgramRules.startingSets(VolumeLevel.STANDARD, true, priority = false))
        assertEquals(5, ProgramRules.startingSets(VolumeLevel.HIGH, true, priority = false))
        assertEquals(3, ProgramRules.startingSets(VolumeLevel.STANDARD, false, priority = false))
        for (volume in VolumeLevel.entries) for (compound in listOf(true, false)) {
            val range = ProgramRules.setRange(volume, compound)
            assertEquals(range.last, ProgramRules.startingSets(volume, compound, priority = true))
            assertTrue(ProgramRules.startingSets(volume, compound, priority = false) in range)
        }
    }

    // ------------------------------------------------------------- time budget

    @Test
    fun `session budget is the minutes in seconds and 75 keeps the old constant`() {
        assertEquals(ProgramRules.SESSION_BUDGET_SECONDS, ProgramRules.sessionBudgetSeconds(75))
        assertEquals(45 * 60, ProgramRules.sessionBudgetSeconds(45))
        assertEquals(90 * 60, ProgramRules.sessionBudgetSeconds(90))
    }

    @Test
    fun `movement and day minutes come from the set clock`() {
        val bench = entry("Bench Press", 3)
        val raise = entry("Lateral Raise", 2)
        val one = ProgramRules.setSeconds(TrainingFocus.GENERAL, "Bench Press", weighted = false)
        assertEquals(3 * one, ProgramRules.movementSeconds(bench))
        val seconds = ProgramRules.movementSeconds(bench) + ProgramRules.movementSeconds(raise)
        assertEquals((seconds + 59) / 60, ProgramRules.dayMinutes(day(1, bench, raise)))
    }

    @Test
    fun `rep windows per focus and kind of movement`() {
        assertEquals(6..10, ProgramRules.repWindow(TrainingFocus.MUSCLE, compound = true))
        assertEquals(10..15, ProgramRules.repWindow(TrainingFocus.MUSCLE, compound = false))
        assertEquals(3..6, ProgramRules.repWindow(TrainingFocus.STRENGTH, compound = true))
        assertEquals(8..12, ProgramRules.repWindow(TrainingFocus.STRENGTH, compound = false))
        assertEquals(5..8, ProgramRules.repWindow(TrainingFocus.GENERAL, compound = true))
        assertEquals(10..15, ProgramRules.repWindow(TrainingFocus.GENERAL, compound = false))
    }

    // -------------------------------------------------------------- prescription

    @Test
    fun `prescription line for a compound and an isolation`() {
        val compound = PlannedEntry(
            "Bench Press", 3, 8, null,
            setsMin = 2, setsMax = 4, effort = EffortCue.RIR_0_2, repLow = 6, repHigh = 10,
        )
        assertEquals("2-4 sets x 6-10 reps, 0-2 reps in reserve", Prescription.line(compound))
        val isolation = PlannedEntry(
            "Lateral Raise", 3, 12, null,
            setsMin = 3, setsMax = 3, effort = EffortCue.TO_FAILURE, repLow = 10, repHigh = 15,
        )
        assertEquals("3 sets x 10-15 reps, last set to failure", Prescription.line(isolation))
    }

    @Test
    fun `a plain entry keeps its sets and reps and defaults to 0-2 reps in reserve`() {
        val plain = PlannedEntry("Bench Press", 3, 8, null)
        assertEquals(3, plain.setsMin)
        assertEquals(3, plain.setsMax)
        assertEquals(8, plain.repLow)
        assertEquals(8, plain.repHigh)
        assertEquals(EffortCue.RIR_0_2, plain.effort)
        assertEquals("3 sets x 8 reps, 0-2 reps in reserve", Prescription.line(plain))
    }

    @Test
    fun `request defaults to a 75 minute session`() {
        val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment(fullGym = true))
        assertEquals(75, request.sessionMinutes)
    }

    // ------------------------------------------------------------------ isHard

    private fun hard(
        kind: SetKind = SetKind.WORKING,
        rpe: Int? = null,
        done: Boolean = true,
        reps: Int = 8,
        weightKg: Double? = 60.0,
        bodyweight: Boolean = false,
    ) = HardSets.isHard(kind, rpe, done, reps, weightKg, bodyweight)

    @Test
    fun `warm-ups and undone sets are never hard`() {
        assertFalse(hard(kind = SetKind.WARMUP))
        assertFalse(hard(kind = SetKind.WARMUP, rpe = 10))
        assertFalse(hard(done = false))
        assertFalse(hard(kind = SetKind.FAILURE, done = false))
    }

    @Test
    fun `with an rpe, 6 is not hard and 7 is`() {
        assertFalse(hard(rpe = 6))
        assertTrue(hard(rpe = 7))
        assertTrue(hard(rpe = 10))
        // The rpe decides even when the rep count alone would qualify.
        assertFalse(hard(rpe = 5, reps = 5))
    }

    @Test
    fun `an unrated working set is hard at 15 reps or fewer when loaded or bodyweight`() {
        assertTrue(hard(reps = 15))
        assertFalse(hard(reps = 16))
        assertFalse(hard(weightKg = null, bodyweight = false))
        assertFalse(hard(weightKg = 0.0, bodyweight = false))
        assertTrue(hard(weightKg = null, bodyweight = true))
        assertFalse(hard(reps = 0))
    }

    @Test
    fun `failure and rest-pause are always hard`() {
        assertTrue(hard(kind = SetKind.FAILURE, rpe = 5, reps = 30, weightKg = null))
        assertTrue(hard(kind = SetKind.REST_PAUSE, rpe = 5, reps = 30, weightKg = null))
    }

    @Test
    fun `a drop set is hard only when unrated or rated 7 and up`() {
        assertTrue(hard(kind = SetKind.DROP))
        assertTrue(hard(kind = SetKind.DROP, rpe = 7))
        assertFalse(hard(kind = SetKind.DROP, rpe = 6))
    }

    // ---------------------------------------------------------------- fromLog

    private val dayMs = 24L * 3600 * 1000
    private val now = 100L * dayMs

    private fun logged(
        name: String = "Bench Press",
        atMs: Long = now - dayMs,
        kind: SetKind = SetKind.WORKING,
        rpe: Int? = null,
        done: Boolean = true,
    ) = LoggedSet(name, "", 60.0, 8, rpe, kind, done, atMs)

    @Test
    fun `fromLog credits hard sets in the last seven days by muscle share`() {
        val credit = HardSets.fromLog(listOf(logged(), logged(), logged(name = "Lateral Raise")), now)
        assertEquals(2.0, credit.getValue(Muscle.MID_CHEST), 1e-9)
        assertEquals(1.0 + 2 * 0.3, credit.getValue(Muscle.SIDE_DELTS), 1e-9)
    }

    @Test
    fun `fromLog leaves out warm-ups, undone, easy and stale sets`() {
        val credit = HardSets.fromLog(
            listOf(
                logged(kind = SetKind.WARMUP),
                logged(done = false),
                logged(rpe = 6),
                logged(atMs = now - 8 * dayMs),
                logged(atMs = now + dayMs),
                logged(rpe = 8),
            ),
            now,
        )
        assertEquals(1.0, credit.getValue(Muscle.MID_CHEST), 1e-9)
    }

    @Test
    fun `fromLog seven day window includes six days and excludes exactly seven`() {
        assertEquals(1.0, HardSets.fromLog(listOf(logged(atMs = now - 6 * dayMs)), now).getValue(Muscle.MID_CHEST), 1e-9)
        assertTrue(HardSets.fromLog(listOf(logged(atMs = now - 7 * dayMs)), now).isEmpty())
    }
}
