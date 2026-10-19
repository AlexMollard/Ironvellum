package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.ironvellum.app.R
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WorkoutSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * What a finished workout becomes in Health Connect. Pure, so the times, the type, the title and the ids are
 * unit-tested without a Health Connect provider; [HealthSync] turns a [Spec] into the real record.
 */
object ExerciseSessionMapping {

    /** The record to upsert; [clientRecordId] and [clientRecordVersion] are what make a rewrite replace, not duplicate. */
    data class Spec(
        val clientRecordId: String,
        val clientRecordVersion: Long,
        val start: Instant,
        val startOffset: ZoneOffset,
        val end: Instant,
        val endOffset: ZoneOffset,
        val exerciseType: Int,
        val title: String?,
        val notes: String?,
    )

    /**
     * Strength training for every workout, bodyweight-only ones included: Health Connect has a calisthenics type,
     * but telling the two apart means guessing from loads and the catalogue, and a wrong guess is worse than the
     * broad, correct label.
     */
    const val EXERCISE_TYPE = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING

    /**
     * Stable per workout. The start time is in it because ids are autoincrement: after a reinstall a new
     * workout 1 must not overwrite the records an old install left in Health Connect.
     */
    fun clientRecordId(sessionId: Long, startedAtMs: Long): String = "ironvellum-$sessionId-$startedAtMs"

    /**
     * The record for [session], or null when it must not be written: not finished yet, or imported (history that
     * came from another app was not recorded here, so it is not ours to publish).
     *
     * The version rises with every change worth writing: the seal, an amendment, and [nowMs] for the edits that
     * touch neither (a title or note), because Health Connect keeps the stored record when the version does not
     * increase.
     */
    fun specFor(session: WorkoutSession, nowMs: Long, zone: ZoneId): Spec? {
        val completed = session.completedAtMs ?: return null
        if (session.imported) return null
        val start = Instant.ofEpochMilli(session.startedAtMs)
        // Health Connect rejects a record that ends before it starts; a zero-length one gets a second.
        val end = Instant.ofEpochMilli(maxOf(completed, session.startedAtMs + 1_000L))
        return Spec(
            clientRecordId = clientRecordId(session.id, session.startedAtMs),
            clientRecordVersion = maxOf(completed, session.editedAtMs ?: 0L, nowMs),
            start = start,
            startOffset = zone.rules.getOffset(start),
            end = end,
            endOffset = zone.rules.getOffset(end),
            exerciseType = EXERCISE_TYPE,
            title = session.title.trim().ifBlank { session.label.trim() }.ifBlank { null },
            notes = session.note.trim().ifBlank { null },
        )
    }
}

/** What Repository tells the write-back about; [HealthWriteBack] is the only implementation. */
interface SessionWriteBack {
    /** A workout was sealed or changed after sealing. */
    fun saved(session: WorkoutSession)

    /** A sealed workout was deleted or reopened, so its record goes. */
    fun removed(sessionId: Long, startedAtMs: Long)
}

/** Whether saving is on, and how the last attempt went; the Health Connect page shows it. */
data class WriteBackState(val enabled: Boolean = false, val lastError: UiText? = null, val lastOkMs: Long? = null)

/**
 * Saves finished workouts to Health Connect, when the lifter turned it on and Health Connect allowed it. Every
 * Health Connect call runs on its own app-lifetime scope, after Room's transaction has committed: a seal never
 * waits for it and never fails because of it. A failure is logged and kept as [WriteBackState.lastError].
 */
class HealthWriteBack(
    private val health: HealthSync,
    private val prefs: SharedPreferences,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // One at a time and in order, so a delete queued after a save is never overtaken by it.
    @OptIn(ExperimentalCoroutinesApi::class)
    dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : SessionWriteBack {
    constructor(context: Context, health: HealthSync) :
        this(health, context.getSharedPreferences("health_write", Context.MODE_PRIVATE))

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val current = MutableStateFlow(
        WriteBackState(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            lastError = decode(prefs.getString(KEY_ERROR, null)),
            lastOkMs = if (prefs.contains(KEY_OK)) prefs.getLong(KEY_OK, 0L) else null,
        ),
    )
    val state: StateFlow<WriteBackState> = current.asStateFlow()

    /** Whether Health Connect currently lets this app save workouts. False when it is missing or says no. */
    suspend fun permitted(): Boolean = WRITE_PERMISSION in health.grantedPermissions()

    /** The switch. Turning it on clears the old failure: it described a state that has just changed. */
    fun setEnabled(on: Boolean) {
        prefs.edit {
            putBoolean(KEY_ENABLED, on)
            if (on) remove(KEY_ERROR)
        }
        current.value = current.value.copy(enabled = on, lastError = if (on) null else current.value.lastError)
    }

    override fun saved(session: WorkoutSession) {
        if (!current.value.enabled) return
        val spec = ExerciseSessionMapping.specFor(session, nowMs(), zone()) ?: return
        scope.launch {
            if (!health.available()) return@launch fail(UNAVAILABLE)
            if (!permitted()) return@launch fail(REVOKED)
            try {
                health.writeExerciseSession(spec)
                succeed()
            } catch (e: Exception) {
                Log.w(TAG, "health connect save failed", e)
                failRaw(e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
            }
        }
    }

    // A removal goes through even when saving is switched off: the record is ours, and the lifter just deleted its source.
    override fun removed(sessionId: Long, startedAtMs: Long) {
        scope.launch {
            if (!health.available() || !permitted()) return@launch
            try {
                health.deleteExerciseSession(ExerciseSessionMapping.clientRecordId(sessionId, startedAtMs))
            } catch (e: Exception) {
                Log.w(TAG, "health connect removal failed", e)
                if (current.value.enabled) fail(REMOVE_FAILED)
            }
        }
    }

    private fun succeed() {
        val at = nowMs()
        prefs.edit {
            remove(KEY_ERROR)
            putLong(KEY_OK, at)
        }
        current.value = current.value.copy(lastError = null, lastOkMs = at)
    }

    // The failure is stored as a code (or the platform's own wording), never as a sentence of ours: it is worded when
    // the page shows it, in that moment's language and Themed names mode.
    private fun fail(code: String) {
        prefs.edit { putString(KEY_ERROR, code) }
        current.value = current.value.copy(lastError = decode(code))
    }

    private fun failRaw(reason: String) = fail(RAW + reason)

    companion object {
        /** The one write permission; kept out of the read set that decides whether Health Connect counts as connected. */
        val WRITE_PERMISSION: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)

        private const val TAG = "Ironvellum"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ERROR = "last_error"
        private const val KEY_OK = "last_ok_ms"

        private const val RAW = "raw:"
        private const val UNAVAILABLE = "unavailable"
        private const val REVOKED = "revoked"
        private const val REMOVE_FAILED = "remove_failed"

        /** A stored failure as words; a value that is none of ours is a sentence an older build stored, shown as it is. */
        private fun decode(stored: String?): UiText? = when {
            stored == null -> null
            stored.startsWith(RAW) -> UiText.Raw(stored.removePrefix(RAW))
            stored == UNAVAILABLE -> UiText.res(R.string.health_writeback_unavailable)
            stored == REVOKED -> UiText.res(R.string.health_writeback_revoked)
            stored == REMOVE_FAILED -> UiText.res(R.string.health_writeback_remove_failed)
            else -> UiText.Raw(stored)
        }
    }
}
