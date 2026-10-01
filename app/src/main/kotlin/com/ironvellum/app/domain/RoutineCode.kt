package com.ironvellum.app.domain

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * A routine as one pasteable line: `IVR1:` + base64url(deflate(compact JSON)).
 *
 * Text, not a file, because the only transport a lifter has is a chat box.
 * Pure Kotlin/JDK so it runs in JVM unit tests, and [decode] never throws:
 * the input is whatever a stranger pasted, and a crash on a mangled code
 * would be the lifter's first impression of the feature.
 *
 * Only the plan travels: names, sets, reps, target loads and modifier tags.
 * No bodyweight, history or profile field exists in the format, so there is
 * nothing private to leak by sharing.
 */
object RoutineCode {
    const val PREFIX = "IVR1:"

    /** Format revision inside the payload; the prefix and this move together. */
    private const val FORMAT = 1

    /** Inflated JSON cap. Real routines are a few KB; the cap stops a tiny
     *  "zip bomb" code from ballooning memory during decode. */
    const val MAX_DECODED_BYTES = 64 * 1024
    const val MAX_WORKOUTS = 14
    const val MAX_ENTRIES = 20

    private const val MAX_TEXT_CHARS = 100_000
    private const val NAME_MAX = 80
    private const val NOTE_MAX = 400
    private const val MODIFIERS_MAX = 80
    private const val MAX_SETS = 50
    private const val MAX_REPS = 1_000
    private const val MAX_WEIGHT_KG = 1_000.0

    data class SharedEntry(
        val exerciseName: String,
        val sets: Int,
        val reps: Int,
        val targetWeightKg: Double?,
        val modifiers: String,
    )

    data class SharedWorkout(
        val name: String,
        val note: String,
        val scheduledDay: Int?,
        val entries: List<SharedEntry>,
    )

    /**
     * Why [workouts] cannot go out as a code, or null when they can. [encode]
     * writes any list, but [decode] refuses more than [MAX_WORKOUTS] rites or
     * [MAX_ENTRIES] exercises in one, so the sender hears it before the
     * receiver gets a code the app will not import.
     */
    fun shareRefusal(workouts: List<SharedWorkout>): String? {
        if (workouts.size > MAX_WORKOUTS) {
            return "A shared cycle holds at most $MAX_WORKOUTS rites and yours has ${workouts.size}. Remove some rites, then share again."
        }
        val long = workouts.firstOrNull { it.entries.size > MAX_ENTRIES } ?: return null
        return "A shared rite holds at most $MAX_ENTRIES exercises and \"${long.name}\" has ${long.entries.size}. Trim it, then share again."
    }

    fun encode(workouts: List<SharedWorkout>): String {
        val json = StringBuilder()
        json.append("{\"v\":").append(FORMAT).append(",\"w\":[")
        workouts.forEachIndexed { w, workout ->
            if (w > 0) json.append(',')
            json.append("{\"n\":").appendQuoted(workout.name)
            json.append(",\"t\":").appendQuoted(workout.note)
            json.append(",\"d\":").append(workout.scheduledDay?.toString() ?: "null")
            json.append(",\"e\":[")
            workout.entries.forEachIndexed { e, entry ->
                if (e > 0) json.append(',')
                json.append('[').appendQuoted(entry.exerciseName)
                json.append(',').append(entry.sets)
                json.append(',').append(entry.reps)
                json.append(',').append(
                    entry.targetWeightKg?.takeIf { it.isFinite() }?.toString() ?: "null",
                )
                json.append(',').appendQuoted(entry.modifiers).append(']')
            }
            json.append("]}")
        }
        json.append("]}")

        val raw = json.toString().toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val packed = try {
            deflater.setInput(raw)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            out.toByteArray()
        } finally {
            deflater.end()
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
    }

    fun decode(text: String): Result<List<SharedWorkout>> {
        if (text.length > MAX_TEXT_CHARS) return fail("That text is too long to be a cycle code.")
        val start = text.indexOf(PREFIX)
        if (start < 0) {
            // "IVR2:" from a newer app is a different failure from "not a code".
            val other = Regex("IVR\\d+:").find(text)
            return if (other != null) {
                fail("This code comes from a different version of Ironvellum. Update the app and try again.")
            } else {
                fail("No cycle code found. Paste the whole message — it starts with IVR1:")
            }
        }
        // Chat apps put text on both sides; the code is the run of URL-safe
        // base64 characters right after the prefix.
        var end = start + PREFIX.length
        while (end < text.length && isCodeChar(text[end])) end++
        val body = text.substring(start + PREFIX.length, end)
        if (body.isEmpty()) return fail("The cycle code is empty.")

        val packed = try {
            Base64.getUrlDecoder().decode(body)
        } catch (_: IllegalArgumentException) {
            return fail(CORRUPT)
        }
        val json = when (val result = inflate(packed)) {
            is Inflated.Ok -> result.text
            Inflated.TooBig -> return fail("This cycle is too large to import.")
            Inflated.Corrupt -> return fail(CORRUPT)
        }
        return try {
            Result.success(read(json))
        } catch (e: CodeException) {
            fail(e.message ?: CORRUPT)
        } catch (_: RuntimeException) {
            fail(CORRUPT)
        }
    }

    private fun isCodeChar(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'

    private const val CORRUPT = "The cycle code is damaged or cut short. Copy it again and paste the whole thing."

    private fun fail(message: String): Result<List<SharedWorkout>> =
        Result.failure(IllegalArgumentException(message))

    private sealed interface Inflated {
        class Ok(val text: String) : Inflated
        object TooBig : Inflated
        object Corrupt : Inflated
    }

    private fun inflate(packed: ByteArray): Inflated {
        val inflater = Inflater()
        try {
            inflater.setInput(packed)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return Inflated.Corrupt
                out.write(buffer, 0, n)
                if (out.size() > MAX_DECODED_BYTES) return Inflated.TooBig
            }
            return Inflated.Ok(out.toString(Charsets.UTF_8.name()))
        } catch (_: DataFormatException) {
            return Inflated.Corrupt
        } finally {
            inflater.end()
        }
    }

    // ------------------------------------------------------------ validation

    private class CodeException(message: String) : RuntimeException(message)

    private fun bad(what: String): Nothing = throw CodeException("The cycle code has $what. Ask for a fresh one.")

    private fun read(json: String): List<SharedWorkout> {
        val root = Parser(json).parseDocument() as? Map<*, *> ?: bad("an unreadable layout")
        val version = (root["v"] as? Double)?.toInt()
        if (version != FORMAT) {
            throw CodeException("This code comes from a different version of Ironvellum. Update the app and try again.")
        }
        val workouts = root["w"] as? List<*> ?: bad("no rites")
        if (workouts.isEmpty()) bad("no rites")
        if (workouts.size > MAX_WORKOUTS) {
            throw CodeException("This cycle has more than $MAX_WORKOUTS rites, which is more than Ironvellum imports.")
        }
        return workouts.map { raw ->
            val o = raw as? Map<*, *> ?: bad("an unreadable rite")
            val name = (o["n"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) bad("a rite without a name")
            val day = when (val d = o["d"]) {
                null -> null
                is Double -> d.toInt().takeIf { it.toDouble() == d && it in 1..7 } ?: bad("an invalid day")
                else -> bad("an invalid day")
            }
            val entries = o["e"] as? List<*> ?: bad("a rite without exercises")
            if (entries.size > MAX_ENTRIES) {
                throw CodeException("A rite in this cycle has more than $MAX_ENTRIES exercises, which is more than Ironvellum imports.")
            }
            SharedWorkout(
                name = name.take(NAME_MAX),
                note = (o["t"] as? String).orEmpty().take(NOTE_MAX),
                scheduledDay = day,
                entries = entries.map { entryRaw -> readEntry(entryRaw) },
            )
        }
    }

    private fun readEntry(raw: Any?): SharedEntry {
        val a = raw as? List<*> ?: bad("an unreadable exercise")
        if (a.size != 5) bad("an unreadable exercise")
        val name = (a[0] as? String)?.trim().orEmpty()
        if (name.isEmpty()) bad("an exercise without a name")
        val sets = wholeIn(a[1], 1..MAX_SETS)
        val reps = wholeIn(a[2], 1..MAX_REPS)
        val weight = when (val w = a[3]) {
            null -> null
            is Double -> w.takeIf { it.isFinite() && it in 0.0..MAX_WEIGHT_KG } ?: bad("an invalid load")
            else -> bad("an invalid load")
        }
        return SharedEntry(
            exerciseName = name.take(NAME_MAX),
            sets = sets,
            reps = reps,
            targetWeightKg = weight,
            modifiers = (a[4] as? String).orEmpty().take(MODIFIERS_MAX),
        )
    }

    private fun wholeIn(value: Any?, range: IntRange): Int {
        val d = value as? Double ?: bad("an invalid set or rep count")
        val i = d.toInt()
        if (i.toDouble() != d || i !in range) bad("an invalid set or rep count")
        return i
    }

    // ----------------------------------------------------------------- JSON

    private fun StringBuilder.appendQuoted(s: String): StringBuilder {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c.code < 0x20 -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
        }
        return append('"')
    }

    /** Recursive-descent reader; numbers are always Double, nesting is capped
     *  so a hostile "[[[[..." cannot overflow the stack. */
    private class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(): Any? {
            val v = parseValue(0)
            skipWs()
            if (i != s.length) bad("trailing data")
            return v
        }

        private fun parseValue(depth: Int): Any? {
            if (depth > 6) bad("nesting that is too deep")
            skipWs()
            if (i >= s.length) bad("cut-off data")
            return when (s[i]) {
                '{' -> parseObj(depth)
                '[' -> parseArr(depth)
                '"' -> parseString()
                'n' -> literal("null", null)
                't' -> literal("true", true)
                'f' -> literal("false", false)
                else -> parseNumber()
            }
        }

        private fun parseObj(depth: Int): Map<String, Any?> {
            i++
            val map = HashMap<String, Any?>()
            skipWs()
            if (peek('}')) return map
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                map[key] = parseValue(depth + 1)
                skipWs()
                if (peek('}')) return map
                expect(',')
            }
        }

        private fun parseArr(depth: Int): List<Any?> {
            i++
            val list = ArrayList<Any?>()
            skipWs()
            if (peek(']')) return list
            while (true) {
                list.add(parseValue(depth + 1))
                skipWs()
                if (peek(']')) return list
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) bad("cut-off text")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) bad("cut-off text")
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'u' -> {
                                if (i + 4 > s.length) bad("cut-off text")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> bad("a bad escape")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-eE.")) i++
            if (start == i) bad("an unexpected character")
            return s.substring(start, i).toDoubleOrNull() ?: bad("a bad number")
        }

        private fun literal(text: String, value: Any?): Any? {
            if (!s.startsWith(text, i)) bad("an unexpected word")
            i += text.length
            return value
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(c: Char): Boolean {
            if (i < s.length && s[i] == c) {
                i++
                return true
            }
            return false
        }

        private fun expect(c: Char) {
            if (i >= s.length || s[i] != c) bad("a misplaced character")
            i++
        }
    }
}
