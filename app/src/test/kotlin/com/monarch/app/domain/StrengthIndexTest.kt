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
        assertNull(StrengthIndex.sessionScore(listOf(10 to null, 8 to 20.0), null))
    }

    @Test
    fun `session score sums rounded reps`() {
        val score = StrengthIndex.sessionScore(listOf(10 to null), 80.0)!!
        assertEquals(StrengthIndex.repScore(10, 0.0, 80.0).toInt(), score)
    }
}
