package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.domain.RestRules

/**
 * The live trial's device settings: whether a rest runs between sets (for every
 * movement, and for single movements), how its end is announced, and whether
 * the screen stays awake. Stored as preferences and included in the portable settings archive.
 * Movements are kept by lower-case name, the key the catalogue and an import already agree on.
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

    /** On by default: the workout as an Android 16 Live Update (a status-bar chip and a lock-screen card). Below 16 it changes nothing. */
    var liveUpdate: Boolean
        get() = prefs.getBoolean(KEY_LIVE_UPDATE, true)
        set(value) = prefs.edit { putBoolean(KEY_LIVE_UPDATE, value) }

    /** On by default, as the notification always was: the set in hand and its load show on the lock screen. Off shows only "Workout in progress". */
    var showLiftsOnLock: Boolean
        get() = prefs.getBoolean(KEY_LIFTS_ON_LOCK, true)
        set(value) = prefs.edit { putBoolean(KEY_LIFTS_ON_LOCK, value) }

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

    var compoundRest: Int?
        get() = seconds(KEY_COMPOUND)
        set(value) = setSeconds(KEY_COMPOUND, value)
    var isolationRest: Int?
        get() = seconds(KEY_ISOLATION)
        set(value) = setSeconds(KEY_ISOLATION, value)
    fun exerciseRest(name: String): Int? = seconds(EXERCISE_PREFIX + key(name))
    fun setExerciseRest(name: String, value: Int?) {
        if (key(name).isNotEmpty()) setSeconds(EXERCISE_PREFIX + key(name), value)
    }
    /** Move only this exercise's override after a successful custom-exercise rename. */
    internal fun renameExerciseRest(oldName: String, newName: String) {
        val old = EXERCISE_PREFIX + key(oldName)
        val new = EXERCISE_PREFIX + key(newName)
        if (old == new) return
        val value = seconds(old)
        prefs.edit {
            remove(old)
            if (value == null) remove(new) else putInt(new, value)
        }
    }

    /** Forget an override only after the custom exercise was actually deleted. */
    internal fun removeExerciseRest(name: String) {
        prefs.edit { remove(EXERCISE_PREFIX + key(name)) }
    }

    fun restPreferences(name: String): RestRules.Preferences {
        val compound = compoundRest
        val isolation = isolationRest
        val exercise = exerciseRest(name)
        return if (compound == null && isolation == null && exercise == null) RestRules.EMPTY_PREFERENCES
        else RestRules.Preferences(compound, isolation, exercise)
    }

    private fun seconds(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0).takeIf { it in RestRules.CUSTOM_SECONDS } else null
    private fun setSeconds(key: String, value: Int?) {
        require(value == null || value in RestRules.CUSTOM_SECONDS)
        prefs.edit { if (value == null) remove(key) else putInt(key, value) }
    }

    // A copy: the set the preferences hand back must never be edited in place.
    private fun movements(): Set<String> = prefs.getStringSet(KEY_OFF_MOVEMENTS, emptySet())?.toSet().orEmpty()

    /** The "Keep as is" answers to the dedicated-exercise prompt, one [com.ironvellum.app.domain.ExerciseTwins.keepKey] each. */
    val twinKept: Set<String> get() = prefs.getStringSet(KEY_TWIN_KEPT, emptySet())?.toSet().orEmpty()

    fun keepTwin(keepKey: String) {
        prefs.edit { putStringSet(KEY_TWIN_KEPT, twinKept + keepKey) }
    }

    /** True once the one-time card offering to convert past sets onto dedicated exercises was shown and closed or acted on. */
    var convertCardDone: Boolean
        get() = prefs.getBoolean(KEY_CONVERT_CARD_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_CONVERT_CARD_DONE, value) }

    private fun key(name: String) = name.trim().lowercase()

    companion object {
        const val EXERCISE_PREFIX = "rest_seconds.exercise."
        const val KEY_COMPOUND = "rest_seconds.compound"
        const val KEY_ISOLATION = "rest_seconds.isolation"
        const val KEY_REST_OFF = "rest_off"
        const val KEY_ALERT = "rest_alert"
        const val KEY_KEEP_AWAKE = "keep_screen_on"
        const val KEY_LIVE_UPDATE = "live_update"
        const val KEY_LIFTS_ON_LOCK = "lifts_on_lock"
        const val KEY_OFF_MOVEMENTS = "rest_off_movements"
        const val KEY_TWIN_KEPT = "twin_kept"
        const val KEY_CONVERT_CARD_DONE = "convert_card_done"
    }
}
