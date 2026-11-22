package com.ironvellum.app.data

import android.content.Context
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import androidx.core.content.IntentCompat
import com.ironvellum.app.domain.LocalJsonReader
import com.ironvellum.app.domain.MAX_PROPOSAL_BYTES
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Classifies only the supplied content grant (or explicitly identifiable shared workout code). */
internal object IncomingAiWorkout {
    sealed interface Source {
        data class Document(val uri: Uri) : Source
        class Code(val payload: Payload) : Source
        data object Unreadable : Source
    }

    sealed interface Payload {
        class Portable(val bytes: ByteArray) : Payload
        class SourceBound(val bytes: ByteArray) : Payload
        data class Csv(val uri: Uri) : Payload
        data object Unsupported : Payload
        data object Unreadable : Payload
        data object InvalidCode : Payload
        data object TooLarge : Payload
    }

    private val portableType = JsonPrimitive("ironvellum.workout")
    private val sourceBoundType = JsonPrimitive("ironvellum.workout-draft")

    fun isIncomingAction(intent: Intent?) = intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_VIEW

    fun source(intent: Intent): Source? {
        if (!isIncomingAction(intent)) return null
        val uri = try {
            if (intent.action == Intent.ACTION_VIEW) intent.data else {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
            }
        } catch (_: RuntimeException) { return Source.Unreadable }
        if (uri != null) return Source.Document(uri)
        if (intent.action == Intent.ACTION_SEND) {
            val text = try {
                (intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                    ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text)?.toString()
            } catch (_: RuntimeException) { return Source.Unreadable }
            if (text != null) {
                val payload = classifyCode(text, requireMarkerForRejection = true)
                return when (payload) {
                    is Payload.Portable, is Payload.SourceBound, Payload.InvalidCode, Payload.TooLarge -> Source.Code(payload)
                    else -> null
                }
            }
            if (intent.type == "text/plain") return null
        }
        return Source.Unreadable
    }

    suspend fun read(context: Context, intent: Intent): Payload? =
        source(intent)?.let { read(context, it) }

    suspend fun read(context: Context, source: Source): Payload = when (source) {
        is Source.Document -> withContext(Dispatchers.IO) {
            if (!hasReadGrant(context, source.uri)) Payload.Unreadable
            else readFile(context.contentResolver, source.uri, explicitPicker = false)
        }
        is Source.Code -> source.payload
        Source.Unreadable -> Payload.Unreadable
    }

    // This overload checks explicit URI grants independently of ambient provider access.
    fun hasReadGrant(context: Context, uri: Uri): Boolean = uri.scheme == ContentResolver.SCHEME_CONTENT &&
        context.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED

    fun paste(text: String): Payload = classifyCode(text)

    private fun classifyCode(text: String, requireMarkerForRejection: Boolean = false): Payload {
        // CharsetEncoder reports malformed UTF-16 and stops at the byte budget without splitting a scalar.
        val buffer = ByteBuffer.allocate(minOf(text.length.toLong() * 3, MAX_PROPOSAL_BYTES.toLong()).toInt())
        val status = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(text), buffer, true)
        val bytes = buffer.array().copyOf(buffer.position())
        if (!status.isUnderflow) {
            val prefix = bytes.decodeToString().trimStart()
            if (requireMarkerForRejection && markedType(prefix) !in setOf(portableType, sourceBoundType)) {
                return Payload.Unsupported
            }
            return if (status.isOverflow) Payload.TooLarge else Payload.InvalidCode
        }
        return classify(bytes, text.trimStart())
    }

    suspend fun readPickedFile(resolver: ContentResolver, uri: Uri): Payload =
        readFile(resolver, uri, explicitPicker = true)

    private suspend fun readFile(resolver: ContentResolver, uri: Uri, explicitPicker: Boolean): Payload =
        withContext(Dispatchers.IO) {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return@withContext Payload.Unreadable
            try {
                val stream = resolver.openInputStream(uri) ?: return@withContext Payload.Unreadable
                stream.use { input ->
                    val bytes = ByteArray(MAX_PROPOSAL_BYTES)
                    var size = 0
                    while (size < bytes.size) {
                        val count = input.read(bytes, size, bytes.size - size)
                        if (count < 0) break
                        size += count
                    }
                    val prefix = bytes.decodeToString(endIndex = size).trimStart().removePrefix("\uFEFF").trimStart()
                    if (IncomingWorkoutShare.isCsvHeader(prefix)) return@withContext Payload.Csv(uri)
                    if (size == bytes.size && input.read() != -1) return@withContext Payload.TooLarge
                    val body = if (size == bytes.size) bytes else bytes.copyOf(size)
                    val payload = classify(body, prefix)
                    if (explicitPicker && payload == Payload.Unsupported && !prefix.startsWith("```"))
                        Payload.SourceBound(body) else payload
                }
            } catch (e: CancellationException) { throw e }
            catch (_: IOException) { Payload.Unreadable }
            catch (_: RuntimeException) { Payload.Unreadable }
        }

    private fun classify(bytes: ByteArray, prefix: String): Payload {
        if (!looksLikeCode(prefix)) return Payload.Unsupported
        val type = try { (LocalJsonReader(jsonBody(prefix)).document() as? JsonObject)?.get("type") }
        catch (_: IllegalArgumentException) {
            // Only a decoded root marker can send damaged input to the full parser for retry feedback.
            markedType(prefix)
        }
        return when (type) {
            portableType -> Payload.Portable(bytes)
            sourceBoundType -> Payload.SourceBound(bytes)
            else -> Payload.Unsupported
        }
    }

    private fun markedType(prefix: String): JsonElement? =
        if (looksLikeCode(prefix)) {
            try { LocalJsonReader(jsonBody(prefix)).firstRootMember("type") }
            catch (_: IllegalArgumentException) { null }
        } else null

    private fun jsonBody(prefix: String): String =
        if (prefix.startsWith("```json")) prefix.substringAfter('\n').trimEnd().removeSuffix("```") else prefix

    private fun looksLikeCode(prefix: String) = prefix.startsWith('{') || prefix.startsWith("```json")
}
