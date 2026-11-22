package com.ironvellum.app.ui.ai

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.concurrent.TimeUnit

private val snapshotName = Regex("""ironvellum-training-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\.json""")
private val partialName = Regex("""\.ironvellum-training-[0-9]+\.part""")
private val aiName = Regex("""ironvellum-ai-[0-9a-f]{64}\.md""")
private val aiPartialName = Regex("""\.ironvellum-ai-[0-9]+\.part""")
private val shareRetentionMs = TimeUnit.HOURS.toMillis(24)

/** Called on I/O. Only the completed snapshot is exposed to a recipient. */
@Synchronized
internal fun stageTrainingShare(directory: File, name: String, write: (OutputStream) -> Unit): File {
    require(snapshotName.matches(name))
    return stageShare(directory, name, ".ironvellum-training-", snapshotName, partialName, write)
}

@Synchronized
internal fun stageAiShare(directory: File, name: String, bytes: ByteArray): File {
    require(bytes.size in 1..AiTrainingPackage.MAX_BYTES && name == AiTrainingPackage.fileName(bytes))
    return stageShare(directory, name, ".ironvellum-ai-", aiName, aiPartialName) { it.write(bytes) }
}

private fun stageShare(
    directory: File, name: String, prefix: String, completedName: Regex, partialName: Regex,
    write: (OutputStream) -> Unit,
): File {
    Files.createDirectories(directory.toPath())
    val snapshot = File(directory, name)
    if (!snapshot.isFile) {
        val partial = Files.createTempFile(directory.toPath(), prefix, ".part")
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
            (completedName.matches(file.name) || partialName.matches(file.name))) {
            file.delete()
        }
    }
    return snapshot
}
