package com.ironvellum.app.data.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.core.content.edit
import com.ironvellum.app.BuildConfig
import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.data.MessageException
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.messageText
import com.ironvellum.app.domain.UiText
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * The oldest backend schema the app works against: below it the app says "Outdated" and stops syncing.
 * The baseline may be ahead of it: optional feed cards and rolling workout rankings need newer schemas.
 * Raise this only when the app cannot work without the newer schema.
 */
const val NEEDED_SCHEMA_VERSION = 31

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
 * Only initialization and backend switches can construct SDKs. Reading an old
 * generation has no side effects, even if the reader resumes after a switch.
 */
internal class CloudClientSelection(private val fallback: CloudConfig, private val build: (CloudConfig) -> SupabaseClient) {
    private data class Generation(val effective: CloudConfig?, val client: Result<SupabaseClient>?, val available: Boolean = true)
    private val selected = MutableStateFlow<Generation?>(null)
    private val writer = Mutex()

    @OptIn(kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
    val config: StateFlow<CloudConfig?> = object : StateFlow<CloudConfig?> {
        override val value get() = selected.value?.effective
        override val replayCache get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<CloudConfig?>): Nothing =
            selected.collect { collector.emit(it?.effective) }
    }

    private fun create(config: CloudConfig?): Generation {
        // The legacy client() API exposes construction errors only when used.
        // A local-only build must still start even with blank BuildConfig fields.
        val client = try {
            Result.success(build(config ?: fallback))
        } catch (error: Exception) {
            Result.failure(error)
        }
        return Generation(config, client)
    }

    /** Application startup calls this before consumers or writers start. Repeated init is a no-op. */
    fun initialize(config: CloudConfig?) {
        if (selected.value != null) return
        runBlocking {
            writer.withLock {
                if (selected.value == null) selected.value = create(config)
            }
        }
    }

    @OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
    suspend fun select(config: CloudConfig?, beforePublish: suspend () -> Unit = {}) = writer.withLock {
        val previous = selected.value
        // Readers fail promptly during disposal. They never wait on a publisher
        // that might need their account monitor or the Android main thread.
        selected.value = previous?.copy(available = false) ?: Generation(null, null, available = false)
        val old = previous?.client?.getOrNull()
        if (old != null) {
            val auth = old.auth
            val jobs = auth.authScope.coroutineContext.job
            try {
                runCatching { auth.signOut(SignOutScope.LOCAL) }
            } finally {
                old.close()
                jobs.join()
            }
            // No retired job can now restore or re-save credentials behind this clear.
            // If persistent cleanup fails, keep the previous configuration unavailable.
            auth.clearSession()
        }
        try {
            beforePublish()
        } catch (failure: Throwable) {
            selected.value = create(previous?.effective)
            throw failure
        }
        selected.value = create(config)
    }

    val requireConfigured: Result<SupabaseClient>
        get() = current(allowFallback = false) ?: Result.failure(MessageException(UiText.res(R.string.cloud_not_configured)))
    fun client(afterSnapshot: (() -> Unit)? = null): SupabaseClient =
        current(allowFallback = true, afterSnapshot)?.getOrThrow() ?: throw CancellationException("Cloud backend is changing")

    private fun current(allowFallback: Boolean, afterSnapshot: (() -> Unit)? = null): Result<SupabaseClient>? {
        while (true) {
            val generation = selected.value
            afterSnapshot?.invoke()
            if (selected.value !== generation) continue
            if (generation == null || !generation.available || (!allowFallback && generation.effective == null)) return null
            return generation.client
        }
    }
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
    private const val PREF_TOMBSTONE_OWNER = "tombstone_owner"
    private const val KEEPER_ONLY = "Only the Keeper"

    private var prefs: SharedPreferences? = null

    private val clients = CloudClientSelection(
        CloudConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY, isDefault = true), ::buildClient,
    )
    val config: StateFlow<CloudConfig?> get() = clients.config

    val configured: Boolean get() = config.value != null
    val googleConfigured: Boolean get() = googleConfiguredFor(config.value, BuildConfig.GOOGLE_WEB_CLIENT_ID)

    /** Read the stored override once, before anything asks who we talk to. */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        this.prefs = prefs
        clients.initialize(resolve(readOverride(prefs)))
    }

    private fun resolve(ov: CloudConfig?): CloudConfig? = resolveCloudConfig(
        overrideUrl = ov?.url,
        overrideKey = ov?.key,
        defaultUrl = BuildConfig.SUPABASE_URL,
        defaultKey = BuildConfig.SUPABASE_KEY,
    )

    fun client(): SupabaseClient = clients.client()

    private fun buildClient(cfg: CloudConfig) = buildCloudClient(cfg, ProcessLifecycleOwner.get().lifecycle)

    val requireConfigured: Result<SupabaseClient>
        get() = clients.requireConfigured

    /**
     * Point the app at [next] (null = back to the BuildConfig default).
     *
     * Order matters. The old session is meaningless against the new project,
     * and signing out AFTER swapping would attempt it there, so:
     * 1. fence account-scoped work and forget the published identity, then
     *    sign out THIS device's session against the OLD project (LOCAL scope:
     *    the lifter's other phones stay signed in there) — best effort, then
     *    close its SDK and join its Auth work (including the lifecycle observer),
     *    then [Auth.clearSession] before a successor can access session storage;
     * 2. [beforeSwap] resets the push watermark so sessions re-upload to the new backend;
     * 3. persist the override and publish a fresh configuration/SDK generation.
     */
    suspend fun reconfigure(next: CloudConfig?, accounts: AccountRepository, beforeSwap: suspend () -> Unit) =
        reconfigure(clients, resolve(next), accounts, beforeSwap) { writeOverride(next) }

    internal suspend fun reconfigure(
        selection: CloudClientSelection,
        config: CloudConfig?,
        accounts: AccountRepository,
        beforeSwap: suspend () -> Unit,
        persist: () -> Unit,
    ) = accounts.withBackendSwitch {
        selection.select(config) {
            beforeSwap()
            persist()
        }
    }

    /**
     * The account the trial deletions waiting on this phone belong to: the last
     * one signed in here. Kept beside the backend choice, which is just as much
     * a fact about this phone and survives the same way. Null before the first
     * sign-in (and when the file has not been opened yet).
     */
    var tombstoneOwner: String?
        get() = prefs?.getString(PREF_TOMBSTONE_OWNER, null)
        set(value) {
            prefs?.edit { putString(PREF_TOMBSTONE_OWNER, value) }
        }

    private fun webCatchUpKey(prefix: String, userId: String): String? =
        config.value?.url?.let { "$prefix:$it:$userId" }

    internal fun webCaughtUp(userId: String): Boolean =
        webCatchUpKey("web_caught_up", userId)?.let { prefs?.getBoolean(it, false) } == true

    internal fun webCatchUpStarted(userId: String): Boolean =
        webCatchUpKey("web_catch_up_started", userId)?.let { prefs?.getBoolean(it, false) } == true

    // KTX edit discards commit's Boolean; callers need disk-write success before catch-up.
    @SuppressLint("UseKtx")
    internal fun markWebCatchUpStarted(userId: String): Boolean {
        val key = webCatchUpKey("web_catch_up_started", userId) ?: return false
        val editor = prefs?.edit() ?: return false
        editor.putBoolean(key, true)
        return editor.commit()
    }

    // Preserve the commit result while clearing the started marker in the same edit.
    @SuppressLint("UseKtx")
    internal fun markWebCaughtUp(userId: String): Boolean {
        val caughtUp = webCatchUpKey("web_caught_up", userId) ?: return false
        val started = webCatchUpKey("web_catch_up_started", userId) ?: return false
        val editor = prefs?.edit() ?: return false
        editor.putBoolean(caughtUp, true)
        editor.remove(started)
        return editor.commit()
    }

    /** Query the same schema beacon as [probe], returning null when it cannot be read. */
    suspend fun schemaVersion(client: SupabaseClient): Int? = try {
        client.postgrest.rpc(RPC_SCHEMA_VERSION).data.trim().toIntOrNull()
    } catch (e: PostgrestRestException) {
        null
    } catch (e: RestException) {
        null
    } catch (e: HttpRequestException) {
        null
    } catch (e: IOException) {
        null
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

    /** PostgREST codes for "that table, column or function is not there" (see [explain]). */
    private val NOT_SET_UP_CODES = setOf("42703", "42P01", "PGRST202", "PGRST204", "PGRST205")

    /**
     * True when the cloud answered that a table, column or function is missing (an unapplied migration), looking
     * through the causes [explain]'s failures keep. Callers use this instead of matching the message text.
     */
    fun isNotSetUp(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { it is PostgrestRestException && isNotSetUpCode(it.code) }

    internal fun isNotSetUpCode(code: String?): Boolean = code in NOT_SET_UP_CODES

    /**
     * Translate the supabase-kt exception zoo into a sentence a player can act on. Unresolved: it becomes words
     * when the screen shows it (a [MessageException] carries it), so it follows the language and the Themed switch.
     */
    fun explain(error: Throwable): UiText = when (error) {
        is MessageException -> error.text
        is IllegalStateException -> error.messageText(UiText.res(R.string.cloud_error_unavailable))
        is AuthRestException -> when (error.errorCode) {
            AuthErrorCode.InvalidCredentials -> UiText.res(R.string.cloud_error_wrong_credentials)
            AuthErrorCode.UserAlreadyExists -> UiText.res(R.string.cloud_error_email_taken)
            AuthErrorCode.EmailNotConfirmed -> UiText.res(R.string.cloud_error_email_unconfirmed)
            AuthErrorCode.OtpExpired -> UiText.res(R.string.cloud_error_code_expired)
            AuthErrorCode.SamePassword -> UiText.res(R.string.cloud_error_same_password)
            AuthErrorCode.WeakPassword -> UiText.res(R.string.cloud_error_weak_password)
            AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit ->
                UiText.res(R.string.cloud_error_rate_limited)
            else -> UiText.res(R.string.cloud_error_signin_refused)
        }
        is PostgrestRestException -> when (error.code) {
            "23505" -> UiText.themed(R.string.cloud_error_name_taken, R.string.cloud_error_name_taken__t)
            // circle_keeper_lock refuses a non-Keeper with 42501 and a sentence
            // for the lifter ("Only the Keeper can ..."): the keys may have just
            // been passed on, and the generic line would hide why.
            "42501" -> error.error.takeIf { it.startsWith(KEEPER_ONLY) }?.let { UiText.Raw(Words.fromServer(it)) }
                ?: UiText.res(R.string.cloud_error_not_allowed)
            "23514" -> UiText.res(R.string.cloud_error_out_of_range)
            // raise exception in our own triggers (rate limits, blocked
            // requests): the message is written for the lifter in the
            // migration, so it is shown as is — "try again" would send them
            // straight back into the same limit.
            "P0001" -> Words.fromServer(error.error).takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: UiText.res(R.string.cloud_error_refused)
            // These codes mean "that table, column or function is not there"
            // for ANY feature — naming one feature was wrong the moment
            // cloud_archives landed and a failed BACKUP blamed the board.
            // PostgREST answers a missing table with PGRST205, a missing
            // column with PGRST204 and a missing FUNCTION with PGRST202, all
            // from its schema cache — the raw Postgres codes only surface
            // when the statement actually reaches the database.
            //
            // A half-applied baseline once left push_aggregates missing, so
            // PGRST202 made it report "try again" and the lifter retried
            // forever while their level sat frozen on the board.
            in NOT_SET_UP_CODES -> UiText.res(R.string.cloud_error_not_set_up)
            else -> UiText.res(R.string.cloud_error_refused)
        }
        is HttpRequestException -> UiText.res(R.string.cloud_error_offline)
        // Never interpolate exception text: Credential Manager and the auth
        // SDK leak API internals that read as debug noise to a lifter.
        else -> UiText.themed(R.string.line_stumbled, R.string.line_stumbled__t)
    }

    fun <T> failure(error: Throwable): Result<T> = Result.failure(MessageException(explain(error), error))
}
