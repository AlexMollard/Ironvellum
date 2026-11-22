package com.ironvellum.app.ui.ai

import android.content.ContentResolver
import android.net.Uri
import com.ironvellum.app.domain.MAX_CONTEXT_BYTES
import com.ironvellum.app.domain.MAX_PROPOSAL_BYTES
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal const val TRAINING_FILE_MIME = "application/json"

internal class DraftFileTooLarge : IOException()

internal suspend fun readDraftStream(input: InputStream): ByteArray {
    // Providers can omit the length or report a stale one. Read at most one byte past the cap.
    val buffer = ByteArray(MAX_PROPOSAL_BYTES + 1)
    var size = 0
    while (size < buffer.size) {
        coroutineContext.ensureActive()
        val read = input.read(buffer, size, buffer.size - size)
        if (read < 0) return buffer.copyOf(size)
        if (read == 0) {
            val next = input.read()
            if (next < 0) return buffer.copyOf(size)
            buffer[size++] = next.toByte()
        } else {
            size += read
        }
    }
    throw DraftFileTooLarge()
}

internal suspend fun readDraftUri(resolver: ContentResolver, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw IOException()
    resolver.openInputStream(uri)?.use { readDraftStream(it) } ?: throw IOException()
}

internal suspend fun writeTrainingUri(resolver: ContentResolver, uri: Uri, bytes: ByteArray) {
    withContext(Dispatchers.IO) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || bytes.size !in 1..MAX_CONTEXT_BYTES) throw IOException()
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw IOException()
    }
}
