package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XpTest {
    private val bodyweight = 79.3

    private fun set(
        name: String,
        reps: Int,
        weightKg: Double? = null,
        rpe: Int? = null,
        id: Long = name.hashCode().toLong(),
        kind: SetKind = SetKind.WORKING,
        external: Boolean = false,
    ) = Xp.SetEffort(name, reps = reps, weightKg = weightKg, rpe = rpe, exerciseId = id, kind = kind, externalLoad = external)

    private fun sets(n: Int, name: String, reps: Int, weightKg: Double? = null, rpe: Int? = null) =
        List(n) { set(name, reps, weightKg, rpe) }

    private fun hold(n: Int, name: String, seconds: Int) =
        List(n) { Xp.SetEffort(name, holdSeconds = seconds, metric = ExerciseMetric.HOLD, exerciseId = name.hashCode().toLong()) }

    private fun history(vararg done: Xp.SetEffort) = Xp.History.EMPTY.after(done.toList(), bodyweight)

    private fun xp(sets: List<Xp.SetEffort>, history: Xp.History = Xp.History.EMPTY) =
        Xp.breakdown(sets, bodyweight, history, trial = Xp.TrialFacts(earnsBonuses = false))

    // ------------------------------------------------------------ agreed examples

    @Test
    fun threeSetsOfTwentyEasyPushUpsPayLessThanThreeHardSetsOfFive() {
        val easyLifter = history(set("Push-up", 25))
        val hardLifter = history(set("Push-up", 5, weightKg = 25.0))
        val easy = xp(sets(3, "Push-up", 20), easyLifter)
        val hard = xp(sets(3, "Push-up", 5, weightKg = 25.0), hardLifter)
        assertTrue("${easy.total} vs ${hard.total}", easy.total < hard.total)
    }

    @Test
    fun aFirstEverLiftCountsItsSetsAsHard() {
        val first = Xp.judge(set("Archer Push-up", 6), bodyweight)
        assertTrue(first.hard)
        assertEquals(null, first.rir)
        // And pays as hard: the same reps in an easy-for-you lift pay less.
        val known = history(set("Archer Push-up", 40))
        assertTrue(Xp.setXp(set("Archer Push-up", 6), bodyweight) > Xp.setXp(set("Archer Push-up", 6), bodyweight, known))
    }

    @Test
    fun tenEasyLightSetsCannotBeatFourHardSets() {
        val known = history(set("Push-up", 30))
        val easy = xp(sets(10, "Push-up", 10), known)
        val hard = xp(sets(4, "Push-up", 5, weightKg = 25.0), history(set("Push-up", 5, weightKg = 25.0)))
        assertTrue("${easy.total} vs ${hard.total}", easy.total < hard.total)
        assertTrue(easy.setDriven <= Xp.EASY_XP_CAP)
    }

    @Test
    fun easySetsAcrossManyMovementsShareOneCap() {
        val names = List(10) { "Movement $it" }
        val known = history(*names.map { set(it, 30) }.toTypedArray())
        val farmed = xp(names.map { set(it, 10) }, known)
        assertTrue(farmed.setDriven <= Xp.EASY_XP_CAP)
    }

    // ------------------------------------------------------------ the judgement

    @Test
    fun aLoggedRatingOverridesTheEstimate() {
        val best = history(set("Pull-up", 5, weightKg = 15.0))
        // Far below the best, so the estimate says easy...
        assertFalse(Xp.judge(set("Pull-up", 3), bodyweight, best).hard)
        // ...but the lifter says RIR 1 (RPE 9): hard.
        assertTrue(Xp.judge(set("Pull-up", 3, rpe = 9), bodyweight, best).hard)
        // And a set at the best the estimate calls hard is easy when rated RIR 5+.
        assertFalse(Xp.judge(set("Pull-up", 5, weightKg = 15.0, rpe = 5), bodyweight, best).hard)
    }

    @Test
    fun failureAndRestPauseSetsAreAlwaysHard() {
        val best = history(set("Pull-up", 20))
        assertTrue(Xp.judge(set("Pull-up", 3, kind = SetKind.FAILURE), bodyweight, best).hard)
        assertTrue(Xp.judge(set("Pull-up", 3, kind = SetKind.REST_PAUSE), bodyweight, best).hard)
    }

    @Test
    fun theHardLineIsTheSameOneCoverageUses() {
        // Estimated reps in reserve become an RPE and go through HardSets.isHard: 3 left is hard, 4 left is not.
        val best = history(set("Chin-up", 10))
        fun rirOf(reps: Int) = Xp.judge(set("Chin-up", reps), bodyweight, best)
        assertTrue(rirOf(7).hard)
        assertFalse(rirOf(6).hard)
    }

    @Test
    fun aFirstTimeLiftDoneForEnduranceIsNotHard() {
        // HardSets.isHard rejects an unrated set past 15 reps, so a first-time 40-rep set cannot farm.
        assertFalse(Xp.judge(set("Push-up", 40), bodyweight).hard)
    }

    @Test
    fun aHoldIsJudgedAgainstTheBestHold() {
        val best = Xp.History.EMPTY.after(hold(1, "Tuck Front Lever", 40), bodyweight)
        val near = Xp.judge(hold(1, "Tuck Front Lever", 36).first(), bodyweight, best)
        val short = Xp.judge(hold(1, "Tuck Front Lever", 10).first(), bodyweight, best)
        assertTrue(near.hard)
        assertFalse(short.hard)
        assertTrue(near.xp > short.xp)
        assertTrue(Xp.judge(hold(1, "Tuck Front Lever", 10).first(), bodyweight).hard)
    }

    @Test
    fun repsPastFiveAddOnlyALittle() {
        val five = Xp.setXp(set("Pull-up", 5, weightKg = 10.0), bodyweight)
        val ten = Xp.setXp(set("Pull-up", 10, weightKg = 10.0), bodyweight)
        assertTrue(ten > five)
        assertTrue(ten < five * 1.4)
    }

    @Test
    fun aHeavierLoadAddsALittleAndIsBounded() {
        val best = history(set("Pull-up", 5, weightKg = 15.0))
        val light = Xp.setXp(set("Pull-up", 4, weightKg = 5.0, rpe = 9), bodyweight, best)
        val heavy = Xp.setXp(set("Pull-up", 4, weightKg = 17.7, rpe = 9), bodyweight, best)
        val absurd = Xp.setXp(set("Pull-up", 4, weightKg = 500.0, rpe = 9), bodyweight, best)
        assertTrue(heavy > light)
        assertTrue(absurd <= heavy + 2)
    }

    @Test
    fun anEmptySetPaysNothing() {
        assertEquals(0, Xp.setXp(set("Push-up", 0), bodyweight))
        assertEquals(0, Xp.setXp(set("Push-up", -5), bodyweight))
        assertEquals(0, Xp.award(emptyList(), bodyweight))
        assertEquals(0, Xp.award(emptyList(), bodyweight, doneSetCount = 0))
    }

    @Test
    fun aWarmupNeverPays() {
        val bd = xp(listOf(set("Push-up", 10, kind = SetKind.WARMUP)))
        assertEquals(0, bd.setDriven)
    }

    @Test
    fun awardAddsTheCompletionBonusOnlyForAWorkoutWithSomethingDone() {
        assertEquals(Xp.COMPLETION_BONUS, Xp.award(emptyList(), bodyweight, doneSetCount = 1))
        val one = set("Push-up", 10)
        assertEquals(Xp.setXp(one, bodyweight) + Xp.COMPLETION_BONUS, Xp.award(listOf(one), bodyweight))
    }

    @Test
    fun movementIntensityStillScalesWhatASetPays() {
        assertTrue(Xp.setXp(set("Handstand Push-up", 5), bodyweight) > Xp.setXp(set("Push-up", 5), bodyweight))
    }

    @Test
    fun anAssistedSetIsLighterNotHeavier() {
        val best = history(set("Pull-up", 8))
        val bare = Xp.SetEffort("Pull-up", reps = 8, exerciseId = 1)
        val assisted = Xp.SetEffort("Pull-up", reps = 8, weightKg = 30.0, modifiers = "assisted", exerciseId = 1)
        assertTrue(Xp.setXp(assisted, bodyweight, best) < Xp.setXp(bare, bodyweight, best))
    }

    // ------------------------------------------------------------ the breakdown

    @Test
    fun theSegmentsSumToTheAwardExactly() {
        val best = history(set("Pull-up", 5, weightKg = 15.0), set("Dip", 8))
        val lifted = sets(4, "Pull-up", 4, weightKg = 17.7) + sets(3, "Dip", 10) + hold(2, "Hollow Hold", 30)
        val bd = Xp.breakdown(
            lifted, bodyweight, best, peakLifts = mapOf("dip" to Xp.PeakAward(Xp.PEAK_BONUS, "more reps")), doneSetCount = lifted.size,
            trial = Xp.TrialFacts(quest = true, welcomeBack = true),
        )
        assertEquals(bd.lines.sumOf { it.xp }, bd.total)
        assertEquals(bd.total, bd.lines.sumOf { it.xp })
        assertTrue(bd.lines.none { it.xp == 0 })
        // One segment per movement, in trial order, then the bonuses.
        assertEquals(listOf("Pull-up", "Dip", "Hollow Hold"), bd.of(Xp.Kind.EXERCISE).map { it.label })
        assertEquals(Xp.COMPLETION_BONUS, bd.of(Xp.Kind.COMPLETION).single().xp)
        assertEquals(Xp.QUEST_BONUS, bd.of(Xp.Kind.QUEST).single().xp)
        assertTrue(bd.describe().endsWith("total ${bd.total}"))
    }

    @Test
    fun eachMovementSegmentIsTheSumOfItsSets() {
        val one = set("Push-up", 8)
        val three = sets(3, "Push-up", 8)
        assertEquals(3 * Xp.setXp(one, bodyweight), xp(three).of(Xp.Kind.EXERCISE).single().xp)
    }

    // ------------------------------------------------------------ peaks and records

    @Test
    fun aRecordAndAPeakOnOneLiftPayTheLargerOnceNotBoth() {
        val best = history(set("Pull-up", 5, weightKg = 15.0))
        val sets = sets(1, "Pull-up", 6, weightKg = 17.0)
        val full = mapOf("pull-up" to Xp.PeakAward(Xp.PEAK_BONUS, "heavier"))
        val both = Xp.breakdown(sets, bodyweight, best, peakLifts = full, doneSetCount = 1)
        assertEquals(Xp.RECORD_BONUS, both.of(Xp.Kind.RECORD).single().xp)
        assertTrue(both.of(Xp.Kind.PEAK).isEmpty())
        val peakOnly = Xp.breakdown(sets(1, "Pull-up", 3, weightKg = 5.0), bodyweight, best, peakLifts = full, doneSetCount = 1)
        assertEquals(Xp.PEAK_BONUS, peakOnly.of(Xp.Kind.PEAK).single().xp)
        assertEquals("Pull-up \u00B7 heavier", peakOnly.of(Xp.Kind.PEAK).single().detail)
        assertTrue(peakOnly.of(Xp.Kind.RECORD).isEmpty())
    }

    @Test
    fun aPeakThatPaysNothingTakesNoBonusSlotAndNoSegment() {
        val names = List(4) { "Lift $it" }
        val none = names.associate { it.lowercase() to Xp.PeakAward(if (it == "Lift 0") 0 else 3, "heavier") }
        val bd = Xp.breakdown(names.map { set(it, 5, weightKg = 20.0) }, bodyweight, peakLifts = none, doneSetCount = 4)
        // The 0 XP peak is skipped and does not use up one of the three slots.
        assertEquals(9, bd.of(Xp.Kind.PEAK).single().xp)
        assertEquals("Lift 1 \u00B7 heavier, Lift 2 \u00B7 heavier, Lift 3 \u00B7 heavier", bd.of(Xp.Kind.PEAK).single().detail)
    }

    @Test
    fun aRecordNeedsAHistoryAndABigEnoughMargin() {
        val first = Xp.breakdown(sets(1, "Pull-up", 6, weightKg = 17.0), bodyweight, doneSetCount = 1)
        assertTrue(first.of(Xp.Kind.RECORD).isEmpty())
        val best = history(set("Pull-up", 5, weightKg = 15.0))
        val same = Xp.breakdown(sets(1, "Pull-up", 5, weightKg = 15.2), bodyweight, best, doneSetCount = 1)
        assertTrue(same.of(Xp.Kind.RECORD).isEmpty())
    }

    @Test
    fun peaksAndRecordsPayForAtMostThreeLifts() {
        val names = List(5) { "Lift $it" }
        val best = history(*names.map { set(it, 5, weightKg = 20.0, id = 1) }.toTypedArray())
        val sets = names.map { set(it, 8, weightKg = 30.0) }
        val bd = Xp.breakdown(sets, bodyweight, best, doneSetCount = sets.size)
        assertEquals(Xp.MAX_BONUS_LIFTS * Xp.RECORD_BONUS, bd.of(Xp.Kind.RECORD).single().xp)
    }

    @Test
    fun noBonusIsPaidOnAnImport() {
        val best = history(set("Pull-up", 5, weightKg = 15.0))
        val bd = Xp.breakdown(
            sets(1, "Pull-up", 8, weightKg = 20.0, rpe = 9), bodyweight, best, peakLifts = mapOf("pull-up" to Xp.PeakAward(Xp.PEAK_BONUS, "heavier")),
            doneSetCount = 1, trial = Xp.TrialFacts(earnsBonuses = false, weekCovered = true, welcomeBack = true),
        )
        assertEquals(listOf(Xp.Kind.EXERCISE, Xp.Kind.COMPLETION), bd.lines.map { it.kind })
    }

    // ------------------------------------------------------------ trial bonuses

    @Test
    fun honestEffortIsPaidOnceAndOnlyWhenEveryLiftsTopSetIsRated() {
        val rated = sets(3, "Pull-up", 5, rpe = 8)
        val bd = Xp.breakdown(rated + sets(2, "Dip", 8, rpe = 9), bodyweight, doneSetCount = 5)
        assertEquals(Xp.HONEST_EFFORT_BONUS, bd.of(Xp.Kind.HONEST_EFFORT).single().xp)
        // A lift whose top set is unrated spoils it.
        val unrated = Xp.breakdown(rated + sets(2, "Dip", 8), bodyweight, doneSetCount = 5)
        assertTrue(unrated.of(Xp.Kind.HONEST_EFFORT).isEmpty())
        // The top set is the one that matters: an unrated lighter set beside a rated heaviest one is fine.
        val mixed = listOf(set("Pull-up", 5, weightKg = 20.0, rpe = 9), set("Pull-up", 5, weightKg = 10.0))
        assertEquals(Xp.HONEST_EFFORT_BONUS, Xp.breakdown(mixed, bodyweight, doneSetCount = 2).of(Xp.Kind.HONEST_EFFORT).single().xp)
    }

    @Test
    fun holdsAreExemptFromHonestEffortAndAHoldOnlyTrialEarnsNone() {
        val withHold = Xp.breakdown(sets(2, "Pull-up", 5, rpe = 8) + hold(2, "Plank", 30), bodyweight, doneSetCount = 4)
        assertEquals(1, withHold.of(Xp.Kind.HONEST_EFFORT).size)
        val holdsOnly = Xp.breakdown(hold(2, "Plank", 30), bodyweight, doneSetCount = 2)
        assertTrue(holdsOnly.of(Xp.Kind.HONEST_EFFORT).isEmpty())
    }

    @Test
    fun riteAsPlannedNeedsEveryPlannedSetDoneAndHard() {
        val pull = set("Pull-up", 5, weightKg = 15.0, id = 1)
        val dip = set("Dip", 8, id = 2)
        val done = List(3) { pull } + List(3) { dip }
        fun bonus(plan: Map<Long, Int>?, sets: List<Xp.SetEffort> = done) = Xp.breakdown(
            sets, bodyweight, doneSetCount = sets.size, trial = Xp.TrialFacts(plan = plan),
        ).of(Xp.Kind.RITE_AS_PLANNED).sumOf { it.xp }
        assertEquals(Xp.RITE_AS_PLANNED_BONUS, bonus(mapOf(1L to 3, 2L to 3)))
        // A planned set short of done.
        assertEquals(0, bonus(mapOf(1L to 4, 2L to 3)))
        // An open trial (no rite) never earns it.
        assertEquals(0, bonus(null))
        assertEquals(0, bonus(emptyMap()))
        // Done, but one of them not hard: rated easy.
        val easy = List(2) { pull } + set("Pull-up", 5, weightKg = 15.0, id = 1, rpe = 5) + List(3) { dip }
        assertEquals(0, bonus(mapOf(1L to 3, 2L to 3), easy))
    }

    @Test
    fun welcomeBackAndFirstOfTheWeekNeverStack() {
        val both = Xp.breakdown(sets(1, "Push-up", 8), bodyweight, trial = Xp.TrialFacts(welcomeBack = true, firstOfWeek = true))
        assertEquals(Xp.WELCOME_BACK_BONUS, both.of(Xp.Kind.WELCOME_BACK).single().xp)
        assertTrue(both.of(Xp.Kind.FIRST_OF_WEEK).isEmpty())
        val first = Xp.breakdown(sets(1, "Push-up", 8), bodyweight, trial = Xp.TrialFacts(firstOfWeek = true))
        assertEquals(Xp.FIRST_OF_WEEK_BONUS, first.of(Xp.Kind.FIRST_OF_WEEK).single().xp)
    }

    // ------------------------------------------------------------ the live tally

    @Test
    fun theLiveRunningTotalIsWhatTheSealAwardsForTheSetsDoneSoFar() {
        val best = history(set("Pull-up", 5, weightKg = 15.0), set("Dip", 8))
        val all = sets(4, "Pull-up", 4, weightKg = 17.7, rpe = 9) + sets(3, "Dip", 10, rpe = 8)
        val peaks = mapOf("pull-up" to Xp.PeakAward(Xp.PEAK_BONUS, "heavier"))
        for (done in 0..all.size) {
            val soFar = all.take(done)
            val live = Xp.tally(soFar, bodyweight, best, peaks)
            val sealed = Xp.breakdown(
                soFar, bodyweight, best, peaks, doneSetCount = soFar.size,
                trial = Xp.TrialFacts(quest = true, weekCovered = true, firstOfWeek = true),
            )
            assertEquals("after $done sets", live, sealed.setDriven)
        }
        // Whole trial: the award is the tally plus the trial-level bonuses, nothing else.
        val live = Xp.tally(all, bodyweight, best, peaks)
        val sealed = Xp.breakdown(all, bodyweight, best, peaks, doneSetCount = all.size)
        val trialLevel = sealed.lines.filter { !it.kind.setDriven }.sumOf { it.xp }
        assertEquals(live + trialLevel, sealed.total)
        assertEquals(Xp.COMPLETION_BONUS + Xp.HONEST_EFFORT_BONUS, trialLevel)
    }

    // ------------------------------------------------------------ levels

    @Test
    fun levelsAreUnchanged() {
        assertEquals(1, Xp.levelFor(0))
        assertEquals(2, Xp.levelFor(100))
        assertEquals(3, Xp.levelFor(300))
        assertEquals(0, Xp.rollsDue(3, 3, 0))
        assertEquals(1, Xp.rollsDue(3, 4, 3))
        assertEquals(0, Xp.rollsDue(3, 4, 5))
    }
}
