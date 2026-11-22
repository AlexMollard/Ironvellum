package com.ironvellum.app.data.localconnector

import android.util.JsonReader
import android.util.JsonToken
import com.ironvellum.app.domain.MAX_CONTEXT_BYTES
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import kotlinx.serialization.json.Json

/** A preparation's private step notes can exceed both a CursorWindow and the entire public file.
 * Fetch large storage values in UTF-8 chunks and discard notes before counting the public bytes.
 * The normal small-document path and this path share the exact same final typed decoder. */
internal fun readPublicPreparation(inlineBytes: ByteArray? = null, chunk: (Int) -> ByteArray): String {
    val stringBudget = PreparationTokenBudget()
    if (inlineBytes != null) {
        stringBudget.inspect(inlineBytes)
        return try {
            inlineBytes.decodeToString(throwOnInvalidSequence = true)
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw LocalExportException(LocalFailure.INVALID_HISTORY, e)
        }
    }
    val stream = object : InputStream() {
        private var buffer = byteArrayOf()
        private var offset = 0
        private var next = 1 // SQLite substr is one-based.
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (offset == buffer.size) {
                buffer = chunk(next)
                stringBudget.inspect(buffer)
                offset = 0
                if (buffer.isEmpty()) return -1
                next = Math.addExact(next, buffer.size)
            }
            val n = minOf(len, buffer.size - offset)
            buffer.copyInto(b,off,offset,offset+n)
            offset += n
            return n
        }
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one,0,1) < 0) -1 else one[0].toInt() and 255
        }
    }
    val output = StringBuilder()
    var bytes = 0
    fun emit(token: String) {
        bytes += token.encodeToByteArray().size
        if (bytes > MAX_CONTEXT_BYTES) throw LocalExportException(LocalFailure.FILE_LIMIT)
        output.append(token)
    }
    try {
        JsonReader(InputStreamReader(stream, Charsets.UTF_8.newDecoder())).use { reader ->
            fun value(depth: Int) {
                when (reader.peek()) {
                    JsonToken.BEGIN_ARRAY -> {
                        require(depth < 16)
                        reader.beginArray()
                        emit("[")
                        var first = true
                        while (reader.hasNext()) {
                            if (!first) emit(",")
                            first = false
                            value(depth + 1)
                        }
                        reader.endArray()
                        emit("]")
                    }
                    JsonToken.BEGIN_OBJECT -> {
                        require(depth < 16)
                        reader.beginObject()
                        emit("{")
                        val seen = mutableSetOf<String>()
                        var first = true
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            require(seen.add(key))
                            if (key == "note") {
                                require(reader.peek() == JsonToken.STRING)
                                reader.skipValue()
                            } else {
                                if (!first) emit(",")
                                first = false
                                emit(Json.encodeToString(key))
                                emit(":")
                                value(depth + 1)
                            }
                        }
                        reader.endObject()
                        emit("}")
                    }
                    JsonToken.STRING -> emit(Json.encodeToString(reader.nextString()))
                    JsonToken.NUMBER -> emit(reader.nextString())
                    JsonToken.BOOLEAN -> emit(reader.nextBoolean().toString())
                    JsonToken.NULL -> { reader.nextNull(); emit("null") }
                    else -> throw IllegalArgumentException("Invalid stored preparation")
                }
            }
            value(0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
        }
    } catch (e: IOException) {
        throw LocalExportException(LocalFailure.INVALID_HISTORY, e)
    }
    return output.toString()
}
