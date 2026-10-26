package com.ironvellum.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * Whether fatigue speaks up where a decision is made: Today's adjust line and rest panel, the live picker's marks and
 * Temper's recovery findings. One switch for all of them, on by default; the Muscles screens never depend on it.
 * Kept on the device with the other training choices, like [EffortSetting]: [enabled] is Compose state, so a screen
 * that reads it redraws when Settings changes it. Read once at app start by [init].
 */
object FatigueAdviceSetting {
    private const val PREFS = "appearance"
    private const val KEY = "fatigueAdvice"

    var enabled: Boolean by mutableStateOf(true)
        private set

    fun init(context: Context) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, on) }
        enabled = on
    }
}
