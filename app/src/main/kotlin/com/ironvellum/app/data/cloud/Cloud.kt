package com.ironvellum.app.data.cloud

import android.content.Context
import android.content.SharedPreferences
import com.ironvellum.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException

/** The schema literal the newest migration writes into [Cloud.NEEDED_SCHEMA_VERSION]. */
const val NEEDED_SCHEMA_VERSION = 16

/**
 * The backend the app currently talks to. [isDefault] distinguishes the
 * maintainer's shared cloud from a lifter's own project: Google sign-in only
 * exists on the default, because the OAuth web client id belongs to the
 * maintainer's Google project and Supabase config.
 */
data class CloudConfig(val url: String, val key: String, val isDefault: Boolean)

/** Outcome of probing a candidate backend's [NEEDED_SCHEMA_VERSION] beacon. */
sealed interface ProbeResult {
    /** The backend answered with at least the schema the app needs. */
    data class Ready(val version: Int) : ProbeResult
    /** The backend is live but its migrations stop short of what the app expects. */
    data class Outdated(val have: Int, val need: Int) : ProbeResult
    /** Reached the project, but the beacon function is missing — migrations unapplied. */
    data object NoSchema : ProbeResult
    /** The project answered 401 — the key is wrong (or a secret, not publishable). */
    data object BadKey : ProbeResult
    /** Nothing answered — wrong host, offline, or a paused free project. */
    data object Unreachable : ProbeResult
}

/**
 * Pure resolution of the active backend: a prefs override wins, then the
 * BuildConfig default, then nothing. Blank override halves are ignored — a
 * half-typed URL must not silently disable the cloud. Free of Android types
 * so the order is unit-testable.
 */
internal fun resolveCloudConfig(
    overrideUrl: String?,
    overrideKey: String?,
    defaultUrl: String,
    defaultKey: String,
): CloudConfig? {
    val url = overrideUrl?.trim().orEmpty()
    val key = overrideKey?.trim().orEmpty()
    if (url.isNotBlank() && key.isNotBlank()) return CloudConfig(url, key, isDefault = false)
    val dUrl = defaultUrl.trim()
    val dKey = defaultKey.trim()
    if (dUrl.isNotBlank() && dKey.isNotBlank()) return CloudConfig(dUrl, dKey, isDefault = true)
    return null
}

/** Google sign-in only works against the default backend (see [CloudConfig]). */
internal fun googleConfiguredFor(config: CloudConfig?, webClientId: String): Boolean =
    config?.isDefault == true && webClientId.isNotBlank()

/**
 * Pure classification of a probe answer. [status] null means the request never
 * completed. On a 200 the body must parse as the beacon's integer — anything
 * else reads as "this is a Supabase project, but not one prepared for us".
 */
internal fun probeResultFor(status: Int?, code: String?, versionText: String?): ProbeResult = when {
    status == 401 -> ProbeResult.BadKey
    code == "PGRST202" -> ProbeResult.NoSchema
    status == null -> ProbeResult.Unreachable
    status in 200..299 -> {
        val have = versionText?.trim()?.trim('"')?.toIntOrNull()
        when {
            have == null -> ProbeResult.NoSchema
            have < NEEDED_SCHEMA_VERSION -> ProbeResult.Outdated(have, NEEDED_SCHEMA_VERSION)
            else -> ProbeResult.Ready(have)
        }
    }
    else -> ProbeResult.Unreachable
}

/**
 * Single entry point to the cloud. `config` is the source of truth: the
 * BuildConfig default unless the lifter pointed the app at their own Supabase
 * project from Settings. Every repository then answers with a readable
 * Result.failure instead of crashing or silently doing nothing.
 */
object Cloud {
    const val NEEDED_SCHEMA_VERSION = com.ironvellum.app.data.cloud.NEEDED_SCHEMA_VERSION
    private const val RPC_SCHEMA_VERSION = "schema_version"

    private const val PREFS_FILE = "cloud_config"
    private const val PREF_URL = "url"
    private const val PREF_KEY = "key"

    private var prefs: SharedPreferences? = null

    @Volatile
    private var override: CloudConfig? = null

    private val _config = MutableStateFlow<CloudConfig?>(null)
    val config: StateFlow<CloudConfig?> = _config.asStateFlow()

    val configured: Boolean get() = _config.value != null
    val googleConfigured: Boolean get() = googleConfiguredFor(_config.value, BuildConfig.GOOGLE_WEB_CLIENT_ID)

    /** Read the stored override once, before anything asks who we talk to. */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        this.prefs = prefs
        override = readOverride(prefs)
        _config.value = resolve(override)
    }

    private fun resolve(ov: CloudConfig?): CloudConfig? = resolveCloudConfig(
        overrideUrl = ov?.url,
        overrideKey = ov?.key,
        defaultUrl = BuildConfig.SUPABASE_URL,
        defaultKey = BuildConfig.SUPABASE_KEY,
    )

    @Volatile
    private var instance: SupabaseClient? = null

    fun client(): SupabaseClient {
        val cfg = _config.value ?: run {
            // Unconfigured build: preserve the historical behaviour of building
            // straight from BuildConfig so a caller that skipped the
            // configured check still gets a client-shaped object, not a crash
            // in a getter.
            CloudConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY, isDefault = true)
        }
        return instance ?: synchronized(this) {
            instance ?: createSupabaseClient(
                supabaseUrl = cfg.url,
                supabaseKey = cfg.key,
            ) {
                install(Auth)
                install(Postgrest)
                // Name the engine instead of letting ktor discover it through
                // ServiceLoader: R8 renames the META-INF/services entries in a
                // minified release, and a failed engine lookup would take the whole
                // cloud down at runtime in a way no debug build can reveal.
                httpEngine = OkHttp.create()
            }.also { instance = it }
        }
    }

    val requireConfigured: Result<SupabaseClient>
        get() = if (configured) {
            Result.success(client())
        } else {
            Result.failure(IllegalStateException("Cloud sync is not configured on this build"))
        }

    /**
     * Point the app at [next] (null = back to the BuildConfig default).
     *
     * Order matters. The old session is meaningless against the new project,
     * and signing out AFTER swapping would attempt it there, so:
     * 1. sign out THIS device's session against the OLD project (LOCAL scope:
     *    the lifter's other phones stay signed in there) — best effort, then
     *    always [Auth.clearSession] so the local session goes even when the
     *    old server is unreachable;
     * 2. [beforeSwap] — the caller resets the push watermark here (see
     *    `CloudSync.forgetPushedState`), so every session re-uploads to the
     *    new backend instead of being skipped as "already pushed";
     * 3. under the client lock, drop the cached client and persist the new
     *    override;
     * 4. emit the new [config], redrawing every screen that collects it.
     */
    suspend fun reconfigure(next: CloudConfig?, beforeSwap: suspend () -> Unit = {}) {
        val old = instance ?: synchronized(this) { instance }
        if (old != null) {
            runCatching { old.auth.signOut(SignOutScope.LOCAL) }
            runCatching { old.auth.clearSession() }
        }
        beforeSwap()
        synchronized(this) {
            instance = null
            override = next
            writeOverride(next)
        }
        _config.value = resolve(next)
    }

    private fun readOverride(prefs: SharedPreferences): CloudConfig? {
        val url = prefs.getString(PREF_URL, null)
        val key = prefs.getString(PREF_KEY, null)
        return if (url.isNullOrBlank() || key.isNullOrBlank()) null else CloudConfig(url, key, isDefault = false)
    }

    private fun writeOverride(next: CloudConfig?) {
        prefs?.edit()?.apply {
            if (next == null) {
                remove(PREF_URL)
                remove(PREF_KEY)
            } else {
                putString(PREF_URL, next.url)
                putString(PREF_KEY, next.key)
            }
        }?.apply()
    }

    /**
     * Ask a candidate backend whether it is ready to hold this lifter's
     * training, using a throwaway client — never the live one, so a TEST of a
     * half-typed URL cannot disturb the running session.
     */
    suspend fun probe(candidate: CloudConfig): ProbeResult = withContext(Dispatchers.IO) {
        val client = createSupabaseClient(
            supabaseUrl = candidate.url,
            supabaseKey = candidate.key,
        ) {
            install(Postgrest)
            // Same R8 reason as the live client above.
            httpEngine = OkHttp.create()
        }
        try {
            val result = client.postgrest.rpc(RPC_SCHEMA_VERSION)
            probeResultFor(status = 200, code = null, versionText = result.data)
        } catch (e: PostgrestRestException) {
            probeResultFor(status = e.statusCode, code = e.code, versionText = null)
        } catch (e: RestException) {
            probeResultFor(status = e.statusCode, code = null, versionText = null)
        } catch (e: HttpRequestException) {
            probeResultFor(status = null, code = null, versionText = null)
        } catch (e: IOException) {
            probeResultFor(status = null, code = null, versionText = null)
        } finally {
            client.close()
        }
    }

    /** Translate the supabase-kt exception zoo into a sentence a player can act on. */
    fun explain(error: Throwable): String = when (error) {
        is IllegalStateException -> error.message ?: "Cloud sync is not available"
        is AuthRestException -> when (error.errorCode) {
            AuthErrorCode.InvalidCredentials -> "Wrong email or password"
            AuthErrorCode.UserAlreadyExists -> "That email is already registered — sign in instead"
            AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit ->
                "Too many attempts — wait a minute and try again"
            else -> "Sign-in was refused — try again in a moment"
        }
        is PostgrestRestException -> when (error.code) {
            "23505" -> "That name is already taken by another lifter"
            "42501" -> "The cloud refused this — you are not allowed to change that record"
            "23514" -> "The cloud rejected this value as out of range"
            // Written for the board's migration 0008, but these codes
            // mean "that table, column or function is not there" for ANY
            // feature — it named the wrong one the moment cloud_archives
            // (0013) was absent and a failed BACKUP blamed the leaderboard.
            // PostgREST answers a missing table with PGRST205, a missing
            // column with PGRST204 and a missing FUNCTION with PGRST202, all
            // from its schema cache — the raw Postgres codes only surface
            // when the statement actually reaches the database.
            //
            // PGRST202 was absent, so an unapplied 0011 made push_aggregates
            // report "try again" and the lifter retried forever while their
            // level sat frozen on the board.
            "42703", "42P01", "PGRST202", "PGRST204", "PGRST205" ->
                "This part of the cloud is not set up yet — its database migration has not been applied"
            else -> "The cloud refused this request — try again"
        }
        is HttpRequestException -> "Could not reach the cloud — check your connection"
        // Never interpolate exception text: Credential Manager and the auth
        // SDK leak API internals that read as debug noise to a lifter.
        else -> "The Ledger stumbled — try again in a moment"
    }

    fun <T> failure(error: Throwable): Result<T> = Result.failure(IllegalStateException(explain(error)))
}
