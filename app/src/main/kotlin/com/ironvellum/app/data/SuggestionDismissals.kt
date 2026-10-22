package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.domain.BlockKey

/**
 * The blocks of a live trial whose progression suggestion the lifter dismissed. A device preference, kept per trial so
 * leaving the screen and coming back to the trial does not bring the suggestion back; only the newest few trials are kept.
 */
class SuggestionDismissals(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("suggestion_dismissals", Context.MODE_PRIVATE))

    fun dismissed(sessionId: Long): Set<BlockKey> =
        prefs.getStringSet(key(sessionId), emptySet()).orEmpty().mapNotNull { entry ->
            val (position, exerciseId) = entry.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            BlockKey(position.toIntOrNull() ?: return@mapNotNull null, exerciseId.toLongOrNull() ?: return@mapNotNull null)
        }.toSet()

    fun dismiss(sessionId: Long, block: BlockKey) {
        val next = prefs.getStringSet(key(sessionId), emptySet()).orEmpty() + "${block.position}:${block.exerciseId}"
        val stale = prefs.all.keys.mapNotNull { it.removePrefix(PREFIX).toLongOrNull() }.sortedDescending().drop(KEEP_TRIALS)
        prefs.edit {
            putStringSet(key(sessionId), next)
            stale.forEach { remove(key(it)) }
        }
    }

    private fun key(sessionId: Long) = "$PREFIX$sessionId"

    private companion object {
        const val PREFIX = "s"
        const val KEEP_TRIALS = 20
    }
}
