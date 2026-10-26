package com.ironvellum.app.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.domain.SessionOverride

/**
 * The [SessionOverride] each workout began with, by session id: how that workout differed from its routine (a swap, a
 * lightened exercise from Today's fatigue advice). A device preference in the file of [SuggestionDismissals], so it
 * outlives the process, and only the newest few are kept. The default [None] remembers nothing, for a caller that
 * never overrides.
 */
interface SessionAdjustments {
    fun get(sessionId: Long): SessionOverride
    fun put(sessionId: Long, override: SessionOverride)

    object None : SessionAdjustments {
        override fun get(sessionId: Long) = SessionOverride.NONE
        override fun put(sessionId: Long, override: SessionOverride) = Unit
    }
}

class PrefsSessionAdjustments(private val prefs: SharedPreferences) : SessionAdjustments {
    override fun get(sessionId: Long): SessionOverride = SessionOverride.decode(prefs.getString(key(sessionId), null))

    override fun put(sessionId: Long, override: SessionOverride) {
        if (override.isEmpty) return
        val ids = prefs.all.keys.filter { it.startsWith(PREFIX) }.mapNotNull { it.removePrefix(PREFIX).toLongOrNull() }
        val stale = (ids + sessionId).distinct().sortedDescending().drop(KEEP_SESSIONS)
        prefs.edit {
            putString(key(sessionId), override.encode())
            stale.forEach { remove(key(it)) }
        }
    }

    private fun key(sessionId: Long) = "$PREFIX$sessionId"

    private companion object {
        const val PREFIX = "adjust_"
        const val KEEP_SESSIONS = 20
    }
}
