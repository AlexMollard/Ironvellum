package com.ironvellum.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.domain.BackupFiles
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The weekly file backup: the same archive "Export archive" shares
 * ([Repository.exportJson]), written to a folder the lifter picked through the
 * Storage Access Framework. Off until a folder is chosen. The newest
 * [BackupFiles.KEEP] files this feature wrote are kept; nothing else in the
 * folder is touched.
 *
 * The weekly run is one WorkManager periodic job, the mechanism the app's
 * other background jobs already use. Every outcome is stored for Settings to
 * show, so a lost folder or a failed write is never silent.
 */
object AutoBackup {
    private const val PREFS = "auto_backup"
    private const val KEY_FOLDER = "folder"
    private const val KEY_FOLDER_NAME = "folder_name"
    private const val KEY_LAST_AT = "last_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val WORK_NAME = "ironvellum-auto-backup"
    private const val LOST = "Access to the backup folder was lost. Choose the folder again."

    private val lock = Mutex()

    /** What Settings shows. [error] is the last run's failure, or the lost folder access, in plain words. */
    data class State(val folderName: String?, val lastAtMs: Long, val error: String?) {
        val on: Boolean get() = folderName != null
    }

    private var flow: MutableStateFlow<State>? = null

    /** Re-read on every call, so access revoked while the app was away shows when Settings opens. */
    fun state(context: Context): StateFlow<State> = flow(context).also { it.value = read(context) }.asStateFlow()

    private fun flow(context: Context): MutableStateFlow<State> =
        flow ?: MutableStateFlow(read(context)).also { flow = it }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun folder(context: Context): Uri? =
        prefs(context).getString(KEY_FOLDER, null)?.let(Uri::parse)

    private fun hasAccess(context: Context, tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    private fun read(context: Context): State {
        val p = prefs(context)
        val tree = folder(context) ?: return State(null, 0L, null)
        val error = if (hasAccess(context, tree)) p.getString(KEY_LAST_ERROR, null) else LOST
        return State(p.getString(KEY_FOLDER_NAME, null) ?: "the chosen folder", p.getLong(KEY_LAST_AT, 0L), error)
    }

    private fun publish(context: Context) {
        flow(context).value = read(context)
    }

    /** Idempotent: keeps the existing weekly job across launches, and books none while off. */
    fun ensureScheduled(context: Context) {
        if (folder(context) == null) return
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(Duration.ofDays(7))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            // Without a delay a periodic job runs at once, doubling the backup enable() already makes.
            .setInitialDelay(Duration.ofDays(7))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Switches on for [tree], keeps the permission across restarts, and makes the first backup now. */
    suspend fun enable(context: Context, repo: Repository, tree: Uri) {
        val old = folder(context)
        context.contentResolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        if (old != null && old != tree) release(context, old)
        val name = withContext(Dispatchers.IO) { displayName(context, tree) }
        prefs(context).edit {
            putString(KEY_FOLDER, tree.toString())
            putString(KEY_FOLDER_NAME, name)
            remove(KEY_LAST_ERROR)
        }
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        ensureScheduled(context)
        publish(context)
        run(context, repo)
    }

    fun disable(context: Context) {
        folder(context)?.let { release(context, it) }
        prefs(context).edit { clear() }
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        publish(context)
    }

    private fun release(context: Context, tree: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** One backup now. Records the outcome either way; false when it failed. */
    suspend fun run(context: Context, repo: Repository): Boolean = lock.withLock {
        val tree = folder(context) ?: return false
        val failure = if (!hasAccess(context, tree)) {
            LOST
        } else {
            runCatching {
                val json = repo.exportJson()
                withContext(Dispatchers.IO) { write(context, tree, json) }
            }.exceptionOrNull()?.let { "The last backup failed: ${it.message ?: "the file could not be written"}" }
        }
        prefs(context).edit {
            if (failure == null) {
                putLong(KEY_LAST_AT, System.currentTimeMillis())
                remove(KEY_LAST_ERROR)
            } else {
                putString(KEY_LAST_ERROR, failure)
            }
        }
        publish(context)
        failure == null
    }

    private fun write(context: Context, tree: Uri, json: String) {
        val resolver = context.contentResolver
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val file = DocumentsContract.createDocument(
            resolver, dir, "application/json", BackupFiles.nameFor(LocalDateTime.now()),
        ) ?: error("the folder refused a new file")
        try {
            val out = resolver.openOutputStream(file, "wt") ?: error("the new file could not be opened")
            out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            // never leave a truncated archive among the real ones
            runCatching { DocumentsContract.deleteDocument(resolver, file) }
            throw e
        }
        // Retention is housekeeping: a failure here must not fail a backup that was written.
        runCatching { prune(context, tree) }
    }

    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val idByName = HashMap<String, String>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c -> while (c.moveToNext()) idByName[c.getString(1)] = c.getString(0) }
        BackupFiles.toDelete(idByName.keys).forEach { name ->
            DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, idByName.getValue(name)))
        }
    }

    private fun displayName(context: Context, tree: Uri): String? {
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return context.contentResolver.query(
            dir, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }
}

/** The weekly run. A failure is recorded for Settings, not retried in a loop: next week tries again. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? IronvellumApp ?: return Result.success()
        AutoBackup.run(app, app.repository)
        return Result.success()
    }
}
