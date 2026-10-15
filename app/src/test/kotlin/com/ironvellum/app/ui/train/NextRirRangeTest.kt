package com.ironvellum.app.ui.train

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Sets sheet's reps-left chips: 0 1 2 3 4+ over one contiguous range. */
class NextRirRangeTest {

    @Test
    fun aTapWithOneValueChosenStretchesTheRangeToIt() {
        assertEquals(1..2, nextRirRange(1..1, 2))
        assertEquals(0..1, nextRirRange(1..1, 0))
        assertEquals(1..3, nextRirRange(3..3, 1))
        // "4+" reaches ten.
        assertEquals(1..10, nextRirRange(1..1, 4))
        assertEquals(2..10, nextRirRange(4..10, 2))
    }

    @Test
    fun aTapWithARangeChosenStartsOverWithThatValue() {
        assertEquals(0..0, nextRirRange(0..2, 0))
        assertEquals(2..2, nextRirRange(0..2, 2))
        assertEquals(1..1, nextRirRange(0..2, 1))
        assertEquals(4..10, nextRirRange(0..2, 4))
        assertEquals(3..3, nextRirRange(0..10, 3))
    }

    @Test
    fun theOnlyChosenValueIsKept() {
        assertEquals(1..1, nextRirRange(1..1, 1))
        // 5 to 6 reads as "4+": it stays as it is.
        assertEquals(5..6, nextRirRange(5..6, 4))
        assertEquals(4..10, nextRirRange(4..10, 4))
    }

    @Test
    fun theLineUnderTheChipsStatesTheRange() {
        assertEquals("Stop with 0–1 reps left", rirRangeLine(0..1, false))
        assertEquals("Stop with 2 reps left", rirRangeLine(2..2, false))
        assertEquals("Stop with 1 rep left", rirRangeLine(1..1, false))
        assertEquals("Stop with 4 or more reps left", rirRangeLine(4..10, false))
        assertEquals("Stop with 2 or more reps left", rirRangeLine(2..10, false))
        assertEquals("Stop around RPE 9–10", rirRangeLine(0..1, true))
        assertEquals("Stop around RPE 8", rirRangeLine(2..2, true))
        assertEquals("Stop around RPE 6 or lower", rirRangeLine(4..10, true))
    }

    @Test
    fun theSpokenRangeNamesTheWholeState() {
        assertEquals("0 to 1", rirRangeSpoken(0..1, false))
        assertEquals("4 or more", rirRangeSpoken(4..10, false))
        assertEquals("RPE 9 to 10", rirRangeSpoken(0..1, true))
    }

    @Test
    fun theDropsHeaderSaysWhereTheDropsFall() {
        assertEquals("1 drop after the last set", dropsHeader(com.ironvellum.app.domain.SetScheme(dropSets = 1, dropCount = 1)))
        assertEquals(
            "2 drops after each of the last 3 sets",
            dropsHeader(com.ironvellum.app.domain.SetScheme(dropSets = 3, dropCount = 2)),
        )
    }
}
