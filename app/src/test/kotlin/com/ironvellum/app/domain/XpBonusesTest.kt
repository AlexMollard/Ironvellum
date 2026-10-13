package com.ironvellum.app.domain

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trial-level facts: decided from the other trials alone, so a reseal can never pay one twice. */
class XpBonusesTest {
    private val zone = ZoneOffset.UTC
    private val pullUp = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
    private val exercises = mapOf(1L to pullUp)

    // 2026-10-05 is a Monday.
    private fun ms(day: String, hour: Int = 9) =
        LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3_600_000L

    private fun trial(id: Long, day: String, sets: Int = 3, imported: Boolean = false, completed: Boolean = true): Pair<WorkoutSession, List<SessionSet>> {
        val start = ms(day)
        return WorkoutSession(
            id = id, label = "t", startedAtMs = start, completedAtMs = if (completed) start + 3_600_000 else null, imported = imported,
        ) to List(sets) { SessionSet(exerciseId = 1, exerciseName = "Pull-up", setIndex = it, reps = 6, done = true) }
    }

    private fun facts(
        self: Pair<WorkoutSession, List<SessionSet>>,
        vararg others: Pair<WorkoutSession, List<SessionSet>>,
        muscles: Collection<Muscle> = Xp.WEEK_COVERED_MUSCLES,
    ): Xp.TrialFacts {
        val book = SessionXp(exercises, others.toList() + self, { 79.3 }, excludeSessionId = self.first.id, coverageMuscles = muscles)
        return book.factsFor(self.first, self.second, planned = null, quest = false, zone = zone)
    }

    @Test
    fun theVeryFirstTrialEarnsFirstOfTheWeekButNeverWelcomeBack() {
        val f = facts(trial(1, "2026-10-07"))
        assertTrue(f.firstOfWeek)
        assertFalse(f.welcomeBack)
    }

    @Test
    fun welcomeBackNeedsTenDaysWithoutATrial() {
        val earlier = trial(1, "2026-09-25")
        // The gap is measured from the end of the last trial (an hour long here).
        assertTrue(facts(trial(2, "2026-10-06"), earlier).welcomeBack)
        assertFalse(facts(trial(2, "2026-10-05"), earlier).welcomeBack)
        assertFalse(facts(trial(2, "2026-10-04"), earlier).welcomeBack)
    }

    @Test
    fun firstOfTheWeekIsMondayToSunday() {
        val sunday = trial(1, "2026-10-04")
        // Monday starts a new week: first again.
        assertTrue(facts(trial(2, "2026-10-05"), sunday).firstOfWeek)
        // Wednesday of the week a Monday trial already started: not first.
        assertFalse(facts(trial(3, "2026-10-07"), trial(2, "2026-10-05")).firstOfWeek)
        // Sunday still belongs to the week that began on the Monday before it.
        assertFalse(facts(trial(3, "2026-10-11"), trial(2, "2026-10-05")).firstOfWeek)
    }

    @Test
    fun aLaterTrialNeverTakesAnEarlierTrialsBonusAway() {
        val a = trial(1, "2026-10-05")
        val b = trial(2, "2026-10-07")
        // Sealing, unsealing and resealing A gives A the same facts whether or not B has since been sealed.
        assertEquals(facts(a), facts(a, b))
        assertTrue(facts(a, b).firstOfWeek)
        assertFalse(facts(b, a).firstOfWeek)
    }

    @Test
    fun anUnsealedTrialIsNotATrialYet() {
        val open = trial(1, "2026-10-05", completed = false)
        assertTrue(facts(trial(2, "2026-10-07"), open).firstOfWeek)
    }

    @Test
    fun anImportedTrialEarnsNoBonusButCountsAsATrialForTheNextOne() {
        val imported = trial(1, "2026-10-05", imported = true)
        assertFalse(facts(imported).earnsBonuses)
        // It is history: the next trial that week is not the first, and a gap is measured from it.
        assertFalse(facts(trial(2, "2026-10-07"), imported).firstOfWeek)
        assertFalse(facts(trial(2, "2026-10-12"), imported).welcomeBack)
        assertTrue(facts(trial(2, "2026-10-20"), imported).welcomeBack)
    }

    @Test
    fun weekCoveredIsPaidByTheTrialThatCrossesTheLineAndOnlyOnce() {
        val lats = listOf(Muscle.LATS)
        val a = trial(1, "2026-10-05", sets = 2)
        val b = trial(2, "2026-10-07", sets = 6)
        val c = trial(3, "2026-10-09", sets = 6)
        // A alone leaves the muscle short.
        assertFalse(facts(a, muscles = lats).weekCovered)
        // B takes it from short to covered.
        assertTrue(facts(b, a, muscles = lats).weekCovered)
        // C finds it already covered.
        assertFalse(facts(c, a, b, muscles = lats).weekCovered)
        // Unseal and reseal B once C has been sealed after it: B still pays, C still does not.
        assertTrue(facts(b, a, c, muscles = lats).weekCovered)
        assertFalse(facts(c, a, b, muscles = lats).weekCovered)
    }

    @Test
    fun weekCoveredIsPerWeekAndNeedsEveryMuscle() {
        val lats = listOf(Muscle.LATS)
        // Last week's sets do not count towards this week.
        val lastWeek = trial(1, "2026-09-30", sets = 8)
        assertFalse(facts(trial(2, "2026-10-05", sets = 2), lastWeek, muscles = lats).weekCovered)
        // The major groups are the default: a pull-up-only week does not cover them all.
        assertFalse(facts(trial(2, "2026-10-05", sets = 20)).weekCovered)
    }

    @Test
    fun weekCoveredNamesTheMajorGroupsOnly() {
        val required = Xp.WEEK_COVERED_MUSCLES
        listOf(Muscle.UPPER_CHEST, Muscle.MID_CHEST, Muscle.LOWER_CHEST, Muscle.LATS, Muscle.RHOMBOIDS, Muscle.TRAPS,
            Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS, Muscle.BICEPS, Muscle.TRICEPS, Muscle.QUADS,
            Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.ABS).forEach { assertTrue(it.name, it in required) }
        listOf(Muscle.NECK, Muscle.TIBIALIS, Muscle.ADDUCTORS, Muscle.ABDUCTORS, Muscle.ROTATOR_CUFF, Muscle.FOREARMS)
            .forEach { assertFalse("$it must not block the bonus", it in required) }
        assertEquals(16, required.size)
    }

    @Test
    fun smallMusclesNeverBlockTheBonus() {
        // Covering only the required muscles pays, whatever the excluded ones did: lats alone stand in for the set here.
        val lats = Xp.WEEK_COVERED_MUSCLES.filter { it == Muscle.LATS }
        assertTrue(facts(trial(2, "2026-10-07", sets = 8), muscles = lats).weekCovered)
        // Rotator cuff, forearms and the rest are not in the default set, so a pull-up week is judged on the required ones only.
        assertFalse(Muscle.FOREARMS in Xp.WEEK_COVERED_MUSCLES)
    }
}
