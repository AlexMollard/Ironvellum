package com.monarch.app.domain

import com.monarch.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A title whose threshold is bounded by the size of a shipped catalogue must
 * never exceed that catalogue: Fifty Paths once asked for 50 distinct
 * activities against a 43-activity catalogue, could never be earned, and
 * padded the codex denominator forever. These tests derive the catalogue size
 * from the seed itself, so either side moving breaks the build here first.
 */
class TitleReachabilityTest {

    /** Activity movements are exactly the catalogue rows with a non-strength metric. */
    private val catalogueActivities: Int =
        Seed.exercises.count { !ExerciseMetric.valueOf(it.metric).isStrength }

    @Test
    fun `every distinct-activities threshold fits the activity catalogue`() {
        val thresholds = Titles.ALL
            .map { it.rule }
            .filterIsInstance<TitleRule.DistinctActivities>()
            .map { it.count }
        assertTrue("no DistinctActivities titles found", thresholds.isNotEmpty())
        val tooBig = thresholds.filter { it > catalogueActivities }
        assertTrue(
            "DistinctActivities thresholds $tooBig exceed the $catalogueActivities " +
                "activities the catalogue ships — unearnable deeds again",
            tooBig.isEmpty(),
        )
    }

    @Test
    fun `every skills-mastered threshold fits the skill tree`() {
        val thresholds = Titles.ALL
            .map { it.rule }
            .filterIsInstance<TitleRule.SkillsMastered>()
            .map { it.count }
        assertTrue("no SkillsMastered titles found", thresholds.isNotEmpty())
        val tooBig = thresholds.filter { it > Skills.ALL.size }
        assertTrue(
            "SkillsMastered thresholds $tooBig exceed the ${Skills.ALL.size} " +
                "skills the tree ships — unearnable deeds again",
            tooBig.isEmpty(),
        )
    }

    /**
     * The codex draws progress as "current of target". If an unearned deed
     * can report current == target the line says you are done when you are
     * not, which is a worse lie than reading a kilometre short — so progress
     * floors, and this pins that it can never reach the target unearned.
     */
    @Test
    fun `an unearned distance deed never reports itself complete`() {
        listOf(4.9, 0.1, 4.999).forEach { km ->
            val ledger = Titles.Ledger(totalXp = 0, workouts = 0, sets = 0, reps = 0, bestRunKm = km)
            val rule = TitleRule.LongestRun(5.0)
            assertFalse("$km km must not satisfy a 5 km deed", Titles.satisfied(rule, ledger))
            val progress = Titles.progress(rule, ledger)
            assertTrue(
                "$km km reported ${progress.current} of ${progress.target} on an unearned deed",
                progress.current < progress.target,
            )
        }
        // Earned, and only then, may the two meet.
        val done = Titles.Ledger(totalXp = 0, workouts = 0, sets = 0, reps = 0, bestRunKm = 5.0)
        val rule = TitleRule.LongestRun(5.0)
        assertTrue(Titles.satisfied(rule, done))
        assertEquals(Titles.progress(rule, done).target, Titles.progress(rule, done).current)
    }
}
