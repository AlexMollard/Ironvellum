package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** A rank-up is a band never held before, once. */
class RankUpTest {

    @Test
    fun `the first seal after the change starts the mark silently`() {
        assertEquals(RankUp.Outcome(2, null), RankUp.check(null, Rank.INTERMEDIATE, Rank.INTERMEDIATE))
    }

    @Test
    fun `the first seal still celebrates a real gain`() {
        assertEquals(RankUp.Outcome(2, Rank.INTERMEDIATE), RankUp.check(null, Rank.NOVICE, Rank.INTERMEDIATE))
        assertEquals(RankUp.Outcome(0, Rank.UNTRAINED), RankUp.check(null, null, Rank.UNTRAINED))
    }

    @Test
    fun `a new band celebrates and raises the mark`() {
        assertEquals(RankUp.Outcome(3, Rank.ADVANCED), RankUp.check(2, Rank.INTERMEDIATE, Rank.ADVANCED))
    }

    @Test
    fun `regaining a band held before is quiet`() {
        assertEquals(RankUp.Outcome(3, null), RankUp.check(3, Rank.INTERMEDIATE, Rank.ADVANCED))
        assertEquals(RankUp.Outcome(3, null), RankUp.check(3, Rank.ADVANCED, Rank.INTERMEDIATE))
        assertEquals(RankUp.Outcome(3, null), RankUp.check(3, Rank.ADVANCED, null))
    }
}
