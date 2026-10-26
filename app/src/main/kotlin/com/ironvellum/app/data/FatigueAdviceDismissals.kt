package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.ironvellum.app.domain.Muscle
import java.time.LocalDate

/**
 * What the lifter waved away from the fatigue suggestions, on the device and in the preferences file of
 * [SuggestionDismissals] (which backup leaves out: a dismissal means nothing without the history it was about).
 *
 *  - "Not today" on Today: keyed by calendar day and routine, so it ends by itself at midnight and only one routine's
 *    suggestion goes. [notToday] is Compose state, so the card redraws when one is added.
 *  - "Keep as is" on a Temper finding: standing, per routine and muscle. A pattern that moves to another muscle is a
 *    different key, so it comes back.
 */
class FatigueAdviceDismissals(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("suggestion_dismissals", Context.MODE_PRIVATE))

    /** The "day:routine" keys of today's "Not today"; Compose state. */
    var notToday: Set<String> by mutableStateOf(readNotToday())
        private set

    fun isNotToday(day: LocalDate, presetId: Long): Boolean = notTodayKey(day, presetId) in notToday

    /** Hides the suggestion for [presetId] on [day]; earlier days are forgotten. */
    fun dismissToday(day: LocalDate, presetId: Long) {
        val keep = notToday.filter { it.substringBefore(':').toLongOrNull()?.let { d -> d >= day.toEpochDay() } == true }.toSet()
        val next = keep + notTodayKey(day, presetId)
        prefs.edit { putStringSet(KEY_TODAY, next) }
        notToday = next
    }

    fun isKept(presetId: Long, muscle: Muscle): Boolean = keepKey(presetId, muscle) in kept()

    /** Stops flagging [muscle] on [presetId]'s starts. */
    fun keepAsIs(presetId: Long, muscle: Muscle) {
        prefs.edit { putStringSet(KEY_KEEP, kept() + keepKey(presetId, muscle)) }
    }

    private fun kept(): Set<String> = prefs.getStringSet(KEY_KEEP, emptySet()).orEmpty().toSet()

    private fun readNotToday(): Set<String> = prefs.getStringSet(KEY_TODAY, emptySet()).orEmpty().toSet()

    private fun notTodayKey(day: LocalDate, presetId: Long) = "${day.toEpochDay()}:$presetId"

    private fun keepKey(presetId: Long, muscle: Muscle) = "$presetId:${muscle.name}"

    private companion object {
        // Not an "s<id>" key: SuggestionDismissals prunes those by number.
        const val KEY_TODAY = "fatigue_not_today"
        const val KEY_KEEP = "fatigue_keep_as_is"
    }
}
