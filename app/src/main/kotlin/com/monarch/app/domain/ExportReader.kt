package com.monarch.app.domain

import java.time.LocalDate

/**
 * Hand-rolled JSON reader for the data export. Pure Kotlin so it is
 * unit-testable on the JVM with no Android or org.json dependency.
 * Never throws: malformed input, missing sections and unknown format
 * versions surface as [Result.failure] with a readable message.
 */
object ExportReader {

    data class Archive(
        val formatVersion: Int,
        val exportedAtMs: Long,
        val profile: PlayerProfile,
        val trainingMode: TrainingMode,
        val presets: List<WorkoutPreset>,
        val sessions: List<Pair<WorkoutSession, List<SessionSet>>>,
        val stats: List<StatEntry>,
        val titles: List<UnlockedTitle>,
        val skills: List<SkillPractice>,
        val healthDays: List<HealthDay>,
        // Absent in v1-v3 archives, so this defaults to empty — an older
        // backup must still restore.
        val measurements: List<MeasurementEntry> = emptyList(),
    )

    fun read(json: String): Result<Archive> = runCatching {
        val p = Parser(json)
        val root = p.parseDocument()
        Archive(
            formatVersion = root.int("formatVersion") ?: fail("missing formatVersion"),
            exportedAtMs = root.long("exportedAtMs") ?: fail("missing exportedAtMs"),
            profile = readProfile(root.obj("profile") ?: fail("missing profile")),
            trainingMode = root.str("trainingMode")?.let { mode ->
                runCatching { TrainingMode.valueOf(mode) }.getOrElse { fail("unknown trainingMode \"$mode\"") }
            } ?: TrainingMode.STRENGTH,
            presets = (root.arr("presets") ?: fail("missing presets")).map { readPreset(it as Obj) },
            sessions = (root.arr("sessions") ?: fail("missing sessions")).map { readSession(it as Obj) },
            stats = (root.arr("stats") ?: fail("missing stats")).map { readStat(it as Obj) },
            titles = (root.arr("titles") ?: fail("missing titles")).map { readTitle(it as Obj) },
            skills = (root.arr("skills") ?: emptyList()).map { readSkill(it as Obj) },
            healthDays = (root.arr("healthDays") ?: emptyList()).map { readHealthDay(it as Obj) },
            measurements = (root.arr("measurements") ?: emptyList()).map { readMeasurement(it as Obj) },
            // Legacy v4 archives may still carry a "measurementGoals" section;
            // the parser absorbs it and we deliberately ignore it — an old
            // backup must restore, not fail.
        ).also {
            // A newer archive may carry fields this build cannot understand.
            if (it.formatVersion > ExportWriter.FORMAT_VERSION) {
                fail("archive formatVersion ${it.formatVersion} is newer than supported ${ExportWriter.FORMAT_VERSION}")
            }
        }
    }.recoverCatching { e ->
        if (e is ReadException) throw e
        throw ReadException("malformed archive: ${e.message}")
    }

    private class ReadException(message: String) : IllegalStateException(message)

    private fun fail(message: String): Nothing = throw ReadException(message)

    private fun readProfile(o: Obj) = PlayerProfile(
        name = o.str("name") ?: fail("profile missing name"),
        totalXp = o.long("totalXp") ?: fail("profile missing totalXp"),
        currentTitleId = o.str("currentTitleId"),
        trainingMode = TrainingMode.STRENGTH,
    )

    private fun readPreset(o: Obj) = WorkoutPreset(
        id = o.long("id") ?: 0,
        name = o.str("name") ?: fail("preset missing name"),
        note = o.str("note") ?: "",
        entries = (o.arr("entries") ?: emptyList()).map { e ->
            val entry = e as Obj
            PresetEntry(
                exerciseId = entry.long("exerciseId") ?: fail("preset entry missing exerciseId"),
                exerciseName = entry.str("exerciseName") ?: "",
                targetSets = entry.int("targetSets") ?: fail("preset entry missing targetSets"),
                targetReps = entry.int("targetReps") ?: fail("preset entry missing targetReps"),
                targetWeightKg = entry.dbl("targetWeightKg"),
                modifiers = entry.str("modifiers") ?: "",
                position = entry.int("position") ?: 0,
            )
        },
        scheduledDay = o.int("scheduledDay"),
    )

    private fun readSession(o: Obj) = WorkoutSession(
        id = o.long("id") ?: 0,
        presetId = o.long("presetId"),
        label = o.str("label") ?: fail("session missing label"),
        startedAtMs = o.long("startedAtMs") ?: fail("session missing startedAtMs"),
        completedAtMs = o.long("completedAtMs"),
        xpAwarded = o.int("xpAwarded") ?: 0,
        strengthScore = o.int("strengthScore") ?: 0,
        // Absent in v1/v2 archives, so default rather than fail — an older
        // backup must still restore.
        title = o.str("title") ?: "",
        note = o.str("note") ?: "",
        privateNote = o.str("privateNote") ?: "",
    ) to (o.arr("sets") ?: emptyList()).map { s ->
        val set = s as Obj
        SessionSet(
            id = set.long("id") ?: 0,
            exerciseId = set.long("exerciseId") ?: fail("set missing exerciseId"),
            exerciseName = set.str("exerciseName") ?: "",
            exercisePosition = set.int("exercisePosition") ?: 0,
            setIndex = set.int("setIndex") ?: fail("set missing setIndex"),
            reps = set.int("reps") ?: fail("set missing reps"),
            weightKg = set.dbl("weightKg"),
            modifiers = set.str("modifiers") ?: "",
            done = set.bool("done") ?: false,
        )
    }

    private fun readStat(o: Obj) = StatEntry(
        takenAtMs = o.long("takenAtMs") ?: fail("stat missing takenAtMs"),
        weightKg = o.dbl("weightKg") ?: fail("stat missing weightKg"),
        heightCm = o.dbl("heightCm") ?: fail("stat missing heightCm"),
        bodyFatPct = o.dbl("bodyFatPct"),
    )

    private fun readTitle(o: Obj) = UnlockedTitle(
        titleId = o.str("titleId") ?: fail("title missing titleId"),
        unlockedAtMs = o.long("unlockedAtMs") ?: fail("title missing unlockedAtMs"),
    )

    private fun readSkill(o: Obj) = SkillPractice(
        skillName = o.str("skillName") ?: fail("skill missing skillName"),
        practicedAtMs = o.long("practicedAtMs") ?: fail("skill missing practicedAtMs"),
        claimed = o.bool("claimed") ?: false,
        value = o.int("value") ?: 0,
        weightKg = o.dbl("weightKg"),
    )

    private fun readHealthDay(o: Obj) = HealthDay(
        date = LocalDate.parse(o.str("date") ?: fail("healthDay missing date")),
        steps = o.int("steps") ?: 0,
        distanceKm = o.dbl("distanceKm") ?: 0.0,
        activeKcal = o.int("activeKcal") ?: 0,
        sleepMinutes = o.int("sleepMinutes") ?: 0,
        restingHr = o.int("restingHr"),
    )

    private fun readMeasurement(o: Obj) = MeasurementEntry(
        site = MeasurementSite.entries.firstOrNull { it.name == o.str("site") }
            ?: fail("measurement missing site"),
        valueCm = o.dbl("valueCm") ?: fail("measurement missing valueCm"),
        takenAtMs = o.long("takenAtMs") ?: fail("measurement missing takenAtMs"),
    )

    // --- JSON value model ---------------------------------------------------

    private class Obj(private val map: Map<String, Any?>) {
        fun str(key: String): String? = map[key] as? String
        fun int(key: String): Int? = (map[key] as? Long)?.toInt() ?: (map[key] as? Double)?.toInt()
        fun long(key: String): Long? = (map[key] as? Long) ?: (map[key] as? Double)?.toLong()
        fun dbl(key: String): Double? = (map[key] as? Double) ?: (map[key] as? Long)?.toDouble()
        fun bool(key: String): Boolean? = map[key] as? Boolean
        fun obj(key: String): Obj? = map[key] as? Obj
        @Suppress("UNCHECKED_CAST")
        fun arr(key: String): List<Any?>? = map[key] as? List<Any?>
    }

    /** Recursive-descent parser over the raw archive text. */
    private class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(): Obj {
            skipWs()
            val v = parseValue()
            skipWs()
            if (i < s.length) fail("trailing characters at offset $i")
            return v as? Obj ?: fail("top-level value is not an object")
        }

        private fun parseValue(): Any? {
            if (i >= s.length) fail("unexpected end of input")
            return when (s[i]) {
                '{' -> parseObj()
                '[' -> parseArr()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObj(): Obj {
            expect('{')
            val map = mutableMapOf<String, Any?>()
            skipWs()
            if (peek('}')) return Obj(map)
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                skipWs()
                map[key] = parseValue()
                skipWs()
                when {
                    peek(',') -> {}
                    peek('}') -> return Obj(map)
                    else -> fail("expected ',' or '}' at offset $i")
                }
            }
        }

        private fun parseArr(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWs()
            if (peek(']')) return list
            while (true) {
                skipWs()
                list.add(parseValue())
                skipWs()
                when {
                    peek(',') -> {}
                    peek(']') -> return list
                    else -> fail("expected ',' or ']' at offset $i")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'u' -> {
                                if (i + 4 > s.length) fail("truncated \\u escape")
                                val hex = s.substring(i, i + 4)
                                i += 4
                                val code = hex.toIntOrNull(16) ?: fail("bad \\u escape \"$hex\"")
                                sb.append(code.toChar())
                            }
                            else -> fail("bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Any {
            val start = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            while (i < s.length && (s[i].isDigit() || s[i] in "eE.")) i++
            val text = s.substring(start, i)
            if (text.isEmpty()) fail("unexpected character '${s.getOrNull(start)}' at offset $start")
            // Whole numbers parse as Long so ids/timestamps keep full precision.
            text.toLongOrNull()?.let { return it }
            return text.toDoubleOrNull() ?: fail("bad number \"$text\"")
        }

        private fun literal(text: String, value: Any?): Any? {
            if (!s.startsWith(text, i)) fail("bad literal at offset $i")
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
            if (i >= s.length || s[i] != c) fail("expected '$c' at offset $i")
            i++
        }
    }
}
