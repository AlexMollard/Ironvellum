package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthIndexTest {

    @Test
    fun `heavier athlete scores more for identical reps`() {
        val light = StrengthIndex.repScore(reps = 10, addedKg = 0.0, bodyweightKg = 50.0)
        val heavy = StrengthIndex.repScore(reps = 10, addedKg = 0.0, bodyweightKg = 80.0)
        // User's rule: 10 pull-ups at 80 kg beats 10 pull-ups at 50 kg.
        assertTrue(heavy > light)
    }

    @Test
    fun `added load scales the score`() {
        val bodyOnly = StrengthIndex.repScore(10, 0.0, 80.0)
        val weighted = StrengthIndex.repScore(10, 20.0, 80.0)
        assertTrue(weighted > bodyOnly)
        // Weighted adds exactly the added weight per rep on top of bodyweight.
        assertEquals(bodyOnly * (100.0 / 80.0), weighted, 0.0001)
    }

    @Test
    fun `zero or negative reps and weight score zero`() {
        assertEquals(0.0, StrengthIndex.repScore(0, 0.0, 80.0), 0.0001)
        assertEquals(0.0, StrengthIndex.repScore(5, 0.0, 0.0), 0.0001)
    }

    @Test
    fun `session score is null without a bodyweight reading`() {
        assertNull(
            StrengthIndex.sessionScore(
                listOf(effort(10), effort(8, addedKg = 20.0)),
                null,
            ),
        )
    }

    @Test
    fun `session score sums rounded reps`() {
        val score = StrengthIndex.sessionScore(listOf(effort(10)), 80.0)!!
        assertEquals(StrengthIndex.repScore(10, 0.0, 80.0).toInt(), score)
    }

    /**
     * The reported bug: a hold's seconds were typed into the reps box, so a
     * 60-second hollow hold scored as sixty bodyweight repetitions — more
     * than a hard set of anything. Seconds now convert at the same rate XP
     * uses, so a minute is twelve rep-equivalents.
     */
    @Test
    fun `a hold scores on its seconds, not as that many reps`() {
        val asReps = StrengthIndex.repScore(60, null, 80.0)
        val asHold = StrengthIndex.holdScore(60, null, 80.0)
        assertEquals(StrengthIndex.repScore(12, null, 80.0), asHold, 0.0001)
        assertTrue("hold \$asHold must be far under \$asReps", asHold < asReps / 4)
    }

    @Test
    fun `a longer hold scores more and added load still counts`() {
        assertTrue(StrengthIndex.holdScore(120, null, 80.0) > StrengthIndex.holdScore(60, null, 80.0))
        assertTrue(StrengthIndex.holdScore(60, 20.0, 80.0) > StrengthIndex.holdScore(60, null, 80.0))
        assertEquals(0.0, StrengthIndex.holdScore(0, null, 80.0), 0.0001)
    }

    /** A session mixing both kinds must score each on its own unit. */
    @Test
    fun `session score mixes reps and holds`() {
        val mixed = StrengthIndex.sessionScore(
            listOf(effort(10), effort(0, holdSeconds = 60)),
            80.0,
        )!!
        val expected = StrengthIndex.repScore(10, null, 80.0) + StrengthIndex.holdScore(60, null, 80.0)
        assertEquals(expected.toInt(), mixed)
    }

    private fun effort(reps: Int, holdSeconds: Int? = null, addedKg: Double? = null) =
        StrengthIndex.Effort(reps, holdSeconds, addedKg)
}
