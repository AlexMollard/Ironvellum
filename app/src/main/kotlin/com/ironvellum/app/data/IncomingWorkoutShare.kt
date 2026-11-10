package com.ironvellum.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Receives file grants only; parsing and the import review still decide whether this is workout data. */
internal object IncomingWorkoutShare {
    private val mimeTypes = setOf(
        "text/csv", "text/comma-separated-values", "application/csv",
        "application/vnd.ms-excel", "text/plain", "application/octet-stream",
    )
    internal const val MAX_BYTES = 32 * 1024 * 1024

    /** Null is not a CSV share; blank routes a malformed share to the ordinary unreadable-file feedback. */
    fun csvUri(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.substringBefore(';')?.lowercase(Locale.ROOT) !in mimeTypes) return null
        val uri = runCatching {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
        }.getOrNull()
        return uri?.takeIf { it.scheme == "content" }?.toString().orEmpty()
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
