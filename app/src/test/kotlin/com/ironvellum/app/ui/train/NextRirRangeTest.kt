package com.ironvellum.app.ui.train

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Sets sheet's reps-left chips: 0 1 2 3 4+ over one contiguous range. */
class NextRirRangeTest {

    @Test
    fun aChipOutsideTheRangeStretchesIt() {
        assertEquals(0..2, nextRirRange(0..1, 2))
        assertEquals(1..3, nextRirRange(2..3, 1))
        // "4+" reaches ten.
        assertEquals(0..10, nextRirRange(0..1, 4))
    }

    @Test
    fun anEndChipShrinksIt() {
        assertEquals(1..2, nextRirRange(0..2, 0))
        assertEquals(0..1, nextRirRange(0..2, 2))
        assertEquals(0..3, nextRirRange(0..10, 4))
    }

    @Test
    fun theLastChipIsNeverTurnedOff() {
        assertEquals(1..1, nextRirRange(1..1, 1))
        // 5 to 6 reads as "4+": it stays as it is.
        assertEquals(5..6, nextRirRange(5..6, 4))
    }

    @Test
    fun aChipInsideTheRangeMakesItTheOnlyOne() {
        assertEquals(2..2, nextRirRange(0..4, 2))
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
