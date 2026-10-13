package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The week-level applier behind Optimise rites: easing, gap and day-spreading changes all land through it, so
 * a kept subset gives one consistent week whatever order it was ticked in, and a change whose target went
 * away is skipped, never half applied.
 */
class ProgramWeekEditsTest {

    private fun entry(name: String, sets: Int) = PlannedEntry(name, sets, 8, null)

    private fun preset(name: String, day: Int?, vararg entries: PlannedEntry) = PlannedPreset(name, "", day, entries.toList())

    private val week = listOf(
        preset("Upper A", 1, entry("Bench Press", 4), entry("Dips", 3), entry("Barbell Row", 3)),
        preset("Lower A", 3, entry("Back Squat", 4)),
        preset("Upper B", 5, entry("Overhead Press", 3)),
    )

    private fun names(p: PlannedPreset) = p.entries.map { it.exerciseName }

    @Test
    fun `a trim sets the count of just that movement`() {
        val out = WeekEdits.apply(week, listOf(WeekEdit(0, ChangeOp.SetSets("Dips", 2))))
        assertEquals(listOf(4, 2, 3), out[0].entries.map { it.sets })
        assertEquals(week[1], out[1])
    }

    @Test
    fun `a swap replaces the movement where it stands`() {
        val out = WeekEdits.apply(week, listOf(WeekEdit(0, ChangeOp.SwapEntry("dips", entry("Push-up", 3)))))
        assertEquals(listOf("Bench Press", "Push-up", "Barbell Row"), names(out[0]))
    }

    @Test
    fun `a swap for a movement the rite already holds is skipped`() {
        val out = WeekEdits.apply(week, listOf(WeekEdit(0, ChangeOp.SwapEntry("Dips", entry("Bench Press", 3)))))
        assertEquals(week, out)
    }

    @Test
    fun `an addition joins the rite once`() {
        val add = WeekEdit(2, ChangeOp.AddEntry(entry("Face Pull", 3)))
        val once = WeekEdits.apply(week, listOf(add))
        assertEquals(listOf("Overhead Press", "Face Pull"), names(once[2]))
        assertEquals(once, WeekEdits.apply(once, listOf(add)))
    }

    @Test
    fun `a move takes the movement to the rite on that day`() {
        val out = WeekEdits.apply(week, listOf(WeekEdit(0, ChangeOp.MoveEntryToDay("Dips", 5))))
        assertEquals(listOf("Bench Press", "Barbell Row"), names(out[0]))
        assertEquals(listOf("Overhead Press", "Dips"), names(out[2]))
    }

    @Test
    fun `a move to a day with no rite, or that would empty the rite, is skipped`() {
        assertEquals(week, WeekEdits.apply(week, listOf(WeekEdit(0, ChangeOp.MoveEntryToDay("Dips", 6)))))
        assertEquals(week, WeekEdits.apply(week, listOf(WeekEdit(1, ChangeOp.MoveEntryToDay("Back Squat", 5)))))
        assertEquals(week, WeekEdits.apply(week, listOf(WeekEdit(0, ChangeOp.MoveEntryToDay("Not There", 5)))))
    }

    @Test
    fun `a trim then a swap of one movement keeps the swap, and the reverse order drops the trim`() {
        val trim = WeekEdit(0, ChangeOp.SetSets("Dips", 2))
        val swap = WeekEdit(0, ChangeOp.SwapEntry("Dips", entry("Push-up", 3)))
        val trimFirst = WeekEdits.apply(week, listOf(trim, swap))
        val swapFirst = WeekEdits.apply(week, listOf(swap, trim))
        // The swap brings its own prescription either way; the trim never lands on the new movement.
        assertEquals(listOf("Bench Press", "Push-up", "Barbell Row"), names(trimFirst[0]))
        assertEquals(trimFirst, swapFirst)
        assertEquals(3, swapFirst[0].entries[1].sets)
    }

    @Test
    fun `an index past the week changes nothing`() {
        assertEquals(week, WeekEdits.apply(week, listOf(WeekEdit(9, ChangeOp.SetSets("Dips", 1)))))
    }

    @Test
    fun `the rite is found by name`() {
        assertEquals(1, WeekEdits.indexOf(week, "Lower A"))
        assertEquals(-1, WeekEdits.indexOf(week, "Nope"))
    }

    @Test
    fun `tight pairs name a muscle hit hard under 48 hours apart`() {
        val push = listOf(
            preset("Push A", 1, entry("Bench Press", 6)),
            preset("Push B", 2, entry("Bench Press", 6)),
        )
        val pairs = Recovery.tightPairs(push)
        assertTrue(pairs.isNotEmpty())
        assertTrue(pairs.all { it.hours == 24 && it.dayA == 1 && it.dayB == 2 })
        assertTrue(pairs.any { it.muscle == Muscle.MID_CHEST })
        // Spread, the same week has none left.
        assertTrue(Recovery.tightPairs(push.mapIndexed { i, p -> p.copy(scheduledDay = if (i == 0) 1 else 4) }).isEmpty())
        assertEquals(Recovery.tightPairs(push).sumOf { (48 - it.hours) / 24.0 }, Recovery.spacingPenalty(push), 1e-9)
    }

    // ---- the ranked movements a gap offers, against the real catalogue

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    @Test
    fun `a short muscle with no rite named still gets ranked movements that train it`() {
        val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, 4)
        val pressOnly = listOf(preset("Push A", 1, entry("Bench Press", 4)))
        val loads = ProgramRules.coverage(pressOnly)
        val short = ProgramRules.TRACKED.filter { (loads[it]?.weekly ?: 0.0) < ProgramRules.COVERED_MIN }
        assertTrue(short.isNotEmpty())
        var withOptions = 0
        for (muscle in short) {
            val ranked = ProgramGenerator.suggest(request, Slot.ShortMuscle(muscle, null), pressOnly, catalogue, StrengthProfile())
            assertTrue("$muscle: at most three", ranked.options.size <= 3)
            if (ranked.options.isNotEmpty()) withOptions++
            for (candidate in ranked.options) {
                assertTrue("$muscle: ${candidate.exercise.name} must train it", (candidate.coverageDelta[muscle] ?: 0.0) > 0.0)
            }
        }
        assertTrue("most short muscles offer a movement ($withOptions of ${short.size})", withOptions >= short.size / 2)
    }
}
