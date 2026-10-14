package com.ironvellum.app.domain

import com.ironvellum.app.domain.Progression.Lever
import com.ironvellum.app.ui.program.withLoadDelta
import com.ironvellum.app.ui.program.wornFor
import com.ironvellum.app.ui.settings.SettingsSummaries
import com.ironvellum.app.ui.train.stepDownKg
import com.ironvellum.app.ui.train.stepUpKg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vest and dip belt: the step function, and every place that steps a worn load through it. */
class LoadKitTest {

    private val kg = LoadUnit.KG
    private val lb = LoadUnit.LB

    private val vest = LoadKit(vestMaxKg = 30.0, vestBlockKg = 1.0)
    private val vestAndBelt = vest.copy(dipBelt = true)
    private val none = LoadKit.NONE

    private fun sets(weight: Double, reps: Int, count: Int = 3) = List(count) { Progression.Attempt(weight, reps) }

    private fun weightedPullUp(
        mode: TrainingMode,
        weight: Double,
        reps: Int,
        kit: LoadKit?,
        unit: LoadUnit = kg,
        targetReps: Int = if (mode == TrainingMode.STRENGTH) 5 else 8,
    ) = Progression.fromSets(
        mode, targetReps, 3, sets(weight, reps), "PULL", "Weighted Pull-up", Lever.LOAD, unit, kit,
    )

    // ---- the step function

    @Test
    fun `under the vest max the step is the block`() {
        assertEquals(19.0, vest.nextKg(18.0, kg)!!, 1e-9)
        assertEquals(1.0, vest.nextKg(0.0, kg)!!, 1e-9)
        assertEquals(18.0, vest.previousKg(19.0, kg)!!, 1e-9)
        assertNull(vest.previousKg(1.0, kg))
        assertEquals(19.5, vest.copy(vestBlockKg = 0.5).nextKg(19.0, kg)!!, 1e-9)
        assertEquals(20.0, vest.copy(vestBlockKg = 2.0).nextKg(18.0, kg)!!, 1e-9)
        assertEquals(20.0, vest.copy(vestBlockKg = 2.5).nextKg(17.5, kg)!!, 1e-9)
    }

    @Test
    fun `the last block lands on the vest max, never past it`() {
        assertEquals(30.0, vest.nextKg(29.5, kg)!!, 1e-9)
        assertEquals(30.0, vest.copy(vestBlockKg = 2.5).nextKg(28.0, kg)!!, 1e-9)
    }

    @Test
    fun `at the vest max a belt goes on in 2 point 5 kg plates and no belt has no next load`() {
        assertEquals(32.5, vestAndBelt.nextKg(30.0, kg)!!, 1e-9)
        assertEquals(32.5, vestAndBelt.nextKg(31.0, kg)!!, 1e-9)
        assertEquals(35.0, vestAndBelt.nextKg(32.5, kg)!!, 1e-9)
        assertNull(vest.nextKg(30.0, kg))
        assertNull(vest.nextKg(35.0, kg))
    }

    @Test
    fun `stepping back down from the belt stops at the vest max, then walks the blocks`() {
        assertEquals(30.0, vestAndBelt.previousKg(32.5, kg)!!, 1e-9)
        assertEquals(30.0, vestAndBelt.previousKg(31.0, kg)!!, 1e-9)
        assertEquals(29.0, vestAndBelt.previousKg(30.0, kg)!!, 1e-9)
    }

    @Test
    fun `a belt on its own does not change the step`() {
        val beltOnly = LoadKit(dipBelt = true)
        assertFalse(beltOnly.hasVest)
        assertNull(WornLoad.forLift(beltOnly, "Weighted Pull-up", Lever.LOAD))
    }

    @Test
    fun `deload and e1rm rounding use the block under the vest max and the plate past it`() {
        assertEquals(19.0, vest.roundKg(18.9, kg), 1e-9)
        assertEquals(1.0, vest.roundKg(0.2, kg), 1e-9)
        assertEquals(32.5, vestAndBelt.roundKg(32.4, kg), 1e-9)
        assertEquals(24.0, vest.floorKg(24.9), 1e-9)
    }

    @Test
    fun `in pounds the grid is built on the displayed figure`() {
        // A 1 kg block is 2.2 lb: it reads as 2 lb, so 40 lb goes to 42 lb and never to 42.2.
        val from = Loads.toKg(40.0, lb)
        val next = vest.nextKg(from, lb)!!
        assertEquals(42.0, Loads.snap(next, lb), 1e-9)
        assertEquals(40.0, Loads.snap(vest.previousKg(next, lb)!!, lb), 1e-9)
        // Past the vest a belt plate is 5 lb.
        val top = Loads.toKg(66.0, lb)
        val topKit = vestAndBelt.copy(vestMaxKg = top)
        assertEquals(70.0, Loads.snap(topKit.nextKg(top, lb)!!, lb), 1e-9)
        assertEquals(44.0, Loads.snap(vest.roundKg(Loads.toKg(43.3, lb), lb), lb), 1e-9)
    }

    @Test
    fun `the picker steppers walk the unit and stay inside their bounds`() {
        assertEquals(31.0, LoadKit.stepVestMax(30.0, 1, kg), 1e-9)
        assertEquals(1.0, LoadKit.stepVestMax(1.0, -1, kg), 1e-9)
        assertEquals(100.0, LoadKit.stepVestMax(100.0, 1, kg), 1e-9)
        assertEquals(70.0, Loads.snap(LoadKit.stepVestMax(Loads.toKg(68.0, lb), 1, lb), lb), 1e-9)
        assertEquals(1.5, LoadKit.stepVestBlock(1.0, 1, kg), 1e-9)
        assertEquals(0.5, LoadKit.stepVestBlock(0.5, -1, kg), 1e-9)
        assertEquals(5.0, LoadKit.stepVestBlock(5.0, 1, kg), 1e-9)
        assertEquals(3.0, Loads.snap(LoadKit.stepVestBlock(Loads.toKg(2.0, lb), 1, lb), lb), 1e-9)
    }

    // ---- which lifts take the kit

    @Test
    fun `the bodyweight lifts and the weighted tree movements take the kit, loaded lifts do not`() {
        for (name in listOf("Weighted Pull-up", "Weighted Dip")) assertNotNull(name, WornLoad.forLift(vest, name, weighted = null))
        for (name in listOf("Pull-up", "Chin-up", "Dip", "Push-up")) {
            assertNotNull(name, WornLoad.forLift(vest, name, weighted = false))
        }
        for (name in listOf("Bench Press", "Back Squat", "Dumbbell Curl")) {
            assertNull(name, WornLoad.forLift(vest, name, weighted = true))
        }
        // Only the name is known: the unweighted lifts cannot be told apart, so they step as before.
        assertNull(WornLoad.forLift(vest, "Pull-up", weighted = null))
        assertNull(WornLoad.forLift(none, "Pull-up", weighted = false))
        assertNull(WornLoad.forLift(null, "Pull-up", weighted = false))
    }

    // ---- progression outcomes

    @Test
    fun `under the vest max a cleared trial adds one block`() {
        val rec = weightedPullUp(TrainingMode.STRENGTH, 18.0, 5, vest)
        assertEquals(19.0, rec.weightKg!!, 1e-9)
        assertEquals(3, rec.reps)
        assertEquals("Target cleared — add 1 kg, drop to 3 reps", rec.reason)
        val hyper = weightedPullUp(TrainingMode.HYPERTROPHY, 18.0, 12, vest)
        assertEquals(19.0, hyper.weightKg!!, 1e-9)
        assertEquals("Rep ceiling reached — add 1 kg, back to 8 reps", hyper.reason)
    }

    @Test
    fun `with no kit the same trial steps as it always did`() {
        assertEquals(20.0, weightedPullUp(TrainingMode.STRENGTH, 18.0, 5, null).weightKg!!, 1e-9)
        assertEquals(20.0, weightedPullUp(TrainingMode.STRENGTH, 18.0, 5, none).weightKg!!, 1e-9)
        assertEquals(20.0, weightedPullUp(TrainingMode.STRENGTH, 18.0, 5, LoadKit(dipBelt = true)).weightKg!!, 1e-9)
    }

    @Test
    fun `a barbell lift ignores the vest`() {
        val bench = { kit: LoadKit? ->
            Progression.fromSets(TrainingMode.STRENGTH, 5, 3, sets(60.0, 5), "PUSH", "Bench Press", Lever.LOAD, kg, kit)
        }
        assertEquals(bench(null), bench(vestAndBelt))
    }

    @Test
    fun `at the vest max a belt carries the next trial on in plates`() {
        val rec = weightedPullUp(TrainingMode.STRENGTH, 30.0, 5, vestAndBelt)
        assertEquals(32.5, rec.weightKg!!, 1e-9)
        assertEquals("Target cleared — add 2.5 kg, drop to 3 reps", rec.reason)
    }

    @Test
    fun `at the vest max with no belt the load holds and reps climb`() {
        val rec = weightedPullUp(TrainingMode.STRENGTH, 30.0, 5, vest)
        assertEquals(30.0, rec.weightKg!!, 1e-9)
        assertEquals(7, rec.reps)
        assertEquals("Vest full at 30 kg — climb to 7 reps", rec.reason)
        assertTrue(rec.changed)
        val hyper = weightedPullUp(TrainingMode.HYPERTROPHY, 30.0, 12, vest)
        assertEquals(30.0, hyper.weightKg!!, 1e-9)
        assertEquals(14, hyper.reps)
        // Reps still climb before load in hypertrophy, with or without the vest being full.
        assertEquals(10, weightedPullUp(TrainingMode.HYPERTROPHY, 30.0, 8, vest).reps)
    }

    @Test
    fun `a full vest stops climbing reps at the ceiling and asks for a harder variation`() {
        val rec = weightedPullUp(TrainingMode.STRENGTH, 30.0, Progression.BODYWEIGHT_REP_CEILING, vest)
        assertEquals(30.0, rec.weightKg!!, 1e-9)
        assertEquals(Progression.BODYWEIGHT_REP_CEILING, rec.reps)
        assertTrue(rec.reason.contains("harder variation"))
    }

    @Test
    fun `a load already past the vest with no belt is held where it is`() {
        val rec = weightedPullUp(TrainingMode.STRENGTH, 35.0, 5, vest)
        assertEquals(35.0, rec.weightKg!!, 1e-9)
        assertEquals(7, rec.reps)
    }

    @Test
    fun `an unweighted bodyweight lift takes its first load in vest blocks`() {
        val worn = WornLoad.forLift(vest, "Pull-up", Lever.REPS_THEN_LOAD)
        val rec = Progression.next(
            TrainingMode.HYPERTROPHY, 8, 3, 3, true, 4.0, 12, lever = Lever.REPS_THEN_LOAD, worn = worn,
        )
        assertEquals(5.0, rec.weightKg!!, 1e-9)
        assertEquals("Rep ceiling reached — add 1 kg, back to 8 reps", rec.reason)
        val bodyweight = Progression.next(
            TrainingMode.HYPERTROPHY, 8, 3, 3, true, null, Progression.BODYWEIGHT_REP_CEILING,
            lever = Lever.REPS_THEN_LOAD, worn = worn,
        )
        assertEquals("Reps are high enough — add 1 kg or take a harder variation", bodyweight.reason)
        val plain = Progression.next(
            TrainingMode.HYPERTROPHY, 8, 3, 3, true, null, Progression.BODYWEIGHT_REP_CEILING, lever = Lever.REPS_THEN_LOAD,
        )
        assertEquals("Reps are high enough — add 2.5 kg or take a harder variation", plain.reason)
    }

    @Test
    fun `fromSets resolves the kit for the lift it is given`() {
        val deep = Progression.fromSets(
            TrainingMode.HYPERTROPHY, 8, 3, sets(11.0, 12), "PULL", "Pull-up", Lever.REPS_THEN_LOAD, kg, null,
        )
        assertEquals(12.5, deep.weightKg!!, 1e-9)
        val deepVest = Progression.fromSets(
            TrainingMode.HYPERTROPHY, 8, 3, sets(11.0, 12), "PULL", "Pull-up", Lever.REPS_THEN_LOAD, kg, vest,
        )
        assertEquals(12.0, deepVest.weightKg!!, 1e-9)
    }

    @Test
    fun `a deload lands on the vest block, not the 2 point 5 grid`() {
        val deload = { kit: LoadKit? ->
            Progression.fromSessions(
                TrainingMode.STRENGTH, 5, 3,
                listOf(sets(21.0, 3), sets(21.0, 3), sets(21.0, 3)), "PULL", "Weighted Pull-up", Lever.LOAD, kg, kit,
            )
        }
        assertTrue(deload(vest).deload)
        assertEquals(19.0, deload(vest).weightKg!!, 1e-9)
        assertEquals(20.0, deload(null).weightKg!!, 1e-9)
    }

    @Test
    fun `in pounds a cleared trial adds the block as it reads`() {
        val from = Loads.toKg(40.0, lb)
        val rec = weightedPullUp(TrainingMode.STRENGTH, from, 5, vest, lb)
        assertEquals(42.0, Loads.snap(rec.weightKg!!, lb), 1e-9)
        assertEquals("Target cleared — add 2 lb, drop to 3 reps", rec.reason)
    }

    // ---- warm-up ramp

    @Test
    fun `the warm-up ramp rounds to the vest block`() {
        fun ramp(kit: LoadKit?) = WarmupRamp.plan(29.0, 2.5, barbell = false, worn = kit).map { it.weightKg }
        assertEquals(listOf(12.0, 17.0, 23.0), ramp(vest))
        assertEquals(listOf(12.5, 17.5, 22.5), WarmupRamp.plan(29.0, 2.5, barbell = false).map { it.weightKg })
    }

    @Test
    fun `the warm-up offer and insert agree on which lifts the kit reaches`() {
        fun ramp(kit: LoadKit?, weighted: Boolean?) =
            WarmupRamp.planFor("Weighted Pull-up", "PULL", ExerciseMetric.REPS, 29.0, kit, weighted).map { it.weightKg }
        assertEquals(listOf(12.0, 17.0, 23.0), ramp(vest, true))
        assertEquals(ramp(null, true), ramp(none, true))
        val bench = WarmupRamp.planFor("Bench Press", "PUSH", ExerciseMetric.REPS, 60.0, vest, true)
        assertEquals(WarmupRamp.planFor("Bench Press", "PUSH", ExerciseMetric.REPS, 60.0), bench)
    }

    // ---- generated working load

    @Test
    fun `a working load from the e1rm floors to the block and never passes a vest with no belt`() {
        val strength = ProgramRules.strengthProfile(listOf(LoggedLift("Weighted Pull-up", 30.0, 10))) // e1rm 40
        val load = { kit: LoadKit? ->
            ProgramRules.workingLoadKg("Weighted Pull-up", "PULL", strength, 5, 2, kit)!!.first
        }
        // 40 / (1 + 7/30) = 32.43 kg: past the vest.
        assertEquals(30.0, load(vest), 1e-9)
        assertEquals(30.0, load(vestAndBelt), 1e-9)
        assertEquals(30.0, load(null), 1e-9)
        val light = ProgramRules.strengthProfile(listOf(LoggedLift("Weighted Pull-up", 22.0, 10))) // e1rm 29.33
        // 29.33 / 1.2333 = 23.78 kg: the block gives 23, the 2.5 grid 22.5.
        assertEquals(23.0, ProgramRules.workingLoadKg("Weighted Pull-up", "PULL", light, 5, 2, vest)!!.first, 1e-9)
        assertEquals(22.5, ProgramRules.workingLoadKg("Weighted Pull-up", "PULL", light, 5, 2)!!.first, 1e-9)
    }

    // ---- the editors' steppers

    @Test
    fun `the live trial stepper walks blocks under the vest and plates past it`() {
        val worn = WornLoad.forLift(vestAndBelt, "Pull-up", weighted = false)
        assertEquals(19.0, stepUpKg(18.0, kg, worn = worn), 1e-9)
        assertEquals(17.0, stepDownKg(18.0, kg, worn = worn)!!, 1e-9)
        assertEquals(32.5, stepUpKg(30.0, kg, worn = worn), 1e-9)
        assertEquals(30.0, stepDownKg(32.5, kg, worn = worn)!!, 1e-9)
        assertEquals(1.0, stepUpKg(null, kg, worn = worn), 1e-9)
        assertNull(stepDownKg(1.0, kg, worn = worn))
        // No kit: the movement's own grid, as ever.
        assertEquals(20.0, stepUpKg(18.0, kg), 1e-9)
        assertEquals(15.0, stepDownKg(17.5, kg)!!, 1e-9)
        // A full vest with no belt: the hand-set stepper falls back to the grid rather than doing nothing.
        assertEquals(32.5, stepUpKg(30.0, kg, worn = WornLoad.forLift(vest, "Pull-up", weighted = false)), 1e-9)
    }

    @Test
    fun `the rite editor steps a weighted pull-up in the kit's increments`() {
        val entry = PlannedEntry("Weighted Pull-up", 3, 5, 18.0)
        val equipment = Equipment(fullGym = false, gear = setOf(Gear.PULL_UP_BAR), kit = vestAndBelt)
        val worn = wornFor(entry, equipment)
        assertNotNull(worn)
        assertEquals(19.0, entry.withLoadDelta(1, 2.5, worn = worn).targetWeightKg!!, 1e-9)
        assertEquals(17.0, entry.withLoadDelta(-1, 2.5, worn = worn).targetWeightKg!!, 1e-9)
        assertEquals(32.5, entry.copy(targetWeightKg = 30.0).withLoadDelta(1, 2.5, worn = worn).targetWeightKg!!, 1e-9)
        // A full vest with no belt stays at its top; the plan never prescribes past what the lifter owns.
        val noBelt = wornFor(entry, equipment.copy(kit = vest))
        assertEquals(30.0, entry.copy(targetWeightKg = 30.0).withLoadDelta(1, 2.5, worn = noBelt).targetWeightKg!!, 1e-9)
        // No kit, or a lift that is not worn: the 2.5 step as before.
        assertNull(wornFor(entry, Equipment(fullGym = false)))
        assertNull(wornFor(PlannedEntry("Bench Press", 3, 5, 60.0), equipment))
        assertEquals(20.5, entry.withLoadDelta(1, 2.5).targetWeightKg!!, 1e-9)
    }

    // ---- the gear summary

    @Test
    fun `the armoury summary names the vest and belt when they are all there is`() {
        assertEquals("Nothing", SettingsSummaries.armoury(Equipment(fullGym = false)))
        assertEquals("Weighted vest", SettingsSummaries.armoury(Equipment(fullGym = false, kit = vest)))
        assertEquals(
            "Pull-up bar, weighted vest +1",
            SettingsSummaries.armoury(Equipment(fullGym = false, gear = setOf(Gear.PULL_UP_BAR), kit = vestAndBelt)),
        )
        assertEquals("Full gym", SettingsSummaries.armoury(Equipment.FULL_GYM.copy(kit = vest)))
    }
}
