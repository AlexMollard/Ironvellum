package com.ironvellum.app.data

import com.ironvellum.app.data.db.SetLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Progression reads each variation's own last 40 sets, not whatever the last 40 of the movement happened to be. */
class SetHistoryTest {

    private fun set(session: Long, position: Int = 0, modifiers: String = "", index: Int = 0) = SetLogEntity(
        sessionId = session, exerciseId = 1, exercisePosition = position, setIndex = index,
        reps = 10, weightKg = null, modifiers = modifiers, done = true,
    )

    @Test
    fun `a variation buried behind more than 40 sets of another still has its history`() {
        // Newest first: 15 flat trials of 4 sets (60 sets), then the one deficit trial.
        val flat = (100L downTo 86L).flatMap { s -> (0..3).map { set(s, index = it) } }
        val deficit = (0..2).map { set(10, modifiers = "deficit", index = it) }
        val rows = flat + deficit
        assertEquals(1, SetHistory.blocks(rows, 0, "deficit").size)
        assertEquals(3, SetHistory.blocks(rows, 0, "deficit").single().size)
        // The flat history is cut at its own 40 sets, and holds none of the deficit ones.
        val plain = SetHistory.blocks(rows, 0, "")
        assertEquals(10, plain.size)
        assertTrue(plain.flatten().all { it.modifiers.isEmpty() })
    }

    @Test
    fun `a combination of tokens is its own history whatever the order they were picked in`() {
        val rows = listOf(
            set(5, modifiers = "deficit"),
            set(4, modifiers = "deficit, archer"),
            set(3, modifiers = "tempo, archer,deficit"),
            set(2, modifiers = "archer"),
        )
        val combo = com.ironvellum.app.domain.Variation.key("archer, deficit")
        assertEquals("archer,deficit", combo)
        assertEquals(listOf(4L, 3L), SetHistory.blocks(rows, 0, combo).map { it.first().sessionId })
        assertEquals(listOf(5L), SetHistory.blocks(rows, 0, "deficit").map { it.first().sessionId })
        assertEquals(listOf(2L), SetHistory.blocks(rows, 0, "archer").map { it.first().sessionId })
    }

    @Test
    fun `a variation never logged has no history`() {
        assertTrue(SetHistory.blocks(listOf(set(1), set(1, index = 1)), 0, "deficit").isEmpty())
    }

    @Test
    fun `the block is picked by position before the variation filter`() {
        // One trial: flat block first, deficit block second. The rite's second block is the deficit one.
        val rows = listOf(set(1, 0), set(1, 1, "deficit"))
        assertEquals(listOf("deficit"), SetHistory.blocks(rows, 1, "deficit").flatten().map { it.modifiers })
        assertTrue(SetHistory.blocks(rows, 0, "deficit").isEmpty())
    }
}
