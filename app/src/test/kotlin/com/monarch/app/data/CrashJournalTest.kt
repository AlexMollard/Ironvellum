package com.monarch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class CrashJournalTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun dir() = tmp.newFolder("crash")

    private fun write(dir: File, index: Int) {
        CrashJournal.record(dir, RuntimeException("crash $index"), "main", Instant.ofEpochMilli(1_000L + index))
    }

    // ---- record formatting ----

    @Test
    fun `format includes every header field and the stack trace`() {
        val text = CrashJournal.format(
            timestamp = "2026-09-15T10:00:00Z",
            versionLine = "1.0 (21)",
            androidLine = "16 (API 36)",
            deviceLine = "samsung SM-S938B",
            threadName = "main",
            stackTrace = "java.lang.RuntimeException: boom\n    at com.monarch.app.MainActivity.onCreate",
        )
        val lines = text.lines()
        assertEquals("Monarch crash — 2026-09-15T10:00:00Z", lines[0])
        assertEquals("app: 1.0 (21)", lines[1])
        assertEquals("android: 16 (API 36)", lines[2])
        assertEquals("device: samsung SM-S938B", lines[3])
        assertEquals("thread: main", lines[4])
        assertTrue(text.contains("java.lang.RuntimeException: boom"))
        assertTrue(text.contains("    at com.monarch.app.MainActivity.onCreate"))
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun `record writes the full chain of causes`() {
        val journalDir = dir()
        val throwable = RuntimeException("wrapper").initCause(IllegalStateException("root"))
        CrashJournal.record(journalDir, throwable, "worker-1", Instant.ofEpochMilli(1_000))

        val files = journalDir.listFiles()!!.sortedBy { it.name }
        assertEquals(1, files.size)
        val text = files.single().readText()
        assertTrue(text.contains("java.lang.RuntimeException: wrapper"))
        assertTrue(text.contains("Caused by: java.lang.IllegalStateException: root"))
        assertTrue(text.contains("thread: worker-1"))
        assertEquals("Monarch crash — 1970-01-01T00:00:01Z", text.lines().first())
    }

    // ---- retention cap ----

    @Test
    fun `retention keeps only the newest records`() {
        val journalDir = dir()
        repeat(25) { write(journalDir, it) }
        val remaining = journalDir.listFiles()!!.sortedBy { it.name }
        assertEquals(20, remaining.size)
        // oldest five ("crash 0..4") must be gone; newest survives
        assertFalse(remaining.first().readText().contains("crash 4"))
        assertTrue(remaining.last().readText().contains("crash 24"))
    }

    @Test
    fun `recent returns newest-first up to the limit`() {
        val journalDir = dir()
        repeat(5) { write(journalDir, it) }
        val records = CrashJournal.recentIn(journalDir, 3)
        assertEquals(3, records.size)
        assertTrue(records[0].contains("crash 4"))
        assertTrue(records[2].contains("crash 2"))
    }

    @Test
    fun `clear removes every record`() {
        val journalDir = dir()
        write(journalDir, 0)
        assertTrue(journalDir.listFiles()!!.isNotEmpty())
        CrashJournal.clearIn(journalDir)
        assertTrue(journalDir.listFiles()!!.isEmpty())
    }

    // ---- robustness ----

    @Test
    fun `recording into an uncreatable directory never throws`() {
        // parent exists as a FILE, so mkdirs cannot succeed
        val parent = tmp.newFile("blocker")
        val journalDir = File(parent, "crash")
        CrashJournal.record(journalDir, RuntimeException("x"), "main", Instant.ofEpochMilli(1))
        assertFalse(journalDir.exists())
    }

    @Test
    fun `recent on a missing directory is empty`() {
        assertEquals(emptyList<String>(), CrashJournal.recentIn(File(tmp.root, "nope"), 10))
    }

    @Test
    fun `latest timestamp of an empty journal is null`() {
        assertNull(CrashJournal.latestIn(dir()))
    }
}
