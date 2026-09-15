package com.monarch.app.data

import android.content.Context
import java.io.File

/**
 * Rolling copies of the raw database file, taken at startup BEFORE Room opens it.
 *
 * Why the raw file and why this early: a failed or missing migration makes the
 * database unopenable, at which point nothing can be exported through Room or
 * the repository — the data is intact on disk but unreachable. A byte copy taken
 * before the first open is the only snapshot that survives that case, so it is
 * deliberately dumb: no schema knowledge, no serialisation, no Room.
 *
 * There is deliberately NO restore function here, and adding a naive one is a
 * trap: copying a .bak over the live file while Room holds it open leaves the
 * open instance writing into the unlinked inode while new opens see the
 * restored file — the two diverge silently. A restore must close and rebuild
 * the Room instance (or clear and repopulate through it), so it belongs
 * wherever the database instance is owned, not in this dumb copier.
 */
object DbSnapshot {

    /** Snapshots kept on disk; one per day, oldest pruned first. */
    const val KEEP = 7

    private const val DIR = "db-snapshots"

    /**
     * Copies the database (and its write-ahead log) into app-private storage,
     * at most once per day. Returns the snapshot written, or null when there was
     * nothing to copy or today's copy already exists.
     *
     * Failures are swallowed on purpose: a backup that crashes the app on launch
     * is worse than a missing backup.
     */
    fun capture(context: Context, dbName: String = MonarchDatabase.NAME, today: Long = System.currentTimeMillis()): File? =
        runCatching {
            val live = context.getDatabasePath(dbName)
            if (!live.exists() || live.length() == 0L) return null

            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val stamp = today / 86_400_000L
            val target = File(dir, "$dbName.$stamp.bak")
            if (target.exists()) return null

            live.copyTo(target, overwrite = true)
            // The WAL holds writes not yet folded into the main file; without it
            // a restored copy can be missing the most recent session.
            File(live.parentFile, "$dbName-wal")
                .takeIf { it.exists() }
                ?.copyTo(File(dir, "$dbName-wal.$stamp.bak"), overwrite = true)

            prune(dir, dbName)
            target
        }.getOrNull()

    /** Keeps the newest [KEEP] day-stamped snapshots, dropping older pairs. */
    private fun prune(dir: File, dbName: String) {
        val stamps = dir.listFiles()
            ?.mapNotNull { stampOf(it.name) }
            ?.distinct()
            ?.sortedDescending()
            ?: return
        stamps.drop(KEEP).forEach { stamp ->
            File(dir, "$dbName.$stamp.bak").delete()
            File(dir, "$dbName-wal.$stamp.bak").delete()
        }
    }

    private fun stampOf(name: String): Long? =
        name.removeSuffix(".bak").substringAfterLast('.').toLongOrNull()

    /** Newest snapshot on disk, for diagnostics and manual recovery. */
    fun latest(context: Context, dbName: String = MonarchDatabase.NAME): File? {
        val dir = File(context.filesDir, DIR)
        return dir.listFiles()
            ?.filter { it.name.startsWith("$dbName.") }
            ?.maxByOrNull { stampOf(it.name) ?: 0L }
    }
}
