package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.SealedEdit
import org.junit.Assert.assertEquals
import org.junit.Test

class AmendXpLineTest {

    private fun line(xpAwarded: Int, old: Int, new: Int, lateMs: Long = 0) =
        amendXpLine(SealedEdit.settle(xpAwarded, xpAwarded, old, new, 10_000, 0, lateMs))

    @Test
    fun `the confirmation says what the amendment does to xp`() {
        assertEquals("+18 XP", line(100, 90, 108))
        assertEquals("−12 XP", line(100, 90, 78))
        assertEquals("XP unchanged", line(100, 90, 90))
        assertEquals("XP unchanged — edits after 48 h can't raise it", line(100, 90, 150, SealedEdit.WINDOW_MS + 1))
        assertEquals("+100 XP (capped at double what it first paid)", line(100, 90, 400))
    }
}
