package com.ironvellum.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.ironvellum.app.domain.EffortScale

/**
 * Whether the live trial asks how hard a set was, and which scale the lifter reads it on. Kept on the device with
 * the other display choices, like [Units]: [enabled] and [scale] are Compose state, so a screen that reads them
 * recomposes when Settings changes them. Off by default. The stored figure is always RPE, so changing the scale
 * only changes what is shown; turning the question off hides the prompt but never an effort already logged.
 * Read once at app start by [init].
 */
object EffortSetting {
    private const val PREFS = "appearance"
    private const val KEY_ON = "logEffort"
    private const val KEY_SCALE = "effortScale"

    var enabled: Boolean by mutableStateOf(false)
        private set

    var scale: EffortScale by mutableStateOf(EffortScale.RPE)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ON, false)
        scale = EffortScale.fromWire(prefs.getString(KEY_SCALE, null))
    }

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ON, on) }
        enabled = on
    }

    fun setScale(context: Context, value: EffortScale) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_SCALE, value.name) }
        scale = value
    }
}
