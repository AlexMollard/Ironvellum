package com.ironvellum.app.data

import com.ironvellum.app.data.db.ProfileEntity
import com.ironvellum.app.domain.PlayerProfile
import com.ironvellum.app.ui.theme.InkStyle
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A fresh install is seeded with `ProfileEntity(name, totalXp, currentTitleId)`
 * and the Kotlin default decides its look. The owner's rule: CLEAN is the
 * default and INK is opt-in. A pre-release audit once flipped this to INK
 * from a code comment; this pins the decision against that happening again.
 */
class ProfileDefaultsTest {

    @Test
    fun `a freshly seeded profile row starts clean`() {
        assertFalse(ProfileEntity(name = "Lifter", totalXp = 0, currentTitleId = null).inkStyle)
    }

    @Test
    fun `the domain profile and the first frame start clean`() {
        assertFalse(PlayerProfile().inkStyle)
        assertFalse(InkStyle.enabled)
    }
}
