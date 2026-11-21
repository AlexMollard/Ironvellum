package com.ironvellum.app.data.cloud

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

internal fun buildCloudClient(
    config: CloudConfig,
    lifecycle: Lifecycle,
    engine: HttpClientEngine = OkHttp.create(),
    sessions: SessionManager? = null,
    verifiers: CodeVerifierCache? = null,
) = createSupabaseClient(config.url, config.key) {
    install(Auth) {
        enableLifecycleCallbacks = false
        if (sessions != null) sessionManager = sessions
        if (verifiers != null) codeVerifierCache = verifiers
    }
    install(Postgrest)
    // R8 renames ServiceLoader entries in minified builds. Pin the shipped engine.
    httpEngine = engine
}.also { followCloudLifecycle(it.auth, lifecycle) }

/**
 * Auth 3.9.0's platform observer has no removal handle. Tie the same foreground
 * restore / background refresh pause to this SDK's Auth job instead. Cancelling
 * and joining that job also removes repeatOnLifecycle's observer on Android main.
 */
@OptIn(SupabaseInternal::class)
internal fun followCloudLifecycle(auth: Auth, lifecycle: Lifecycle) = auth.authScope.launch(Dispatchers.IO) {
    auth.awaitInitialization()
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        try {
            if (auth.config.alwaysAutoRefresh && !auth.isAutoRefreshRunning) {
                if (!auth.loadFromStorage() && auth.sessionStatus.value == SessionStatus.Initializing) {
                    auth.setSessionStatus(SessionStatus.NotAuthenticated())
                }
            }
            awaitCancellation()
        } finally {
            if (auth.isAutoRefreshRunning) {
                auth.stopAutoRefreshForCurrentSession()
                auth.setSessionStatus(SessionStatus.Initializing)
            }
        }
    }
}
