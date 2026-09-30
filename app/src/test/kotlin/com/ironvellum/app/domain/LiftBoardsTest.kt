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
        assertEquals("Iron I", LiftBoards.tierName(1))
        assertEquals("Iron II", LiftBoards.tierName(2))
        assertEquals("Bronze I", LiftBoards.tierName(3))
        assertEquals("Gold II", LiftBoards.tierName(8))
        assertEquals("Mythic II", LiftBoards.tierName(10))
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
        for (lift in Lift.entries) for (sex in Sex.entries) {
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
}
