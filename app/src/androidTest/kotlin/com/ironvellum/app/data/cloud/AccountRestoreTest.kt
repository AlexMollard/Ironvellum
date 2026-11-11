package com.ironvellum.app.data.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
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

/** Real SDK session import/logout over an isolated transport and in-memory storage. */
@RunWith(AndroidJUnit4::class)
class AccountRestoreTest {
    private fun session() = UserSession(
        accessToken = "isolated-test-token", refreshToken = "isolated-refresh", expiresIn = 3600, tokenType = "bearer",
        user = UserInfo(aud = "authenticated", id = USER, email = "test@example.invalid"),
    )

    private fun client(storage: SessionManager, engine: MockEngine) = createSupabaseClient("https://account-test.invalid", "test-key") {
        install(Auth) {
            sessionManager = storage
            autoLoadFromStorage = false
            alwaysAutoRefresh = false
            enableLifecycleCallbacks = false
        }
        install(Postgrest)
        httpEngine = engine
    }

    @Test fun aRestoreInFlightCannotPublishAnAccountAfterExplicitSignOut() = runBlocking {
        withTimeout(10_000) {
            val profileStarted = CompletableDeferred<Unit>()
            val releaseProfile = CompletableDeferred<Unit>()
            var profileReads = 0
            val storage = MemorySessionManager(session())
            val engine = MockEngine { request ->
                if (request.url.encodedPath.endsWith("/profiles")) {
                    profileReads++
                    profileStarted.complete(Unit)
                    releaseProfile.await()
                    respond(PROFILE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    assertTrue(request.url.encodedPath.endsWith("/logout"))
                    respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                val restore = async { accounts.restore() }
                profileStarted.await()
                val signOut = async(start = CoroutineStart.UNDISPATCHED) { accounts.signOut() }
                assertNull("local identity clears before network logout", accounts.account.value)
                releaseProfile.complete(Unit)
                assertTrue(restore.await().isSuccess)
                assertTrue(signOut.await().isSuccess)
                assertNull(accounts.account.value)
                assertNull(client.auth.currentSessionOrNull())
                assertNull(storage.loadSessionOrNull())
                assertTrue(accounts.restore().isSuccess)
                assertNull("automatic recovery cannot undo an explicit sign-out", accounts.account.value)
                assertEquals(1, profileReads)
            } finally {
                releaseProfile.complete(Unit)
                client.close()
            }
        }
    }

    @Test fun aFailedSessionImportRetainsItsCauseAndCanRecover() = runBlocking {
        val memory = MemorySessionManager(session())
        var failSave = true
        val storage = object : SessionManager by memory {
            override suspend fun saveSession(session: UserSession) {
                if (failSave) {
                    failSave = false
                    throw IOException("temporary storage failure")
                }
                memory.saveSession(session)
            }
        }
        val engine = MockEngine { request ->
            assertTrue(request.url.encodedPath.endsWith("/profiles"))
            respond(PROFILE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = client(storage, engine)
        try {
            val accounts = AccountRepository(client)
            val failure = checkNotNull(accounts.restore().exceptionOrNull())
            assertTrue(InboxPolling.shouldRetry(failure, 0))
            assertNull(accounts.account.value)
            assertTrue(accounts.restore().isSuccess)
            assertEquals(USER, accounts.account.value?.userId)
        } finally {
            client.close()
        }
    }

    companion object {
        private const val USER = "d23fd1d4-a085-456e-9ea9-b1214b464918"
        private const val PROFILE = "[{\"id\":\"$USER\",\"display_name\":\"Test Ally\",\"visibility\":\"private\"}]"
    }
}
