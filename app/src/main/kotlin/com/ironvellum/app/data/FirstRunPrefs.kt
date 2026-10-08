package com.ironvellum.app.data

import android.content.Context
import androidx.core.content.edit

/**
 * The first-run flags that live in SharedPreferences: the Welcome screen, the Veil's introduction and
 * the notification ask. Setup's own "dismissed" flag stays with the setup flow in the same file.
 */
object FirstRunPrefs {

    private const val ONBOARDING = "onboarding"
    private const val KEY_WELCOME_SEEN = "welcome_seen"
    private const val KEY_VEIL_INTRO_SEEN = "veil_intro_seen"

    // The trial's own file and key, so a lifter already asked on an older build is never asked again.
    private const val TRIAL = "trial"
    private const val KEY_ASKED_NOTIFICATIONS = "asked_post_notifications"
    private const val KEY_NOTIFICATIONS_NOT_NOW = "post_notifications_not_now"

    private fun onboarding(context: Context) = context.getSharedPreferences(ONBOARDING, Context.MODE_PRIVATE)

    private fun trial(context: Context) = context.getSharedPreferences(TRIAL, Context.MODE_PRIVATE)

    fun welcomeSeen(context: Context): Boolean = onboarding(context).getBoolean(KEY_WELCOME_SEEN, false)

    fun setWelcomeSeen(context: Context, seen: Boolean = true) =
        onboarding(context).edit { putBoolean(KEY_WELCOME_SEEN, seen) }

    fun veilIntroSeen(context: Context): Boolean = onboarding(context).getBoolean(KEY_VEIL_INTRO_SEEN, false)

    fun setVeilIntroSeen(context: Context, seen: Boolean = true) =
        onboarding(context).edit { putBoolean(KEY_VEIL_INTRO_SEEN, seen) }

    fun notificationsAsked(context: Context): Boolean = trial(context).getBoolean(KEY_ASKED_NOTIFICATIONS, false)

    fun setNotificationsAsked(context: Context, asked: Boolean = true) =
        trial(context).edit { putBoolean(KEY_ASKED_NOTIFICATIONS, asked) }

    fun notificationsNotNow(context: Context): Int = trial(context).getInt(KEY_NOTIFICATIONS_NOT_NOW, 0)

    fun addNotificationsNotNow(context: Context) =
        trial(context).edit { putInt(KEY_NOTIFICATIONS_NOT_NOW, notificationsNotNow(context) + 1) }
}
