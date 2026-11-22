package com.ironvellum.app.data.cloud

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.russhwolf.settings.Settings
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import java.io.IOException
import java.util.UUID

/** A legacy installation keeps its SDK store until the user explicitly changes backend. */
internal class BackendChoice(private val prefs: SharedPreferences, private val fallback: CloudConfig) {
    private fun resolve(url: String?, key: String?) =
        resolveCloudConfig(url, key, fallback.url, fallback.key)

    fun read() = synchronized(prefs) {
        resolve(prefs.getString("url", null), prefs.getString("key", null))
            ?.copy(authNamespace = prefs.getString("auth_namespace", null))
    }

    fun fresh(override: CloudConfig?) = resolve(override?.url, override?.key)
        ?.copy(authNamespace = UUID.randomUUID().toString())

    @SuppressLint("UseKtx") // The caller must observe disk write failures.
    private fun write(url: String?, key: String?, namespace: String?): Boolean =
        prefs.edit().putString("url", url).putString("key", key)
            .putString("auth_namespace", namespace).commit()

    fun persist(override: CloudConfig?, choice: CloudConfig?) = synchronized(prefs) {
        val url = prefs.getString("url", null)
        val key = prefs.getString("key", null)
        val namespace = prefs.getString("auth_namespace", null)
        if (!write(override?.url, override?.key, choice?.authNamespace)) {
            // Android publishes edits to its in-memory map even when the disk commit fails.
            // Restore the original keys before another writer can flush that map.
            val restored = write(url, key, namespace)
            throw IOException(if (restored) "Could not persist backend selection"
                else "Could not persist or confirm restoration of backend selection")
        }
    }
}

internal fun CloudConfig.authStores(settings: Settings) = authNamespace?.let { namespace ->
    val prefix = "ironvellum:$namespace:$url"
    SettingsSessionManager(settings, "$prefix:session") to SettingsCodeVerifierCache(settings, "$prefix:verifier")
}
