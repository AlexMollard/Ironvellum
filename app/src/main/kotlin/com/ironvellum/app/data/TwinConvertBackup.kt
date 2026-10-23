package com.ironvellum.app.data

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The safety copy [Repository.convertTwins] asks for before it rewrites history: the same archive "Export archive"
 * shares ([Repository.exportJson]), kept in app storage. Written whole or not at all: a failed or empty write
 * throws, and the convert does not start. The newest [KEEP] copies stay.
 */
object TwinConvertBackup {
    const val KEEP = 3
    private const val DIR = "backups"
    private const val PREFIX = "ironvellum_before_twin_convert_"

    suspend fun write(context: Context, json: String, at: LocalDateTime = LocalDateTime.now()): File = withContext(Dispatchers.IO) {
        require(json.isNotBlank()) { "the archive is empty" }
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val name = PREFIX + at.toString().replace(':', '-') + ".json"
        val file = File(dir, name)
        file.writeBytes(json.toByteArray(Charsets.UTF_8))
        check(file.length() > 0) { "the backup could not be written" }
        dir.listFiles { f -> f.name.startsWith(PREFIX) }?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        file
    }
}
