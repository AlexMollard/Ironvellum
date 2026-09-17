package com.monarch.app.data.cloud

import com.monarch.app.data.cloud.Cloud.failure
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    suspend fun signUp(email: String, password: String, displayName: String): Result<Account> {
        val name = displayName.trim()
        if (name.length < WireLimits.DISPLAY_NAME_MIN || name.length > WireLimits.DISPLAY_NAME_MAX) {
            return Result.failure(IllegalStateException("Display name must be 2 to 24 characters"))
        }
        val client = requireClient().getOrElse { return failure(it) }
        return runCatching {
            val user = client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            } ?: throw IllegalStateException("Sign-up returned no user")
            // The auth user exists but the profiles row does not; RLS only
            // lets us insert our own id. A taken name fails with 23505 here.
            client.postgrest.from("profiles").insert(ProfileDto(id = user.id, displayName = name))
            Account(
                userId = user.id,
                email = user.email ?: email,
                displayName = name,
                visibility = "friends",
            ).also { _account.value = it }
        }.recoverCatching { error ->
            // The unique index on lower(display_name) fires after the auth
            // user was already created, so the retry is a plain sign-in.
            if (error is io.github.jan.supabase.postgrest.exception.PostgrestRestException &&
                error.code == "23505"
            ) {
                try {
                    signIn(email, password).getOrThrow()
                } catch (missing: ProfileMissingException) {
                    // Re-attempt the row for the signed-in user; a failing
                    // repair is translated, never shown as raw SDK text.
                    try {
                        repairProfileForCurrentUser(name)
                    } catch (repair: Exception) {
                        throw IllegalStateException(Cloud.explain(repair))
                    }
                    loadSignedInAccount(email)
                        ?: throw IllegalStateException(
                            "Your sigil is known to the System, but the hunter record could not be restored — try signing in again",
                        )
                }
            } else {
                throw IllegalStateException(Cloud.explain(error))
            }
        }
    }

    /** Re-insert the profiles row for an auth user whose first insert failed. */
    private suspend fun repairProfileForCurrentUser(displayName: String) {
        val client = Cloud.client()
        val user = client.auth.currentUserOrNull()
            ?: throw ProfileMissingException()
        client.postgrest.from("profiles").insert(ProfileDto(id = user.id, displayName = displayName))
    }

    suspend fun signIn(email: String, password: String): Result<Account> {
        val client = requireClient().getOrElse { return failure(it) }
        return runCatching {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            loadSignedInAccount(email)
                ?: throw ProfileMissingException()
        }.recoverCatching { error ->
            // signUp's 23505 recovery keys on this sentinel: rethrow it as-is
            // so the profile repair path can catch it.
            if (error is ProfileMissingException) throw error
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
            val email = user.email ?: ""
            // Google users get no profiles row automatically: seed a neutral
            // handle they can later claim as a real name.
            val existing = loadAccount(user.id, email)
            if (existing != null) {
                _account.value = existing
                existing
            } else {
                createProfileForNewGoogleUser(client, user.id)
                val created = loadAccount(user.id, email)
                    ?: throw IllegalStateException("Signed in with Google, but your hunter profile is missing")
                _account.value = created
                created
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    /**
     * The DB rejects duplicate handles with 23505 (unique index on
     * lower(display_name)); append a numeric suffix and retry.
     */
    private suspend fun createProfileForNewGoogleUser(
        client: SupabaseClient,
        userId: String,
    ) {
        // A Google account must NOT lock the handle to the real legal name:
        // seed a neutral, deterministic Hunter handle the user can claim later.
        val base = neutralHandle(userId)
        var name = base
        var suffix = 1
        while (true) {
            try {
                client.postgrest.from("profiles").insert(
                    ProfileDto(id = userId, displayName = name),
                )
                return
            } catch (error: io.github.jan.supabase.postgrest.exception.PostgrestRestException) {
                if (error.code != "23505" || suffix > 99) throw error
                // The DB caps display_name at 24 chars ("between 2 and 24"),
                // so the suffix must shorten the base, not extend the whole
                // name past the cap — a 24-char base + " 1" would trip the
                // check constraint and kill the retry loop.
                val suffixText = " $suffix"
                name = base.take(24 - suffixText.length) + suffixText
                suffix++
            }
        }
    }

    /** Keep the derived handle inside the DB's 2..24 char trim check. */
    private fun sanitizeHandle(raw: String): String {
        val cleaned = raw.filter { it.isLetterOrDigit() || it == ' ' }.trim()
        return when {
            cleaned.length >= WireLimits.DISPLAY_NAME_MAX -> cleaned.take(WireLimits.DISPLAY_NAME_MAX)
            cleaned.length >= WireLimits.DISPLAY_NAME_MIN -> cleaned
            else -> "Hunter" + cleaned.ifEmpty { "0" }
        }
    }

    /**
     * Unclaimed convention: "Hunter" + 4 digits derived from the userId hash —
     * stable across sign-ins (String.hashCode is spec-fixed, never Random) so
     * the same account always regenerates the same seed handle.
     */
    private fun neutralHandle(userId: String): String =
        "Hunter" + ((userId.hashCode() and Int.MAX_VALUE) % 10_000).toString().padStart(4, '0')

    /**
     * Claim a real name. For a MANUAL claim, "that name is taken" beats
     * silently renaming the user — so unlike profile creation, a 23505 on the
     * update fails explicitly with house copy instead of retrying a suffix.
     */
    suspend fun updateDisplayName(raw: String): Result<Unit> {
        val current = _account.value
            ?: return Result.failure(IllegalStateException("Sign in before claiming a name"))
        val client = requireClient().getOrElse { return failure(it) }
        val name = sanitizeHandle(raw)
        if (name.length < WireLimits.DISPLAY_NAME_MIN) {
            return Result.failure(IllegalStateException("Your name needs at least 2 characters"))
        }
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
                if (taken) "That name is already taken — another hunter got there first"
                else Cloud.explain(error),
            )
        }
    }


    /**
     * Deletes every trace of this hunter from the cloud, then signs out.
     *
     * One delete is enough: every social table references `profiles (id) on
     * delete cascade`, and the RLS policy `profiles_delete` already allows the
     * owner — and only the owner — to remove their own row. The capability
     * existed server-side from the first migration and was simply never
     * reachable from the app, which left the user no way to withdraw their
     * data.
     *
     * The auth identity itself is deliberately left intact: this removes the
     * training data, and the hunter can sign in again to start clean. Deleting
     * the `auth.users` row needs service-role credentials that must never ship
     * in an APK.
     */
    suspend fun deleteCloudData(): Result<Unit> {
        val client = requireClient().getOrElse { return failure(it) }
        val userId = _account.value?.userId
            ?: return Result.failure(IllegalStateException("Sign in before deleting cloud data"))
        return runCatching {
            client.postgrest.from("profiles").delete {
                filter { eq("id", userId) }
            }
            client.auth.signOut()
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
                ?: throw IllegalStateException("Your hunter profile could not be loaded")
            _account.value = profile
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun signOut(): Result<Unit> {
        // Local state clears first: client.auth.signOut() is a network
        // revocation, and treating it as a precondition stranded the hunter
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

private val UnclaimedHandle = Regex("^Hunter\\d{4}$")


/** Signed-out guard for cloud features that need an identity. */
internal fun requireAccount(account: AccountRepository): Result<Account> {
    val current = account.account.value
    return if (current == null) {
        Result.failure(IllegalStateException("Sign in to use cloud features"))
    } else {
        Result.success(current)
    }
}

/** Sentinel: auth succeeded but the profiles row is absent — recoverable by
 *  re-inserting the row, not by telling the user to sign up again. */
private class ProfileMissingException : IllegalStateException(
    "Signed in, but your hunter profile is missing",
)
