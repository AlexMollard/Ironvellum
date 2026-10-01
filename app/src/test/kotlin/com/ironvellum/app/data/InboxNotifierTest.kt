package com.ironvellum.app.data

import com.ironvellum.app.data.cloud.CircleChange
import com.ironvellum.app.data.cloud.InboxItem
import com.ironvellum.app.data.cloud.InboxRowDto
import com.ironvellum.app.data.cloud.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxNotifierTest {

    private fun tribute(session: String, actor: String, at: Long) =
        InboxItem.NewReaction(at, actor, actor, session, "Heavy Pull", Reaction.FLAME)

    @Test
    fun `a tribute paid again on the same trial does not notify twice`() {
        val first = listOf(tribute("s1", "eli", 100))
        val announced = InboxNotifier.rememberTributes(emptyList(), InboxNotifier.coalesce(first, emptySet()))
        // Taken back and paid again: the server stamps a fresh row.
        val again = listOf(tribute("s1", "eli", 200))
        assertEquals(emptyList<InboxItem>(), InboxNotifier.coalesce(again, announced.toSet()))
        // Another ally on that trial, or the same ally on another, still notifies.
        val others = listOf(tribute("s1", "gus", 300), tribute("s2", "eli", 300))
        assertEquals(others, InboxNotifier.coalesce(others, announced.toSet()))
    }

    @Test
    fun `the tribute memory is bounded and keeps the newest`() {
        val many = (1..InboxNotifier.TRIBUTE_MEMORY + 50).map { tribute("s$it", "eli", it.toLong()) }
        val kept = InboxNotifier.rememberTributes(emptyList(), many)
        assertEquals(InboxNotifier.TRIBUTE_MEMORY, kept.size)
        assertTrue("s${InboxNotifier.TRIBUTE_MEMORY + 50}:eli" in kept)
        assertTrue("s1:eli" !in kept)
    }

    @Test
    fun `replies and circle goals parse into their own items`() {
        val reply = InboxRowDto(
            kind = "reply", occurredAt = "2026-10-01T18:00:00Z", actorId = "gus", actorName = "Gus",
            sessionId = "s9", sessionHeadline = "Leg Day", commentId = "c1", body = "Same here",
        ).toInboxItem()
        assertEquals(InboxItem.NewReply(reply!!.occurredAtMs, "gus", "Gus", "s9", "Leg Day", "c1", "Same here"), reply)
        val goal = InboxRowDto(
            kind = "band_goal", occurredAt = "2026-10-01T18:00:00Z", actorId = "rey", actorName = "Rey", body = "North Gate",
        ).toInboxItem()
        assertEquals(InboxItem.CircleGoalMet(goal!!.occurredAtMs, "rey", "Rey", "North Gate"), goal)
        // The server's wire kinds keep their old spelling; a later circle_* spelling parses too.
        val renamed = InboxRowDto(
            kind = "circle_goal", occurredAt = "2026-10-01T18:00:00Z", actorId = "rey", actorName = "Rey", body = "North Gate",
        ).toInboxItem()
        assertEquals(goal, renamed)
    }

    @Test
    fun `roster missives parse into circle notices`() {
        fun notice(kind: String) = InboxRowDto(
            kind = kind, occurredAt = "2026-10-01T18:00:00Z", actorId = "rey", actorName = "Rey", body = "North Gate",
        ).toInboxItem()
        val at = notice("circle_left")!!.occurredAtMs
        assertEquals(InboxItem.CircleNotice(at, "rey", "Rey", "North Gate", CircleChange.LEFT), notice("circle_left"))
        assertEquals(InboxItem.CircleNotice(at, "rey", "Rey", "North Gate", CircleChange.KEEPER), notice("circle_keeper"))
        assertEquals(InboxItem.CircleNotice(at, "rey", "Rey", "North Gate", CircleChange.REMOVED), notice("circle_removed"))
        // A kind a later server adds is dropped, never thrown.
        assertEquals(null, notice("circle_something_new"))
    }
}
