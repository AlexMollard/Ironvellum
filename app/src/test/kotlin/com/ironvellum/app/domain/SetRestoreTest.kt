package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Undo of a removal numbers the movement's sets back into their old order, whatever happened meanwhile. */
class SetRestoreTest {

    private val before = mapOf(10L to 0, 11L to 1, 12L to 2)

    @Test
    fun `a removed middle set returns to its place and the one after it moves back`() {
        // 11 was removed: 12 closed the gap to 1, then 11 was inserted again at its old index 1.
        val current = listOf(10L to 0, 12L to 1, 11L to 1)
        assertEquals(mapOf(10L to 0, 11L to 1, 12L to 2), restoredIndexes(before, current))
    }

    @Test
    fun `a removed last set returns after the others`() {
        val current = listOf(10L to 0, 11L to 1, 12L to 2)
        assertEquals(mapOf(10L to 0, 11L to 1, 12L to 2), restoredIndexes(before, current))
    }

    @Test
    fun `a set added during the window follows the restored ones without a duplicate index`() {
        // 11 removed, 12 renumbered to 1, a new set 13 took index 2, then 11 came back at 1.
        val current = listOf(10L to 0, 12L to 1, 13L to 2, 11L to 1)
        assertEquals(mapOf(10L to 0, 11L to 1, 12L to 2, 13L to 3), restoredIndexes(before, current))
    }

    @Test
    fun `a whole removed exercise returns with its sets in order`() {
        val gone = mapOf(5L to 0, 6L to 1, 7L to 2)
        assertEquals(gone, restoredIndexes(gone, listOf(7L to 2, 5L to 0, 6L to 1)))
    }

    @Test
    fun `new sets keep their own order after the restored ones`() {
        val current = listOf(21L to 3, 20L to 2, 10L to 0)
        assertEquals(mapOf(10L to 0, 20L to 1, 21L to 2), restoredIndexes(mapOf(10L to 0), current))
    }
}
