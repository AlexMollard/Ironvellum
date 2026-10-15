package com.ironvellum.app.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The names the automatic backup writes, and which of a folder's files it may
 * delete. The folder is the lifter's own, so only a name this feature could
 * have written is ever touched; anything else in it is left alone.
 */
object BackupFiles {
    /** How many backups the folder keeps. */
    const val KEEP = 5

    private const val PREFIX = "ironvellum_backup_"
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
    private val OURS = Regex("""ironvellum_backup_\d{4}-\d{2}-\d{2}_\d{6}\.json""")

    /** Sorts by name in date order, so the newest is the greatest. */
    fun nameFor(at: LocalDateTime): String = PREFIX + STAMP.format(at) + ".json"

    fun isOurs(name: String): Boolean = OURS.matches(name)

    /** The names to delete so only the newest [keep] of ours remain; foreign names are never returned. */
    fun toDelete(names: Collection<String>, keep: Int = KEEP): List<String> =
        names.filter(::isOurs).sortedDescending().drop(keep)
}
