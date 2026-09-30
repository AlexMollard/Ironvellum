package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tier a lifter reads on the ally boards. Every test pins one rule of
 * [LiftBoards.marks]: a broken rule moves someone to a tier they did not earn
 * (or off the board), which is visible to every ally.
 */
class LiftBoardsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private val bodyweight = 100.0

    private fun set(
        name: String,
        reps: Int,
        weightKg: Double?,
        done: Boolean = true,
        modifiers: String = "",
    ) = SessionSet(exerciseId = 1, exerciseName = name, setIndex = 0, reps = reps, weightKg = weightKg, modifiers = modifiers, done = done)

    private fun workout(daysAgo: Int, vararg sets: SessionSet): Pair<WorkoutSession, List<SessionSet>> {
        val at = now - daysAgo * day
        return WorkoutSession(id = 1, label = "w", startedAtMs = at - 3_600_000, completedAtMs = at) to sets.toList()
    }

    private fun marks(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        sex: Sex = Sex.MALE,
        bw: (Long) -> Double = { bodyweight },
    ) = LiftBoards.marks(history, bw, sex, now)

    /** A one-rep barbell set whose Epley e1RM is [ratio] x bodyweight. */
    private fun barbellAt(name: String, ratio: Double) =
        set(name, reps = 1, weightKg = ratio * bodyweight / (1.0 + 1 / 30.0))

    private fun squatStep(ratio: Double, sex: Sex = Sex.MALE): Int? =
        marks(listOf(workout(1, barbellAt("Back Squat", ratio))), sex).singleOrNull()?.step

    @Test
    fun belowIronFloorIsInitiateAndAtFloorIsIron() {
        // Male squat Iron floor is 0.75x bodyweight.
        assertEquals(0, squatStep(0.7499))
        assertEquals(1, squatStep(0.7501))
    }

    @Test
    fun tierSplitsIntoOneAndTwoAtTheGeometricMidpoint() {
        // sqrt(0.75 * 1.25) = 0.968 between the Iron and Bronze floors.
        assertEquals(1, squatStep(0.96))
        assertEquals(2, squatStep(0.975))
        assertEquals(3, squatStep(1.2501))
    }

    @Test
    fun mythicCeilingIsOpenAndTopStepIsTen() {
        assertEquals(9, squatStep(2.76))
        assertEquals(10, squatStep(3.5))
        assertEquals(LiftBoards.MAX_STEP, squatStep(50.0))
    }

    @Test
    fun tierNamesFollowTheLadder() {
        assertEquals("Initiate", LiftBoards.tierName(0))
        assertEquals("Ash I", LiftBoards.tierName(1))
        assertEquals("Ash II", LiftBoards.tierName(2))
        assertEquals("Bone I", LiftBoards.tierName(3))
        assertEquals("Gold II", LiftBoards.tierName(8))
        assertEquals("Umbral II", LiftBoards.tierName(10))
    }

    @Test
    fun weightedPullUpLiftsBodyweightPlusAddedLoad() {
        // (100 + 46) x 1.033 / 100 = 1.51 - just over the male Silver floor of
        // 1.5. Added load alone would be 0.48 and rank at Initiate.
        val mark = marks(listOf(workout(1, set("Weighted Pull-up", 1, 46.0)))).single()
        assertEquals(Lift.PULL_UP, mark.lift)
        assertEquals(5, mark.step)
    }

    @Test
    fun unweightedPullUpStillCountsAtBodyweight() {
        // A bare pull-up marks no kilo but still moves bodyweight: 1.033x = Iron I.
        val mark = marks(listOf(workout(1, set("Pull-up", 1, null)))).single()
        assertEquals(1, mark.step)
    }

    @Test
    fun barbellLiftUsesLoadOnlyNotBodyweight() {
        // Bench 100 kg x 1 at 100 kg bodyweight = 1.033x -> Bronze I (male floors
        // 0.5 / 1.0 / 1.25, split at 1.118). Adding bodyweight as for a pull-up
        // would read 2.07x and jump to Mythic.
        val mark = marks(listOf(workout(1, set("Bench Press", 1, 100.0)))).single()
        assertEquals(Lift.BENCH, mark.lift)
        assertEquals(3, mark.step)
    }

    @Test
    fun barbellSetWithNoLoadDoesNotQualify() {
        assertTrue(marks(listOf(workout(1, set("Back Squat", 5, null)))).isEmpty())
    }

    @Test
    fun setsAboveTwelveRepsAreExcludedButTwelveCounts() {
        assertTrue(marks(listOf(workout(1, set("Back Squat", 13, 150.0)))).isEmpty())
        assertEquals(Lift.SQUAT, marks(listOf(workout(1, set("Back Squat", 12, 150.0)))).single().lift)
    }

    @Test
    fun assistedSetsAndUndoneSetsAreExcluded() {
        assertTrue(marks(listOf(workout(1, set("Assisted Pull-up", 5, 20.0)))).isEmpty())
        assertTrue(marks(listOf(workout(1, set("Pull-up", 5, 20.0, modifiers = "assisted")))).isEmpty())
        assertTrue(marks(listOf(workout(1, set("Back Squat", 5, 150.0, done = false)))).isEmpty())
    }

    @Test
    fun exercisesOutsideTheMappingAreIgnored() {
        assertTrue(marks(listOf(workout(1, set("Incline Bench Press", 5, 100.0), set("Push Press", 5, 80.0)))).isEmpty())
    }

    @Test
    fun exerciseNamesMatchIgnoringCaseAndPadding() {
        assertEquals(Lift.DEADLIFT, marks(listOf(workout(1, set("  DEADLIFT ", 3, 150.0)))).single().lift)
    }

    @Test
    fun unknownBodyweightYieldsNoMarks() {
        val history = listOf(workout(1, set("Back Squat", 5, 200.0)))
        assertTrue(marks(history) { 0.0 }.isEmpty())
    }

    @Test
    fun bodyweightIsReadAtTheWorkoutNotToday() {
        // 200 kg squat: at 100 kg bodyweight it is 2.07x; if the lookup returned
        // today's 200 kg it would be 1.03x. The step must follow the workout's day.
        val history = listOf(workout(50, set("Back Squat", 1, 200.0)))
        val early = marks(history) { at -> if (at < now - 10 * day) 100.0 else 200.0 }.single()
        assertEquals(6, early.step)
    }

    @Test
    fun recentWindowIsSevenDays() {
        val old = marks(listOf(workout(8, set("Back Squat", 1, 150.0)))).single()
        assertTrue(old.step > 0)
        assertNull(old.recentStep)
        assertNull(old.recentAtMs)

        val fresh = marks(listOf(workout(6, set("Back Squat", 1, 150.0)))).single()
        assertEquals(fresh.step, fresh.recentStep)
        assertEquals(now - 6 * day, fresh.recentAtMs)
    }

    @Test
    fun recentStepIsTheBestOfTheWeekNotOfAllTime() {
        val history = listOf(
            workout(30, set("Back Squat", 1, 200.0)),
            workout(2, set("Back Squat", 1, 100.0)),
        )
        val mark = marks(history).single()
        assertTrue(mark.step > mark.recentStep!!)
        assertEquals(now - 2 * day, mark.recentAtMs)
    }

    @Test
    fun oneMarkPerLiftAndOnlyForLiftsLogged() {
        val history = listOf(
            workout(1, set("Back Squat", 1, 100.0), set("Front Squat", 1, 150.0), set("Deadlift", 1, 150.0)),
        )
        val lifts = marks(history).map { it.lift }
        assertEquals(listOf(Lift.SQUAT, Lift.DEADLIFT), lifts)
    }

    @Test
    fun femaleFloorsAreLowerThanMaleForTheSameRatio() {
        // 0.55x squat: below the male Iron floor (0.75), above the female one (0.5).
        assertEquals(0, squatStep(0.55, Sex.MALE))
        assertEquals(1, squatStep(0.55, Sex.FEMALE))
        // The 1.25x squat TitleEngine prices for women is Silver I for her, Bronze I for him.
        assertEquals(5, squatStep(1.26, Sex.FEMALE))
        assertEquals(3, squatStep(1.26, Sex.MALE))
    }

    @Test
    fun stepNeverDecreasesAsTheRatioGrows() {
        for (lift in Lift.entries.filter { it.kind == LiftKind.TIERED }) for (sex in Sex.entries) {
            val steps = listOf(0.0, 0.3, 0.6, 0.9, 1.2, 1.5, 1.8, 2.1, 2.4, 3.0, 5.0, 9.0)
                .map { LiftBoards.stepFor(lift, sex, it) }
            assertEquals("$lift $sex", steps.sorted(), steps)
        }
    }

    @Test
    fun wireRoundTripsAndUnknownIsNull() {
        for (lift in Lift.entries) assertEquals(lift, Lift.fromWire(lift.wire))
        assertNull(Lift.fromWire("snatch"))
        assertNull(Lift.fromWire(null))
    }

    @Test
    fun wireNamesAreUnique() {
        assertEquals(Lift.entries.size, Lift.entries.map { it.wire }.toSet().size)
    }

    @Test
    fun barbellBoardsComeLastAndCalisthenicsFirst() {
        val groups = Lift.entries.map { it.group.ordinal }
        assertEquals(groups.sorted(), groups)
        assertEquals(LiftGroup.BARBELL, Lift.entries.last().group)
        assertEquals(LiftGroup.PULL, Lift.entries.first().group)
    }

    // ---- ladders

    private fun hold(name: String, seconds: Int, done: Boolean = true, modifiers: String = "") =
        SessionSet(exerciseId = 1, exerciseName = name, setIndex = 0, reps = 0, modifiers = modifiers, done = done, durationSec = seconds)

    private fun frontLeverStep(vararg sets: SessionSet, daysAgo: Int = 1, bw: (Long) -> Double = { bodyweight }) =
        marks(listOf(workout(daysAgo, *sets)), bw = bw).singleOrNull { it.lift == Lift.FRONT_LEVER }?.step

    @Test
    fun ladderRungNeedsItsStandardAndOneShortDoesNotCount() {
        val rungs = LiftBoards.rungs(Lift.FRONT_LEVER)
        val straddle = rungs.indexOfFirst { it.exercise == "Straddle Front Lever" } + 1
        val target = rungs[straddle - 1].target
        assertEquals(straddle, frontLeverStep(hold("Straddle Front Lever", target)))
        // One second short of the standard clears nothing on that exercise.
        assertNull(frontLeverStep(hold("Straddle Front Lever", target - 1)))

        val pistol = LiftBoards.rungs(Lift.PISTOL)
        val pistolRung = pistol.indexOfFirst { it.exercise == "Pistol Squat" }
        val need = pistol[pistolRung].target
        fun pistolStep(reps: Int) = marks(listOf(workout(1, set("Pistol Squat", reps, null)))).singleOrNull { it.lift == Lift.PISTOL }?.step
        assertEquals(pistolRung + 1, pistolStep(need))
        assertNull(pistolStep(need - 1))
    }

    @Test
    fun ladderHoldReadsSecondsNotReps() {
        val need = LiftBoards.rungs(Lift.FRONT_LEVER).first().target
        val repsOnly = SessionSet(exerciseId = 1, exerciseName = "Front Row Hold", setIndex = 0, reps = need, done = true)
        assertNull(frontLeverStep(repsOnly))
    }

    @Test
    fun higherRungWinsAndImpliesLowerOnes() {
        val rungs = LiftBoards.rungs(Lift.FRONT_LEVER)
        val top = rungs.last()
        // Only the top rung was ever logged: the board still reads the top step.
        assertEquals(rungs.size, frontLeverStep(hold(top.exercise, top.target)))
        val mixed = frontLeverStep(
            hold(rungs[1].exercise, rungs[1].target),
            hold(rungs[3].exercise, rungs[3].target),
            hold(rungs[2].exercise, rungs[2].target),
        )
        assertEquals(4, mixed)
    }

    @Test
    fun ladderMarkExistsWithUnknownBodyweightButTieredDoesNot() {
        val rung = LiftBoards.rungs(Lift.FRONT_LEVER)[1]
        val history = listOf(workout(1, hold(rung.exercise, rung.target), set("Back Squat", 5, 150.0)))
        val lifts = marks(history) { 0.0 }.map { it.lift }
        assertEquals(listOf(Lift.FRONT_LEVER), lifts)
    }

    @Test
    fun assistedAndUndoneLadderSetsNeverCount() {
        val rung = LiftBoards.rungs(Lift.FRONT_LEVER)[0]
        assertNull(frontLeverStep(hold(rung.exercise, rung.target, modifiers = "assisted")))
        assertNull(frontLeverStep(hold(rung.exercise, rung.target, done = false)))
        assertNull(frontLeverStep(hold("Assisted " + rung.exercise, rung.target)))
    }

    @Test
    fun ladderRecentWindowIsSevenDays() {
        val rungs = LiftBoards.rungs(Lift.FRONT_LEVER)
        val old = marks(listOf(workout(30, hold(rungs[3].exercise, rungs[3].target)))).single()
        assertEquals(4, old.step)
        assertNull(old.recentStep)

        val history = listOf(
            workout(30, hold(rungs[3].exercise, rungs[3].target)),
            workout(2, hold(rungs[1].exercise, rungs[1].target)),
        )
        val mark = marks(history).single()
        assertEquals(4, mark.step)
        assertEquals(2, mark.recentStep)
        assertEquals(now - 2 * day, mark.recentAtMs)
    }

    @Test
    fun practiceRecordsCountLikeSetsButClaimsDoNot() {
        val rung = LiftBoards.rungs(Lift.FRONT_LEVER)[2]
        fun withPractice(p: SkillPractice) =
            LiftBoards.marks(emptyList(), { 0.0 }, Sex.MALE, now, listOf(p)).singleOrNull()
        val met = withPractice(SkillPractice(rung.exercise, now - 2 * day, value = rung.target))!!
        assertEquals(3, met.step)
        assertEquals(3, met.recentStep)
        assertNull(withPractice(SkillPractice(rung.exercise, now - 2 * day, value = rung.target - 1)))
        assertNull(withPractice(SkillPractice(rung.exercise, now - 2 * day, claimed = true, value = rung.target)))
        assertNull(withPractice(SkillPractice(rung.exercise, now - 20 * day, value = rung.target))!!.recentStep)
    }

    @Test
    fun stepLabelNamesTheTierOrTheRung() {
        assertEquals("Ash I", LiftBoards.stepLabel(Lift.SQUAT, 1))
        val rungs = LiftBoards.rungs(Lift.FRONT_LEVER)
        assertEquals(rungs[3].exercise, LiftBoards.stepLabel(Lift.FRONT_LEVER, 4))
        assertEquals("rung 4 of ${rungs.size}", LiftBoards.stepDetail(Lift.FRONT_LEVER, 4))
        assertNull(LiftBoards.stepDetail(Lift.SQUAT, 4))
    }

    @Test
    fun everyLadderIsWellFormedAndItsRungsAreInTheCatalogue() {
        val catalogue = com.ironvellum.app.data.Seed.exercises.map { it.name }.toSet()
        for (lift in Lift.entries) {
            val rungs = LiftBoards.rungs(lift)
            if (lift.kind == LiftKind.TIERED) {
                assertTrue("$lift", rungs.isEmpty())
                continue
            }
            assertTrue("$lift needs 3+ rungs", rungs.size >= 3)
            assertTrue("$lift over the step cap", rungs.size <= LiftBoards.MAX_STEP)
            assertEquals("$lift repeats a rung", rungs.size, rungs.map { it.exercise }.toSet().size)
            for (rung in rungs) {
                assertTrue("${rung.exercise} is not in the catalogue", rung.exercise in catalogue)
                assertTrue("${rung.exercise} has no target", rung.target > 0)
                // A workout set cannot carry a distance, so a metres rung could never be met.
                assertTrue("${rung.exercise} is measured in metres", rung.metric != Skills.Metric.METRES)
            }
        }
    }

    @Test
    fun boardsForResolvesRungsAndTieredNamesButNotUnrelatedExercises() {
        val rungs = LiftBoards.rungs(Lift.ONE_ARM_PULL)
        val index = rungs.indexOfFirst { it.exercise == "Archer Pull-up" }
        assertTrue(index >= 0)
        assertTrue(
            LiftBoards.BoardEntry(Lift.ONE_ARM_PULL, index + 1, rungs.size) in LiftBoards.boardsFor(" archer pull-up "),
        )
        assertTrue(LiftBoards.BoardEntry(Lift.DIP, null, 0) in LiftBoards.boardsFor("Weighted Dip"))
        assertTrue(LiftBoards.boardsFor("Face Pull").isEmpty())
    }
}
