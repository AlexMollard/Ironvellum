package com.ironvellum.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.ironvellum.app.domain.LiftingExperience

/**
 * How long the lifter says they have been lifting, asked once in setup and changed in Settings. Kept on the device
 * with the other training choices, like [EffortSetting]: [answer] is Compose state, so the fatigue estimate and
 * Settings follow a change. Never asked of an existing install, which stays [LiftingExperience.UNANSWERED].
 * Read once at app start by [init].
 */
object LiftingExperienceSetting {
    private const val PREFS = "appearance"
    private const val KEY = "liftingExperience"

    var answer: LiftingExperience by mutableStateOf(LiftingExperience.UNANSWERED)
        private set

    fun init(context: Context) {
        answer = LiftingExperience.fromWire(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun set(context: Context, value: LiftingExperience) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, value.name) }
        answer = value
    }
}
