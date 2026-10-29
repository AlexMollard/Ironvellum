package com.ironvellum.app.data.cloud

import com.ironvellum.app.domain.FeedCard
import com.ironvellum.app.domain.SessionAudience
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SetKind
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
    fun `a card changes the fingerprint once`() {
        val card = FeedCard(9, 600.0, emptyList(), emptyMap())
        val plain = CloudSync.pushFingerprint(session, sets)
        // Below schema 32 no card is passed, so the fingerprint is the one already pushed.
        assertEquals(plain, CloudSync.pushFingerprint(session, sets, null))
        val withCard = CloudSync.pushFingerprint(session, sets, card)
        assertNotEquals(plain, withCard)
        // Stable: the same card gives the same fingerprint, so it re-pushes once and then settles.
        assertEquals(withCard, CloudSync.pushFingerprint(session, sets, card.copy()))
        val pushed = mapOf(session.id to withCard)
        assertTrue(CloudSync.pendingForPush(listOf(session to sets), pushed, mapOf(session.id to card)).isEmpty())
        assertEquals(1, CloudSync.pendingForPush(listOf(session to sets), mapOf(session.id to plain), mapOf(session.id to card)).size)
    }

    @Test
    fun `an amended workout refreshes its card`() {
        val card = FeedCard(9, 600.0, emptyList(), emptyMap())
        val pushed = mapOf(session.id to CloudSync.pushFingerprint(session, sets, card))
        val changed = card.copy(hardSets = 10)
        assertEquals(1, CloudSync.pendingForPush(listOf(session to sets), pushed, mapOf(session.id to changed)).size)
    }

    @Test
    fun `a set with kind rpe or group changes the fingerprint`() {
        val baseline = CloudSync.pushFingerprint(session, sets)
        for (changed in listOf(
            sets[0].copy(kind = SetKind.TOP),
            sets[0].copy(rpe = 8),
            sets[0].copy(supersetGroup = 1),
        )) {
            assertNotEquals(baseline, CloudSync.pushFingerprint(session, listOf(changed, sets[1])))
        }
    }

    @Test
    fun `plain sets keep the legacy fingerprint`() {
        val legacy = Objects.hash(
            session.label, session.title, session.note, session.completedAtMs, session.xpAwarded, session.strengthScore,
            sets.map { s ->
                listOf(s.exerciseName, s.setIndex, s.reps, s.weightKg, s.modifiers, s.done, s.durationSec, s.distanceM, s.grade, s.exercisePosition)
            },
        )
        assertEquals(legacy, CloudSync.pushFingerprint(session, sets))
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

    @Test
    fun `a trial whose sets moved to a dedicated exercise re-pushes and its old rows go`() {
        val pushed = mapOf(session.id to CloudSync.pushFingerprint(session, sets))
        val moved = sets.map { it.copy(exerciseName = "Weighted Bench Press") }
        val converted = session.copy(editedAtMs = 1_790_000_000_000)
        assertEquals(1, CloudSync.pendingForPush(listOf(converted to moved), pushed).size)
        val onCloud = listOf(
            SessionSetKeyDto("r1", "cloud-a", "Bench Press", 0),
            SessionSetKeyDto("r2", "cloud-a", "Bench Press", 1),
            SessionSetKeyDto("r3", "cloud-a", "Weighted Bench Press", 0),
        )
        assertEquals(listOf("r1", "r2"), CloudSync.staleSetRowIds(mapOf("cloud-a" to moved), onCloud))
    }
}
