package com.ironvellum.app.data.localconnector

import com.ironvellum.app.domain.MAX_CONTEXT_BYTES
import com.ironvellum.app.domain.PREPARATION_NOTE_MAX

/**
 * JsonReader can read an entire name, string or number inside peek(), before the projection gets
 * to inspect it. Inspect each source chunk first. Charge the canonical public UTF-8 bytes of a
 * string as it arrives and enforce the domain UTF-16 length of each private note. Recognizing
 * the four decoded letters of the note key needs no token buffer, even for escaped names.
 * JsonReader and the typed decoder still validate the full structure and final document.
 */
internal class PreparationTokenBudget {
    private var inString = false
    private var escape = false
    private var hexRemaining = 0
    private var hexValue = 0
    private var tokenBytes = 0
    private var notePrefix = 0
    private var justReadNote = false
    private var noteValue = false
    private var bareBytes = 0
    private var noteUnits = 0
    private var pendingHighSurrogate = false

    fun inspect(chunk: ByteArray) {
        for (byte in chunk) {
            val ch = (byte.toInt() and 255).toChar()
            if (inString) stringByte(ch) else delimiterOrLiteral(ch)
        }
    }

    private fun delimiterOrLiteral(ch: Char) {
        when (ch) {
            ' ', '\t', '\n', '\r' -> { bareBytes = 0; return }
            '"' -> {
                inString = true
                tokenBytes = 0
                noteUnits = 0
                notePrefix = 0
            }
            ':' -> noteValue = justReadNote
            ',', '{', '}', '[', ']' -> noteValue = false
            else -> {
                require(++bareBytes <= 128)
                justReadNote = false
                noteValue = false
                return
            }
        }
        bareBytes = 0
        justReadNote = false
    }

    private fun stringByte(ch: Char) {
        when {
            hexRemaining != 0 -> {
                hexValue = hexValue * 16 + (ch.digitToIntOrNull(16) ?: throw IllegalArgumentException("Invalid JSON escape"))
                if (--hexRemaining == 0) {
                    val decoded = hexValue.toChar()
                    decodedUnit(decoded, escapedUtf8Bytes(decoded))
                }
            }
            escape -> {
                escape = false
                when (ch) {
                    'u' -> { hexRemaining = 4; hexValue = 0 }
                    '\\', '"' -> decodedUnit(ch, 2)
                    '/' -> decodedUnit(ch, 1)
                    'b', 'f', 'n', 'r', 't' -> decodedUnit('\u0000', 2)
                    else -> throw IllegalArgumentException("Invalid JSON escape")
                }
            }
            ch == '\\' -> escape = true
            ch == '"' -> {
                require(!pendingHighSurrogate)
                inString = false
                justReadNote = notePrefix == NOTE.length
                noteValue = false
            }
            else -> {
                require(ch.code >= 0x20)
                // The reporting UTF-8 decoder validates the sequence. Count its UTF-16 units on the lead byte.
                val units = when (ch.code) {
                    in 0x80..0xbf -> 0
                    in 0xf0..0xf4 -> 2
                    else -> 1
                }
                decodedUnit(ch, 1, units)
            }
        }
    }

    private fun decodedUnit(ch: Char, bytes: Int, units: Int = 1) {
        require(ch.isLowSurrogate() == pendingHighSurrogate)
        pendingHighSurrogate = ch.isHighSurrogate()
        notePrefix = if (notePrefix in NOTE.indices && NOTE[notePrefix] == ch) notePrefix + 1 else -1
        if (noteValue) {
            noteUnits += units
            require(noteUnits <= PREPARATION_NOTE_MAX)
        } else {
            tokenBytes += bytes
            if (tokenBytes > MAX_CONTEXT_BYTES) throw LocalExportException(LocalFailure.FILE_LIMIT)
        }
    }

    private fun escapedUtf8Bytes(ch: Char): Int = when {
        ch == '"' || ch == '\\' || ch in "\b\u000c\n\r\t" -> 2
        ch.code < 0x20 -> 6
        ch.code < 0x80 -> 1
        ch.code < 0x800 || ch.isSurrogate() -> 2 // two surrogate escapes encode one four-byte scalar
        else -> 3
    }

    private companion object { const val NOTE = "note" }
}
