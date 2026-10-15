package com.ironvellum.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The first-run flags that live in SharedPreferences: the Welcome screen, the Veil's introduction and
 * the notification ask. Setup's own "dismissed" flag stays with the setup flow in the same file.
 */
object FirstRunPrefs {

    private const val ONBOARDING = "onboarding"
    private const val KEY_WELCOME_SEEN = "welcome_seen"
    private const val KEY_PREFS_SEEN = "preferences_seen"
    private const val KEY_VEIL_INTRO_SEEN = "veil_intro_seen"
    private const val KEY_VEIL_TERMS_SEEN = "veil_terms_seen"

    // The trial's own file and key, so a lifter already asked on an older build is never asked again.
    private const val TRIAL = "trial"
    private const val KEY_ASKED_NOTIFICATIONS = "asked_post_notifications"
    private const val KEY_NOTIFICATIONS_NOT_NOW = "post_notifications_not_now"

    private fun onboarding(context: Context) = context.getSharedPreferences(ONBOARDING, Context.MODE_PRIVATE)

    private fun trial(context: Context) = context.getSharedPreferences(TRIAL, Context.MODE_PRIVATE)

    fun welcomeSeen(context: Context): Boolean = onboarding(context).getBoolean(KEY_WELCOME_SEEN, false)

    fun setWelcomeSeen(context: Context, seen: Boolean = true) =
        onboarding(context).edit { putBoolean(KEY_WELCOME_SEEN, seen) }

    /**
     * Whether the first-run preferences screen (names, units, colour) has been passed. An install that left the
     * Welcome screen before this screen existed has no stored answer and counts as having passed it.
     */
    fun preferencesSeen(context: Context): Boolean {
        val prefs = onboarding(context)
        return if (prefs.contains(KEY_PREFS_SEEN)) prefs.getBoolean(KEY_PREFS_SEEN, true) else welcomeSeen(context)
    }

    fun setPreferencesSeen(context: Context, seen: Boolean = true) =
        onboarding(context).edit { putBoolean(KEY_PREFS_SEEN, seen) }

    fun veilIntroSeen(context: Context): Boolean = onboarding(context).getBoolean(KEY_VEIL_INTRO_SEEN, false)

    fun setVeilIntroSeen(context: Context, seen: Boolean = true) =
        onboarding(context).edit { putBoolean(KEY_VEIL_INTRO_SEEN, seen) }

    /** Whether the Veil has already explained its own words (echoes, crests) in either mode. */
    fun veilTermsSeen(context: Context): Boolean = onboarding(context).getBoolean(KEY_VEIL_TERMS_SEEN, false)

    fun setVeilTermsSeen(context: Context, seen: Boolean = true) =
        onboarding(context).edit { putBoolean(KEY_VEIL_TERMS_SEEN, seen) }

    /** Whether the one-line explanation of the word [key] has been dismissed ("Got it"): one flag per word. */
    fun termSeen(context: Context, key: String): Boolean = onboarding(context).getBoolean("term_seen_$key", false)

    fun setTermSeen(context: Context, key: String, seen: Boolean = true) =
        onboarding(context).edit { putBoolean("term_seen_$key", seen) }

    private const val KEY_SEX_UNSET = "sex_unset"
    private val sexUnset = MutableStateFlow<Boolean?>(null)

    /**
     * True after a Skip that never asked for a sex: the profile row holds the MALE default, but the lifter has not
     * chosen it, so Profile shows no selection until they do. Existing installs never set it.
     */
    fun sexUnsetFlow(context: Context): StateFlow<Boolean> {
        if (sexUnset.value == null) sexUnset.value = onboarding(context).getBoolean(KEY_SEX_UNSET, false)
        @Suppress("UNCHECKED_CAST")
        return sexUnset as StateFlow<Boolean>
    }

    fun setSexUnset(context: Context, unset: Boolean) {
        onboarding(context).edit { putBoolean(KEY_SEX_UNSET, unset) }
        sexUnset.value = unset
    }

    fun notificationsAsked(context: Context): Boolean = trial(context).getBoolean(KEY_ASKED_NOTIFICATIONS, false)

    fun setNotificationsAsked(context: Context, asked: Boolean = true) =
        trial(context).edit { putBoolean(KEY_ASKED_NOTIFICATIONS, asked) }

    fun notificationsNotNow(context: Context): Int = trial(context).getInt(KEY_NOTIFICATIONS_NOT_NOW, 0)

    fun addNotificationsNotNow(context: Context) =
        trial(context).edit { putInt(KEY_NOTIFICATIONS_NOT_NOW, notificationsNotNow(context) + 1) }
}
