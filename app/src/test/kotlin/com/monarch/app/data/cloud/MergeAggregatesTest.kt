package com.monarch.app.data.cloud

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cloud row is the only copy of a hunter's progress. Every case here is a
 * way to destroy it that no build, lint or screenshot would catch, and that the
 * user would only discover as lost levels.
 */
class MergeAggregatesTest {

    private fun local(
        level: Int = 1,
        totalXp: Long = 0,
        streakDays: Int = 0,
        titlesCount: Int = 0,
        lifetimeStrength: Long = 0,
    ) = ProfileDto(
        id = "hunter-1",
        displayName = "Kaida",
        visibility = "friends",
        level = level,
        totalXp = totalXp,
        streakDays = streakDays,
        titlesCount = titlesCount,
        lifetimeStrength = lifetimeStrength,
        currentTitleId = "the-awakened",
    )

    @Test
    fun `a fresh install does not overwrite real cloud progress`() {
        // The scenario that motivated the merge: reinstall, sign in, sync.
        // Local is LV 1 / 0 XP and the cloud holds the only record of LV 7.
        val merged = mergeAggregates(
            local = local(level = 1, totalXp = 0, titlesCount = 0, lifetimeStrength = 0),
            remote = ProfileAggregatesDto(level = 7, totalXp = 5400, titlesCount = 12, lifetimeStrength = 98_000),
        )
        assertEquals(7, merged.level)
        assertEquals(5400, merged.totalXp)
        assertEquals(12, merged.titlesCount)
        assertEquals(98_000, merged.lifetimeStrength)
    }

    @Test
    fun `genuine local progress still advances the cloud`() {
        // The merge must not be a one-way ratchet in the cloud's favour, or
        // training after a sync would never persist.
        val merged = mergeAggregates(
            local = local(level = 9, totalXp = 7200, titlesCount = 15, lifetimeStrength = 120_000),
            remote = ProfileAggregatesDto(level = 7, totalXp = 5400, titlesCount = 12, lifetimeStrength = 98_000),
        )
        assertEquals(9, merged.level)
        assertEquals(7200, merged.totalXp)
        assertEquals(15, merged.titlesCount)
        assertEquals(120_000, merged.lifetimeStrength)
    }

    @Test
    fun `an unknown remote column keeps the local value instead of zeroing it`() {
        // Null means "column absent/unknown" — a pre-migration row, or a column
        // the select could not read. Treating it as 0 would push a real profile
        // down to nothing on the next sync.
        val merged = mergeAggregates(
            local = local(level = 4, totalXp = 2100, titlesCount = 6, lifetimeStrength = 33_000),
            remote = ProfileAggregatesDto(level = null, totalXp = null, titlesCount = null, lifetimeStrength = null),
        )
        assertEquals(4, merged.level)
        assertEquals(2100, merged.totalXp)
        assertEquals(6, merged.titlesCount)
        assertEquals(33_000, merged.lifetimeStrength)
    }

    @Test
    fun `a broken streak is allowed to fall back to zero`() {
        // streakDays is the one aggregate that must NOT be maxed: a missed day
        // legitimately resets it, and a merged streak would advertise a dead
        // streak forever.
        val merged = mergeAggregates(
            local = local(streakDays = 0, level = 7),
            remote = ProfileAggregatesDto(level = 7),
        )
        assertEquals(0, merged.streakDays)
    }

    @Test
    fun `live profile choices are not subject to the merge`() {
        // Equipping a title or changing visibility must take effect even when
        // every cumulative field came from the cloud.
        val merged = mergeAggregates(
            local = local(level = 1).copy(
                displayName = "Shadow",
                visibility = "private",
                currentTitleId = "monarch",
            ),
            remote = ProfileAggregatesDto(level = 40),
        )
        assertEquals("Shadow", merged.displayName)
        assertEquals("private", merged.visibility)
        assertEquals("monarch", merged.currentTitleId)
        assertEquals(40, merged.level)
    }

    @Test
    fun `the merge reads the same column names the query asks for`() {
        // Constructing the DTO in Kotlin cannot catch @SerialName drift, and
        // drift here fails in the data-destroying direction: a renamed column
        // decodes to null, the merge then keeps the fresh-install value, and
        // the push overwrites a real cloud profile. So decode the exact row
        // shape push() selects (CloudSync.kt:67 Columns.list) instead.
        val row = """
            {"level":7,"total_xp":5400,"titles_count":12,"lifetime_strength":98000}
        """.trimIndent()
        val remote = Json.decodeFromString<ProfileAggregatesDto>(row)
        val merged = mergeAggregates(local = local(level = 1, totalXp = 0), remote = remote)
        assertEquals(7, merged.level)
        assertEquals(5400, merged.totalXp)
        assertEquals(12, merged.titlesCount)
        assertEquals(98_000, merged.lifetimeStrength)
    }
}
