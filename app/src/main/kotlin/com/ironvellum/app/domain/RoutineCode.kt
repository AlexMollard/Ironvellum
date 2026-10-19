package com.ironvellum.app.domain

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
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
            return Words.text(UiText.themed(R.string.routinecode_share_too_many_rites, R.string.routinecode_share_too_many_rites__t, MAX_WORKOUTS, workouts.size))
        }
        val long = workouts.firstOrNull { it.entries.size > MAX_ENTRIES } ?: return null
        return Words.text(
            UiText.themed(R.string.routinecode_share_too_many_exercises, R.string.routinecode_share_too_many_exercises__t, MAX_ENTRIES, long.name, long.entries.size),
        )
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
        if (text.length > MAX_TEXT_CHARS) return fail(Msg.TOO_LONG.text)
        val start = text.indexOf(PREFIX)
        if (start < 0) {
            // "IVR2:" from a newer app is a different failure from "not a code".
            val other = Regex("IVR\\d+:").find(text)
            return if (other != null) {
                fail(Words.text(UiText.res(R.string.routinecode_other_version)))
            } else {
                fail(Msg.NOT_FOUND.text)
            }
        }
        // Chat apps put text on both sides; the code is the run of URL-safe
        // base64 characters right after the prefix.
        var end = start + PREFIX.length
        while (end < text.length && isCodeChar(text[end])) end++
        val body = text.substring(start + PREFIX.length, end)
        if (body.isEmpty()) return fail(Msg.EMPTY.text)

        val packed = try {
            Base64.getUrlDecoder().decode(body)
        } catch (_: IllegalArgumentException) {
            return fail(CORRUPT)
        }
        val json = when (val result = inflate(packed)) {
            is Inflated.Ok -> result.text
            Inflated.TooBig -> return fail(Msg.TOO_LARGE.text)
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

    private val CORRUPT: String get() = Msg.CORRUPT.text

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

    private fun bad(msg: Msg): Nothing = throw CodeException(msg.text)

    /** Every fixed refusal, a whole sentence each in strings.xml (`routinecode_*`, plain and themed). */
    private enum class Msg(@StringRes val plain: Int, @StringRes val themed: Int) {
        TOO_LONG(R.string.routinecode_too_long, R.string.routinecode_too_long__t),
        NOT_FOUND(R.string.routinecode_not_found, R.string.routinecode_not_found__t),
        EMPTY(R.string.routinecode_empty, R.string.routinecode_empty__t),
        TOO_LARGE(R.string.routinecode_too_large, R.string.routinecode_too_large__t),
        CORRUPT(R.string.routinecode_corrupt, R.string.routinecode_corrupt__t),
        BAD_LAYOUT(R.string.routinecode_bad_layout, R.string.routinecode_bad_layout__t),
        BAD_NO_RITES(R.string.routinecode_bad_no_rites, R.string.routinecode_bad_no_rites__t),
        BAD_UNREADABLE_RITE(R.string.routinecode_bad_unreadable_rite, R.string.routinecode_bad_unreadable_rite__t),
        BAD_RITE_NO_NAME(R.string.routinecode_bad_rite_no_name, R.string.routinecode_bad_rite_no_name__t),
        BAD_INVALID_DAY(R.string.routinecode_bad_invalid_day, R.string.routinecode_bad_invalid_day__t),
        BAD_RITE_NO_EXERCISES(R.string.routinecode_bad_rite_no_exercises, R.string.routinecode_bad_rite_no_exercises__t),
        BAD_UNREADABLE_EXERCISE(R.string.routinecode_bad_unreadable_exercise, R.string.routinecode_bad_unreadable_exercise__t),
        BAD_EXERCISE_NO_NAME(R.string.routinecode_bad_exercise_no_name, R.string.routinecode_bad_exercise_no_name__t),
        BAD_INVALID_LOAD(R.string.routinecode_bad_invalid_load, R.string.routinecode_bad_invalid_load__t),
        BAD_INVALID_COUNT(R.string.routinecode_bad_invalid_count, R.string.routinecode_bad_invalid_count__t),
        BAD_TRAILING_DATA(R.string.routinecode_bad_trailing_data, R.string.routinecode_bad_trailing_data__t),
        BAD_TOO_DEEP(R.string.routinecode_bad_too_deep, R.string.routinecode_bad_too_deep__t),
        BAD_CUT_OFF_DATA(R.string.routinecode_bad_cut_off_data, R.string.routinecode_bad_cut_off_data__t),
        BAD_CUT_OFF_TEXT(R.string.routinecode_bad_cut_off_text, R.string.routinecode_bad_cut_off_text__t),
        BAD_BAD_ESCAPE(R.string.routinecode_bad_bad_escape, R.string.routinecode_bad_bad_escape__t),
        BAD_UNEXPECTED_CHAR(R.string.routinecode_bad_unexpected_char, R.string.routinecode_bad_unexpected_char__t),
        BAD_BAD_NUMBER(R.string.routinecode_bad_bad_number, R.string.routinecode_bad_bad_number__t),
        BAD_UNEXPECTED_WORD(R.string.routinecode_bad_unexpected_word, R.string.routinecode_bad_unexpected_word__t),
        BAD_MISPLACED_CHAR(R.string.routinecode_bad_misplaced_char, R.string.routinecode_bad_misplaced_char__t),
        ;

        val text: String get() = Words.text(UiText.themed(plain, themed))
    }

    private fun read(json: String): List<SharedWorkout> {
        val root = Parser(json).parseDocument() as? Map<*, *> ?: bad(Msg.BAD_LAYOUT)
        val version = (root["v"] as? Double)?.toInt()
        if (version != FORMAT) {
            throw CodeException(Words.text(UiText.res(R.string.routinecode_other_version)))
        }
        val workouts = root["w"] as? List<*> ?: bad(Msg.BAD_NO_RITES)
        if (workouts.isEmpty()) bad(Msg.BAD_NO_RITES)
        if (workouts.size > MAX_WORKOUTS) {
            throw CodeException(Words.text(UiText.themed(R.string.routinecode_too_many_rites, R.string.routinecode_too_many_rites__t, MAX_WORKOUTS)))
        }
        return workouts.map { raw ->
            val o = raw as? Map<*, *> ?: bad(Msg.BAD_UNREADABLE_RITE)
            val name = (o["n"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) bad(Msg.BAD_RITE_NO_NAME)
            val day = when (val d = o["d"]) {
                null -> null
                is Double -> d.toInt().takeIf { it.toDouble() == d && it in 1..7 } ?: bad(Msg.BAD_INVALID_DAY)
                else -> bad(Msg.BAD_INVALID_DAY)
            }
            val entries = o["e"] as? List<*> ?: bad(Msg.BAD_RITE_NO_EXERCISES)
            if (entries.size > MAX_ENTRIES) {
                throw CodeException(
                    Words.text(UiText.themed(R.string.routinecode_too_many_exercises, R.string.routinecode_too_many_exercises__t, MAX_ENTRIES)),
                )
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
        val a = raw as? List<*> ?: bad(Msg.BAD_UNREADABLE_EXERCISE)
        if (a.size != 5) bad(Msg.BAD_UNREADABLE_EXERCISE)
        val name = (a[0] as? String)?.trim().orEmpty()
        if (name.isEmpty()) bad(Msg.BAD_EXERCISE_NO_NAME)
        val sets = wholeIn(a[1], 1..MAX_SETS)
        val reps = wholeIn(a[2], 1..MAX_REPS)
        val weight = when (val w = a[3]) {
            null -> null
            is Double -> w.takeIf { it.isFinite() && it in 0.0..MAX_WEIGHT_KG } ?: bad(Msg.BAD_INVALID_LOAD)
            else -> bad(Msg.BAD_INVALID_LOAD)
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
        val d = value as? Double ?: bad(Msg.BAD_INVALID_COUNT)
        val i = d.toInt()
        if (i.toDouble() != d || i !in range) bad(Msg.BAD_INVALID_COUNT)
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
            if (i != s.length) bad(Msg.BAD_TRAILING_DATA)
            return v
        }

        private fun parseValue(depth: Int): Any? {
            if (depth > 6) bad(Msg.BAD_TOO_DEEP)
            skipWs()
            if (i >= s.length) bad(Msg.BAD_CUT_OFF_DATA)
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
                if (i >= s.length) bad(Msg.BAD_CUT_OFF_TEXT)
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) bad(Msg.BAD_CUT_OFF_TEXT)
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'u' -> {
                                if (i + 4 > s.length) bad(Msg.BAD_CUT_OFF_TEXT)
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> bad(Msg.BAD_BAD_ESCAPE)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-eE.")) i++
            if (start == i) bad(Msg.BAD_UNEXPECTED_CHAR)
            return s.substring(start, i).toDoubleOrNull() ?: bad(Msg.BAD_BAD_NUMBER)
        }

        private fun literal(text: String, value: Any?): Any? {
            if (!s.startsWith(text, i)) bad(Msg.BAD_UNEXPECTED_WORD)
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
            if (i >= s.length || s[i] != c) bad(Msg.BAD_MISPLACED_CHAR)
            i++
        }
    }
}
