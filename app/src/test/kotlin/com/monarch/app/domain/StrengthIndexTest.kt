package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class StrengthIndexTest {

    @Test
    fun `heavier athlete scores more for identical reps`() {
        val light = StrengthIndex.repScore("Pull-up", reps = 10, addedKg = 0.0, bodyweightKg = 50.0)
        val heavy = StrengthIndex.repScore("Pull-up", reps = 10, addedKg = 0.0, bodyweightKg = 80.0)
        // User's rule: 10 pull-ups at 80 kg beats 10 pull-ups at 50 kg.
        assertTrue(heavy > light)
    }

    @Test
    fun `added load scales the score`() {
        val bodyOnly = StrengthIndex.repScore("Pull-up", 10, 0.0, 80.0)
        val weighted = StrengthIndex.repScore("Pull-up", 10, 20.0, 80.0)
        assertTrue(weighted > bodyOnly)
        // Weighted adds exactly the added weight per rep on top of bodyweight.
        assertEquals(bodyOnly * (100.0 / 80.0), weighted, 0.0001)
    }

    @Test
    fun `zero or negative reps and weight score zero`() {
        assertEquals(0.0, StrengthIndex.repScore("Pull-up", 0, 0.0, 80.0), 0.0001)
        assertEquals(0.0, StrengthIndex.repScore("Pull-up", 5, 0.0, 0.0), 0.0001)
    }

    /**
     * The tie this weighting exists to break: at equal reps and bodyweight a
     * tier-V movement used to score exactly what a tier-I movement scored
     * (42.5 both ways at 80 kg). The damped weight must separate them, and
     * cap the gap at 4x — sqrt of the XP intensity ratio of 16.
     */
    @Test
    fun `a tier V movement outscores a tier I movement at equal reps`() {
        val hardest = StrengthIndex.repScore("One-Arm Pull-up", 10, 0.0, 80.0)
        val easiest = StrengthIndex.repScore("Bodyweight Squat", 10, 0.0, 80.0)
        assertTrue("hardest $hardest easiest $easiest", hardest > easiest)
        assertEquals(4.0, hardest / easiest, 0.0001)
    }

    /**
     * The volume-farm the taper exists to kill: 100 easy reps used to score
     * exactly 10x a 10-rep set and bank it forever on the summed leaderboard.
     * More reps must still score more, but past the full-value band each
     * extra rep pays a third, so the ratio lands well under 10.
     */
    @Test
    fun `a 100-rep set outscores a 10-rep set by less than ten times`() {
        val big = StrengthIndex.repScore("Bodyweight Squat", 100, 0.0, 80.0)
        val small = StrengthIndex.repScore("Bodyweight Squat", 10, 0.0, 80.0)
        assertTrue(big > small)
        val ratio = big / small
        assertTrue("ratio $ratio must be tapered under 10x", ratio < 10.0)
        // And it must match XP's taper arithmetic exactly: (10 + 90/3) / 10 = 4.
        assertEquals(4.0, ratio, 0.0001)
    }

    @Test
    fun `session score is null without a bodyweight reading`() {
        assertNull(
            StrengthIndex.sessionScore(
                listOf(effort("Pull-up", 10), effort("Dip", 8, addedKg = 20.0)),
                null,
            ),
        )
    }

    @Test
    fun `session score sums rounded reps`() {
        // sessionScore rounds the SUM, not each set — flooring the per-set
        // double here read 84 against a correctly-rounded 85.
        val score = StrengthIndex.sessionScore(listOf(effort("Pull-up", 10)), 80.0)!!
        assertEquals(StrengthIndex.repScore("Pull-up", 10, 0.0, 80.0).roundToInt(), score)
    }

    /**
     * The reported bug: a hold's seconds were typed into the reps box, so a
     * 60-second hollow hold scored as sixty bodyweight repetitions — more
     * than a hard set of anything. Seconds now convert at the same rate XP
     * uses, so a minute is twelve rep-equivalents.
     */
    @Test
    fun `a hold scores on its seconds, not as that many reps`() {
        val asReps = StrengthIndex.repScore("Hollow Hold", 60, null, 80.0)
        val asHold = StrengthIndex.holdScore("Hollow Hold", 60, null, 80.0)
        assertEquals(StrengthIndex.repScore("Hollow Hold", 12, null, 80.0), asHold, 0.0001)
        assertTrue("hold \$asHold must be far under \$asReps", asHold < asReps / 2)
    }

    @Test
    fun `a longer hold scores more and added load still counts`() {
        assertTrue(
            StrengthIndex.holdScore("Hollow Hold", 120, null, 80.0) >
                StrengthIndex.holdScore("Hollow Hold", 60, null, 80.0),
        )
        assertTrue(
            StrengthIndex.holdScore("Hollow Hold", 60, 20.0, 80.0) >
                StrengthIndex.holdScore("Hollow Hold", 60, null, 80.0),
        )
        assertEquals(0.0, StrengthIndex.holdScore("Hollow Hold", 0, null, 80.0), 0.0001)
    }

    /** A session mixing both kinds must score each on its own unit. */
    @Test
    fun `session score mixes reps and holds`() {
        val mixed = StrengthIndex.sessionScore(
            listOf(effort("Pull-up", 10), effort("Hollow Hold", 0, holdSeconds = 60)),
            80.0,
        )!!
        val expected = StrengthIndex.repScore("Pull-up", 10, null, 80.0) +
            StrengthIndex.holdScore("Hollow Hold", 60, null, 80.0)
        assertEquals(expected.toInt(), mixed)
    }

    /**
     * A hold and a rep movement at the same tier keep their existing
     * relationship: the hold is its twelve rep-equivalents, never its sixty
     * seconds-as-reps, and weighting cancels because the tier is the same.
     */
    @Test
    fun `a hold and a rep movement at the same tier keep their relationship`() {
        // "Active Bar Hang" (hold) and "Bodyweight Squat" are both tier I.
        val hold = StrengthIndex.holdScore("Active Bar Hang", 60, null, 80.0)
        val asTwelveReps = StrengthIndex.repScore("Bodyweight Squat", 12, null, 80.0)
        assertEquals(asTwelveReps, hold, 0.0001)
        val asSixtyReps = StrengthIndex.repScore("Bodyweight Squat", 60, null, 80.0)
        assertTrue(hold < asSixtyReps)
    }

    private fun effort(name: String, reps: Int, holdSeconds: Int? = null, addedKg: Double? = null) =
        StrengthIndex.Effort(name, reps, holdSeconds, addedKg)
}
