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

    // ---- U10: one set ceiling

    @Test
    fun `sets stop at the generator's ceiling for the movement`() {
        val ceiling = ProgramRules.maxSetsPerEntry("Push-up")
        assertEquals(ceiling, entry(sets = ceiling).withSetsDelta(1).sets)
        assertEquals(1, entry(sets = 1).withSetsDelta(-1).sets)
        assertEquals(30, entry(reps = 30).withRepsDelta(1).reps)
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
}
