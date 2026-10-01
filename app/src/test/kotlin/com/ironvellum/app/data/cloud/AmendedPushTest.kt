package com.ironvellum.app.data.cloud

import com.ironvellum.app.domain.SessionAudience
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WorkoutSession
import java.util.Objects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An amended trial must re-push, and its removed sets must leave the cloud;
 * a never-amended trial must keep the fingerprint it was already pushed at,
 * or the update would re-upload the whole history once.
 */
class AmendedPushTest {

    private val sets = listOf(
        SessionSet(exerciseId = 1, exerciseName = "Bench Press", setIndex = 0, reps = 8, weightKg = 60.0, done = true),
        SessionSet(exerciseId = 1, exerciseName = "Bench Press", setIndex = 1, reps = 6, weightKg = 70.0, done = true),
    )
    private val session = WorkoutSession(id = 7, label = "Push", startedAtMs = 1, completedAtMs = 2, xpAwarded = 90)

    @Test
    fun `a never-amended trial keeps the fingerprint it was pushed at`() {
        // The formula before the amended stamp existed, verbatim.
        val legacy = Objects.hash(
            session.label, session.title, session.note, session.completedAtMs, session.xpAwarded, session.strengthScore,
            sets.map { s ->
                listOf(s.exerciseName, s.setIndex, s.reps, s.weightKg, s.modifiers, s.done, s.durationSec, s.distanceM, s.grade, s.exercisePosition)
            },
        )
        assertEquals(legacy, CloudSync.pushFingerprint(session, sets))
        val friends = session.copy(audience = SessionAudience.FRIENDS)
        val legacyFriends = Objects.hash(
            friends.label, friends.title, friends.note, friends.completedAtMs, friends.xpAwarded, friends.strengthScore,
            sets.map { s ->
                listOf(s.exerciseName, s.setIndex, s.reps, s.weightKg, s.modifiers, s.done, s.durationSec, s.distanceM, s.grade, s.exercisePosition)
            },
            "friends",
        )
        assertEquals(legacyFriends, CloudSync.pushFingerprint(friends, sets))
    }

    @Test
    fun `an amended trial re-pushes`() {
        val pushed = mapOf(session.id to CloudSync.pushFingerprint(session, sets))
        val amended = session.copy(editedAtMs = 1_790_000_000_000)
        assertNotEquals(pushed[session.id], CloudSync.pushFingerprint(amended, sets))
        assertEquals(1, CloudSync.pendingForPush(listOf(amended to sets), pushed).size)
    }

    @Test
    fun `only cloud rows the amended trial no longer has are stale`() {
        val onCloud = listOf(
            SessionSetKeyDto("r1", "cloud-a", "Bench Press", 0),
            SessionSetKeyDto("r2", "cloud-a", "Bench Press", 1),
            SessionSetKeyDto("r3", "cloud-a", "Bench Press", 2),
            SessionSetKeyDto("r4", "cloud-a", "Dip", 0),
            SessionSetKeyDto("r5", "cloud-b", "Squat", 9),
        )
        val stale = CloudSync.staleSetRowIds(mapOf("cloud-a" to sets), onCloud)
        assertEquals(listOf("r3", "r4"), stale)
        assertTrue("a trial not being pushed is never touched", "r5" !in stale)
    }
}
