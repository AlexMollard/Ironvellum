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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class Account(
    val userId: String,
    val email: String,
    val displayName: String,
    /** "public" | "friends" | "private" — mirrors the profile_visibility enum. */
    val visibility: String,
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
                loadAccount(user.id, user.email ?: "")?.let { _account.value = it }
            }
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
    }

    suspend fun signUp(email: String, password: String, displayName: String): Result<Account> {
        val name = displayName.trim()
        if (name.length < 2 || name.length > 24) {
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
            // Google users get no profiles row automatically: derive a
            // starting handle from the Google name, else the email local-part.
            val existing = loadAccount(user.id, email)
            if (existing != null) {
                _account.value = existing
                existing
            } else {
                createProfileForNewGoogleUser(client, user.id, email, user.userMetadata)
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
        email: String,
        metadata: JsonObject?,
    ) {
        val metaName = (metadata?.get("name") as? JsonPrimitive)?.content
        val base = sanitizeHandle(metaName ?: email.substringBefore('@'))
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
            cleaned.length >= 24 -> cleaned.take(24)
            cleaned.length >= 2 -> cleaned
            else -> "Hunter" + cleaned.ifEmpty { "0" }
        }
    }

    suspend fun signOut(): Result<Unit> {
        val client = requireClient().getOrElse { return failure(it) }
        return runCatching {
            client.auth.signOut()
            _account.value = null
        }.recoverCatching { error ->
            throw IllegalStateException(Cloud.explain(error))
        }
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
