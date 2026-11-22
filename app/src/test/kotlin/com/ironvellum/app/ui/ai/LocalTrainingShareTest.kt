package com.ironvellum.app.ui.ai

import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalTrainingShareTest {
    @get:Rule val folder = TemporaryFolder()
    private val directory get() = folder.root
    private fun snapshotName() = "ironvellum-training-${UUID.randomUUID()}.json"

    @Test fun repeatedShareReusesPublishedBytesWhileTheFirstReaderIsOpen() {
        val name = snapshotName()
        val original = "complete training export".encodeToByteArray()
        val file = stageTrainingShare(directory, name) { it.write(original) }
        file.inputStream().use { reader ->
            assertEquals(original.first().toInt(), reader.read())
            assertEquals(file, stageTrainingShare(directory, name) { fail("Repeat share reopened the snapshot for writing") })
            assertArrayEquals(original.drop(1).toByteArray(), reader.readBytes())
        }
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun interruptedWriteNeverPublishesPartialBytesAndCanBeRetried() {
        val name = snapshotName()
        val finalFile = File(directory, name)
        assertThrows(IOException::class.java) {
            stageTrainingShare(directory, name) {
                it.write(1)
                it.flush()
                assertFalse("Partial snapshot became visible", finalFile.exists())
                throw IOException("interrupted")
            }
        }
        assertTrue(directory.listFiles()!!.isEmpty())
        assertEquals("complete", stageTrainingShare(directory, name) { it.write("complete".encodeToByteArray()) }.readText())
    }

    @Test fun reuseRefreshesCurrentSnapshotAndPrunesOnlyExpiredConnectorFiles() {
        val current = snapshotName()
        val keep = stageTrainingShare(directory, current) { it.write(1) }
        val stale = stageTrainingShare(directory, snapshotName()) { it.write(2) }
        val recent = stageTrainingShare(directory, snapshotName()) { it.write(3) }
        val other = File(directory, "ironvellum-training-personal.json").apply { writeText("keep") }
        val archive = File(directory, "ironvellum-backup.json").apply { writeText("keep") }
        val abandoned = File(directory, ".ironvellum-training-12345.part").apply { writeText("incomplete") }
        val now = System.currentTimeMillis()
        val expired = now - TimeUnit.HOURS.toMillis(25)
        listOf(keep, stale, other, archive, abandoned).forEach { assertTrue(it.setLastModified(expired)) }
        assertTrue(recent.setLastModified(now - TimeUnit.HOURS.toMillis(23)))
        stageTrainingShare(directory, current) { fail("Current snapshot must remain available") }
        assertTrue(keep.lastModified() >= now - 1000)
        assertFalse(stale.exists())
        assertFalse(abandoned.exists())
        assertArrayEquals(byteArrayOf(3), recent.readBytes())
        assertEquals("keep", other.readText())
        assertEquals("keep", archive.readText())
    }
}
