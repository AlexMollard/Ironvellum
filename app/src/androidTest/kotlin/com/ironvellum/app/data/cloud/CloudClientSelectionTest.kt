package com.ironvellum.app.data.cloud

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
class CloudClientSelectionTest {
    @Test fun replacedSdkHasFinishedItsAuthWorkBeforeTheNextSessionIsAvailable() = runBlocking {
        val old = CloudConfig("https://retired.invalid", "test-first", false)
        val next = CloudConfig("https://current.invalid", "test-next", false)
        val selection = CloudClientSelection { config ->
            createSupabaseClient(config.url, config.key) {
                install(Auth) {
                    sessionManager = MemorySessionManager()
                    codeVerifierCache = io.github.jan.supabase.auth.MemoryCodeVerifierCache()
                    enableLifecycleCallbacks = false
                }
                httpEngine = MockEngine { error("Unexpected transport call during retirement") }
            }
        }
        selection.select(old)
        val client = selection.client()
        try {
            val job = client.auth.authScope.coroutineContext[kotlinx.coroutines.Job]!!
            assertTrue(job.isActive)
            selection.select(next)
            assertTrue("Old SDK Auth scope must finish before publishing the new backend", job.isCompleted)
        } finally {
            client.close()
            selection.client().close()
        }
    }

    @Test fun inFlightConstructionCannotPublishAcrossBackendOrKeyChanges() = runBlocking {
        val a = CloudConfig("https://first.invalid", "first-key", false)
        val b = CloudConfig("https://second.invalid", "second-key", false)
        for (end in listOf(b, a.copy(key = "new-key"), a)) {
            val started = CountDownLatch(1)
            val proceed = CountDownLatch(1)
            val built = CopyOnWriteArrayList<SupabaseClient>()
            val destinations = CopyOnWriteArrayList<Pair<String, String?>>()
            val selection = CloudClientSelection { cfg ->
                createSupabaseClient(cfg.url, cfg.key) {
                    install(Auth) {
                        sessionManager = MemorySessionManager()
                        codeVerifierCache = io.github.jan.supabase.auth.MemoryCodeVerifierCache()
                        autoLoadFromStorage = false
                        alwaysAutoRefresh = false
                        enableLifecycleCallbacks = false
                    }
                    httpEngine = MockEngine { request ->
                        check(request.url.encodedPath.endsWith("/token"))
                        destinations.add(request.url.host to request.headers["apikey"])
                        respond("""{"code":"invalid_credentials","message":"synthetic refusal"}""", HttpStatusCode.BadRequest,
                            headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }.also(built::add)
            }
            try {
                selection.initialize(a)
                val reader = async(Dispatchers.IO) {
                    selection.client {
                        started.countDown()
                        check(proceed.await(5, TimeUnit.SECONDS))
                    }
                }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                selection.select(b)
                if (end != b) selection.select(end)
                proceed.countDown()
                val active = reader.await()
                assertSame(active, selection.client())
                assertNotSame(built.first(), active)
                assertEquals(end, selection.config.value)
                assertTrue(AccountRepository(active).signIn("test@example.invalid", "synthetic-test-value").isFailure)
                assertEquals(listOf(end.url.substringAfter("//") to end.key), destinations)
            } finally {
                proceed.countDown()
                built.forEach { it.close() }
            }
        }
    }
}
