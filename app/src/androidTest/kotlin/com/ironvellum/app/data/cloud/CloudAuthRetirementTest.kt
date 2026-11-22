package com.ironvellum.app.data.cloud

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.platform.app.InstrumentationRegistry
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(SupabaseInternal::class)
class CloudAuthRetirementTest {
    @Test fun onlyCurrentSdkCanReadPersistedSessionOnForeground() = verifyRetirement(pauseConstruction = false)

    @Test fun switchWaitsForStartupConstructionAndAPausedReaderCannotConstructAnOldSdk() =
        verifyRetirement(pauseConstruction = true)

    private fun verifyRetirement(pauseConstruction: Boolean) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val file = "retirement-" + UUID.randomUUID()
        val settings = SharedPreferencesSettings(context.getSharedPreferences(file, Context.MODE_PRIVATE))
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
        val a = CloudConfig("https://retirement.one.invalid", "old-test-key", false)
        val b = CloudConfig("https://retirement-one.invalid", "new-test-key", false)
        // Use the installed SDK's default key function, scoped to a fresh private test file.
        val sdkKey = Class.forName("io.github.jan.supabase.auth.SettingsUtilKt")
            .getMethod("createDefaultSettingsKey", String::class.java)
        fun sessionKey(url: String) = (sdkKey.invoke(null, url) as String) + "-session"
        fun key(config: CloudConfig) = sessionKey(config.url.removePrefix("https://"))
        assertEquals(key(a), key(b))
        val shared = SettingsSessionManager(settings, key(b))
        val built = CopyOnWriteArrayList<SupabaseClient>()
        val refreshHosts = CopyOnWriteArrayList<String>()
        val refreshed = CompletableDeferred<Unit>()
        val constructing = CountDownLatch(1)
        val finishConstruction = CountDownLatch(1)
        val readerHasSnapshot = CountDownLatch(1)
        val finishRead = CountDownLatch(1)
        val selection = CloudClientSelection { cfg ->
            if (pauseConstruction && cfg == a) {
                constructing.countDown()
                check(finishConstruction.await(10, TimeUnit.SECONDS))
            }
            val engine = MockEngine { request ->
                if (request.url.encodedPath.endsWith("/token")) {
                    refreshHosts.add(request.url.host)
                    refreshed.complete(Unit)
                }
                respond("""{"code":"invalid_grant","message":"synthetic refusal"}""", HttpStatusCode.BadRequest,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }
            buildCloudClient(cfg, owner.lifecycle, engine, SettingsSessionManager(settings, key(cfg)), MemoryCodeVerifierCache())
                .also(built::add)
        }
        suspend fun state(value: Lifecycle.State) = withContext(Dispatchers.Main) { owner.lifecycle.currentState = value }
        suspend fun observers() = withContext(Dispatchers.Main) { owner.lifecycle.observerCount }
        try {
            state(Lifecycle.State.CREATED)
            val starting = async(Dispatchers.IO) { selection.initialize(a) }
            if (pauseConstruction) {
                assertTrue(constructing.await(5, TimeUnit.SECONDS))
            } else {
                starting.await()
                withTimeout(5_000) { while (observers() != 1) delay(10) }
            }
            val reader = async(Dispatchers.IO) {
                selection.client {
                    readerHasSnapshot.countDown()
                    check(finishRead.await(15, TimeUnit.SECONDS))
                }
            }
            assertTrue(readerHasSnapshot.await(5, TimeUnit.SECONDS))
            val switching = async(start = CoroutineStart.UNDISPATCHED) {
                val accounts = AccountRepository { selection.requireConfigured }
                Cloud.reconfigure(selection, b, accounts, beforeSwap = {
                    assertTrue(selection.requireConfigured.isFailure)
                    val old = built.single()
                    assertEquals(key(a), sessionKey(old.supabaseUrl))
                    assertTrue(old.auth.config.autoSaveToStorage && old.auth.config.autoLoadFromStorage && old.auth.config.alwaysAutoRefresh)
                    assertFalse(old.auth.config.enableLifecycleCallbacks)
                    assertTrue(old.auth.authScope.coroutineContext.job.isCompleted)
                    assertEquals("The old SDK has no remaining foreground callback", 0, observers())
                }, persist = {})
            }
            if (pauseConstruction) {
                assertFalse(switching.isCompleted)
                finishConstruction.countDown()
            }
            starting.await()
            switching.await()
            val current = selection.client()
            assertEquals(key(b), sessionKey(current.supabaseUrl))
            current.auth.awaitInitialization()
            withTimeout(5_000) { while (observers() != 1) delay(10) }
            state(Lifecycle.State.STARTED)
            val session = UserSession(accessToken = "synthetic-access", refreshToken = "synthetic-refresh",
                expiresIn = 3600, tokenType = "bearer")
            current.auth.importSession(session)
            withTimeout(5_000) { while (!current.auth.isAutoRefreshRunning) delay(10) }
            assertEquals(session.refreshToken, shared.loadSession().refreshToken)
            state(Lifecycle.State.CREATED)
            withTimeout(5_000) { while (current.auth.sessionStatus.value != SessionStatus.Initializing) delay(10) }
            shared.saveSession(session.copy(expiresIn = 0, expiresAt = kotlin.time.Instant.fromEpochMilliseconds(1)))
            state(Lifecycle.State.STARTED)
            withTimeout(5_000) { refreshed.await() }
            finishRead.countDown()
            assertSame(current, reader.await())
            assertEquals(listOf("retirement-one.invalid"), refreshHosts.toList())
            assertEquals(2, built.size)
        } finally {
            finishConstruction.countDown()
            finishRead.countDown()
            state(Lifecycle.State.DESTROYED)
            built.forEach { client ->
                client.close()
                client.auth.authScope.coroutineContext.job.join()
            }
            context.deleteSharedPreferences(file)
        }
    }

}
