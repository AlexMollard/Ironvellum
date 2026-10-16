package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.domain.Muscle

/** The muscles whose Heavy flag the lifter dismissed on Muscle Coverage. A device preference, kept per muscle. */
class DismissedHeavyStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("appearance", Context.MODE_PRIVATE))

    var muscles: Set<Muscle>
        get() = prefs.getStringSet(KEY, emptySet()).orEmpty()
            .mapNotNull { name -> runCatching { Muscle.valueOf(name) }.getOrNull() }
            .toSet()
        set(value) = prefs.edit { putStringSet(KEY, value.map { it.name }.toSet()) }

    private companion object {
        const val KEY = "coverage_heavy_dismissed"
    }
}
