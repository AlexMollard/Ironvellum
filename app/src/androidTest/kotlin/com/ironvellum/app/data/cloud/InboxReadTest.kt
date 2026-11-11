package com.ironvellum.app.data.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.AppearanceStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The real CloudSync inbox API, with SDK auth and isolated HTTP/storage, never a real account. */
@RunWith(AndroidJUnit4::class)
class InboxReadTest {
    private class Fixture {
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val seenStarted = CompletableDeferred<Unit>()
        val releaseSeen = CompletableDeferred<Unit>()
        val watermarkStarted = CompletableDeferred<Unit>()
        val releaseWatermark = CompletableDeferred<Unit>()
        val freshReadStarted = CompletableDeferred<Unit>()
        var holdWatermark = false
        var failWatermark = false
        var holdFirstRead = false
        var holdSeen = false
        var failRead = false
        private val readCalls = java.util.concurrent.atomic.AtomicInteger()
        val reads: Int get() = readCalls.get()
        var seenCalls = 0
        var eventAt = OLD
        var eventCount = 1
        var seenAt: String? = null
        private val engine = MockEngine { request ->
            val body = when {
                request.url.encodedPath.endsWith("/profiles") -> PROFILE
                request.url.encodedPath.endsWith("/my_inbox") -> {
                    val readNumber = readCalls.incrementAndGet()
                    if (readNumber == 2) freshReadStarted.complete(Unit)
                    val captured = eventAt
                    val count = eventCount
                    if (holdFirstRead && reads == 1) { readStarted.complete(Unit); releaseRead.await() }
                    if (failRead) throw IOException("temporary inbox failure")
                    (1..count).joinToString(prefix = "[", postfix = "]") {
                        """{"kind":"request","occurred_at":"$captured","actor_id":"ally-$it","actor_name":"Test Ally $it"}"""
                    }
                }
                request.url.encodedPath.endsWith("/mark_inbox_seen") -> {
                    seenCalls++
                    seenAt = OLD
                    seenStarted.complete(Unit)
                    if (holdSeen) releaseSeen.await()
                    "null"
                }
                request.url.encodedPath.endsWith("/inbox_seen") -> {
                    if (failWatermark) throw IOException("watermark read unavailable")
                    if (holdWatermark) { watermarkStarted.complete(Unit); releaseWatermark.await() }
                    seenAt?.let { """[{"seen_at":"$it"}]""" } ?: "[]"
                }
                request.url.encodedPath.endsWith("/logout") -> "{}"
                else -> error("Unexpected isolated request ${request.url.encodedPath}")
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client: SupabaseClient = createSupabaseClient("https://inbox-test.invalid", "test-key") {
            install(Auth) {
                sessionManager = MemorySessionManager(UserSession("test-access", "test-refresh", expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = USER)))
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                enableLifecycleCallbacks = false
            }
            install(Postgrest) { maxRetries = 0 }
            httpEngine = engine
        }
        val account = AccountRepository(client)
        suspend fun sync(): CloudSync {
            account.restore().getOrThrow()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            return CloudSync((context.applicationContext as IronvellumApp).repository, account, client, AppearanceStore(context), checkNotNull(account.account.value))
        }
        suspend fun close() { releaseRead.complete(Unit); releaseSeen.complete(Unit); releaseWatermark.complete(Unit); client.close() }
    }

    @Test fun acknowledgementAfterAnOlderReadCannotResurrectItsUnreadBadge() = runBlocking {
        withTimeout(10_000) {
            val fixture = Fixture()
            try {
                val sync = fixture.sync()
                fixture.holdFirstRead = true
                val read = async { sync.inbox(force = true) }
                fixture.readStarted.await()
                val seen = async(start = CoroutineStart.UNDISPATCHED) { sync.markInboxSeen(USER, fixture.client) }
                fixture.seenStarted.await()
                seen.await().getOrThrow()
                fixture.releaseRead.complete(Unit)
                assertEquals(0, read.await().getOrThrow().unread)
                assertEquals(0, sync.inboxUnread.value)
                assertEquals(0, sync.inbox().getOrThrow().unread)
                assertEquals(0, sync.inboxUnread.value)
                assertEquals("seen must invalidate the pre-seen cached inbox", 2, fixture.reads)
            } finally { fixture.close() }
        }
    }

    @Test fun aLateSeenResponseCannotEraseAnEventFetchedAfterIt() = runBlocking {
        withTimeout(10_000) {
            val fixture = Fixture()
            try {
                val sync = fixture.sync()
                assertEquals(1, sync.inbox().getOrThrow().unread)
                fixture.holdSeen = true
                val seen = async { sync.markInboxSeen(USER, fixture.client) }
                fixture.seenStarted.await()
                fixture.eventAt = NEW
                val refresh = async(start = CoroutineStart.UNDISPATCHED) { sync.inbox(force = true) }
                assertEquals(1, refresh.await().getOrThrow().unread)
                assertEquals(1, sync.inboxUnread.value)
                fixture.releaseSeen.complete(Unit)
                seen.await().getOrThrow()
                assertEquals(1, sync.inboxUnread.value)
            } finally { fixture.close() }
        }
    }

    @Test fun forcedRefreshDoesNotWaitForOrGetOverwrittenByAnOlderRead() = runBlocking {
        withTimeout(10_000) {
            val fixture = Fixture()
            try {
                val sync = fixture.sync()
                fixture.holdFirstRead = true
                fixture.eventCount = 2
                val old = async { sync.inbox() }
                fixture.readStarted.await()
                fixture.eventAt = NEW
                fixture.eventCount = 1
                assertEquals(1, sync.inbox(force = true).getOrThrow().unread)
                fixture.releaseRead.complete(Unit)
                assertEquals(2, old.await().getOrThrow().unread)
                assertEquals(1, sync.inboxUnread.value)
            } finally { fixture.close() }
        }
    }

    @Test fun signOutCancelsAnInFlightInboxInsteadOfWaitingForItsNetworkResponse() = runBlocking {
        withTimeout(10_000) {
            val fixture = Fixture()
            try {
                val sync = fixture.sync()
                fixture.holdFirstRead = true
                val read = async { sync.inbox(force = true) }
                fixture.readStarted.await()
                fixture.account.signOut().getOrThrow()
                try { read.await(); fail("the old account's query must be cancelled") }
                catch (_: kotlinx.coroutines.CancellationException) { }
                assertNull(fixture.account.account.value)
                assertNull(fixture.client.auth.currentSessionOrNull())
            } finally { fixture.close() }
        }
    }

    @Test fun aFailedRefreshDoesNotEraseTheLastSuccessfulUnreadCount() = runBlocking {
        val fixture = Fixture()
        try {
            val sync = fixture.sync()
            sync.inbox(force = true).getOrThrow()
            fixture.failRead = true
            assertTrue(sync.inbox(force = true).isFailure)
            assertEquals(1, sync.inboxUnread.value)
        } finally { fixture.close() }
    }

    @Test fun failedAcknowledgementWatermarkKeepsLastKnownStateAndCanRecover() = runBlocking {
        val fixture = Fixture()
        try {
            val sync = fixture.sync()
            sync.inbox().getOrThrow()
            fixture.failWatermark = true
            assertTrue(sync.markInboxSeen(USER, fixture.client).isFailure)
            assertEquals("an unconfirmed watermark must not fabricate an empty inbox", 1, sync.inboxUnread.value)
            fixture.failWatermark = false
            assertEquals(0, sync.inbox().getOrThrow().unread)
            assertEquals(0, sync.inboxUnread.value)
        } finally { fixture.close() }
    }

    @Test fun ordinaryReadsStillJoinWhileTheAcknowledgementWatermarkIsPending() = runBlocking {
        withTimeout(10_000) {
            val fixture = Fixture()
            try {
                val sync = fixture.sync()
                sync.inbox().getOrThrow()
                fixture.holdWatermark = true
                val seen = async { sync.markInboxSeen(USER, fixture.client) }
                fixture.watermarkStarted.await()
                val first = async(start = CoroutineStart.UNDISPATCHED) { sync.inbox() }
                fixture.freshReadStarted.await()
                val joined = async(start = CoroutineStart.UNDISPATCHED) { sync.inbox() }
                fixture.releaseWatermark.complete(Unit)
                seen.await().getOrThrow()
                assertEquals(0, first.await().getOrThrow().unread)
                assertEquals(0, joined.await().getOrThrow().unread)
                assertEquals("ordinary reads must share one fresh HTTP inbox read", 2, fixture.reads)
            } finally { fixture.close() }
        }
    }

    @Test fun aMismatchedAcknowledgementNeverMarksTheCurrentUsersInboxSeen() = runBlocking {
        val fixture = Fixture()
        try {
            val sync = fixture.sync()
            sync.inbox(force = true).getOrThrow()
            try {
                sync.markInboxSeen("another-account", fixture.client)
                fail("an obsolete acknowledgement must be cancelled")
            } catch (_: kotlinx.coroutines.CancellationException) { }
            assertEquals(0, fixture.seenCalls)
            assertEquals(1, sync.inboxUnread.value)
        } finally { fixture.close() }
    }

    companion object {
        private const val USER = "89d4d6f6-14c1-426a-b6c4-c98a11728cd7"
        private const val OLD = "2026-11-11T01:00:00Z"
        private const val NEW = "2026-11-11T01:01:00Z"
        private const val PROFILE = "[{\"id\":\"$USER\",\"display_name\":\"Inbox Test\",\"visibility\":\"private\"}]"
    }
}
