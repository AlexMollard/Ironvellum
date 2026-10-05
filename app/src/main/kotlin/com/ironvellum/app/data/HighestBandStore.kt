package com.ironvellum.app.data

import android.content.Context
import androidx.core.content.edit

/** Where the rank-up high-water mark lives: an index into Rank.BANDS, null before the first seal records one. */
interface HighestBandStore {
    fun get(): Int?
    fun set(index: Int)
}

/** [HighestBandStore] in its own prefs file. A reinstall starts it fresh, which costs at most one repeat moment. */
class PrefsHighestBandStore(context: Context) : HighestBandStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun get(): Int? = if (prefs.contains(KEY)) prefs.getInt(KEY, -1) else null

    override fun set(index: Int) = prefs.edit { putInt(KEY, index) }

    private companion object {
        const val PREFS = "rank_state"
        const val KEY = "highest_band"
    }
}
