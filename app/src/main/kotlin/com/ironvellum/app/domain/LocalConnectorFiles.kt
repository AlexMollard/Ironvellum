package com.ironvellum.app.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import kotlinx.serialization.json.*

internal const val MAX_CONTEXT_BYTES = 8 * 1024 * 1024
internal const val MAX_PROPOSAL_BYTES = 64 * 1024
internal const val MAX_LOCAL_TIME = 253402300799999L

internal data class LocalProposal(
    val datasetId: String, val snapshotId: String, val contextSha256: String, val draftId: String,
    val sender: String, val createdAtMs: Long, val expiresAtMs: Long, val draft: WorkoutDraft, val sha256: String,
)

internal class UnsupportedLocalFormat : IllegalArgumentException()
internal object LocalConnectorFiles {
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val hash = Regex("[0-9a-f]{64}")
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    fun proposal(bytes: ByteArray): LocalProposal {
        require(bytes.size in 1..MAX_PROPOSAL_BYTES)
        val source = try { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() }
        catch (_: CharacterCodingException) { throw IllegalArgumentException("Invalid UTF-8") }
        val obj = LocalJsonReader(source).document() as? JsonObject ?: throw IllegalArgumentException()
        fun string(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException()
        fun time(key: String) = (obj[key] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toLongOrNull()
            ?.takeIf { it in 0..MAX_LOCAL_TIME } ?: throw IllegalArgumentException()
        fun id(key: String) = string(key).also { require(uuid.matches(it)) }
        if (string("type") != "ironvellum.workout-draft" || time("schemaVersion") != 1L) throw UnsupportedLocalFormat()
        require(obj.keys == setOf("type", "schemaVersion", "datasetId", "snapshotId", "contextSha256",
            "draftId", "sender", "createdAtMs", "expiresAtMs", "draft"))
        val sender = string("sender")
        require(sender.trim(' ') == sender && sender.none(Char::isISOControl) && sender.codePointCount(0, sender.length) in 1..80)
        val created = time("createdAtMs")
        val expires = time("expiresAtMs")
        require(expires - created in 1..30L * 86400000)
        val body = obj["draft"] as? JsonObject ?: throw IllegalArgumentException()
        return LocalProposal(id("datasetId"), id("snapshotId"), string("contextSha256").also { require(hash.matches(it)) },
            id("draftId"), sender, created, expires, WorkoutDraft.parse(body), sha256(bytes))
    }
}

/** Read one RFC 8259 value, checking member uniqueness before building each object. */
internal class LocalJsonReader(private val input: String) {
    private var pos = 0
    private fun space() { while (pos < input.length && input[pos] in " \t\r\n") pos++ }
    private fun take(c: Char): Boolean {
        space()
        if (input.getOrNull(pos) != c) return false
        pos++
        return true
    }

    fun document(): JsonElement {
        val result = value(0)
        space()
        require(pos == input.length)
        return result
    }

    /** Finds a decoded root property in a bounded prefix, even if the document ends after it. */
    fun firstRootMember(name: String): JsonElement? {
        if (!take('{') || take('}')) return null
        do {
            space()
            val key = string()
            require(take(':'))
            val element = value(1)
            if (key == name) return element
        } while (take(','))
        return null
    }

    private fun value(depth: Int): JsonElement {
        space()
        return when (input.getOrNull(pos)) {
            '{' -> {
                require(depth < 16)
                pos++
                val fields = mutableMapOf<String, JsonElement>()
                if (!take('}')) {
                    do {
                        space()
                        val key = string()
                        require(key !in fields && take(':'))
                        fields[key] = value(depth + 1)
                    } while (take(','))
                    require(take('}'))
                }
                JsonObject(fields)
            }
            '[' -> {
                require(depth < 16)
                pos++
                val items = mutableListOf<JsonElement>()
                if (!take(']')) {
                    do { items += value(depth + 1) } while (take(','))
                    require(take(']'))
                }
                JsonArray(items)
            }
            '"' -> JsonPrimitive(string())
            else -> {
                val start = pos
                while (pos < input.length && input[pos] !in ",]} \t\r\n") pos++
                require(pos - start in 1..128)
                val token = input.substring(start, pos)
                if (token !in listOf("null", "true", "false")) {
                    require(NUMBER.matches(token))
                    val decimal = token.toBigDecimal()
                    val double = decimal.toDouble()
                    require(double.isFinite() && (decimal.signum() == 0 || double != 0.0))
                }
                Json.parseToJsonElement(token)
            }
        }
    }

    private fun string(): String {
        require(input.getOrNull(pos++) == '"')
        val out = StringBuilder()
        while (pos < input.length) {
            val c = input[pos++]
            when {
                c == '"' -> {
                    val decoded = out.toString()
                    var i = 0
                    while (i < decoded.length) {
                        val cp = decoded.codePointAt(i)
                        require(cp !in 0xd800..0xdfff)
                        i += Character.charCount(cp)
                    }
                    return decoded
                }
                c == '\\' -> when (val esc = input.getOrNull(pos++)) {
                    '"', '/', '\\' -> out.append(esc)
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000c')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        require(pos + 4 <= input.length)
                        val hex = input.substring(pos, pos + 4)
                        require(hex.all { it in "0123456789abcdefABCDEF" })
                        out.append(hex.toInt(16).toChar())
                        pos += 4
                    }
                    else -> throw IllegalArgumentException()
                }
                else -> { require(c >= ' '); out.append(c) }
            }
        }
        throw IllegalArgumentException()
    }

    companion object {
        private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
    }
}
