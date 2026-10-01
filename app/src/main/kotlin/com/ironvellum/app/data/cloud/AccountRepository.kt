package com.ironvellum.app.data.cloud

import com.ironvellum.app.data.cloud.Cloud.failure
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
class AccountRepository {

    private val _account = MutableStateFlow<Account?>(null)

    /** The signed-in account, or null while signed out / cloud unconfigured. */
    val account: StateFlow<Account?> = _account.asStateFlow()

    private suspend fun requireClient(): Result<SupabaseClient> = Cloud.requireConfigured

    /** Resume a persisted session on app start. Quiet success when signed out;
     *  a failed import surfaces as a Result — silently swallowing it left the
     *  user "signed out" forever with no explanation on any social tab. */
    suspend fun restore(): Result<Unit> {
        val client = requireClient().getOrElse { return failure(it) }
        // supabase-kt persists the session in its SessionManager but does not
        // import it automatically: load then import (refreshing if stale).
        val session = runCatching { client.auth.sessionManager.loadSessionOrNull() }.getOrNull()
            ?: return Result.success(Unit)
        return runCatching {
            client.auth.importSession(session)
            val user = client.auth.currentUserOrNull()
            // Statement, not an expression: a trailing `?.let` inferred the
            // lambda as Unit? and the function no longer returned Result<Unit>.
            if (user != null) {
                val email = user.email ?: ""
                // A failed profiles round trip must not erase a valid session:
                // offline restores used to read as signed OUT on every social
                // tab. Keep the identity with a blank name and retry the row
                // on the next social read (see [refreshProfile]).
                val profile = loadAccount(user.id, email)
                _account.value = profile ?: Account(
                    userId = user.id,
                    email = email,
                    displayName = "",
                    // The sign-up default; replaced once the row is readable.
                    visibility = "friends",
                    profileLoaded = false,
                )
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
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
        trueNameProblem(name)?.let { return Result.failure(IllegalStateException(it)) }
        val client = requireClient().getOrElse { return failure(it) }
        return runCatching {
            // Asked first: on a taken name the server quietly seeds a
            // Lifter#### handle instead of failing the sign-up.
            val free = client.postgrest.rpc(
                RPC_DISPLAY_NAME_AVAILABLE,
                buildJsonObject { put("name", name) },
            ).data.trim() == "true"
            if (!free) throw NameTakenException()
            val user = client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                data = buildJsonObject { put("display_name", name) }
            }
            if (client.auth.currentSessionOrNull() == null) {
                // With email confirmation on, an address that is already
                // registered gets a fake success (a user with no identities)
                // so the server never reveals which emails have accounts.
                // Nothing was created and no email was sent.
                if (user?.identities?.isEmpty() == true) throw EmailTakenException()
                SignUpOutcome.ConfirmEmail
            } else {
                SignUpOutcome.SignedIn(
                    loadSignedInAccount(email)
                        ?: throw IllegalStateException("Account created, but your profile could not be loaded — sign in again"),
                )
            }
        }.recoverCatching { error ->
            if (error is NameTakenException || error is EmailTakenException) throw error
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun signIn(email: String, password: String): Result<Account> {
        val client = requireClient().getOrElse { return failure(it) }
        return runCatching {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            loadSignedInAccount(email)
                ?: throw IllegalStateException("Signed in, but your profile could not be loaded — try again")
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
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
        return runCatching {
            client.auth.resetPasswordForEmail(email, redirectUrl = null)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /** The emailed code signs the lifter in; the new password is then set on that session. */
    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<Account> {
        val client = requireClient().getOrElse { return failure(it) }
        return runCatching {
            client.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code)
            client.auth.updateUser { password = newPassword }
            loadSignedInAccount(email)
                ?: throw IllegalStateException("Password changed, but your profile could not be loaded — sign in again")
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * Google entry point via supabase-kt's IDToken provider. The caller (UI)
     * runs the Credential Manager flow and hands over the id token; the nonce
     * must be the same raw value that fed Credential Manager.
     */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String?): Result<Account> {
        val client = requireClient().getOrElse { return failure(it) }
        return runCatching {
            client.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
                this.nonce = rawNonce
            }
            val user = client.auth.currentUserOrNull()
                ?: throw IllegalStateException("Google sign-in returned no user")
            // The server seeded a neutral Lifter#### handle for a new Google
            // user (never the legal name); ALLIES offers to claim a real one.
            loadAccount(user.id, user.email ?: "")?.also { _account.value = it }
                ?: throw IllegalStateException("Signed in with Google, but your profile could not be loaded — try again")
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
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
        val current = _account.value
            ?: return Result.failure(IllegalStateException("Sign in before taking a true name"))
        val client = requireClient().getOrElse { return failure(it) }
        trueNameProblem(raw)?.let { return Result.failure(IllegalStateException(it)) }
        val name = sanitizeHandle(raw)
        return runCatching {
            client.postgrest.from("profiles").update(
                {
                    set("display_name", name)
                },
            ) {
                filter { eq("id", current.userId) }
            }
            _account.value = current.copy(displayName = name)
        }.recoverCatching { error ->
            val taken = error is io.github.jan.supabase.postgrest.exception.PostgrestRestException &&
                error.code == "23505"
            throw IllegalStateException(
                if (taken) "That true name is taken — another Ironbound got there first"
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
            ?: return Result.failure(IllegalStateException("Sign in before deleting your cloud account"))
        return runCatching {
            client.postgrest.rpc("delete_my_account")
            // The server session died with the user; a failed sign-out call
            // must not report the deletion itself as failed.
            runCatching { client.auth.signOut() }
            _account.value = null
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /** One retry of the profiles row for a session that restored offline. */
    suspend fun refreshProfile(): Result<Unit> {
        val current = _account.value ?: return Result.success(Unit)
        if (current.profileLoaded) return Result.success(Unit)
        val client = requireClient().getOrElse { return failure(it) }
        return runCatching {
            val profile = loadAccount(current.userId, current.email)
                ?: throw IllegalStateException("Your profile could not be loaded")
            _account.value = profile
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun signOut(): Result<Unit> {
        // Local state clears first: client.auth.signOut() is a network
        // revocation, and treating it as a precondition stranded the lifter
        // signed in with no connectivity and no way out.
        _account.value = null
        requireClient().onSuccess { client ->
            runCatching { client.auth.signOut() }
        }
        return Result.success(Unit)
    }

    suspend fun setVisibility(visibility: String): Result<Unit> {
        val current = _account.value
            ?: return Result.failure(IllegalStateException("Sign in before changing visibility"))
        val client = requireClient().getOrElse { return failure(it) }
        return runCatching {
            client.postgrest.from("profiles").update(
                {
                    set("visibility", visibility)
                },
            ) {
                filter { eq("id", current.userId) }
            }
            _account.value = current.copy(visibility = visibility)
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    private suspend fun loadSignedInAccount(fallbackEmail: String): Account? {
        val client = Cloud.client()
        val userId = client.auth.currentUserOrNull()?.id ?: return null
        return loadAccount(userId, fallbackEmail)?.also { _account.value = it }
    }

    /** Profiles are readable per RLS; a missing row (Google signup) returns null. */
    private suspend fun loadAccount(userId: String, email: String): Account? {
        val client = Cloud.client()
        return runCatching {
            client.postgrest.from("profiles").select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<ProfileDto>()
        }.getOrNull()?.let { row ->
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
fun trueNameProblem(raw: String): String? {
    val trimmed = raw.trim()
    return when {
        trimmed.length !in WireLimits.DISPLAY_NAME_MIN..WireLimits.DISPLAY_NAME_MAX ->
            "Your true name must be 2 to 24 characters"
        // A claim keeps letters, digits and spaces only; "!!" would otherwise become a seeded-looking handle.
        trimmed.count { it.isLetterOrDigit() } < WireLimits.DISPLAY_NAME_MIN ->
            "Your true name needs at least 2 letters or digits"
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
    return if (name.equals(DEFAULT_PROFILE_NAME, ignoreCase = true) || name.length > WireLimits.DISPLAY_NAME_MAX) "" else name
}

private const val DEFAULT_PROFILE_NAME = "Ironbound"

// "Hunter" and "Lifter" match handles seeded by older builds; new seeds use "Ironbound".
private val UnclaimedHandle = Regex("^(?:Hunter|Lifter|Ironbound)\\d{4}$")


/** Signed-out guard for cloud features that need an identity. */
internal fun requireAccount(account: AccountRepository): Result<Account> {
    val current = account.account.value
    return if (current == null) {
        Result.failure(IllegalStateException("Sign in to use cloud features"))
    } else {
        Result.success(current)
    }
}

/** The display name was taken before sign-up; shown to the lifter as is. */
private class NameTakenException : IllegalStateException("That true name is taken — pick another")

/** A repeat sign-up of a registered email; shown to the lifter as is. */
private class EmailTakenException : IllegalStateException(
    "That email already has an account — sign in, or use Forgot password?",
)
