package com.ironvellum.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plain words for glossary concepts (the Never columns' unambiguous entries). */
internal val RETIRED_WORDS: List<Regex> = listOf(
    "quests?", "lifters?", "lifter's", "routines?", "warbands?", "garrison", "vessel",
    "hunts?", "cheers?", "cheered", "streaks?", "marshal", "soldiers?", "recruit",
    "mustering", "muster", "marched", "marching", "legion", "conquered", "victory",
    "presets?", "workout log", "full log", "skill tree", "leaderboard", "inbox",
    "epic", "equip", "equipped", "stand fast", "frontline", "scouting", "summoning",
    "workouts?", "sessions?", "stats", "skills?", "plans?", "programs?", "gear", "equipment",
    "schedule", "scheduled", "schedules", "templates?", "achievements?", "badges?", "friends?",
    "followers?", "feed", "comments?", "rest days?", "daily reminders?", "onboarding",
    "kudos", "display names?", "practice logs?", "idle rewards", "weekly plans?", "empty workouts?", "mark(?:ed)? as mastered",
).map { Regex("""(?i)(?<![\w'])$it(?![\w'])""") }

/**
 * The themed names for glossary concepts. Unlike the plain words these also match as a possessive ("circle's"),
 * since "Ironbound's" is as much the word as "Ironbound". Ambiguous English (standing, frame, pattern, yours) is
 * left out: those are reviewed by hand and reach the screen only through Words.
 */
internal val THEMED_WORDS: List<Regex> = listOf(
    "ironbound", "true names?", "folios?", "rites?", "cycles?", "trials?", "seal(?:s|ed|ing)?", "unsealed",
    "chronicles?", "oaths?", "respites?", "summons", "the binding", "deeds?", "techniques?", "journals?", "ledger",
    "readings?", "allies", "ally", "circles?", "keepers?", "tidings", "reckonings?", "rungs?", "tributes?",
    "remarks?", "missives?", "armoury", "armouries", "forge[sd]?", "temper", "codex", "veil", "paths?", "peaks?",
    "claim(?:s|ed|ing)?", "acolyte", "adept", "warden", "magister", "archon", "exarch", "sovereign",
).map { Regex("""(?i)(?<!\w)$it(?!\w)""") }

/**
 * docs/GLOSSARY.md gives every concept a plain word and a themed name, and the app shows one or the other
 * (Words, chosen in Settings). So a concept word typed into a string literal is a bug in one mode: it would stay
 * themed with the switch off, or plain with it on. This scans every string literal under src/main for both
 * vocabularies; the words belong to Words and WordLines. Like InkCoverageTest it is a source scan, justified
 * the same way: the contract is textual and the alternative is a human flipping the switch on every screen.
 *
 * `${…}` templates are stripped before matching, matching is whole-word and
 * case-insensitive, and every exception is listed with the reason it stays.
 */
class GlossaryTest {

    private val root = File("src/main/kotlin/com/ironvellum/app")

    private val sources: List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private val retired = RETIRED_WORDS + THEMED_WORDS

    /** Files whose literals are data formats, not copy. */
    private val exemptFiles = mapOf(
        "data/Words.kt" to "the vocabulary: both vocabularies are written out here",
        "data/WordLines.kt" to "whole lines in both vocabularies",
        "ui/dashboard/TodayNarration.kt" to "Today's narration, written out in both vocabularies",
        "domain/LibraryGuides.kt" to "exercise instructions: \"technique\", \"path\" and \"circle\" are the English words",
        "domain/ExerciseGuides.kt" to "exercise instructions: the English words",
        "domain/ExerciseArtIndex.kt" to "exercise names and art ids",
        "domain/ExerciseLibrary.kt" to "exercise names and cues: the English words",
        "domain/Evidence.kt" to "citations, always plain (journal titles)",
        "ui/settings/OpenSourceNotices.kt" to "licence texts",
        "domain/CsvWorkoutReader.kt" to "parses other apps' CSV headers (\"Workout Name\")",
        "domain/WorkoutCsvWriter.kt" to "writes Strong's CSV header (\"Workout Name\")",
        "domain/ImportAliases.kt" to "maps other apps' exercise names on import",
        "domain/ExportReader.kt" to "reads archive JSON keys",
        "domain/ExportWriter.kt" to "writes archive JSON keys",
        "data/IronvellumDatabase.kt" to "SQL and the migration that retires \"Lifter\"",
        "data/db/Daos.kt" to "SQL",
    )

    /** Literal (after template stripping) to the reason it may keep a retired word. */
    private val allowed = mapOf(
        "Hunter 2014" to "citation author",
        "Inbox poll failed" to "logcat line, never shown",
        "Remmert JF," to "citation, always plain (\"Is There Too Much of a Good Thing... Session\")",
        "Moesgaard L et al." to "citation, always plain (\"...Programs\")",
        "comments/" to "nav route, a protected name",
        "Workout Guide (Bryl Lim" to "proper name of the upstream project credited in the licence panel",
        "Acolyte" to "ascension names: rank titles are themed in both modes",
        "Adept" to "ascension name, both modes",
        "Warden" to "ascension name, both modes",
        "Magister" to "ascension name, both modes",
        "Archon" to "ascension name, both modes",
        "Exarch" to "ascension name, both modes",
        "Sovereign" to "ascension name and crest id, both modes",
        "Set Rite" to "a crest's name, kept in both modes",
        "Ironhold Rite" to "a crest's name, kept in both modes",
        "Ledger Crest" to "a crest's name, kept in both modes",
        "Tribute" to "a crest perk's name, kept in both modes",
        "Seal of Vigil" to "a relic's name, kept in both modes",
        "Seal of Craft" to "a relic's name, kept in both modes",
        "the Ledger" to "a relic house's name, kept in both modes",
        "The peak of hand balance" to "the English word",
        "rite of passage" to "the English idiom",
        "path data" to "a developer error about SVG path data",
        ".paths" to "the file extension of the exercise art path files",
        "(?:Hunter|Lifter|Ironbound)" to "legacy seeded handles still count as unclaimed",
    )

    /** Whole literals that are data, not copy, with the reason. */
    private val allowedExact = mapOf(
        "Ironbound" to "the seeded default name and the wire handle prefix: a sentinel stored and matched, never printed as a word",
        "Lifter" to "the plain-mode default name, matched as unclaimed so signup does not prefill it",
        "Only the Keeper" to "the wire text a refusal starts with; Words.fromServer turns the rest plain for display",
    ).keys

    /** Raised messages that name a retired word on purpose, with the reason. */
    private val allowedRaises = mapOf(
        "warbands and circles both exist: merge them by hand before applying this file" to
            "the migration guard names the old tables; only the project owner ever sees it",
    ).keys

    @Test
    fun `the scan actually sees the sources`() {
        assertTrue(
            "sources not found from ${File(".").absolutePath}; the scan below would pass vacuously",
            sources.size > 50,
        )
    }

    @Test
    fun `no retired glossary word reaches a string literal`() {
        val hits = mutableListOf<String>()
        for (file in sources) {
            val rel = file.relativeTo(root).invariantSeparatorsPath
            if (rel in exemptFiles) continue
            for ((line, text) in literals(file.readText())) {
                val words = text.replace(TEMPLATE, " ")
                // Routes, cache keys, tags and channel ids: no space, no capital outside {args}.
                if (KEY.matches(words.trim())) continue
                if (allowed.keys.any { words.contains(it) } || words.trim() in allowedExact) continue
                retired.firstOrNull { it.containsMatchIn(words) }?.let {
                    hits += "$rel:$line  \"${text.take(90)}\"  (${it.find(words)!!.value})"
                }
            }
        }
        assertEquals(
            "Retired words from docs/GLOSSARY.md are back; use the glossary term " +
                "or add an allowlist entry with a reason:\n" + hits.joinToString("\n"),
            0,
            hits.size,
        )
    }

    /**
     * The baseline's `raise exception` messages are copy too: Cloud.explain
     * passes a P0001 through word for word (a remark rate limit, a blocked ally
     * request, a circle refusal). A refusal carrying errcode 42501 is the
     * developer-facing kind a revoked grant gives, which the client replaces
     * (except "Only the Keeper ...", which is written as copy), so it is skipped.
     */
    @Test
    fun `no retired glossary word reaches an error the baseline raises`() {
        val sql = baselineSql()
        val raise = Regex("""raise\s+exception\s+'((?:[^']|'')*)'([^;]*);""")
        val hits = mutableListOf<String>()
        var messages = 0
        for (m in raise.findAll(sql)) {
            if ("42501" in m.groupValues[2]) continue
            messages++
            val text = m.groupValues[1].replace("''", "'")
            if (text in allowedRaises) continue
            RETIRED_WORDS.firstOrNull { it.containsMatchIn(text) }?.let {
                hits += "\"$text\"  (${it.find(text)!!.value})"
            }
        }
        assertTrue("fewer than 15 raise exception messages found in the baseline; the scan is broken", messages >= 15)
        assertEquals(
            "The baseline raises a message with a word docs/GLOSSARY.md retired:\n" + hits.joinToString("\n"),
            0,
            hits.size,
        )
    }

    private fun baselineSql(): String {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "supabase/migrations").isDirectory) dir = dir.parentFile
        val found = dir?.let { File(it, "supabase/migrations/0001_baseline.sql") }
        requireNotNull(found) { "supabase/migrations/0001_baseline.sql not found from ${File("").absolutePath}" }
        return found.readText()
    }

    @Test
    fun `the literal scanner sees through templates and comments`() {
        val src = """
            // a "quest" in a comment is not copy
            val a = "Begin ${'$'}{if (x) "Trial" else "Rite"} now"
            val b = '"'
            /* "streak" */ val c = "Oath"
        """.trimIndent()
        val found = literals(src).map { it.second }
        assertEquals(listOf("Begin ${'$'}{if (x) \"Trial\" else \"Rite\"} now", "Oath"), found)
    }

    private companion object {
        val KEY = Regex("""[a-z0-9_./:?&=\-]*(\{[A-Za-z]+\}[a-z0-9_./:?&=\-]*)*""")
        val TEMPLATE = Regex("""\$\{[^{}]*(\{[^{}]*\}[^{}]*)*\}|\$\w+""")

        /** Every string literal in [src] with its starting line; comments and char literals skipped. */
        fun literals(src: String): List<Pair<Int, String>> {
            val out = mutableListOf<Pair<Int, String>>()
            var i = 0
            var line = 1
            while (i < src.length) {
                val c = src[i]
                when {
                    c == '\n' -> { line++; i++ }
                    src.startsWith("//", i) -> i = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                    src.startsWith("/*", i) -> {
                        val end = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                        line += src.substring(i, end).count { it == '\n' }
                        i = end
                    }
                    c == '\'' -> {
                        val close = if (src.getOrNull(i + 1) == '\\') i + 3 else i + 2
                        i = if (src.getOrNull(close) == '\'') close + 1 else i + 1
                    }
                    c == '"' -> {
                        val triple = src.startsWith("\"\"\"", i)
                        val (end, text) = readString(src, i + if (triple) 3 else 1, triple)
                        out += line to text
                        line += text.count { it == '\n' }
                        i = end
                    }
                    else -> i++
                }
            }
            return out
        }

        /** Index after the closing quote, and the raw text with `${…}` kept verbatim. */
        fun readString(src: String, start: Int, triple: Boolean): Pair<Int, String> {
            val sb = StringBuilder()
            var i = start
            while (i < src.length) {
                if (triple && src.startsWith("\"\"\"", i)) return i + 3 to sb.toString()
                val c = src[i]
                if (!triple && c == '\\') { sb.append(src, i, minOf(i + 2, src.length)); i += 2; continue }
                if (!triple && c == '"') return i + 1 to sb.toString()
                if (src.startsWith("\${", i)) {
                    var depth = 1
                    var j = i + 2
                    while (j < src.length && depth > 0) {
                        when (src[j]) {
                            '"' -> {
                                val inner = src.startsWith("\"\"\"", j)
                                j = readString(src, j + if (inner) 3 else 1, inner).first
                                continue
                            }
                            '{' -> depth++
                            '}' -> depth--
                        }
                        j++
                    }
                    sb.append(src, i, j)
                    i = j
                    continue
                }
                sb.append(c)
                i++
            }
            return i to sb.toString()
        }
    }
}
