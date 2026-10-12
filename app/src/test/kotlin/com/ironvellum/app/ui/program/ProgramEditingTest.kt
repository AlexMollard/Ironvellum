package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.RoutinePlan
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.domain.WorkoutPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The edits first-run setup and the Forge share, and the rules that keep an added week off a saved cycle. */
class ProgramEditingTest {

    private fun entry(name: String = "Push-up", sets: Int = 3, reps: Int = 10) = PlannedEntry(name, sets, reps, null)
    private fun rite(name: String, day: Int?, vararg entries: PlannedEntry) =
        PlannedPreset(name, "", day, entries.toList().ifEmpty { listOf(entry()) })

    // ---- U4: an added week avoids the days the cycle already uses

    @Test
    fun `a week whose days are free is left as it is`() {
        val plan = RoutinePlan(listOf(rite("A", 1), rite("B", 3)))
        assertSame(plan, plan.avoidingDays(setOf(2, 4)))
    }

    @Test
    fun `a colliding rite takes the next free weekday after its own`() {
        val plan = RoutinePlan(listOf(rite("A", 1), rite("B", 3)))
        val placed = plan.avoidingDays(setOf(1, 2))!!
        // Monday is taken and so is Tuesday, so A lands on Wednesday; that is B's own day, so B moves on to Thursday.
        assertEquals(listOf(4, 3), placed.presets.map { it.scheduledDay })
    }

    @Test
    fun `a rite that fits keeps its day even when another one collides`() {
        val plan = RoutinePlan(listOf(rite("A", 1), rite("B", 2)))
        val placed = plan.avoidingDays(setOf(1))!!
        assertEquals(listOf(3, 2), placed.presets.map { it.scheduledDay })
    }

    @Test
    fun `days wrap past Sunday`() {
        val plan = RoutinePlan(listOf(rite("A", 7)))
        assertEquals(listOf(1), plan.avoidingDays(setOf(7))!!.presets.map { it.scheduledDay })
    }

    @Test
    fun `no free day left is a refusal, never a collision`() {
        val plan = RoutinePlan(listOf(rite("A", 1), rite("B", 2)))
        assertNull(plan.avoidingDays(setOf(1, 2, 3, 4, 5, 6)))
    }

    @Test
    fun `unscheduled rites stay unscheduled and take no day`() {
        val plan = RoutinePlan(listOf(rite("A", null), rite("B", 1)))
        assertEquals(listOf(null, 2), plan.avoidingDays(setOf(1))!!.presets.map { it.scheduledDay })
    }

    // ---- U13: the same rule as the saved Weekly Coverage page

    @Test
    fun `only scheduled rites count, unless none is scheduled`() {
        val scheduled = rite("A", 1)
        val loose = rite("B", null)
        assertEquals(listOf(scheduled), scheduledRoutine(listOf(scheduled, loose)) { it.scheduledDay })
        assertEquals(listOf(loose), scheduledRoutine(listOf(loose)) { it.scheduledDay })
    }

    // ---- U17: stale indices are a no-op, not a crash

    @Test
    fun `an edit or removal past the end of the plan changes nothing`() {
        val plan = RoutinePlan(listOf(rite("A", 1, entry("Push-up"), entry("Pull-up"))))
        assertNull(plan.withEntryEdited(1, 0) { it })
        assertNull(plan.withEntryEdited(0, 2) { it })
        assertNull(plan.withEntryRemoved(1, 0))
        assertNull(plan.withEntryRemoved(0, 2))
        assertNull(plan.withEntryRemoved(0, -1))
    }

    @Test
    fun `removing the last entry drops its day`() {
        val plan = RoutinePlan(listOf(rite("A", 1, entry()), rite("B", 2, entry())))
        assertEquals(listOf("B"), plan.withEntryRemoved(0, 0)!!.presets.map { it.name })
    }

    @Test
    fun `an edit lands on the entry it names`() {
        val plan = RoutinePlan(listOf(rite("A", 1, entry("Push-up"), entry("Pull-up"))))
        val edited = plan.withEntryEdited(0, 1) { it.withSetsDelta(1) }!!
        assertEquals(listOf(3, 4), edited.presets[0].entries.map { it.sets })
    }

    // ---- U10: the stepper has a sanity range and no dose cap

    @Test
    fun `hard sets step through one to ten and no further`() {
        assertEquals(10, entry(sets = 9).withSetsDelta(1).sets)
        assertEquals(10, entry(sets = 10).withSetsDelta(1).sets)
        assertEquals(1, entry(sets = 1).withSetsDelta(-1).sets)
        // The generator's own stopping point is a suggestion: a lifter may go past it.
        val stop = ProgramRules.maxSetsPerEntry("Push-up")
        assertEquals(stop + 1, entry(sets = stop).withSetsDelta(1).sets)
        assertEquals(30, entry(reps = 30).withRepsDelta(1).reps)
    }

    @Test
    fun `load steps by the unit and stops at the lifter's own dumbbell`() {
        val loaded = PlannedEntry("Dumbbell Shoulder Press", 3, 10, 17.5)
        assertEquals(20.0, loaded.withLoadDelta(1, 2.5, capKg = 20.0).targetWeightKg!!, 1e-9)
        assertEquals(20.0, loaded.withLoadDelta(1, 2.5, capKg = 20.0).withLoadDelta(1, 2.5, capKg = 20.0).targetWeightKg!!, 1e-9)
        assertEquals(15.0, loaded.withLoadDelta(-1, 2.5).targetWeightKg!!, 1e-9)
        // Never below one step, and a bodyweight movement has no load to step.
        assertEquals(2.5, PlannedEntry("Curl", 3, 10, 2.5).withLoadDelta(-1, 2.5).targetWeightKg!!, 1e-9)
        assertNull(entry().withLoadDelta(1, 2.5).targetWeightKg)
    }

    // ---- U11: one progression rule

    @Test
    fun `only strength and muscle goals move the progression mode`() {
        assertEquals(TrainingMode.STRENGTH, progressionModeFor(TrainingFocus.STRENGTH))
        assertEquals(TrainingMode.HYPERTROPHY, progressionModeFor(TrainingFocus.MUSCLE))
        assertNull(progressionModeFor(TrainingFocus.SKILL))
        assertNull(progressionModeFor(TrainingFocus.GENERAL))
    }

    // ---- U4 coverage: adding counts the saved cycle too

    @Test
    fun `a proposed week joins the saved cycle in the coverage read`() {
        val saved = listOf(WorkoutPreset(id = 7, name = "Mine", scheduledDay = 1))
        val proposed = listOf(rite("New", 3))
        val names = proposalCoveragePresets("week", proposed, null, saved, null).map { it.name }
        assertEquals(listOf("New", "Mine"), names)
        assertEquals(listOf("New", "Mine"), proposalCoveragePresets("template", proposed, null, saved, null).map { it.name })
    }

    @Test
    fun `an unscheduled saved rite does not count once a day is scheduled`() {
        val saved = listOf(WorkoutPreset(id = 7, name = "Loose", scheduledDay = null))
        val names = proposalCoveragePresets("week", listOf(rite("New", 3)), null, saved, null).map { it.name }
        assertEquals(listOf("New"), names)
    }

    @Test
    fun `a tempered rite stands in for the original`() {
        val saved = listOf(
            WorkoutPreset(id = 1, name = "Old", scheduledDay = 1),
            WorkoutPreset(id = 2, name = "Other", scheduledDay = 2),
        )
        val names = proposalCoveragePresets("improve", null, rite("Better", 1), saved, 1).map { it.name }
        assertEquals(listOf("Better", "Other"), names)
    }

    // ---- what a discard would lose

    private fun plan(vararg entries: PlannedEntry) = RoutinePlan(listOf(rite("Upper A", 1, *entries)))

    @Test
    fun `an untouched proposal has nothing to lose`() {
        val forged = plan(entry("Bench Press"), entry("Row"))
        assertEquals(PlanEdits(), planEdits(forged, forged))
        assertEquals(false, planEdits(forged, forged).any)
    }

    @Test
    fun `swaps, set counts and removals are counted apart`() {
        val forged = plan(entry("Bench Press", 3), entry("Row", 3), entry("Curl", 2), entry("Raise", 2), entry("Dip", 3))
        val edited = plan(
            entry("Incline Press", 3),
            entry("Row", 4),
            entry("Curl", 3),
            entry("Dip", 3),
        )
        val edits = planEdits(forged, edited)
        // Bench Press left and Incline Press arrived: one swap. Raise left with nothing in its place: one removal.
        assertEquals(PlanEdits(swapped = 1, setsChanged = 2, removed = 1), edits)
        assertEquals(listOf("1 movement swapped", "2 set counts changed", "1 movement removed"), edits.lines())
    }

    @Test
    fun `an added movement and a changed load are counted`() {
        val forged = plan(PlannedEntry("Bench Press", 3, 8, 60.0))
        val edited = plan(PlannedEntry("Bench Press", 3, 8, 62.5), entry("Dip"))
        assertEquals(PlanEdits(loadsChanged = 1, added = 1), planEdits(forged, edited))
        assertEquals(listOf("1 load changed", "1 movement added"), planEdits(forged, edited).lines())
    }

    @Test
    fun `a rite removed with its last movement counts its movements as removed`() {
        val forged = RoutinePlan(listOf(rite("A", 1, entry("Push-up")), rite("B", 2, entry("Pull-up"), entry("Row"))))
        val edited = RoutinePlan(listOf(rite("A", 1, entry("Push-up"))))
        assertEquals(PlanEdits(removed = 2), planEdits(forged, edited))
    }

    @Test
    fun `no proposal on either side is no edits`() {
        assertEquals(PlanEdits(), planEdits(null, plan(entry())))
        assertEquals(PlanEdits(), planEdits(plan(entry()), null))
    }

    // ---- the days sheet

    private fun saved(name: String, day: Int?) = WorkoutPreset(id = name.hashCode().toLong(), name = name, scheduledDay = day)

    @Test
    fun `free days read free and a taken day says who holds it and where it moves`() {
        val week = RoutinePlan(listOf(rite("Upper A", 1), rite("Lower A", 2), rite("Upper B", 4), rite("Lower B", 5)))
        val cycle = listOf(saved("Push", 1), saved("Pull", 3), saved("Legs", 5))
        val conflict = dayConflict(week, cycle)
        assertEquals(true, conflict.hasClash)
        assertEquals(2, conflict.clashes)
        assertEquals(
            listOf(
                DayPlacement("Upper A", 1, "Push", 6),
                DayPlacement("Lower A", 2, null, null),
                DayPlacement("Upper B", 4, null, null),
                DayPlacement("Lower B", 5, "Legs", 7),
            ),
            conflict.placements,
        )
        assertEquals(true, conflict.fitsAround)
    }

    @Test
    fun `a week that does not collide shows no sheet`() {
        val week = RoutinePlan(listOf(rite("A", 2), rite("B", 4)))
        assertEquals(false, dayConflict(week, listOf(saved("Mine", 1))).hasClash)
        // An unscheduled saved rite holds no day.
        assertEquals(false, dayConflict(week, listOf(saved("Loose", null))).hasClash)
    }

    @Test
    fun `too few free days is said, not offered`() {
        val week = RoutinePlan(listOf(rite("A", 1), rite("B", 2)))
        val cycle = (1..6).map { saved("R$it", it) }
        val conflict = dayConflict(week, cycle)
        assertEquals(true, conflict.hasClash)
        assertEquals(false, conflict.fitsAround)
        assertEquals(listOf(null, null), conflict.placements.map { it.movedTo })
    }
}
