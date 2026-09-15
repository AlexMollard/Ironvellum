package com.monarch.app.data.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Account screen uses this to decide whether to nag the player with the
 * ClaimNamePanel. A mis-anchored regex either nags forever on a real name
 * (treated as "Hunter"-prefixed but user-chosen) or never offers the panel on
 * a fresh seeded account.
 */
class UnclaimedHandleTest {

    @Test
    fun `only the seeded Hunter handle with exactly four digits counts as unclaimed`() {
        assertTrue(isUnclaimedHandle("Hunter1234"))

        // Boundaries of the seed format — each of these is a name the player
        // could legitimately hold and must count as CLAIMED:
        assertFalse(isUnclaimedHandle(null))          // signed out
        assertFalse(isUnclaimedHandle("Hunter123"))   // too few digits
        assertFalse(isUnclaimedHandle("Hunter12345")) // too many digits
        assertFalse(isUnclaimedHandle("hunter1234"))  // different casing is a choice
        assertFalse(isUnclaimedHandle("Hunter1234x")) // suffix is a choice
    }

    @Test
    fun `the signed-out guard fails cloud calls instead of handing out a null identity`() {
        // AccountRepository has no offline path to a signed-in state, so only
        // the signed-out side is provable here; it guards every social call.
        val guarded = requireAccount(AccountRepository())
        assertTrue(guarded.isFailure)
        assertFalse(guarded.exceptionOrNull()?.message.isNullOrBlank())
    }
}
