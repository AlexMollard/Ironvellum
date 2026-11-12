package com.ironvellum.app.data.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.AppearanceStore
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
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

    private fun client(storage: SessionManager, engine: MockEngine, dispatcher: CoroutineDispatcher? = null) = createSupabaseClient("https://account-test.invalid", "test-key") {
        if (dispatcher != null) coroutineDispatcher = dispatcher
        install(Auth) {
            sessionManager = storage
            autoLoadFromStorage = false
            alwaysAutoRefresh = false
            enableLifecycleCallbacks = false
        }
        install(Postgrest)
        httpEngine = engine
    }

    @Test fun restoredIdentityIsNotPublishedBeforeSdkInitializationCompletes() = runTest {
        val storage = MemorySessionManager(session())
        val engine = MockEngine { request ->
            check(request.url.encodedPath.endsWith("/profiles"))
            respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
        }
        val client = client(storage, engine, StandardTestDispatcher(testScheduler))
        try {
            val accounts = AccountRepository(client)
            val restoring = async(start = CoroutineStart.UNDISPATCHED) { accounts.restore() }
            assertNull("SDK setup must finish before importing a stored identity", accounts.account.value)
            assertNull(client.auth.currentUserOrNull())
            assertFalse(restoring.isCompleted)
            testScheduler.runCurrent()
            restoring.await().getOrThrow()
            assertEquals(USER, accounts.account.value?.userId)
            assertEquals("Test Ally", accounts.account.value?.displayName)
            assertTrue(checkNotNull(accounts.account.value).profileLoaded)
            assertEquals(USER, client.auth.currentUserOrNull()?.id)
        } finally {
            client.close()
        }
    }

    @OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
    @Test fun firstStorageFailureThenSdkPauseStillAllowsBackgroundRecovery() = runTest {
        val sdkReadStarted = CompletableDeferred<Unit>()
        val releaseSdkRead = CompletableDeferred<Unit>()
        val memory = MemorySessionManager(session())
        var reads = 0
        val storage = object : SessionManager by memory {
            override suspend fun loadSession(): UserSession {
                when (++reads) {
                    1 -> {
                        sdkReadStarted.complete(Unit)
                        releaseSdkRead.await()
                    }
                    2 -> throw IOException("app startup read failed")
                }
                return memory.loadSession()
            }
        }
        var profiles = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/my_inbox") -> respond(
                    """[{"kind":"request","occurred_at":"2026-10-01T18:00:00Z","actor_id":"ally","actor_name":"Test Ally"}]""",
                    HttpStatusCode.OK, JSON_HEADERS,
                )
                request.url.encodedPath.endsWith("/inbox_seen") -> respond("[]", HttpStatusCode.OK, JSON_HEADERS)
                request.url.encodedPath.endsWith("/profiles") -> {
                    profiles++
                    respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                }
                else -> error("Unexpected request ${request.url}")
            }
        }
        val client = createSupabaseClient("https://account-test.invalid", "test-key") {
            coroutineDispatcher = StandardTestDispatcher(testScheduler)
            install(Auth) {
                sessionManager = storage
                autoLoadFromStorage = true
                alwaysAutoRefresh = true
                enableLifecycleCallbacks = true
            }
            install(Postgrest)
            httpEngine = engine
        }
        try {
            testScheduler.runCurrent()
            sdkReadStarted.await()
            val accounts = AccountRepository(client)
            assertTrue(accounts.restore().isFailure)
            val genuinelyInitializing = async(start = CoroutineStart.UNDISPATCHED) { accounts.restore() }
            assertFalse(genuinelyInitializing.isCompleted)
            assertEquals(0, profiles)
            genuinelyInitializing.cancelAndJoin()
            releaseSdkRead.complete(Unit)
            testScheduler.runCurrent()
            assertEquals("SDK must have imported the stored identity", USER, client.auth.currentUserOrNull()?.id)
            client.auth.stopAutoRefreshForCurrentSession()
            client.auth.setSessionStatus(io.github.jan.supabase.auth.status.SessionStatus.Initializing)
            accounts.restore().getOrThrow()
            assertEquals(USER, accounts.account.value?.userId)
            assertEquals("Test Ally", accounts.account.value?.displayName)
            assertTrue(checkNotNull(accounts.account.value).profileLoaded)
            assertEquals(1, profiles)
            assertFalse("background recovery must not restart refresh", client.auth.isAutoRefreshRunning)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val sync = CloudSync((context.applicationContext as IronvellumApp).repository, accounts, client, AppearanceStore(context), checkNotNull(accounts.account.value))
            assertEquals(1, sync.inbox(force = true).getOrThrow().unread)
        } finally {
            releaseSdkRead.complete(Unit)
            client.close()
        }
    }

    @OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
    @Test fun setupPublicationCannotBeConflatedWithImmediatePause() = runTest {
        val memory = MemorySessionManager(session())
        var firstRead = true
        val storage = object : SessionManager by memory {
            override suspend fun loadSession(): UserSession {
                if (firstRead) {
                    firstRead = false
                    throw IOException("startup read failed")
                }
                return memory.loadSession()
            }
        }
        val engine = MockEngine { request ->
            check(request.url.encodedPath.endsWith("/profiles"))
            respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
        }
        val client = client(storage, engine, StandardTestDispatcher(testScheduler))
        try {
            val accounts = AccountRepository(client)
            assertTrue(accounts.restore().isFailure)
            // Publish and pause without allowing the test dispatcher a turn.
            client.auth.importSession(session(), autoRefresh = false)
            client.auth.setSessionStatus(io.github.jan.supabase.auth.status.SessionStatus.Initializing)
            val restoring = async(start = CoroutineStart.UNDISPATCHED) { accounts.restore() }
            assertEquals("restore must pass the remembered setup before dispatch", USER, client.auth.currentUserOrNull()?.id)
            testScheduler.runCurrent()
            restoring.await().getOrThrow()
            assertEquals("Test Ally", accounts.account.value?.displayName)
            assertFalse(client.auth.isAutoRefreshRunning)
        } finally {
            client.close()
        }
    }

    @OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
    @Test fun anInitializedSdkPauseDoesNotBlockStoredSessionRecovery() = runBlocking {
        withTimeout(10_000) {
            val storage = MemorySessionManager(session())
            val engine = MockEngine { request ->
                check(request.url.encodedPath.endsWith("/profiles"))
                respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                accounts.restore().getOrThrow()
                client.auth.setSessionStatus(io.github.jan.supabase.auth.status.SessionStatus.Initializing)
                accounts.restore().getOrThrow()
                assertEquals(USER, accounts.account.value?.userId)
                assertEquals("Test Ally", accounts.account.value?.displayName)
                assertEquals(USER, client.auth.currentUserOrNull()?.id)
            } finally {
                client.close()
            }
        }
    }

    @Test fun aFailedStoredSessionReadRetainsItsCauseAndCanRecover() = runBlocking {
        val memory = MemorySessionManager(session())
        var failRead = true
        val storage = object : SessionManager by memory {
            override suspend fun loadSession(): UserSession {
                if (failRead) {
                    failRead = false
                    throw IOException("temporary session read failure")
                }
                return memory.loadSession()
            }
        }
        val engine = MockEngine { request ->
            check(request.url.encodedPath.endsWith("/profiles"))
            respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
        }
        val client = client(storage, engine)
        try {
            val accounts = AccountRepository(client)
            val failed = accounts.restore()
            assertTrue(generateSequence(failed.exceptionOrNull()) { it.cause }.any { it is IOException })
            assertNull(accounts.account.value)
            accounts.restore().getOrThrow()
            assertEquals(USER, accounts.account.value?.userId)
            assertEquals("Test Ally", accounts.account.value?.displayName)
        } finally {
            client.close()
        }
    }

    @Test fun cancellationWhileReadingStoredCredentialsIsNotReportedAsSignedOut() = runBlocking {
        val memory = MemorySessionManager(session())
        var cancelRead = true
        val storage = object : SessionManager by memory {
            override suspend fun loadSession(): UserSession {
                if (cancelRead) {
                    cancelRead = false
                    throw kotlinx.coroutines.CancellationException("cancelled session read")
                }
                return memory.loadSession()
            }
        }
        val engine = MockEngine { request ->
            check(request.url.encodedPath.endsWith("/profiles"))
            respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
        }
        val client = client(storage, engine)
        try {
            val accounts = AccountRepository(client)
            try {
                accounts.restore()
                fail("session-read cancellation must propagate")
            } catch (_: kotlinx.coroutines.CancellationException) { }
            accounts.restore().getOrThrow()
            assertEquals(USER, accounts.account.value?.userId)
            assertEquals(USER, client.auth.currentUserOrNull()?.id)
        } finally {
            client.close()
        }
    }

    @OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
    @Test fun privacyChangesCanResumeAPausedSessionWithoutDroppingOtherProfileFields() = runBlocking {
        withTimeout(10_000) {
            val engine = MockEngine { request ->
                check(request.url.encodedPath.endsWith("/profiles"))
                respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
            }
            val client = client(MemorySessionManager(session()), engine)
            try {
                val accounts = AccountRepository(client)
                accounts.restore().getOrThrow()
                client.auth.setSessionStatus(io.github.jan.supabase.auth.status.SessionStatus.Initializing)
                accounts.setVisibility("friends").getOrThrow()
                assertEquals("Test Ally", accounts.account.value?.displayName)
                assertEquals("friends", accounts.account.value?.visibility)
                assertEquals(USER, client.auth.currentUserOrNull()?.id)
            } finally {
                client.close()
            }
        }
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

    @Test fun restoreOfLoadedIdentityLeavesRegisteredQueryAndProfileIntact() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var profileReads = 0
            val storage = MemorySessionManager(session())
            val engine = MockEngine { request ->
                assertTrue(request.url.encodedPath.endsWith("/profiles"))
                profileReads++
                respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                assertTrue(accounts.restore().isSuccess)
                val query = async {
                    accounts.withAccount(USER) {
                        started.complete(Unit)
                        release.await()
                        "finished"
                    }
                }
                started.await()
                assertTrue(accounts.restore().isSuccess)
                assertFalse(query.isCancelled)
                assertEquals(1, profileReads)
                assertEquals("Test Ally", accounts.account.value?.displayName)
                assertEquals("private", accounts.account.value?.visibility)
                release.complete(Unit)
                assertEquals("finished", query.await())
            } finally {
                release.complete(Unit)
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

    @Test fun signInBlockedAtProfileCannotSurviveSignOut() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val storage = MemorySessionManager()
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/token") -> respond(SESSION, HttpStatusCode.OK, JSON_HEADERS)
                    request.url.encodedPath.endsWith("/profiles") -> {
                        started.complete(Unit)
                        release.await()
                        respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                    }
                    else -> respond("{}", HttpStatusCode.OK, JSON_HEADERS)
                }
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                val signIn = async { accounts.signIn("test@example.invalid", "password") }
                started.await()
                val signOut = async(start = CoroutineStart.UNDISPATCHED) { accounts.signOut() }
                release.complete(Unit)
                try { signIn.await(); fail("superseded sign-in must be cancelled") }
                catch (_: kotlinx.coroutines.CancellationException) { }
                assertTrue(signOut.await().isSuccess)
                assertNull(accounts.account.value)
                assertNull(client.auth.currentSessionOrNull())
                assertNull(storage.loadSessionOrNull())
                assertTrue(accounts.restore().isSuccess)
                assertNull(accounts.account.value)
            } finally {
                release.complete(Unit)
                client.close()
            }
        }
    }

    @Test fun newerExplicitSignInWinsAfterSignOut() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var reads = 0
            val storage = MemorySessionManager()
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/token") -> respond(SESSION, HttpStatusCode.OK, JSON_HEADERS)
                    request.url.encodedPath.endsWith("/profiles") -> {
                        if (++reads == 1) {
                            started.complete(Unit)
                            release.await()
                        }
                        respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                    }
                    else -> respond("{}", HttpStatusCode.OK, JSON_HEADERS)
                }
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                val old = async { accounts.signIn("old@example.invalid", "password") }
                started.await()
                val logout = async(start = CoroutineStart.UNDISPATCHED) { accounts.signOut() }
                val newer = async(start = CoroutineStart.UNDISPATCHED) {
                    accounts.signIn("new@example.invalid", "password")
                }
                release.complete(Unit)
                try { old.await(); fail("older sign-in must be cancelled") }
                catch (_: kotlinx.coroutines.CancellationException) { }
                assertTrue(logout.await().isSuccess)
                assertTrue(newer.await().isSuccess)
                assertEquals("new@example.invalid", accounts.account.value?.email)
                assertNotNull(client.auth.currentSessionOrNull())
                assertNotNull(storage.loadSessionOrNull())
            } finally {
                release.complete(Unit)
                client.close()
            }
        }
    }

    @Test fun cancelledSignInClearsImportedSessionAndPropagatesCancellation() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val storage = MemorySessionManager()
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/token") -> respond(SESSION, HttpStatusCode.OK, JSON_HEADERS)
                    request.url.encodedPath.endsWith("/profiles") -> {
                        started.complete(Unit)
                        CompletableDeferred<Unit>().await()
                        respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                    }
                    else -> throw IOException("logout offline")
                }
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                val signIn = async { accounts.signIn("test@example.invalid", "password") }
                started.await()
                signIn.cancelAndJoin()
                assertTrue(signIn.isCancelled)
                assertNull(accounts.account.value)
                assertNull(client.auth.currentSessionOrNull())
                assertNull(storage.loadSessionOrNull())
            } finally {
                client.close()
            }
        }
    }

    @Test fun delayedProfileRefreshCannotRestoreSignedOutAccount() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var reads = 0
            val storage = MemorySessionManager(session())
            val engine = MockEngine { request ->
                if (request.url.encodedPath.endsWith("/profiles")) {
                    if (++reads == 1) respond("[]", HttpStatusCode.OK, JSON_HEADERS)
                    else {
                        started.complete(Unit)
                        release.await()
                        respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                    }
                } else respond("{}", HttpStatusCode.OK, JSON_HEADERS)
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                assertTrue(accounts.restore().isSuccess)
                assertFalse(accounts.account.value!!.profileLoaded)
                val refresh = async { accounts.refreshProfile() }
                started.await()
                val logout = async(start = CoroutineStart.UNDISPATCHED) { accounts.signOut() }
                release.complete(Unit)
                try { refresh.await(); fail("stale profile must be cancelled") }
                catch (_: kotlinx.coroutines.CancellationException) { }
                assertTrue(logout.await().isSuccess)
                assertNull(accounts.account.value)
                assertNull(client.auth.currentSessionOrNull())
                assertNull(storage.loadSessionOrNull())
            } finally {
                release.complete(Unit)
                client.close()
            }
        }
    }

    @Test fun queuedFailedSignInDoesNotSuppressRestoredAccount() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val storage = MemorySessionManager(session())
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/profiles") -> {
                        started.complete(Unit)
                        release.await()
                        respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                    }
                    else -> respond("""{"message":"invalid credentials"}""", HttpStatusCode.BadRequest, JSON_HEADERS)
                }
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                val restore = async { accounts.restore() }
                started.await()
                val signIn = async(start = CoroutineStart.UNDISPATCHED) {
                    accounts.signIn("other@example.invalid", "wrong")
                }
                release.complete(Unit)
                assertTrue(restore.await().isSuccess)
                assertTrue(signIn.await().isFailure)
                assertEquals(USER, accounts.account.value?.userId)
                assertTrue(accounts.account.value!!.profileLoaded)
                assertNotNull(client.auth.currentSessionOrNull())
            } finally {
                release.complete(Unit)
                client.close()
            }
        }
    }

    @Test fun cancelledRestorePreservesImportedIdentityAndSession() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val storage = MemorySessionManager(session())
            val engine = MockEngine {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
                respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                val restore = async { accounts.restore() }
                started.await()
                restore.cancelAndJoin()
                assertTrue(restore.isCancelled)
                assertEquals(USER, accounts.account.value?.userId)
                assertFalse(accounts.account.value!!.profileLoaded)
                assertNotNull(client.auth.currentSessionOrNull())
                assertNotNull(storage.loadSessionOrNull())
            } finally {
                client.close()
            }
        }
    }

    @Test fun cancelledPreImportSignInPreservesExistingIdentityAndSession() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val storage = MemorySessionManager(session())
            val engine = MockEngine { request ->
                if (request.url.encodedPath.endsWith("/profiles")) {
                    respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                } else {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    respond(SESSION, HttpStatusCode.OK, JSON_HEADERS)
                }
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                assertTrue(accounts.restore().isSuccess)
                val original = client.auth.currentSessionOrNull()
                val signIn = async { accounts.signIn("other@example.invalid", "password") }
                started.await()
                signIn.cancelAndJoin()
                assertTrue(signIn.isCancelled)
                assertEquals(USER, accounts.account.value?.userId)
                assertEquals(original, client.auth.currentSessionOrNull())
                assertNotNull(storage.loadSessionOrNull())
            } finally {
                client.close()
            }
        }
    }

    @Test fun cancelledPreImportSignInPreservesRefreshedSameUserSession() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val storage = MemorySessionManager(session())
            val engine = MockEngine { request ->
                if (request.url.encodedPath.endsWith("/profiles")) {
                    respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                } else {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    respond(SESSION, HttpStatusCode.OK, JSON_HEADERS)
                }
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                assertTrue(accounts.restore().isSuccess)
                val signIn = async { accounts.signIn("other@example.invalid", "password") }
                started.await()
                val rotated = session().copy(accessToken = "rotated-token", refreshToken = "rotated-refresh")
                client.auth.importSession(rotated)
                signIn.cancelAndJoin()
                assertTrue(signIn.isCancelled)
                assertEquals(USER, accounts.account.value?.userId)
                assertEquals("rotated-token", client.auth.currentSessionOrNull()?.accessToken)
                assertEquals("rotated-token", storage.loadSessionOrNull()?.accessToken)
            } finally {
                client.close()
            }
        }
    }

    @Test fun oldAccountProfileCannotOverwriteNewSignIn() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var profileReads = 0
            val storage = MemorySessionManager(session())
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/token") -> respond(SECOND_SESSION, HttpStatusCode.OK, JSON_HEADERS)
                    request.url.encodedPath.endsWith("/profiles") -> when (++profileReads) {
                        1 -> respond("[]", HttpStatusCode.OK, JSON_HEADERS)
                        2 -> {
                            started.complete(Unit)
                            release.await()
                            respond(SECOND_PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                        }
                        else -> respond(SECOND_PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                    }
                    else -> respond("{}", HttpStatusCode.OK, JSON_HEADERS)
                }
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                assertTrue(accounts.restore().isSuccess)
                val signIn = async { accounts.signIn("second@example.invalid", "password") }
                started.await()
                val refresh = async(start = CoroutineStart.UNDISPATCHED) { accounts.refreshProfile() }
                release.complete(Unit)
                assertTrue(signIn.await().isSuccess)
                try { refresh.await(); fail("old profile must not publish") }
                catch (_: kotlinx.coroutines.CancellationException) { }
                assertEquals(SECOND_USER, accounts.account.value?.userId)
                assertEquals(SECOND_USER, client.auth.currentUserOrNull()?.id)
            } finally {
                release.complete(Unit)
                client.close()
            }
        }
    }

    @Test fun queuedVisibilityChangePreservesRestoredProfileFields() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val storage = MemorySessionManager(session())
            val engine = MockEngine { request ->
                if (request.method.value == "PATCH") {
                    respond("", HttpStatusCode.NoContent, JSON_HEADERS)
                } else {
                    started.complete(Unit)
                    release.await()
                    respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                }
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                val restore = async { accounts.restore() }
                started.await()
                assertFalse(accounts.account.value!!.profileLoaded)
                val visibility = async(start = CoroutineStart.UNDISPATCHED) { accounts.setVisibility("public") }
                release.complete(Unit)
                assertTrue(restore.await().isSuccess)
                assertTrue(visibility.await().isSuccess)
                assertEquals("Test Ally", accounts.account.value?.displayName)
                assertEquals("public", accounts.account.value?.visibility)
                assertTrue(accounts.account.value!!.profileLoaded)
            } finally {
                release.complete(Unit)
                client.close()
            }
        }
    }

    @Test fun retryableProfileFailureKeepsNewSessionAndCanRefreshWithoutCredentials() = runBlocking {
        for (serverFailure in listOf(false, true)) {
            val storage = MemorySessionManager(session())
            var profileReads = 0
            var tokenRequests = 0
            var profileUnavailable = true
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/token") -> {
                        tokenRequests++
                        respond(SECOND_SESSION, HttpStatusCode.OK, JSON_HEADERS)
                    }
                    request.url.encodedPath.endsWith("/profiles") -> when (++profileReads) {
                        1 -> respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                        else -> if (!profileUnavailable) respond(SECOND_PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                            else if (serverFailure) respond("{}", HttpStatusCode.ServiceUnavailable, JSON_HEADERS)
                            else throw IOException("profile temporarily unavailable")
                    }
                    else -> error("Unexpected request ${request.url}")
                }
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                accounts.restore().getOrThrow()
                val signedIn = accounts.signIn("second@example.invalid", "password").getOrThrow()
                assertEquals(SECOND_USER, signedIn.userId)
                assertFalse(signedIn.profileLoaded)
                assertEquals("", signedIn.displayName)
                assertEquals(SECOND_USER, storage.loadSession().user?.id)
                profileUnavailable = false
                accounts.refreshProfile().getOrThrow()
                assertEquals("Second Ally", accounts.account.value?.displayName)
                assertTrue(accounts.account.value!!.profileLoaded)
                assertEquals(1, tokenRequests)
            } finally {
                client.close()
            }
        }
    }

    @Test fun failedProfileAfterNewSessionImportClearsOldIdentityAndNewSession() = runBlocking {
        val storage = MemorySessionManager(session())
        var profileReads = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/token") -> respond(SECOND_SESSION, HttpStatusCode.OK, JSON_HEADERS)
                request.url.encodedPath.endsWith("/profiles") -> {
                    if (++profileReads == 1) respond(PROFILE, HttpStatusCode.OK, JSON_HEADERS)
                    else respond("[]", HttpStatusCode.OK, JSON_HEADERS)
                }
                else -> respond("{}", HttpStatusCode.OK, JSON_HEADERS)
            }
        }
        val client = client(storage, engine)
        try {
            val accounts = AccountRepository(client)
            assertTrue(accounts.restore().isSuccess)
            assertEquals(USER, accounts.account.value?.userId)
            assertTrue(accounts.signIn("second@example.invalid", "password").isFailure)
            assertNull(accounts.account.value)
            assertNull(client.auth.currentSessionOrNull())
            assertNull(storage.loadSessionOrNull())
        } finally {
            client.close()
        }
    }

    @Test fun confirmationSignupDoesNotMistakeAnotherAccountsTokenRefreshForItsOwnSession() = runBlocking {
        withTimeout(10_000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val storage = MemorySessionManager(session())
            val engine = MockEngine { request ->
                val body = when {
                    request.url.encodedPath.endsWith("/profiles") -> PROFILE
                    request.url.encodedPath.endsWith("/display_name_available") -> "true"
                    request.url.encodedPath.endsWith("/signup") -> {
                        started.complete(Unit)
                        release.await()
                        """{"id":"$SECOND_USER","aud":"authenticated","email":"second@example.invalid"}"""
                    }
                    else -> error("Unexpected isolated request ${request.url.encodedPath}")
                }
                respond(body, HttpStatusCode.OK, JSON_HEADERS)
            }
            val client = client(storage, engine)
            try {
                val accounts = AccountRepository(client)
                accounts.restore().getOrThrow()
                val signup = async { accounts.signUp("second@example.invalid", "password", "New Lifter") }
                started.await()
                client.auth.importSession(session().copy(accessToken = "rotated-access", refreshToken = "rotated-refresh"))
                release.complete(Unit)
                assertEquals(SignUpOutcome.ConfirmEmail, signup.await().getOrThrow())
                assertEquals(USER, accounts.account.value?.userId)
                assertEquals("test@example.invalid", accounts.account.value?.email)
                assertEquals("rotated-access", client.auth.currentSessionOrNull()?.accessToken)
                assertEquals("rotated-access", storage.loadSessionOrNull()?.accessToken)
            } finally {
                release.complete(Unit)
                client.close()
            }
        }
    }

    companion object {
        private const val USER = "d23fd1d4-a085-456e-9ea9-b1214b464918"
        private const val PROFILE = "[{\"id\":\"$USER\",\"display_name\":\"Test Ally\",\"visibility\":\"private\"}]"
        private const val SESSION = """{"access_token":"isolated-test-token","refresh_token":"isolated-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"$USER","aud":"authenticated","email":"test@example.invalid"}}"""
        private const val SECOND_USER = "1c94fbc8-0f02-4f86-9870-d870645895e1"
        private const val SECOND_PROFILE = "[{\"id\":\"$SECOND_USER\",\"display_name\":\"Second Ally\",\"visibility\":\"friends\"}]"
        private const val SECOND_SESSION = """{"access_token":"second-test-token","refresh_token":"second-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"$SECOND_USER","aud":"authenticated","email":"second@example.invalid"}}"""
        private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
