package com.ironvellum.app.data.cloud

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.reflect.typeOf

/**
 * Schema 32 adds sessions.feed_card and appends it to public_feed. Every phone already in the field
 * reads those rows with today's DTOs, so an unknown key must not fail a decode: one bad row would blank
 * a whole feed page. The serializer here is the default one of a client built the way Cloud.client()
 * builds the app's, not a hand-built Json that could differ from it.
 */
class FeedCardCompatTest {

    // Built the way Cloud.client() builds the app's client (same plugins, same engine, no serializer named).
    private val serializer = createSupabaseClient(supabaseUrl = "https://example.supabase.co", supabaseKey = "key") {
        install(Postgrest)
        httpEngine = OkHttp.create()
    }.defaultSerializer

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> decode(text: String): T = serializer.decode(typeOf<T>(), text) as T

    @Test
    fun `old DTOs decode rows carrying an extra feed_card key`() {
        val feed = decode<FeedEntryDto>(
            """{"session_id":"s1","user_id":"u1","display_name":"Rowan","level":4,"title":"","note":"",
                "label":"Pull","completed_at":"2026-10-28T09:00:00Z","xp_awarded":120,"strength_score":80,
                "sets_done":12,"reps_done":96,"current_crest_id":null,
                "feed_card":{"v":1,"hardSets":9,"volumeKg":4210.5,"prs":[],"levels":{"LATS":3}}}""",
        )
        assertEquals("s1", feed.sessionId)
        assertEquals(12, feed.setsDone)

        val friend = decode<FriendSessionDto>(
            """{"id":"s1","label":"Pull","title":"","note":"","completed_at":"2026-10-28T09:00:00Z",
                "xp_awarded":120,"strength_score":80,"session_sets":[{"count":12}],"feed_card":{"v":1}}""",
        )
        assertEquals("Pull", friend.label)

        val ally = decode<AllyWorkoutSessionDto>(
            """{"id":"s1","user_id":"u1","label":"Pull","completed_at":"2026-10-28T09:00:00Z",
                "xp_awarded":120,"strength_score":80,"feed_card":{"v":1,"hardSets":9}}""",
        )
        assertEquals("u1", ally.userId)
    }

    @Test
    fun `a null feed_card decodes too`() {
        val ally = decode<AllyWorkoutSessionDto>(
            """{"id":"s1","user_id":"u1","label":"Pull","feed_card":null}""",
        )
        assertEquals("s1", ally.id)
    }
}
