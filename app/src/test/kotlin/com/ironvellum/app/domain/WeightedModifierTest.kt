package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** "weighted" follows the added load on a bodyweight movement. */
class WeightedModifierTest {

    @Test
    fun `load arriving adds the tag after the others`() {
        assertEquals("weighted", modifiersAfterLoadChange("", becameLoaded = true, anyLoaded = true))
        assertEquals("deficit, paused, weighted", modifiersAfterLoadChange("deficit, paused", becameLoaded = true, anyLoaded = true))
        // Already there, in any case: nothing is rewritten or doubled.
        assertEquals("Weighted,deficit", modifiersAfterLoadChange("Weighted,deficit", becameLoaded = true, anyLoaded = true))
    }

    @Test
    fun `the last load leaving removes only the tag`() {
        assertEquals("deficit, paused", modifiersAfterLoadChange("deficit, weighted, paused", becameLoaded = false, anyLoaded = false))
        assertEquals("", modifiersAfterLoadChange("Weighted", becameLoaded = false, anyLoaded = false))
        // Another set still loaded: the tag stays.
        assertEquals("weighted", modifiersAfterLoadChange("weighted", becameLoaded = false, anyLoaded = true))
    }

    @Test
    fun `a change between loads leaves a hand-removed tag alone`() {
        assertEquals("deficit", modifiersAfterLoadChange("deficit", becameLoaded = false, anyLoaded = true))
        assertEquals("", modifiersAfterLoadChange("", becameLoaded = false, anyLoaded = false))
    }
}
