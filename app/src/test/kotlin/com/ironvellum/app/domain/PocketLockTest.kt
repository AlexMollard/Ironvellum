package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pocket lock only lets go after a deliberate hold, and shows the rest or the set in hand. */
class PocketLockTest {

    @Test
    fun `a brush or a tap never unlocks, only a full hold does`() {
        assertFalse(PocketLock.unlocks(0))
        assertFalse(PocketLock.unlocks(150))
        assertFalse(PocketLock.unlocks(PocketLock.HOLD_MS - 1L))
        assertTrue(PocketLock.unlocks(PocketLock.HOLD_MS.toLong()))
        assertTrue(PocketLock.unlocks(PocketLock.HOLD_MS + 5_000L))
    }

    @Test
    fun `the hold fills from nothing to full and never leaves that range`() {
        assertEquals(0f, PocketLock.progress(0), 0f)
        assertEquals(0.5f, PocketLock.progress(PocketLock.HOLD_MS / 2L), 0.001f)
        assertEquals(1f, PocketLock.progress(PocketLock.HOLD_MS.toLong()), 0f)
        assertEquals(1f, PocketLock.progress(60_000), 0f)
        assertEquals(0f, PocketLock.progress(-5), 0f)
    }

    @Test
    fun `the cover shows the rest while one runs, else the set in hand, else nothing`() {
        assertEquals("Rest 1:05", PocketLock.summary("1:05", "Set 3 · Squat"))
        assertEquals("Set 3 · Squat", PocketLock.summary(null, "Set 3 · Squat"))
        assertNull(PocketLock.summary(null, null))
    }
}
