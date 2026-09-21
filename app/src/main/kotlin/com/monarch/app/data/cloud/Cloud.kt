package com.monarch.app.data.cloud

import com.monarch.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Single entry point to the cloud. `configured` is false while the local
 * project has no Supabase credentials; every repository then answers with a
 * readable Result.failure instead of crashing or silently doing nothing.
 */
object Cloud {
    val configured: Boolean = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()
    val googleConfigured: Boolean = configured && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    @Volatile
    private var instance: SupabaseClient? = null

    fun client(): SupabaseClient = instance ?: synchronized(this) {
        instance ?: createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY,
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

    val requireConfigured: Result<SupabaseClient>
        get() = if (configured) {
            Result.success(client())
        } else {
            Result.failure(IllegalStateException("Cloud sync is not configured on this build"))
        }

    /** Translate the supabase-kt exception zoo into a sentence a player can act on. */
    fun explain(error: Throwable): String = when (error) {
        is IllegalStateException -> error.message ?: "Cloud sync is not available"
        is AuthRestException -> when (error.errorCode) {
            AuthErrorCode.InvalidCredentials -> "Wrong email or password"
            AuthErrorCode.UserAlreadyExists -> "That email is already registered — sign in instead"
            AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit ->
                "Too many attempts — wait a minute and try again"
            else -> "The gate refused this sign-in — try again in a moment"
        }
        is PostgrestRestException -> when (error.code) {
            "23505" -> "That name is already taken by another hunter"
            "42501" -> "The cloud refused this — you are not allowed to change that record"
            "23514" -> "The cloud rejected this value as out of range"
            // Written for the shadow board's migration 0008, but these codes
            // mean "that table or column is not there" for ANY feature — it
            // named the wrong one the moment cloud_archives (0013) was absent
            // and a failed BACKUP blamed the leaderboard. PostgREST answers a
            // missing table with PGRST205 and a missing column with PGRST204
            // from its schema cache — the raw Postgres codes only surface when
            // the statement actually reaches the database.
            "42703", "42P01", "PGRST204", "PGRST205" ->
                "This part of the cloud is not set up yet — its database migration has not been applied"
            else -> "The cloud refused this request — try again"
        }
        is HttpRequestException -> "Could not reach the cloud — check your connection"
        // Never interpolate exception text: Credential Manager and the auth
        // SDK leak API internals that read as debug noise to a hunter.
        else -> "The System stumbled — try again in a moment"
    }

    fun <T> failure(error: Throwable): Result<T> = Result.failure(IllegalStateException(explain(error)))
}
