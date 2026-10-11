package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.domain.RestAlert

/**
 * The live trial's device settings: whether a rest runs between sets (for every
 * movement, and for single movements), how its end is announced, and whether
 * the screen stays awake. Preferences, not columns: they describe this phone,
 * and nothing in the database or the cloud reads them. Movements are kept by
 * lower-case name, the key the catalogue and an import already agree on.
 */
class TrialPrefs(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("trial_prefs", Context.MODE_PRIVATE))

    /** True when no rest starts after any set. */
    var restOff: Boolean
        get() = prefs.getBoolean(KEY_REST_OFF, false)
        set(value) = prefs.edit { putBoolean(KEY_REST_OFF, value) }

    var restAlert: RestAlert
        get() = RestAlert.fromWire(prefs.getString(KEY_ALERT, null))
        set(value) = prefs.edit { putString(KEY_ALERT, value.wire) }

    /** On by default: a phone that sleeps between sets hides the rest timer. */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_AWAKE, true)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_AWAKE, value) }

    fun restOffFor(exerciseName: String): Boolean = key(exerciseName) in movements()

    fun setRestOffFor(exerciseName: String, off: Boolean) {
        val next = movements().toMutableSet()
        if (off) next += key(exerciseName) else next -= key(exerciseName)
        prefs.edit { putStringSet(KEY_OFF_MOVEMENTS, next) }
    }

    /** The movements (lower-case names) that opted out of the rest timer. */
    fun restOffNames(): Set<String> = movements()

    /** Whether ticking a set of [exerciseName] starts no rest: the global switch or the movement's own. */
    fun restIsOff(exerciseName: String): Boolean = restOff || restOffFor(exerciseName)

    // A copy: the set the preferences hand back must never be edited in place.
    private fun movements(): Set<String> = prefs.getStringSet(KEY_OFF_MOVEMENTS, emptySet())?.toSet().orEmpty()

    private fun key(name: String) = name.trim().lowercase()

    private companion object {
        const val KEY_REST_OFF = "rest_off"
        const val KEY_ALERT = "rest_alert"
        const val KEY_KEEP_AWAKE = "keep_screen_on"
        const val KEY_OFF_MOVEMENTS = "rest_off_movements"
    }
}
