package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recovery-aware scheduling: a muscle hit hard (2+ fractional sets) wants 48
 * hours before it is hit hard again. The rule only ever penalises, never
 * refuses, and the day pass moves nothing but day numbers between sessions
 * of the same role.
 */
class ProgramSchedulingTest {

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    private val strength = ProgramRules.strengthProfile(
        listOf(LoggedLift("Bench Press", 75.0, 10), LoggedLift("Back Squat", 80.0, 10)),
    )

    private fun entry(name: String, sets: Int) = PlannedEntry(name, sets, 8, null)

    private fun preset(name: String, day: Int?, vararg entries: PlannedEntry) =
        PlannedPreset(name, "", day, entries.toList())

    private val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM)

    // ----------------------------------------------------------------- gaps

    @Test
    fun `gap is 24 hours per ISO day and wraps around the week`() {
        assertEquals(24, Recovery.gapHours(1, 2))
        assertEquals(48, Recovery.gapHours(1, 3))
        assertEquals(24, Recovery.gapHours(7, 1))
        assertEquals(24, Recovery.gapHours(1, 7))
        assertEquals(72, Recovery.gapHours(1, 5))
    }

    // -------------------------------------------------------------- penalty

    @Test
    fun `two hard days 24 hours apart cost one point per shared muscle`() {
        val week = listOf(
            preset("A", 1, entry("Leg Extension", 3)),
            preset("B", 2, entry("Leg Extension", 3)),
        )
        assertEquals(1.0, Recovery.spacingPenalty(week), 1e-9)
    }

    @Test
    fun `48 hours apart costs nothing`() {
        val week = listOf(
            preset("A", 1, entry("Leg Extension", 3)),
            preset("B", 3, entry("Leg Extension", 3)),
        )
        assertEquals(0.0, Recovery.spacingPenalty(week), 1e-9)
    }

    @Test
    fun `Sunday into Monday is adjacent`() {
        val week = listOf(
            preset("A", 7, entry("Leg Extension", 3)),
            preset("B", 1, entry("Leg Extension", 3)),
        )
        assertEquals(1.0, Recovery.spacingPenalty(week), 1e-9)
    }

    @Test
    fun `a day under two fractional sets is not hard`() {
        val week = listOf(
            preset("A", 1, entry("Leg Extension", 1)),
            preset("B", 2, entry("Leg Extension", 3)),
        )
        assertEquals(0.0, Recovery.spacingPenalty(week), 1e-9)
    }

    // ------------------------------------------------------------ best days

    private val lowerUpperWeek = listOf(
        preset("Upper A", 1, entry("Bench Press", 4)),
        preset("Lower A", 2, entry("Back Squat", 4)),
        preset("Upper B", 3, entry("Bench Press", 4)),
    )

    @Test
    fun `a spaced day beats an adjacent one and is tagged for recovery`() {
        val ranked = Recovery.bestFitDays(entry("Leg Extension", 3), lowerUpperWeek, request)
        assertTrue(ranked.options.first().tags.any { it.reason == Reason.RECOVERY_SPACED })
        // Beside the squat day the quads would be hit hard on back-to-back days.
        assertEquals(listOf(2, 1, 3), ranked.options.map { it.day })
    }

    @Test
    fun `equal penalties go to the lowest day`() {
        val week = listOf(
            preset("Upper A", 1, entry("Bench Press", 4)),
            preset("Upper B", 3, entry("Bench Press", 4)),
            preset("Upper C", 5, entry("Bench Press", 4)),
        )
        val ranked = Recovery.bestFitDays(entry("Leg Extension", 3), week, request)
        assertEquals(listOf(1, 3, 5), ranked.options.map { it.day })
    }

    @Test
    fun `a day already over the session clock is penalised`() {
        val week = listOf(
            preset("Upper A", 1, entry("Bench Press", 8), entry("Barbell Row", 8), entry("Back Squat", 8)),
            preset("Upper B", 3, entry("Bench Press", 2)),
        )
        val ranked = Recovery.bestFitDays(entry("Lateral Raise", 3), week, request.copy(sessionMinutes = 45))
        assertEquals(3, ranked.options.first().day)
        assertTrue(ranked.options.first().tags.any { it.reason == Reason.FITS_DAY_LENGTH })
    }

    @Test
    fun `an entry never offers the day it already sits on`() {
        val ranked = Recovery.bestFitDays(entry("Back Squat", 4), lowerUpperWeek, request)
        assertTrue(ranked.options.none { it.day == 2 })
    }

    // ------------------------------------------------------------- spacing

    @Test
    fun `spaceDays swaps same-role days to remove an adjacency`() {
        val week = listOf(
            preset("Full Body A", 1, entry("Leg Extension", 3)),
            preset("Full Body B", 2, entry("Leg Extension", 3)),
            preset("Full Body C", 3, entry("Bench Press", 3)),
        )
        val spaced = Recovery.spaceDays(week)
        assertEquals(0.0, Recovery.spacingPenalty(spaced), 1e-9)
        assertEquals(setOf(1, 2, 3), spaced.map { it.scheduledDay }.toSet())
        assertEquals(week.map { it.name to it.entries }.toSet(), spaced.map { it.name to it.entries }.toSet())
    }

    @Test
    fun `spaceDays never moves a day across roles`() {
        val week = listOf(
            preset("Lower A", 1, entry("Leg Extension", 3)),
            preset("Upper A", 2, entry("Leg Extension", 3)),
        )
        assertEquals(week, Recovery.spaceDays(week))
    }

    @Test
    fun `spaceDays is deterministic and leaves unscheduled sessions alone`() {
        val week = listOf(
            preset("Full Body A", 1, entry("Leg Extension", 3)),
            preset("Full Body B", 2, entry("Leg Extension", 3)),
            preset("Full Body C", 3, entry("Bench Press", 3)),
            preset("Full Body D", null, entry("Bench Press", 3)),
        )
        val once = Recovery.spaceDays(week)
        assertEquals(once, Recovery.spaceDays(week))
        assertNull(once.single { it.name == "Full Body D" }.scheduledDay)
    }

    @Test
    fun `an unavoidable adjacency keeps every session and the best penalty`() {
        val week = listOf(
            preset("Lower A", 1, entry("Leg Extension", 3)),
            preset("Lower B", 2, entry("Leg Extension", 3)),
            preset("Lower C", 3, entry("Leg Extension", 3)),
        )
        val spaced = Recovery.spaceDays(week)
        assertEquals(3, spaced.size)
        assertEquals(Recovery.spacingPenalty(week), Recovery.spacingPenalty(spaced), 1e-9)
        assertEquals(setOf(1, 2, 3), spaced.map { it.scheduledDay }.toSet())
    }

    // -------------------------------------------------------- generated weeks

    private fun week(split: TrainingSplit, days: Int, focus: TrainingFocus = TrainingFocus.MUSCLE) =
        ProgramGenerator.week(
            ProgramRequest(focus, VolumeLevel.STANDARD, Equipment.FULL_GYM, days, split = split, maxExercises = 8),
            catalogue, strength,
        )

    @Test
    fun `a two day week is spread at least 48 hours apart`() {
        val plan = week(TrainingSplit.FULL_BODY, 2)
        val days = plan.presets.map { it.scheduledDay!! }
        assertTrue(Recovery.gapHours(days[0], days[1]) >= 48)
        assertEquals(0.0, Recovery.spacingPenalty(plan.presets), 1e-9)
    }

    @Test
    fun `a six day push pull legs week has no muscle hit hard under 48 hours`() {
        val plan = week(TrainingSplit.PUSH_PULL_LEGS, 6)
        assertEquals(6, plan.presets.size)
        assertEquals(0.0, Recovery.spacingPenalty(plan.presets), 1e-9)
    }

    @Test
    fun `a generated week is already as spaced as its day numbers allow`() {
        for ((split, days) in TrainingSplit.OPTIONS) {
            val plan = week(split, days)
            val again = Recovery.spaceDays(plan.presets)
            assertEquals("$split/$days", Recovery.spacingPenalty(plan.presets), Recovery.spacingPenalty(again), 1e-9)
            assertEquals("$split/$days", plan.presets.map { it.scheduledDay }.toSet(), again.map { it.scheduledDay }.toSet())
        }
    }

    @Test
    fun `week keeps each role on its own days`() {
        val plan = week(TrainingSplit.UPPER_LOWER, 4)
        val roles = plan.presets.groupBy { it.name.substringBeforeLast(' ') }
        assertEquals(setOf(1, 4), roles.getValue("Upper").map { it.scheduledDay }.toSet())
        assertEquals(setOf(2, 5), roles.getValue("Lower").map { it.scheduledDay }.toSet())
    }

    @Test
    fun `the facade ranks days like the scheduler`() {
        assertEquals(
            Recovery.bestFitDays(entry("Leg Extension", 3), lowerUpperWeek, request),
            ProgramGenerator.bestFitDays(entry("Leg Extension", 3), lowerUpperWeek, request),
        )
    }
}
