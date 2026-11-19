package com.ironvellum.app.ui.social

import com.ironvellum.app.data.cloud.Account
import com.ironvellum.app.domain.SessionAudience
import com.ironvellum.app.domain.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnSocialProfileTest {
    private val account = Account("owner", "owner@example.invalid", "Owner", "friends")
    private val workout = WorkoutSession(id = 1, label = "Shared", startedAtMs = 1, completedAtMs = 2)

    @Test fun `friend preview excludes private imported and unfinished workouts before limiting`() {
        val sessions = List(5) { workout.copy(id = it + 10L, audience = SessionAudience.PRIVATE, xpAwarded = 999) } +
            listOf(workout.copy(id = 20, imported = true), workout.copy(id = 21, completedAtMs = null), workout)
        assertEquals(listOf(1L), ownProfileSessions(account, true, sessions).map { it.id })
        assertEquals(listOf(10L, 11L, 12L, 13L, 14L), ownProfileSessions(account, false, sessions).map { it.id })
    }

    @Test fun `friend preview fails closed when visibility is unavailable`() {
        for (unavailable in listOf(null, account.copy(profileLoaded = false), account.copy(visibility = "private"), account.copy(visibility = "unknown"))) {
            assertEquals(emptyList<WorkoutSession>(), ownProfileSessions(unavailable, true, listOf(workout)))
        }
    }
}
