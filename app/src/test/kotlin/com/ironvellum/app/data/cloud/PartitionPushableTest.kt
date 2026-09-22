package com.ironvellum.app.data.cloud

import com.ironvellum.app.domain.SessionSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * partitionPushable decides both which sets are uploaded AND whether a session
 * may be watermarked. If a blank exercise name ever slipped into the pushable
 * bucket, the watermark would advance past a session whose reps/weight never
 * reached the cloud — permanently, since the fingerprint then matches.
 */
class PartitionPushableTest {

    private fun set(name: String) =
        SessionSet(exerciseId = 1, exerciseName = name, setIndex = 0, reps = 5)

    @Test
    fun `a session with a blank-name set is excluded from the watermark bucket`() {
        val (pushable, skipped) = partitionPushable(
            listOf(set("Push-up"), set(""), set("Pull-up")),
        )
        // The blank set must not ride along in the upload bucket...
        assertEquals(listOf("Push-up", "Pull-up"), pushable.map { it.exerciseName })
        // ...and its presence alone must hold the whole session back.
        assertEquals(1, skipped.size)
    }

    @Test
    fun `a clean session lands entirely in the watermark bucket`() {
        val (pushable, skipped) = partitionPushable(listOf(set("Squat"), set("Deadlift")))
        assertEquals(listOf("Squat", "Deadlift"), pushable.map { it.exerciseName })
        assertTrue(skipped.isEmpty())
    }
}
