package com.ironvellum.app.data

import com.ironvellum.app.data.db.ProfileEntity
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A fresh install is seeded with `ProfileEntity(name, totalXp, currentTitleId)`.
 * The retired `inkStyle` column stays in the schema; a new row must still
 * start at false so it never reads as a stored choice.
 */
class ProfileDefaultsTest {

    @Test
    fun `a freshly seeded profile row starts with the retired flag off`() {
        assertFalse(ProfileEntity(name = "Lifter", totalXp = 0, currentTitleId = null).inkStyle)
    }
}
