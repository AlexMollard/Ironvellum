package com.ironvellum.app.data.cloud

import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.data.cloud.Cloud.failure
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import com.ironvellum.app.data.MessageException

/** How an email sign-up ended: signed in now, or waiting on the confirmation link. */
sealed interface SignUpOutcome {
    data class SignedIn(val account: Account) : SignUpOutcome
    data object ConfirmEmail : SignUpOutcome
}

data class Account(
    val userId: String,
    val email: String,
    val displayName: String,
    /** "public" | "friends" | "private" — mirrors the profile_visibility enum. */
    val visibility: String,
    /** False when the profiles row has not been read yet (offline restore) —
     *  the name/visibility here are placeholders, not server truth. */
    val profileLoaded: Boolean = true,
)

/**
 * Authenticated identity + the one row of `profiles` the player owns.
 * All methods speak in Result so the UI never sees a raw stack trace.
 */
class AccountRepository(private val clientOverride: SupabaseClient? = null) {

    private val _account = MutableStateFlow<Account?>(null)
    private val sessionLock = Mutex()
    private val activeQueries = mutableSetOf<Job>()
    @Volatile private var automaticRestoreAllowed = true
    private var transition = 0L
    private var completedAuthentication = 0L
    private var identityGeneration = 0L
    private var identityClient: SupabaseClient? = null
    private var setupClient: SupabaseClient? = null
    private var setupObservation: Deferred<Unit>? = null

    private fun newTransition(): Long = synchronized(_account) { ++transition }

    /** SDK loadSessionOrNull also hides I/O failures and cancellation; only absence means signed out. */
    private suspend fun storedSession(client: SupabaseClient): UserSession? = try {
        client.auth.sessionManager.loadSession()
    } catch (_: NoSessionFoundException) {
        null
    }

    /**
     * Observe the SDK's first non-Initializing status before the app reads
     * storage. The SDK may complete that transition and pause after a failed
     * read; the observer belongs to the client, not the individual restore.
     * Resume inline so StateFlow cannot conflate the first publication with
     * the SDK's subsequent Android background pause.
     */
    @OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
    private fun observeClientSetup(client: SupabaseClient): Deferred<Unit> {
        if (setupClient === client) return checkNotNull(setupObservation)
        setupObservation?.cancel()
        setupClient = client
        return client.auth.authScope.async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            coroutineScope {
                val backendWatch = launch(start = CoroutineStart.UNDISPATCHED) {
                    Cloud.config.first { requireClient().getOrNull() !== client }
                    this@coroutineScope.cancel("Account backend changed")
                }
                try {
                    client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
                } finally {
                    backendWatch.cancel()
                }
            }
            Unit
        }.also { setupObservation = it }
    }

    private suspend fun awaitClientSetup(client: SupabaseClient) {
        observeClientSetup(client).await()
    }

    private suspend fun importStoredSession(client: SupabaseClient, session: UserSession) {
        currentCoroutineContext().ensureActive()
        val now = Clock.System.now()
        val current = client.auth.currentSessionOrNull()
        if (current != null) {
            if (current.user?.id != session.user?.id) throw CancellationException("Account identity changed")
            if (current.expiresAt > now) return
        }
        val resumed = if (session.expiresAt <= now) client.auth.refreshSession(session.refreshToken) else session
        // Preserve rotated credentials once refresh has returned, even if the requesting screen was cancelled.
        withContext(NonCancellable) {
            if (requireClient().getOrNull() !== client || resumed.user?.id != session.user?.id ||
                client.auth.currentUserOrNull()?.id?.let { it != session.user?.id } == true
            ) throw CancellationException("Account identity changed")
            // Do not restart Android's paused refresh loop for a background read.
            val autoRefresh = client.auth.isAutoRefreshRunning ||
                (client.auth.sessionStatus.value is SessionStatus.NotAuthenticated && client.auth.config.alwaysAutoRefresh)
            client.auth.importSession(resumed, autoRefresh = autoRefresh)
        }
    }

    /** Called with sessionLock held before both private reads and profile writes. */
    private suspend fun ensureAccountSession(client: SupabaseClient, userId: String) {
        synchronized(_account) {
            if (requireClient().getOrNull() !== client || identityClient !== client ||
                _account.value?.userId != userId
            ) throw CancellationException("Account transition superseded")
        }
        val current = client.auth.currentSessionOrNull()
        if (current == null || current.expiresAt <= Clock.System.now()) {
            val session = current ?: storedSession(client)
            synchronized(_account) {
                if (requireClient().getOrNull() !== client || identityClient !== client ||
                    _account.value?.userId != userId || session?.user?.id != userId
                ) throw CancellationException("Account transition superseded")
            }
            importStoredSession(client, checkNotNull(session))
        }
        synchronized(_account) {
            if (requireClient().getOrNull() !== client || identityClient !== client ||
                _account.value?.userId != userId || client.auth.currentUserOrNull()?.id != userId
            ) throw CancellationException("Account transition superseded")
        }
    }

    internal suspend fun <T> withAccount(userId: String, block: suspend () -> T): T {
        val job = checkNotNull(currentCoroutineContext()[Job])
        sessionLock.withLock {
            val client = requireClient().getOrNull() ?: throw CancellationException("Account backend changed")
            ensureAccountSession(client, userId)
            synchronized(activeQueries) { activeQueries.add(job) }
        }
        try {
            return block()
        } finally {
            synchronized(activeQueries) { activeQueries.remove(job) }
        }
    }

    private suspend fun cancelQueries() {
        val current = currentCoroutineContext()[Job]
        val jobs = synchronized(activeQueries) { activeQueries.filter { it !== current } }
        jobs.forEach { it.cancel() }
        jobs.forEach { it.cancelAndJoin() }
    }

    private fun accountSnapshot(): Pair<Account, Long>? = synchronized(_account) {
        _account.value?.let { it to identityGeneration }
    }

    private fun publishProfile(client: SupabaseClient, generation: Long, account: Account) {
        synchronized(_account) {
            if (requireClient().getOrNull() !== client || identityClient !== client || identityGeneration != generation || _account.value?.userId != account.userId) {
                throw CancellationException("Account identity changed")
            }
            _account.value = account
        }
    }

    private fun publishIfCurrent(client: SupabaseClient, token: Long, account: Account) {
        synchronized(_account) {
            if (requireClient().getOrNull() !== client || transition != token) throw CancellationException("Account transition superseded")
            identityGeneration++
            identityClient = client
            _account.value = account
        }
    }

    private class AuthAttempt(val token: Long) {
        var imported = false
    }

    private suspend fun <T> authenticate(client: SupabaseClient, block: suspend (AuthAttempt) -> T): T {
        val token = newTransition()
        return sessionLock.withLock {
            synchronized(_account) {
                if (transition != token || requireClient().getOrNull() !== client) throw CancellationException("Account transition superseded")
            }
            awaitClientSetup(client)
            cancelQueries()
            synchronized(_account) {
                if (transition != token || requireClient().getOrNull() !== client) throw CancellationException("Account transition superseded")
            }
            val previousUserId = client.auth.currentUserOrNull()?.id
            val authAttempt = AuthAttempt(token)
            try {
                val result = block(authAttempt)
                synchronized(_account) {
                    if (transition != token || requireClient().getOrNull() !== client) throw CancellationException("Account transition superseded")
                    completedAuthentication = token
                }
                result
            } catch (error: Exception) {
                if (authAttempt.imported || client.auth.currentUserOrNull()?.id != previousUserId) {
                    synchronized(_account) {
                        _account.value = null
                        identityClient = null
                        identityGeneration++
                    }
                    withContext(NonCancellable) {
                        try {
                            runCatching { client.auth.signOut() }
                        } finally {
                            runCatching { client.auth.clearSession() }
                        }
                    }
                }
                throw error
            }
        }
    }

    private fun explainAuthFailure(error: Throwable): Nothing {
        if (error is CancellationException) throw error
        throw MessageException(Cloud.explain(error))
    }

    /** The signed-in account, or null while signed out / cloud unconfigured. */
    val account: StateFlow<Account?> = _account.asStateFlow()

    private fun requireClient(): Result<SupabaseClient> = clientOverride?.let { Result.success(it) } ?: Cloud.requireConfigured

    /** Resume a persisted session on app start. Quiet success when signed out;
     *  a failed import surfaces as a Result — silently swallowing it left the
     *  user "signed out" forever with no explanation on any social tab. */
    suspend fun restore(): Result<Unit> = sessionLock.withLock {
        if (!automaticRestoreAllowed) return@withLock Result.success(Unit)
        val client = requireClient().getOrElse { return@withLock failure(it) }
        observeClientSetup(client)
        val alreadyLoaded = synchronized(_account) {
            val current = _account.value
            current != null && current.profileLoaded && identityClient === client &&
                client.auth.currentUserOrNull()?.id == current.userId
        }
        if (alreadyLoaded) return@withLock Result.success(Unit)
        // No saved identity is a valid signed-out result, including background starts
        // where the SDK is waiting for a foreground lifecycle callback.
        attempt { storedSession(client) }.getOrElse { return@withLock failure(it) }
            ?: return@withLock Result.success(Unit)
        attempt {
            cancelQueries()
            awaitClientSetup(client)
            if (requireClient().getOrNull() !== client) throw CancellationException("Account transition superseded")
            val latest = client.auth.currentSessionOrNull() ?: storedSession(client)
            if (latest != null) importStoredSession(client, latest)
            val user = client.auth.currentUserOrNull()
            // Statement, not an expression: a trailing `?.let` inferred the
            // lambda as Unit? and the function no longer returned Result<Unit>.
            if (user != null) {
                val email = user.email ?: ""
                val generation = synchronized(_account) {
                    if (!automaticRestoreAllowed || requireClient().getOrNull() !== client) null else {
                        identityClient = client
                        _account.value = Account(
                            userId = user.id,
                            email = email,
                            displayName = "",
                            visibility = "friends",
                            profileLoaded = false,
                        )
                        ++identityGeneration
                    }
                }
                val profile = loadAccount(client, user.id, email)
                if (generation != null && profile != null) {
                    synchronized(_account) {
                        if (automaticRestoreAllowed && requireClient().getOrNull() === client && identityClient === client && identityGeneration == generation) _account.value = profile
                    }
                }
            }
        }.recoverCatching { error ->
            if (error is CancellationException) throw error
            throw MessageException(Cloud.explain(error), error)
        }
    }

    /**
     * The server creates the profiles row from the sign-up metadata
     * (`handle_new_user` in the baseline schema): with email confirmation on
     * there is no session yet, and a client-side insert ran as anon and was
     * refused.
     */
    suspend fun signUp(email: String, password: String, displayName: String): Result<SignUpOutcome> {
        val name = displayName.trim()
        trueNameProblem(name)?.let { return Result.failure(MessageException(it)) }
        val client = requireClient().getOrElse { return failure(it) }
        return attempt { authenticate(client) { authAttempt ->
            // Asked first: on a taken name the server quietly seeds a
            // Lifter#### handle instead of failing the sign-up.
            val free = client.postgrest.rpc(
                RPC_DISPLAY_NAME_AVAILABLE,
                buildJsonObject { put("name", name) },
            ).data.trim() == "true"
            if (!free) throw NameTakenException()
            val previousUserId = client.auth.currentUserOrNull()?.id
            val user = client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                data = buildJsonObject { put("display_name", name) }
            }
            val session = client.auth.currentSessionOrNull()
            if (user == null || session?.user?.id != user.id || user.id == previousUserId) {
                // With email confirmation on, an address that is already
                // registered gets a fake success (a user with no identities)
                // so the server never reveals which emails have accounts.
                // Nothing was created and no email was sent.
                if (user?.identities?.isEmpty() == true) throw EmailTakenException()
                SignUpOutcome.ConfirmEmail
            } else {
                authAttempt.imported = true
                SignUpOutcome.SignedIn(
                    loadSignedInAccount(client, email, authAttempt.token)
                        ?: throw MessageException(UiText.res(R.string.cloud_account_signup_no_profile)),
                )
            }
        } }.recoverCatching { error ->
            if (error is NameTakenException || error is EmailTakenException) throw error
            explainAuthFailure(error)
        }
    }

    suspend fun signIn(email: String, password: String): Result<Account> {
        val client = requireClient().getOrElse { return failure(it) }
        return attempt { authenticate(client) { authAttempt ->
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            authAttempt.imported = true
            loadSignedInAccount(client, email, authAttempt.token)
                ?: throw MessageException(UiText.res(R.string.cloud_account_signin_no_profile))
        } }.recoverCatching { error ->
            explainAuthFailure(error)
        }
    }

    /**
     * Emails a one-time recovery code. The project's Reset Password template
     * must print `{{ .Token }}`: the app takes the code, not the link, so no
     * deep-link setup is needed. An unknown address succeeds too — the server
     * never says which emails have accounts.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val client = requireClient().getOrElse { return failure(it) }
        return attempt {
            client.auth.resetPasswordForEmail(email, redirectUrl = null)
        }.recoverCatching { error ->
            explainAuthFailure(error)
        }
    }

    /** The emailed code signs the lifter in; the new password is then set on that session. */
    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<Account> {
        val client = requireClient().getOrElse { return failure(it) }
        return attempt { authenticate(client) { authAttempt ->
            client.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code)
            authAttempt.imported = true
            client.auth.updateUser { password = newPassword }
            loadSignedInAccount(client, email, authAttempt.token)
                ?: throw MessageException(UiText.res(R.string.cloud_account_reset_no_profile))
        } }.recoverCatching { error ->
            explainAuthFailure(error)
        }
    }

    /**
     * Google entry point via supabase-kt's IDToken provider. The caller (UI)
     * runs the Credential Manager flow and hands over the id token; the nonce
     * must be the same raw value that fed Credential Manager.
     */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String?): Result<Account> {
        val client = requireClient().getOrElse { return failure(it) }
        return attempt { authenticate(client) { authAttempt ->
            client.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                this.nonce = rawNonce
            }
            authAttempt.imported = true
            loadSignedInAccount(client, "", authAttempt.token)
                ?: throw MessageException(UiText.res(R.string.cloud_account_google_no_profile))
        } }.recoverCatching { error ->
            explainAuthFailure(error)
        }
    }

    /** Keep the derived handle inside the DB's 2..24 char trim check. */
    private fun sanitizeHandle(raw: String): String {
        val cleaned = raw.filter { it.isLetterOrDigit() || it == ' ' }.trim()
        return when {
            cleaned.length >= WireLimits.DISPLAY_NAME_MAX -> cleaned.take(WireLimits.DISPLAY_NAME_MAX)
            cleaned.length >= WireLimits.DISPLAY_NAME_MIN -> cleaned
            else -> "Ironbound" + cleaned.ifEmpty { "0" }
        }
    }

    /**
     * Claim a real name. For a MANUAL claim, "that name is taken" beats
     * silently renaming the user — so unlike profile creation, a 23505 on the
     * update fails explicitly with house copy instead of retrying a suffix.
     */
    suspend fun updateDisplayName(raw: String): Result<Unit> {
        val (current, generation) = accountSnapshot()
            ?: return Result.failure(MessageException(UiText.themed(R.string.cloud_account_claim_signed_out, R.string.cloud_account_claim_signed_out__t)))
        val client = requireClient().getOrElse { return failure(it) }
        trueNameProblem(raw)?.let { return Result.failure(MessageException(it)) }
        val name = sanitizeHandle(raw)
        return attempt { sessionLock.withLock {
            val latest = currentProfile(client, current.userId, generation)
            client.postgrest.from("profiles").update(
                {
                    set("display_name", name)
                },
            ) {
                filter { eq("id", current.userId) }
            }
            publishProfile(client, generation, latest.copy(displayName = name))
        } }.recoverCatching { error ->
            if (error is CancellationException) throw error
            val taken = error is io.github.jan.supabase.postgrest.exception.PostgrestRestException &&
                error.code == "23505"
            throw MessageException(
                if (taken) UiText.themed(R.string.cloud_account_name_taken, R.string.cloud_account_name_taken__t)
                else Cloud.explain(error),
            )
        }
    }


    /**
     * Deletes this lifter's whole cloud account, then signs out.
     *
     * `delete_my_account()` (baseline schema) removes the caller's auth.users
     * row server-side; profiles and every social table cascade from it, and
     * so does `cloud_archives`, which a profiles-only delete used to leave
     * behind. The RPC can only ever delete auth.uid(), so no service-role
     * credential ships in the APK. Signing in again afterwards is a new
     * account.
     */
    suspend fun deleteCloudData(): Result<Unit> {
        val client = requireClient().getOrElse { return failure(it) }
        _account.value?.userId
            ?: return Result.failure(MessageException(UiText.res(R.string.cloud_account_delete_signed_out)))
        return runCatching {
            client.postgrest.rpc("delete_my_account")
            // The server session died with the user; a failed sign-out call
            // must not report the deletion itself as failed.
            runCatching { client.auth.signOut() }
            synchronized(_account) {
                _account.value = null
                identityClient = null
                identityGeneration++
            }
            Unit
        }.recoverCatching { error ->
            throw MessageException(Cloud.explain(error))
        }
    }

    /** One retry of the profiles row for a session that restored offline. */
    suspend fun refreshProfile(): Result<Unit> {
        val (current, generation) = accountSnapshot() ?: return Result.success(Unit)
        if (current.profileLoaded) return Result.success(Unit)
        val client = requireClient().getOrElse { return failure(it) }
        return attempt { sessionLock.withLock {
            val latest = currentProfile(client, current.userId, generation)
            if (!latest.profileLoaded) {
                val profile = loadAccount(client, latest.userId, latest.email)
                    ?: throw MessageException(UiText.res(R.string.cloud_account_no_profile))
                publishProfile(client, generation, profile)
            }
        } }.recoverCatching { error ->
            explainAuthFailure(error)
        }
    }

    suspend fun signOut(): Result<Unit> {
        // Local state clears first: client.auth.signOut() is a network
        // revocation, and treating it as a precondition stranded the lifter
        // signed in with no connectivity and no way out.
        val token = synchronized(_account) {
            automaticRestoreAllowed = false
            _account.value = null
            identityClient = null
            identityGeneration++
            ++transition
        }
        // A restore already importing a token must finish before logout clears the SDK's stored session.
        withContext(NonCancellable) { sessionLock.withLock {
            if (synchronized(_account) { completedAuthentication <= token }) {
                cancelQueries()
                requireClient().onSuccess { client ->
                    try {
                        awaitClientSetup(client)
                        runCatching { client.auth.signOut() }
                    } finally {
                        runCatching { client.auth.clearSession() }
                    }
                }
            }
        } }
        return Result.success(Unit)
    }

    suspend fun setVisibility(visibility: String): Result<Unit> {
        val (current, generation) = accountSnapshot()
            ?: return Result.failure(MessageException(UiText.res(R.string.cloud_account_visibility_signed_out)))
        val client = requireClient().getOrElse { return failure(it) }
        return attempt { sessionLock.withLock {
            val latest = currentProfile(client, current.userId, generation)
            client.postgrest.from("profiles").update(
                {
                    set("visibility", visibility)
                },
            ) {
                filter { eq("id", current.userId) }
            }
            publishProfile(client, generation, latest.copy(visibility = visibility))
        } }.recoverCatching { error ->
            explainAuthFailure(error)
        }
    }

    private suspend fun currentProfile(client: SupabaseClient, userId: String, generation: Long): Account {
        ensureAccountSession(client, userId)
        return synchronized(_account) {
            val current = _account.value ?: throw CancellationException("Account identity changed")
            if (requireClient().getOrNull() !== client || identityClient !== client || identityGeneration != generation || current.userId != userId ||
                client.auth.currentUserOrNull()?.id != userId
            ) throw CancellationException("Account identity changed")
            current
        }
    }

    private suspend fun loadSignedInAccount(client: SupabaseClient, fallbackEmail: String, token: Long): Account? {
        val user = client.auth.currentUserOrNull() ?: return null
        val email = fallbackEmail.ifBlank { user.email ?: "" }
        val account = try {
            loadAccount(client, user.id, email, propagateRetryable = true)
        } catch (error: Exception) {
            if (!InboxPolling.transient(error)) throw error
            Account(user.id, email, "", "friends", profileLoaded = false)
        }
        return account?.also { publishIfCurrent(client, token, it) }
    }

    /** Profiles are readable per RLS; a missing row (Google signup) returns null. */
    private suspend fun loadAccount(client: SupabaseClient, userId: String, email: String, propagateRetryable: Boolean = false): Account? {
        return attempt {
            client.postgrest.from("profiles").select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<ProfileDto>()
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            if (propagateRetryable && InboxPolling.transient(error)) throw error
            null
        }?.let { row ->
            Account(
                userId = row.id,
                email = email,
                displayName = row.displayName,
                visibility = row.visibility,
            )
        }
    }
}
/** A seeded handle the user has not replaced with a name of their own. */
fun isUnclaimedHandle(name: String?): Boolean = name != null && UnclaimedHandle.matches(name)

/** Why [raw] cannot be the true name, or null when it can: 2..24 trimmed, as the profiles table checks. */
fun trueNameProblem(raw: String): UiText? {
    val trimmed = raw.trim()
    return when {
        trimmed.length !in WireLimits.DISPLAY_NAME_MIN..WireLimits.DISPLAY_NAME_MAX ->
            UiText.themed(R.string.cloud_name_length, R.string.cloud_name_length__t, WireLimits.DISPLAY_NAME_MIN, WireLimits.DISPLAY_NAME_MAX)
        // A claim keeps letters, digits and spaces only; "!!" would otherwise become a seeded-looking handle.
        trimmed.count { it.isLetterOrDigit() } < WireLimits.DISPLAY_NAME_MIN ->
            UiText.themedPlural(R.plurals.cloud_name_letters, R.plurals.cloud_name_letters__t, WireLimits.DISPLAY_NAME_MIN)
        else -> null
    }
}

/**
 * The cloud name is the source of truth when signed in. Returns what the local
 * profile name should become when [cloud] loads, or null to leave it alone: a
 * seeded handle (Ironbound####) is not a name anyone chose, so the local name
 * stays; so does a profile that was never read from the server.
 */
fun localNameToAdopt(local: String?, cloud: String, profileLoaded: Boolean): String? {
    val name = cloud.trim()
    return if (!profileLoaded || name.isEmpty() || isUnclaimedHandle(name) || name == local) null else name
}

/** What the sign-up name field starts with: the local name, unless it is still the default. */
fun signUpNamePrefill(local: String?): String {
    val name = local?.trim().orEmpty()
    return if (name.equals(DEFAULT_PROFILE_NAME, ignoreCase = true) || name.equals("Lifter", ignoreCase = true) || name.length > WireLimits.DISPLAY_NAME_MAX) "" else name
}

private const val DEFAULT_PROFILE_NAME = "Ironbound"

// "Hunter" and "Lifter" match handles seeded by older builds; new seeds use "Ironbound".
private val UnclaimedHandle = Regex("^(?:Hunter|Lifter|Ironbound)\\d{4}$")


/** Signed-out guard for cloud features that need an identity. */
internal fun requireAccount(account: AccountRepository): Result<Account> {
    val current = account.account.value
    return if (current == null) {
        Result.failure(MessageException(UiText.res(R.string.cloud_account_signed_out)))
    } else {
        Result.success(current)
    }
}

/** The display name was taken before sign-up; shown to the lifter as is. */
private class NameTakenException : MessageException(
    UiText.themed(R.string.cloud_account_name_taken_pick, R.string.cloud_account_name_taken_pick__t),
)

/** A repeat sign-up of a registered email; shown to the lifter as is. */
private class EmailTakenException : MessageException(UiText.res(R.string.cloud_account_email_has_account))
