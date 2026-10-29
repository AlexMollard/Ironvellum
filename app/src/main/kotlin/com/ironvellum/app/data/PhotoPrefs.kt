package com.ironvellum.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.ironvellum.app.domain.PhotoRules
import java.time.DayOfWeek

/**
 * The two small choices around the weekly pose photos: which weekday they are asked on (portable: it rides in the
 * archive's settings), and the day the lifter last closed Today's prompt (this install only). Both are Compose state, so
 * Today redraws the moment one changes.
 */
object PhotoPrefs {
    private const val PREFS = "progress_photos"
    private const val KEY_POSE_DAY = "pose_day"
    private const val KEY_DISMISSED = "prompt_dismissed_day"

    /** The weekday the pose prompt shows on. Sunday until the lifter picks another. */
    var poseDay: DayOfWeek by mutableStateOf(PhotoRules.DEFAULT_POSE_DAY)
        private set

    /** The epoch day Today's prompt was last dismissed on, or null. */
    var dismissedDay: Long? by mutableStateOf(null)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        poseDay = prefs.getInt(KEY_POSE_DAY, PhotoRules.DEFAULT_POSE_DAY.value).let { v -> DayOfWeek.entries.firstOrNull { it.value == v } } ?: PhotoRules.DEFAULT_POSE_DAY
        dismissedDay = if (prefs.contains(KEY_DISMISSED)) prefs.getLong(KEY_DISMISSED, 0L) else null
    }

    fun setPoseDay(context: Context, day: DayOfWeek) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt(KEY_POSE_DAY, day.value) }
        poseDay = day
    }

    /** Hushes Today's prompt for [epochDay]; it returns on the next pose day. */
    fun dismissPrompt(context: Context, epochDay: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong(KEY_DISMISSED, epochDay) }
        dismissedDay = epochDay
    }
}
