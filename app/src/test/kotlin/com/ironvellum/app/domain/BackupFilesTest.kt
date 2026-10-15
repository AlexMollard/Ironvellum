package com.ironvellum.app.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFilesTest {

    private fun name(day: Int, hour: Int = 4) = BackupFiles.nameFor(LocalDateTime.of(2026, 10, day, hour, 5, 6))

    @Test
    fun `a name carries a sortable date and is recognised as ours`() {
        assertEquals("ironvellum_backup_2026-10-09_040506.json", name(9))
        assertTrue(BackupFiles.isOurs(name(9)))
        // lexical order is date order, across a month and a day-part boundary
        val dates = listOf(
            BackupFiles.nameFor(LocalDateTime.of(2026, 9, 30, 23, 59, 59)),
            name(1, 0),
            name(1, 13),
            name(10),
        )
        assertEquals(dates, dates.shuffled().sorted())
    }

    @Test
    fun `keeps the newest five and deletes the older ones`() {
        val names = (1..8).map { name(it) }.shuffled()
        assertEquals(listOf(name(3), name(2), name(1)), BackupFiles.toDelete(names))
    }

    @Test
    fun `five or fewer backups delete nothing`() {
        assertEquals(emptyList<String>(), BackupFiles.toDelete((1..5).map { name(it) }))
        assertEquals(emptyList<String>(), BackupFiles.toDelete(emptyList()))
    }

    @Test
    fun `files this feature did not write are never deleted`() {
        val foreign = listOf(
            "ironvellum_export.json",
            "ironvellum_backup_2026-10-01_040506.json.bak",
            "ironvellum_backup_2026-10-01_040506 (1).json",
            "ironvellum_backup_old.json",
            "photo.jpg",
            "ironvellum_backup_2026-10-01.json",
        )
        val ours = (1..7).map { name(it) }
        val doomed = BackupFiles.toDelete(foreign + ours)
        assertEquals(listOf(name(2), name(1)), doomed)
        assertTrue(doomed.none { it in foreign })
        foreign.forEach { assertFalse(it, BackupFiles.isOurs(it)) }
    }

    @Test
    fun `foreign files do not count against the five`() {
        val names = (1..5).map { name(it) } + (1..10).map { "notes_$it.txt" }
        assertEquals(emptyList<String>(), BackupFiles.toDelete(names))
    }
}
