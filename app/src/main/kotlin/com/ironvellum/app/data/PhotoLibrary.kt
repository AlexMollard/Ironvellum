package com.ironvellum.app.data

import android.content.Context
import android.net.Uri
import com.ironvellum.app.data.db.ProgressPhotoDao
import com.ironvellum.app.data.db.ProgressPhotoEntity
import com.ironvellum.app.domain.PhotoKind
import com.ironvellum.app.domain.PhotoRules
import com.ironvellum.app.domain.PhotoSnapshot
import com.ironvellum.app.domain.Pose
import com.ironvellum.app.domain.ProgressPhoto
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The progress photos: the rows in Room and the files in [store]. Device-only by promise: no method here, and no row,
 * reaches the cloud copy of the archive or the web snapshot (NoCloudPhotosTest). [store] is null only in tests that
 * never take a photo; every file step is then a no-op.
 */
class PhotoLibrary(private val dao: ProgressPhotoDao, val store: PhotoStore?) {

    /** Every kept photo, newest first. A row whose file is not on this phone (a restore from a bare archive) is still listed: [hasFile]. */
    val photos: Flow<List<ProgressPhoto>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun hasFile(photo: ProgressPhoto): Boolean = store?.exists(photo.fileName) == true

    /**
     * Keeps the picture at [source] as the photo of [day], attached to [sessionId]. A photo already kept for the day is
     * replaced, its file removed, so there is only ever one a day. Nothing is written when the picture cannot be read.
     */
    suspend fun saveWorkout(context: Context, source: Uri, sessionId: Long, day: Long, takenAtMs: Long = System.currentTimeMillis()) =
        save(context, source, PhotoKind.WORKOUT, day, null, sessionId, takenAtMs)

    /** Keeps the picture at [source] as [pose] of the week starting [week] (an epoch day), replacing the one that was there. */
    suspend fun savePose(context: Context, source: Uri, week: Long, pose: Pose, takenAtMs: Long = System.currentTimeMillis()) =
        save(context, source, PhotoKind.POSE, week, pose, null, takenAtMs)

    private suspend fun save(context: Context, source: Uri, kind: PhotoKind, day: Long, pose: Pose?, sessionId: Long?, takenAtMs: Long) {
        val store = checkNotNull(store) { "no photo storage" }
        val salt = UUID.randomUUID().toString().replace("-", "").take(8)
        val name = if (pose == null) PhotoRules.workoutFileName(day, salt) else PhotoRules.poseFileName(day, pose, salt)
        withContext(Dispatchers.IO) { store.importFrom(context, source, name) }
        try {
            val old = dao.find(kind.name, day, pose?.key.orEmpty())
            dao.insert(ProgressPhotoEntity(kind = kind.name, day = day, sessionId = sessionId, pose = pose?.key.orEmpty(), fileName = name, takenAtMs = takenAtMs))
            old?.takeIf { it.fileName != name }?.let { withContext(Dispatchers.IO) { store.delete(it.fileName) } }
        } catch (e: Throwable) {
            withContext(Dispatchers.IO) { store.delete(name) }
            throw e
        }
    }

    /** Removes one photo: its row and its file. */
    suspend fun delete(id: Long) {
        val row = dao.byId(id) ?: return
        dao.delete(id)
        withContext(Dispatchers.IO) { store?.delete(row.fileName) }
    }

    /** The days that already hold a workout photo: the one-a-day rule reads this ([PhotoRules.workoutPhotoOffered]). */
    suspend fun workoutPhotoDays(): Set<Long> = dao.all().filter { it.kind == PhotoKind.WORKOUT.name }.map { it.day }.toSet()

    /** Every kept photo's file name: what the folder backup mirrors. */
    suspend fun fileNames(): Set<String> = dao.all().map { it.fileName }.toSet()

    /** The file names of the photos kept with [sessionId], read before the workout is deleted (the rows go with it). */
    suspend fun namesFor(sessionId: Long): List<String> = dao.forSession(sessionId).map { it.fileName }

    suspend fun deleteFiles(names: Collection<String>) {
        withContext(Dispatchers.IO) { names.forEach { store?.delete(it) } }
    }

    /** What the archive carries: the rows only, each workout photo named by its workout's start time. */
    suspend fun snapshot(startedAtOf: suspend (Long) -> Long?): List<PhotoSnapshot> = dao.all().mapNotNull { row ->
        val kind = PhotoKind.valueOf(row.kind)
        val started = if (kind == PhotoKind.WORKOUT) (row.sessionId?.let { startedAtOf(it) } ?: return@mapNotNull null) else null
        PhotoSnapshot(kind, row.day, started, Pose.fromKey(row.pose), row.fileName, row.takenAtMs)
    }

    /**
     * Replaces every row with [rows]; call inside the restore's transaction, after the sessions are back
     * ([sessionIdByStart]: start time to new id). A row that names an unknown workout or a name that is not ours is skipped.
     */
    suspend fun restoreRows(rows: List<PhotoSnapshot>, sessionIdByStart: Map<Long, Long>) {
        dao.clearAll()
        rows.forEach { r ->
            if (!PhotoRules.isOurName(r.fileName)) return@forEach
            val sessionId = if (r.kind == PhotoKind.WORKOUT) (sessionIdByStart[r.sessionStartedAtMs] ?: return@forEach) else null
            if (r.kind == PhotoKind.POSE && r.pose == null) return@forEach
            dao.insert(ProgressPhotoEntity(kind = r.kind.name, day = r.day, sessionId = sessionId, pose = r.pose?.key.orEmpty(), fileName = r.fileName, takenAtMs = r.takenAtMs))
        }
    }

    /** Deletes the files no row names: what a deleted workout, a replaced set or a restore leaves behind. */
    suspend fun sweep() {
        val store = store ?: return
        val keep = dao.all().map { it.fileName }.toSet()
        withContext(Dispatchers.IO) { store.sweep(keep) }
    }

    /** The names of rows whose file is missing here: what a restore from a bare archive still needs from the backup folder. */
    suspend fun missingFiles(): Set<String> {
        val store = store ?: return emptySet()
        return dao.all().map { it.fileName }.filterNot(store::exists).toSet()
    }

    private fun ProgressPhotoEntity.toDomain() = ProgressPhoto(id, PhotoKind.valueOf(kind), day, sessionId, Pose.fromKey(pose), fileName, takenAtMs)
}
