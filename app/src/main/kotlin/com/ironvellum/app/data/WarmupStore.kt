package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.domain.WarmupStep

/**
 * The warm-up reminder's two device settings: whether it shows at all, and how
 * the live trial answered it. Only one trial is live at a time, so only that
 * trial's answer is kept; a new trial finds the old one's gone. Preferences,
 * not a column: it is a reminder, and nothing else reads it.
 */
class WarmupStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("warmup", Context.MODE_PRIVATE))

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    fun stateFor(sessionId: Long): WarmupStep.State =
        if (prefs.getLong(KEY_SESSION, -1L) != sessionId) {
            WarmupStep.State.OPEN
        } else {
            runCatching { WarmupStep.State.valueOf(prefs.getString(KEY_STATE, null).orEmpty()) }
                .getOrDefault(WarmupStep.State.OPEN)
        }

    fun setState(sessionId: Long, state: WarmupStep.State) = prefs.edit {
        putLong(KEY_SESSION, sessionId)
        putString(KEY_STATE, state.name)
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_SESSION = "session"
        const val KEY_STATE = "state"
    }
}
