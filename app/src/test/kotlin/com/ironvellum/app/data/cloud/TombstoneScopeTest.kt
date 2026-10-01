package com.ironvellum.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A trial deleted on this phone is deleted from the cloud on the next push, by
 * (user_id, local_id). The local id means nothing to another account, so the
 * waiting deletions belong to the account that was signed in here when they were
 * made; anyone else signing in must not run them.
 */
class TombstoneScopeTest {

    private val pending = listOf(4L, 9L)

    @Test
    fun `the account that was here keeps its waiting deletions`() {
        assertEquals(emptyList<Long>(), tombstonesToDrop(owner = "a", signedIn = "a", pending = pending))
    }

    @Test
    fun `another account signing in drops them`() {
        assertEquals(pending, tombstonesToDrop(owner = "a", signedIn = "b", pending = pending))
    }

    @Test
    fun `deletions made before any account was ever here are dropped, not run against the first one`() {
        assertEquals(pending, tombstonesToDrop(owner = null, signedIn = "a", pending = pending))
    }
}
