package com.ironvellum.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * The month and year reviews the lifter dismissed from Today ([com.ironvellum.app.domain.ReviewPeriod.key]), so a card
 * they closed does not come back. Kept in the weekly recap's preferences file, which backup already leaves out: a
 * dismissal means nothing without the history it was about. [keys] is Compose state, so Today redraws when one is added.
 */
object ReviewDismissals {
    private const val PREFS = "weekly_recap"
    private const val KEY = "dismissed_reviews"

    var keys: Set<String> by mutableStateOf(emptySet())
        private set

    fun init(context: Context) {
        keys = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, null)?.toSet().orEmpty()
    }

    /** Remembers that the review named [key] was dismissed. */
    fun dismiss(context: Context, key: String) {
        val next = keys + key
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putStringSet(KEY, next) }
        keys = next
    }
}
