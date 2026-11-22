package com.ironvellum.app.ui.ai

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.concurrent.TimeUnit

private val snapshotName = Regex("""ironvellum-training-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\.json""")
private val partialName = Regex("""\.ironvellum-training-[0-9]+\.part""")
private val shareRetentionMs = TimeUnit.HOURS.toMillis(24)

/** Called on I/O. Only the completed snapshot is exposed to a recipient. */
@Synchronized
internal fun stageTrainingShare(directory: File, name: String, write: (OutputStream) -> Unit): File {
    require(snapshotName.matches(name))
    Files.createDirectories(directory.toPath())
    val snapshot = File(directory, name)
    if (!snapshot.isFile) {
        val partial = Files.createTempFile(directory.toPath(), ".ironvellum-training-", ".part")
        try {
            Files.newOutputStream(partial).use(write)
            Files.move(partial, snapshot.toPath(), ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(partial)
        }
    }
    val now = System.currentTimeMillis()
    if (!snapshot.setLastModified(now)) throw IOException("Could not refresh shared snapshot")
    directory.listFiles()?.forEach { file ->
        if (file != snapshot && file.isFile && file.lastModified() <= now - shareRetentionMs &&
            (snapshotName.matches(file.name) || partialName.matches(file.name))) {
            file.delete()
        }
    }
    return snapshot
}
