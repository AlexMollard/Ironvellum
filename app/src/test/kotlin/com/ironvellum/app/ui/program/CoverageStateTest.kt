package com.ironvellum.app.ui.program

import com.ironvellum.app.data.Seed
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Evidence
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.MuscleLoad
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.ProgramRequest
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.StrengthProfile
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.VolumeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage v2 in words: a muscle is not trained, below the minimum, covered, or heavy; the headline, the order
 * of the list and the heavy reason all read from that one rule. Heavy is a soft flag the lifter may dismiss.
 */
class CoverageStateTest {

    private fun load(weekly: Double, session: Double = weekly) = MuscleLoad(Muscle.LATS, weekly, session)

    @Test
    fun `the four states sit on the minimum and the heavy lines`() {
        assertEquals(CoverageState.NOT_TRAINED, stateOf(load(0.0)))
        assertEquals(CoverageState.BELOW_MINIMUM, stateOf(load(0.5)))
        assertEquals(CoverageState.BELOW_MINIMUM, stateOf(load(3.99)))
        assertEquals(CoverageState.COVERED, stateOf(load(4.0)))
        // A sum of 0.3 shares that drifts under four must still read covered.
        assertEquals(CoverageState.COVERED, stateOf(load(0.3 * 13 + 0.1)))
        assertEquals(CoverageState.COVERED, stateOf(load(20.0, session = 8.0)))
        assertEquals(CoverageState.HEAVY, stateOf(load(20.5, session = 8.0)))
    }

    @Test
    fun `a busy session alone makes a muscle heavy`() {
        assertEquals(CoverageState.HEAVY, stateOf(load(weekly = 12.0, session = 10.5)))
        assertEquals(CoverageState.COVERED, stateOf(load(weekly = 12.0, session = 10.0)))
    }

    @Test
    fun `a dismissed heavy flag reads covered and never hides a gap`() {
        assertEquals(CoverageState.COVERED, stateOf(load(24.0), dismissedHeavy = true))
        assertEquals(CoverageState.BELOW_MINIMUM, stateOf(load(2.0), dismissedHeavy = true))
        assertEquals(CoverageState.NOT_TRAINED, stateOf(load(0.0), dismissedHeavy = true))
    }

    @Test
    fun `a pooled week is judged on its weekly total only`() {
        // Twelve hard sets of lats logged over seven days is one ordinary week, not one 12-set session.
        val pooled = PlannedPreset("Last 7 days", "", null, listOf(PlannedEntry("Pull-up", 12, 8, null)))
        val lats = { m: Map<Muscle, MuscleLoad> -> m.getValue(Muscle.LATS) }
        assertEquals(CoverageState.HEAVY, stateOf(lats(loadsOf(listOf(pooled), pooled = false))))
        assertEquals(CoverageState.COVERED, stateOf(lats(loadsOf(listOf(pooled), pooled = true))))
    }

    private fun asLoads(weekly: Map<Muscle, Double>): Map<Muscle, MuscleLoad> =
        weekly.mapValues { (muscle, sets) -> MuscleLoad(muscle, sets, sets) }

    @Test
    fun `the headline counts heavy, below minimum and not trained in that order`() {
        val all = JUDGED_MUSCLES.associateWith { 6.0 }.toMutableMap()
        assertEquals("Every muscle covered", coverageHeadline(coverageTally(asLoads(all))))

        all[Muscle.FOREARMS] = 0.0
        all[Muscle.REAR_DELTS] = 3.0
        all[Muscle.CALVES] = 2.0
        val two = coverageTally(asLoads(all))
        assertEquals("3 muscles need more sets", coverageHeadline(two))
        assertEquals(3, two.gaps)

        all[Muscle.FRONT_DELTS] = 24.0
        all[Muscle.CALVES] = 6.0
        val heavy = coverageTally(asLoads(all))
        assertEquals("1 heavy · 2 muscles need more sets", coverageHeadline(heavy))
        assertEquals(CoverageState.BELOW_MINIMUM, heavy.lead)
    }

    @Test
    fun `heavy alone is a hint and not a gap`() {
        val all = JUDGED_MUSCLES.associateWith { 6.0 }.toMutableMap()
        all[Muscle.FRONT_DELTS] = 24.0
        val tally = coverageTally(asLoads(all))
        assertEquals(0, tally.gaps)
        assertTrue(!tally.allClear)
        assertEquals("1 heavy", coverageHeadline(tally))
        assertEquals(CoverageState.HEAVY, tally.lead)
        // Dismissed, it is clear again.
        val dismissed = coverageTally(asLoads(all), setOf(Muscle.FRONT_DELTS))
        assertTrue(dismissed.allClear)
        assertEquals("Every muscle covered", coverageHeadline(dismissed))
    }

    @Test
    fun `a muscle missing from the loads is not trained and the neck is never judged`() {
        val tally = coverageTally(emptyMap<Muscle, MuscleLoad>())
        assertEquals(JUDGED_MUSCLES.size, tally.notTrained.size)
        assertTrue(Muscle.NECK !in tally.states)
    }

    @Test
    fun `the list shows heavy first, then untrained, then the least trained below the minimum`() {
        val all = JUDGED_MUSCLES.associateWith { 6.0 }.toMutableMap()
        all[Muscle.FOREARMS] = 0.0
        all[Muscle.REAR_DELTS] = 3.0
        all[Muscle.CALVES] = 2.0
        all[Muscle.FRONT_DELTS] = 24.0
        all[Muscle.QUADS] = 22.0
        val l = asLoads(all)
        assertEquals(
            listOf(Muscle.FRONT_DELTS, Muscle.QUADS, Muscle.FOREARMS, Muscle.CALVES, Muscle.REAR_DELTS),
            attentionOrder(l, coverageTally(l)),
        )
    }

    @Test
    fun `hard sets read as a count with the right noun`() {
        assertEquals("0 hard sets", hardSetsLabel(0.0))
        assertEquals("1 hard set", hardSetsLabel(1.0))
        assertEquals("3.5 hard sets", hardSetsLabel(3.5))
        assertEquals("24 hard sets", hardSetsLabel(24.0))
    }

    @Test
    fun `heavy says which line it crossed and cites the paper behind it`() {
        val (week, weekPaper) = heavyReason(load(24.0, 8.0))
        assertTrue(week, "20 sets a week" in week && "not a limit" in week)
        assertEquals(Evidence.PELLAND_2026, weekPaper)
        val (session, sessionPaper) = heavyReason(load(12.0, 11.0))
        assertTrue(session, "10 hard sets in one rite" in session)
        assertEquals(Evidence.REMMERT_2025, sessionPaper)
        assertNotEquals(week, session)
    }

    // ---------------------------------------------------------------- against the generator

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
    fun `a standard full gym week the Forge builds is never short on the muscles it promises`() {
        for (focus in TrainingFocus.entries) for (days in 3..6) {
            val plan = ProgramGenerator.week(
                ProgramRequest(focus, VolumeLevel.STANDARD, Equipment.FULL_GYM, days),
                catalogue, StrengthProfile(),
            )
            val tally = coverageTally(plan.presets)
            // The generator promises the tracked muscles four hard sets and never heavy; the chip must agree.
            val tracked = ProgramRules.TRACKED.filter { tally.states[it] != CoverageState.COVERED }
            assertTrue("$focus/${days}d tracked not covered: $tracked", tracked.isEmpty())
            assertTrue("$focus/${days}d heavy: ${tally.heavy}", tally.heavy.isEmpty())
        }
    }
}
