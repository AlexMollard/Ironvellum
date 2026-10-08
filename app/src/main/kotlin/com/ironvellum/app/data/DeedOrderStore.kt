package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.domain.EarnedOrder

/** The lifter's pick of Rarest or Newest for the earned deeds. A device preference, not a column. */
class DeedOrderStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("deeds", Context.MODE_PRIVATE))

    var order: EarnedOrder
        get() = runCatching { EarnedOrder.valueOf(prefs.getString(KEY, null).orEmpty()) }
            .getOrDefault(EarnedOrder.Rarest)
        set(value) = prefs.edit { putString(KEY, value.name) }

    private companion object {
        const val KEY = "earned_order"
    }
}
