package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.domain.BlockKey
import org.json.JSONArray
import org.json.JSONObject

/** The figures of one set still to log, as they were before an accepted suggestion changed them. */
data class SetFigures(val id: Long, val reps: Int, val weightKg: Double?, val durationSec: Int?)

/**
 * What an accepted progression suggestion changed, kept per trial and block so "Undo" still works after the screen is
 * left and come back to, or the process dies. It shares the device preference file of [SuggestionDismissals] (so it is
 * kept out of backups with it) and keeps only the newest few trials.
 */
class SuggestionUndos(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("suggestion_dismissals", Context.MODE_PRIVATE))

    fun snapshots(sessionId: Long): Map<BlockKey, List<SetFigures>> {
        val root = runCatching { JSONObject(prefs.getString(key(sessionId), null) ?: return emptyMap()) }.getOrNull() ?: return emptyMap()
        val out = mutableMapOf<BlockKey, List<SetFigures>>()
        for (entry in root.keys()) {
            val (position, exerciseId) = entry.split(":").takeIf { it.size == 2 } ?: continue
            val block = BlockKey(position.toIntOrNull() ?: continue, exerciseId.toLongOrNull() ?: continue)
            val rows = root.optJSONArray(entry) ?: continue
            out[block] = (0 until rows.length()).mapNotNull { i ->
                val row = rows.optJSONArray(i) ?: return@mapNotNull null
                SetFigures(
                    id = row.optLong(0),
                    reps = row.optInt(1),
                    weightKg = if (row.isNull(2)) null else row.optDouble(2),
                    durationSec = if (row.isNull(3)) null else row.optInt(3),
                )
            }
        }
        return out
    }

    fun save(sessionId: Long, block: BlockKey, figures: List<SetFigures>) = write(sessionId) { it.put(name(block), encode(figures)) }

    fun clear(sessionId: Long, block: BlockKey) = write(sessionId) { it.remove(name(block)) }

    private fun write(sessionId: Long, change: (JSONObject) -> Unit) {
        val root = runCatching { JSONObject(prefs.getString(key(sessionId), null) ?: "{}") }.getOrDefault(JSONObject())
        change(root)
        val stale = prefs.all.keys.filter { it.startsWith(PREFIX) }.mapNotNull { it.removePrefix(PREFIX).toLongOrNull() }
            .sortedDescending().drop(KEEP_TRIALS)
        prefs.edit {
            if (root.length() == 0) remove(key(sessionId)) else putString(key(sessionId), root.toString())
            stale.forEach { remove(key(it)) }
        }
    }

    private fun encode(figures: List<SetFigures>) = JSONArray().also { rows ->
        figures.forEach { f ->
            rows.put(JSONArray().put(f.id).put(f.reps).put(f.weightKg ?: JSONObject.NULL).put(f.durationSec ?: JSONObject.NULL))
        }
    }

    private fun name(block: BlockKey) = "${block.position}:${block.exerciseId}"
    private fun key(sessionId: Long) = "$PREFIX$sessionId"

    private companion object {
        const val PREFIX = "u"
        const val KEEP_TRIALS = 20
    }
}
