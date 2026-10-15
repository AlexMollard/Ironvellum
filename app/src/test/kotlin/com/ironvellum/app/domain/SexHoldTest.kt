package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** A score scaled by sex waits for a chosen sex; with one it reads as ever. */
class SexHoldTest {

    @Test
    fun anUnsetSexHoldsEveryScaledScore() {
        assertEquals("Add sex in Profile", SexHold.rank("Silver II", sexUnset = true))
        assertEquals("Add sex in Profile", SexHold.line("from 3 Oct", sexUnset = true))
        assertEquals("—", SexHold.figure("19.8", sexUnset = true))
    }

    @Test
    fun aChosenSexShowsTheScoreUnchanged() {
        assertEquals("Silver II", SexHold.rank("Silver II", sexUnset = false))
        assertEquals("from 3 Oct", SexHold.line("from 3 Oct", sexUnset = false))
        assertEquals("19.8", SexHold.figure("19.8", sexUnset = false))
    }

    @Test
    fun onlyBarsThatDifferBySexAreScaled() {
        assertEquals(true, SexHold.deedScaled(TitleRule.LiftMultiple(setOf("bench press"), 1.0, 0.5)))
        assertEquals(false, SexHold.deedScaled(TitleRule.LiftMultiple(setOf("bench press"), 1.0, 1.0)))
        assertEquals(false, SexHold.deedScaled(TitleRule.LongestHold(60)))
        assertEquals(false, SexHold.skillScaled("Not A Technique"))
    }
}
