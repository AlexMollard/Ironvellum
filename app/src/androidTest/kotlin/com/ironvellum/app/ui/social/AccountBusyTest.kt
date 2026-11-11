package com.ironvellum.app.ui.social

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.AppearanceStore
import com.ironvellum.app.data.cloud.Account
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.CloudSync
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountBusyTest {
    @Test fun cancellingAnOlderSignInDoesNotStrandBusyOrEnableControlsDuringAnotherOperation() = runBlocking {
        withTimeout(10_000) {
            val profileStarted = CompletableDeferred<Unit>()
            val releaseProfile = CompletableDeferred<Unit>()
            val resetStarted = CompletableDeferred<Unit>()
            val releaseReset = CompletableDeferred<Unit>()
            val engine = MockEngine { request ->
                val body = when {
                    request.url.encodedPath.endsWith("/token") -> SESSION
                    request.url.encodedPath.endsWith("/profiles") -> { profileStarted.complete(Unit); releaseProfile.await(); PROFILE }
                    request.url.encodedPath.endsWith("/recover") -> { resetStarted.complete(Unit); releaseReset.await(); "{}" }
                    request.url.encodedPath.endsWith("/logout") -> "{}"
                    else -> error("Unexpected isolated request ${request.url.encodedPath}")
                }
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val client = createSupabaseClient("https://account-busy.invalid", "test-key") {
                install(Auth) { sessionManager = MemorySessionManager(); autoLoadFromStorage = false; alwaysAutoRefresh = false; enableLifecycleCallbacks = false }
                install(Postgrest)
                httpEngine = engine
            }
            val models = ViewModelStore()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val accounts = AccountRepository(client)
            val cloud = CloudSync((context.applicationContext as IronvellumApp).repository, accounts, client, AppearanceStore(context), Account(USER, "test@example.invalid", "Busy Test", "private"))
            lateinit var vm: AccountViewModel
            try {
                instrumentation.runOnMainSync {
                    vm = AccountViewModel(accounts, cloud)
                    models.put("account-busy", vm)
                    vm.signIn("test@example.invalid", "password")
                }
                profileStarted.await()
                instrumentation.runOnMainSync { vm.sendResetCode("test@example.invalid") }
                resetStarted.await()
                assertTrue(vm.ui.value.busy)
                val logout = async(start = CoroutineStart.UNDISPATCHED) { accounts.signOut() }
                releaseProfile.complete(Unit)
                logout.await().getOrThrow()
                assertTrue("another request is still active", vm.ui.value.busy)
                releaseReset.complete(Unit)
                vm.ui.first { !it.busy }
                assertFalse(vm.ui.value.busy)
                assertNull(vm.ui.value.account)
            } finally {
                releaseProfile.complete(Unit)
                releaseReset.complete(Unit)
                instrumentation.runOnMainSync { models.clear() }
                client.close()
            }
        }
    }

    companion object {
        private const val USER = "6f5a6e6f-0f25-43d5-b881-edc97cb47cc4"
        private const val PROFILE = "[{\"id\":\"$USER\",\"display_name\":\"Busy Test\",\"visibility\":\"private\"}]"
        private const val SESSION = """{"access_token":"isolated-access","refresh_token":"isolated-refresh","expires_in":3600,"token_type":"bearer","user":{"id":"$USER","aud":"authenticated","email":"test@example.invalid"}}"""
    }
}
