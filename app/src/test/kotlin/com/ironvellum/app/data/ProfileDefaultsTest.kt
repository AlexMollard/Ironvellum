package com.ironvellum.app.data

import com.ironvellum.app.data.db.ProfileEntity
import com.ironvellum.app.domain.PlayerProfile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A fresh install is seeded with `ProfileEntity(name, totalXp, currentTitleId)`
 * and the Kotlin default decides its look. INK is the product's appearance
 * (MIGRATION_25_26 restored it for every existing lifter); a CLEAN default
 * would hand every new lifter the generic look the migration removed.
 */
class ProfileDefaultsTest {

    @Test
    fun `a freshly seeded profile row wears the ink look`() {
        assertTrue(ProfileEntity(name = "Lifter", totalXp = 0, currentTitleId = null).inkStyle)
    }

    @Test
    fun `the domain profile before Room emits wears the ink look`() {
        assertTrue(PlayerProfile().inkStyle)
    }
}
