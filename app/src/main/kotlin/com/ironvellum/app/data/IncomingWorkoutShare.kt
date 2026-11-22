package com.ironvellum.app.data

import android.content.Context
import android.net.Uri
import com.ironvellum.app.domain.CsvWorkoutReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The existing CSV import reads the whole file only after its header identifies a supported export. */
internal object IncomingWorkoutShare {
    internal const val MAX_BYTES = 32 * 1024 * 1024

    fun isCsvHeader(text: String): Boolean {
        val firstLine = text.lineSequence().firstOrNull() ?: return false
        val columns = runCatching { CsvWorkoutReader.parseCsv(firstLine) }.getOrNull()?.firstOrNull() ?: return false
        return CsvWorkoutReader.sourceForHeader(columns.map(String::trim)) != null
    }

    suspend fun readSharedText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        if (!IncomingAiWorkout.hasReadGrant(context, uri)) throw IOException()
        readText(context, uri)
    }

    suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        if (uri.scheme != "content") throw IOException()
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException()
        stream.use { input ->
            val output = ByteArrayOutputStream(8192)
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_BYTES) throw IOException()
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }
}
