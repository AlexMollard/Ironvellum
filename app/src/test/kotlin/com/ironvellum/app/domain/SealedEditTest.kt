package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SealedEditTest {

    private val sealedAt = 1_790_000_000_000L
    private val hour = 60L * 60 * 1000

    private fun settle(xpAwarded: Int, old: Int, new: Int, total: Long = 10_000, afterMs: Long = hour, sealed: Int = xpAwarded) =
        SealedEdit.settle(xpAwarded, sealed, old, new, total, sealedAt, sealedAt + afterMs)

    @Test
    fun `an untouched set list moves nothing`() {
        val s = settle(xpAwarded = 145, old = 120, new = 120)
        assertEquals(0, s.applied)
        assertEquals(145, s.xpAwarded)
        assertFalse(s.raiseRefused)
        assertFalse(s.raiseCapped)
    }

    @Test
    fun `a raise inside the window is paid in full up to the trial's own xp`() {
        assertEquals(18, settle(xpAwarded = 100, old = 90, new = 108).applied)
        val capped = settle(xpAwarded = 100, old = 90, new = 400)
        assertEquals("at most double", 100, capped.applied)
        assertEquals(200, capped.xpAwarded)
        assertTrue(capped.raiseCapped)
    }

    @Test
    fun `a raise after the window clamps to zero but a cut still lands`() {
        val late = settle(xpAwarded = 100, old = 90, new = 150, afterMs = SealedEdit.WINDOW_MS + 1)
        assertEquals(0, late.applied)
        assertEquals(100, late.xpAwarded)
        assertTrue(late.raiseRefused)
        assertFalse(late.withinWindow)
        val cut = settle(xpAwarded = 100, old = 90, new = 78, afterMs = SealedEdit.WINDOW_MS + 1)
        assertEquals(-12, cut.applied)
        assertEquals(88, cut.xpAwarded)
    }

    @Test
    fun `the window edge is inclusive and a clock behind the seal counts as inside`() {
        assertTrue(SealedEdit.withinWindow(sealedAt, sealedAt + SealedEdit.WINDOW_MS))
        assertFalse(SealedEdit.withinWindow(sealedAt, sealedAt + SealedEdit.WINDOW_MS + 1))
        assertTrue(SealedEdit.withinWindow(sealedAt, sealedAt - hour))
    }

    @Test
    fun `repeated raises never take the trial past double what it was sealed at`() {
        // Each amendment re-prices from the last one, so a cap on the CURRENT
        // figure would ratchet 100 -> 200 -> 400. The sealed figure holds it.
        val first = settle(xpAwarded = 100, old = 90, new = 400, sealed = 100)
        assertEquals(200, first.xpAwarded)
        val second = settle(xpAwarded = first.xpAwarded, old = 400, new = 900, sealed = 100)
        assertEquals(0, second.applied)
        assertEquals(200, second.xpAwarded)
        assertTrue(second.raiseCapped)
    }

    @Test
    fun `a cut can be restored inside the window but not past double`() {
        val cut = settle(xpAwarded = 100, old = 90, new = 30, sealed = 100)
        assertEquals(40, cut.xpAwarded)
        val restored = settle(xpAwarded = cut.xpAwarded, old = 30, new = 90, sealed = 100)
        assertEquals("the slip is undone", 100, restored.xpAwarded)
        val greedy = settle(xpAwarded = 40, old = 30, new = 1_000, sealed = 100)
        assertEquals("one raise is at most the sealed figure", 140, greedy.xpAwarded)
        val again = settle(xpAwarded = greedy.xpAwarded, old = 1_000, new = 2_000, sealed = 100)
        assertEquals(200, again.xpAwarded)
    }

    @Test
    fun `a cut never takes more than the trial holds`() {
        // Re-pricing at another bodyweight can value the old sets above what
        // the trial actually paid; the row must not go negative.
        val s = settle(xpAwarded = 40, old = 120, new = 0)
        assertEquals(-40, s.applied)
        assertEquals(0, s.xpAwarded)
    }

    @Test
    fun `a cut never takes more than the ledger holds and the row moves with it`() {
        val s = settle(xpAwarded = 100, old = 100, new = 20, total = 30)
        assertEquals(-30, s.applied)
        assertEquals(70, s.xpAwarded)
    }
}
