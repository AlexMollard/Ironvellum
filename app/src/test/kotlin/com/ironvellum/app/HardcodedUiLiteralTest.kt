package com.ironvellum.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen text belongs in `res/values/strings.xml` (docs/STRINGS.md), so a string literal handed to something that
 * shows text is a literal that still has to move. This finds them and holds the line with a ratchet: the checked-in
 * baseline (`src/test/resources/hardcoded-ui-literals.baseline`) says how many each file has today, and
 *
 *  - a file with MORE than its baseline fails and prints the new literals: use a string resource instead;
 *  - a file with FEWER fails too, telling you the number to lower the baseline to, so progress cannot be spent
 *    on a new literal elsewhere in the same file.
 *
 * Re-baseline after a migration with `UPDATE_UI_LITERAL_BASELINE=1 ./gradlew :app:testFossDebugUnitTest --tests
 * "*HardcodedUiLiteralTest*"`; `DUMP_UI_LITERALS=<file>` writes every counted literal (the migration worklist).
 *
 * What counts: a string literal with a letter, inside the argument handed to a text parameter. That is `Text(`,
 * the named arguments in [NAMED] (`label =`, `title =`, `contentDescription =`...), the notification builder's
 * `setContentTitle(` and friends, and every `String` parameter of every composable declared in this source tree
 * (`SettingsCaption("...")`, `SettingsGroup("...")`), found by reading the declarations so a new wrapper is covered
 * without touching this test. A literal inside a lambda argument is reached through the call it makes.
 * Like GlossaryTest it is a source scan; the contract is textual and the alternative is a human reading every screen.
 */
class HardcodedUiLiteralTest {

    private val srcDir = File("src")
    private val baselineFile = File("src/test/resources/hardcoded-ui-literals.baseline")

    private val sources: List<Pair<String, File>> =
        listOf("main", "foss", "play").flatMap { flavour ->
            val root = File(srcDir, "$flavour/kotlin/com/ironvellum/app")
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }
                .map { "$flavour/" + it.relativeTo(root).invariantSeparatorsPath to it }.toList()
        }

    /** Files whose literals are data, not screen text, each with the reason; the scan skips them. */
    private val exempt = mapOf(
        "main/domain/LibraryGuides.kt" to "exercise guides: English data for a later step (owner, 2026-10-16)",
        "main/domain/ExerciseGuides.kt" to "exercise guides: English data for a later step",
        "main/domain/ExerciseArtIndex.kt" to "exercise names and art ids",
        "main/domain/ExerciseLibrary.kt" to "exercise names and cues: English data for a later step",
        "main/domain/Evidence.kt" to "citations, always plain (journal titles)",
        "main/domain/MuscleMap.kt" to "exercise-name keyed muscle data: English data for a later step",
        "main/domain/GearRequirements.kt" to "exercise-name keyed gear data: English data for a later step",
        "main/domain/MovementDifficulty.kt" to "exercise-name keyed difficulty data: English data for a later step",
        "main/domain/BodyShare.kt" to "exercise-name keyed body-share data, the sibling of MovementDifficulty: English data for a later step",
        "main/domain/FatigueTraits.kt" to "exercise-name keyed fatigue traits (stretch-heavy, heavy whole-body): English data, the sibling of MuscleMap",
        "main/domain/StarterLoads.kt" to "exercise-name keyed starter loads: English data for a later step",
        "main/domain/ExerciseSearch.kt" to "exercise search aliases, matched against English names",
        "main/data/Seed.kt" to "seeded exercise, activity and template rows: stored data, English for a later step",
        "main/ui/settings/OpenSourceNotices.kt" to "licence texts, legal wording kept as published",
        "main/domain/CsvWorkoutReader.kt" to "parses other apps' CSV headers",
        "main/domain/WorkoutCsvWriter.kt" to "writes Strong's CSV header",
        "main/domain/ImportAliases.kt" to "maps other apps' exercise names on import",
        "main/domain/ExerciseTwins.kt" to "the table of dedicated exercises and the base each is: exercise names, English data like Seed",
        "main/domain/ExportReader.kt" to "reads archive JSON keys",
        "main/domain/ExportWriter.kt" to "writes archive JSON keys",
        "main/domain/Skills.kt" to "skill names and descriptions: data, deferred with title/skill names (owner, 2026-10-16)",
        "main/domain/TitleEngine.kt" to "title and deed names and descriptions: data, deferred with title/skill names",
        "main/domain/Crests.kt" to "crest names and descriptions: data, deferred with title/skill names",
        "main/domain/DeedLadders.kt" to "deed category names and blurbs, derived from the title catalogue: data, deferred with title/skill names",
        "main/domain/RelicHouses.kt" to "relic names and perk text: catalogue data, deferred with title/skill names",
        "main/domain/CrestCatalogue.kt" to "crest names and descriptions: data, deferred with title/skill names",
        "main/data/IronvellumDatabase.kt" to "SQL and migrations",
        "main/data/db/Daos.kt" to "SQL",
    )

    /**
     * The files the baseline may still list, each with why what is left in it is not screen text. The baseline keeps
     * counting them (a new literal in one still fails); a file that is not listed here cannot hold a baseline entry.
     */
    private val justified = mapOf(
        "main/data/cloud/AccountRepository.kt" to "regex for generated handles (Hunter/Lifter/Ironbound plus four digits), not screen text",
        "main/data/cloud/Cloud.kt" to "wire data: the server's English refusal 'Only the Keeper', matched by Words.fromServer",
        "main/data/cloud/CloudSync.kt" to "PostgREST column lists, select expressions and cache keys sent to the server",
        "main/data/CrashJournal.kt" to "crash report file content and file-name template: a developer artefact, never shown in the UI",
        "main/data/DbSnapshot.kt" to "database snapshot file-name templates",
        "main/data/HealthWriteBack.kt" to "Health Connect client record id template",
        "main/data/PlateStore.kt" to "SharedPreferences key templates",
        "main/data/Words.kt" to "ServerTerm table: the server's themed English words, matched in refusals and replaced from resources",
        "main/domain/Circles.kt" to "regex that finds an invite code in pasted text",
        "main/domain/Gacha.kt" to "legacy relic house names (the Mark, the Ledger...) used to derive old relic identities: catalogue data",
        "main/domain/LiftBoards.kt" to "exercise, progression and lift-board names and their matching keys: exercise catalogue data",
        "main/domain/Modifiers.kt" to "lower-case substrings matched against exercise names, never shown",
        "main/domain/ProgramEasing.kt" to "de-duplication keys for easing proposals",
        "main/domain/ProgramGenerator.kt" to "citations ('Lopez 2021') and exercise names: catalogue data; every sentence moved to resources",
        "main/domain/ProgramRules.kt" to "exercise-name keyed rule tables, one citation and two developer-only @Deprecated messages",
        "main/domain/ProgramTemplates.kt" to "template, day and exercise names, citations and the remaining summaries: template catalogue data (the three summaries that named a concept moved)",
        "main/domain/RankPatterns.kt" to "exercise names that map lifts to rank patterns: catalogue data",
        "main/domain/RestRules.kt" to "lower-case exercise-name keys of the rest-time table",
        "main/domain/RoutineCode.kt" to "regex for the shared-routine code prefix",
        "main/domain/SetKind.kt" to "import aliases for other apps' Set Type cells (Strong, Hevy, FitNotes)",
        "main/ui/components/CelebrationPages.kt" to "animation label",
        "main/ui/components/Common.kt" to "animation labels and a date pattern",
        "main/ui/components/CrestMarks.kt" to "SVG path data",
        "main/ui/components/ExerciseDrawing.kt" to "asset path template",
        "main/ui/components/InkTabs.kt" to "animation label",
        "main/ui/components/Sigil.kt" to "animation labels",
        "main/ui/components/TermInfo.kt" to "lower-case stem matched against exercise text; the term's title and definition are resources",
        "main/ui/components/XpPage.kt" to "animation label",
        "main/ui/dashboard/DashboardScreen.kt" to "first-use key and a date pattern",
        "main/ui/dashboard/TodaySoul.kt" to "Compose test tags",
        "main/ui/dashboard/VeilShared.kt" to "animation labels",
        "main/ui/idle/IdleScreen.kt" to "number format patterns and a relic id",
        "main/ui/idle/RateScreen.kt" to "number format patterns",
        "main/ui/idle/VeilBuySheet.kt" to "number format patterns",
        "main/ui/IronvellumNav.kt" to "route patterns and navigation argument names",
        "main/ui/program/OptimiseModel.kt" to "de-duplication key",
        "main/ui/program/OptimiseScreen.kt" to "de-duplication key",
        "main/ui/program/ProgramBuilderScreen.kt" to "state key",
        "main/ui/settings/AdvancedSettings.kt" to "date pattern",
        "main/ui/settings/AppearanceSettings.kt" to "animation label",
        "main/ui/settings/HealthConnectSettings.kt" to "number and date format patterns",
        "main/ui/settings/SettingsViewModel.kt" to "export file-name template and the exercise-name keyed typical-load table for the kg/lb guess",
        "main/ui/social/CommentsScreen.kt" to "lazy-list item keys, a unit symbol format and set-marker glyphs: no words",
        "main/ui/social/FeedScreen.kt" to "unit symbol format",
        "main/ui/stats/LedgerBody.kt" to "date pattern and first-use key",
        "main/ui/stats/MeasurementDetailScreen.kt" to "number and date format patterns",
        "main/ui/theme/MetalGradient.kt" to "animation labels",
        "main/ui/titles/DeedDetailSheet.kt" to "date pattern",
        "main/ui/titles/DeedSeal.kt" to "SVG path data",
        "main/ui/titles/SkillGlyph.kt" to "skill names used as glyph lookup keys: catalogue data",
        "main/ui/titles/SkillJournal.kt" to "time pattern",
        "main/ui/titles/SkillTreeLayout.kt" to "lane markers in layout keys",
        "main/ui/train/ExerciseExplorerScreen.kt" to "number format patterns",
        "main/ui/train/SessionScreen.kt" to "date patterns",
    )

    @Test
    fun `the scan actually sees the sources and finds the known shapes`() {
        assertTrue("sources not found from ${File("").absolutePath}", sources.size > 50)
        val found = scanText(
            """
            @Composable fun Wrap(title: String, count: Int, hint: String? = null) { Text(title) }
            fun f() {
                Text("one")
                Text(text = "two", color = x)
                Text(if (a) "three" else "four")
                Text(Words.x.c)
                Text("${'$'}{x}%")
                Wrap("five", 1)
                Wrap(count = 2, hint = "six", title = x)
                Foo(label = "seven", other = "elsewhere")
                val label = "param"
                val shown = pick("a prose line")
                error("a developer message")
                val fmt = DateTimeFormatter.ofPattern("EEE d MMM")
                nb.setContentTitle("eight").setContentText("nine" + x)
                Foo(contentDescription = null, onClick = { Text("ten") })
            }
            """.trimIndent(),
            wrappers = declaredStringParams(
                SourceScan.scan("@Composable fun Wrap(title: String, count: Int, hint: String? = null) {}").masked,
            ),
        ).map { it.text }
        assertEquals(
            listOf("one", "two", "three", "four", "five", "six", "seven", "a prose line", "eight", "nine", "ten"),
            found,
        )
    }

    @Test
    fun `no new hardcoded screen text`() {
        val counted = sources.filter { it.first !in exempt }.associate { (rel, file) -> rel to scanFile(file.readText()) }
        val actual = counted.mapValues { it.value.size }.filterValues { it > 0 }
        dump(counted)
        if (System.getenv("UPDATE_UI_LITERAL_BASELINE") == "1") {
            writeBaseline(actual)
            return
        }
        val baseline = readBaseline()
        val problems = mutableListOf<String>()
        for (path in (actual.keys + baseline.keys).sorted()) {
            val now = actual[path] ?: 0
            val was = baseline[path] ?: 0
            when {
                now > was -> problems += "$path: $now literals, baseline $was. New text goes in res/values/strings.xml " +
                    "(docs/STRINGS.md). The literals:\n" + counted.getValue(path).joinToString("\n") { "    line ${it.line}: \"${it.text.take(100)}\"" }
                now < was -> problems += "$path: $now literals, baseline $was. Lower the baseline to $now " +
                    "(UPDATE_UI_LITERAL_BASELINE=1), so the room you made is not spent on a new literal."
            }
        }
        assertEquals(
            "Hardcoded UI text ratchet (total ${actual.values.sum()} in ${actual.size} files, baseline ${baseline.values.sum()}):\n" +
                problems.joinToString("\n"),
            0,
            problems.size,
        )
    }

    @Test
    fun `every baseline entry is justified`() {
        val baseline = readBaseline()
        val unjustified = baseline.keys.filter { it !in justified }
        assertEquals("baseline files with no justification (migrate them, or add a reason to justified): $unjustified", 0, unjustified.size)
        val stale = justified.keys.filter { it !in baseline }
        assertEquals("justified files that no longer hold a literal (delete the entry): $stale", 0, stale.size)
    }

    @Test
    fun `every exemption names a file that exists`() {
        val present = sources.map { it.first }.toSet()
        val stale = exempt.keys.filter { it !in present }
        assertEquals("exempt files that no longer exist (delete the entry): $stale", 0, stale.size)
    }

    private fun readBaseline(): Map<String, Int> {
        require(baselineFile.isFile) { "$baselineFile missing; generate it with UPDATE_UI_LITERAL_BASELINE=1" }
        return baselineFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line -> line.substringBeforeLast(' ') to line.substringAfterLast(' ').toInt() }
    }

    private fun writeBaseline(actual: Map<String, Int>) {
        val text = buildString {
            append("# Hardcoded screen-text literals per file (HardcodedUiLiteralTest). Counts may only go down.\r\n")
            append("# Total ${actual.values.sum()} in ${actual.size} files. Regenerate: UPDATE_UI_LITERAL_BASELINE=1.\r\n")
            for ((path, n) in actual.toSortedMap()) append("$path $n\r\n")
        }
        baselineFile.parentFile.mkdirs()
        baselineFile.writeBytes(text.toByteArray(Charsets.UTF_8))
    }

    private fun dump(counted: Map<String, List<Lit>>) {
        val out = System.getenv("DUMP_UI_LITERALS") ?: return
        File(out).writeText(
            counted.toSortedMap().flatMap { (path, lits) -> lits.map { "$path:${it.line}\t${it.text.replace("\n", "\\n")}" } }
                .joinToString("\n"),
        )
    }

    // ---- the scan ----

    /** Composable name to the parameter names (and positions) that are `String` in some declaration of it in the tree. */
    private val wrappers: Map<String, Set<Any>> by lazy {
        val all = mutableMapOf<String, MutableSet<Any>>()
        for ((_, file) in sources) {
            for ((name, params) in declaredStringParams(SourceScan.scan(file.readText()).masked)) {
                all.getOrPut(name) { mutableSetOf() }.addAll(params)
            }
        }
        all
    }

    private fun scanFile(src: String): List<Lit> = scanText(src, wrappers)

    private fun scanText(src: String, wrappers: Map<String, Set<Any>>): List<Lit> {
        val scan = SourceScan.scan(src)
        val m = scan.masked
        val hits = sortedMapOf<Int, Lit>()
        fun collect(range: IntRange) {
            if (range.isEmpty()) return
            var depth = 0
            for (lit in scan.literals) {
                if (lit.start < range.first || lit.start > range.last) continue
                // Depth of braces opened inside this argument: a literal in a lambda body belongs to the call it makes.
                depth = 0
                for (k in range.first until lit.start) when (m[k]) { '{' -> depth++; '}' -> depth-- }
                if (depth == 0 && HAS_TEXT(lit.text)) hits[lit.start] = lit
            }
        }
        // Positional text parameters of known calls, then named arguments anywhere.
        for (call in CALL.findAll(m)) {
            val name = call.groupValues[1]
            val positions: Set<Any> = when {
                name == "Text" -> setOf(0)
                name in BUILDER_CALLS -> setOf(0)
                else -> wrappers[name] ?: continue
            }
            if (m.substring(maxOf(0, call.range.first - 8), call.range.first).trimEnd().endsWith("fun")) continue
            val args = arguments(m, call.range.last)
            args.forEachIndexed { i, arg ->
                val named = NAMED_ARG.find(m.substring(arg.first, arg.last + 1))
                if (named != null) {
                    if (named.groupValues[1] in positions || (name == "Text" && named.groupValues[1] == "text")) {
                        collect(arg.first + named.range.last + 1..arg.last)
                    }
                } else if (i in positions) {
                    collect(arg)
                }
            }
        }
        for (arg in NAMED_ANYWHERE.findAll(m)) {
            val before = m.substring(0, arg.range.first).trimEnd()
            if (before.endsWith("val") || before.endsWith("var")) continue
            collect(argumentFrom(m, arg.range.last + 1))
        }
        // The net under the lists above: prose (two or more words) is copy wherever it sits, whether it reaches
        // the screen through a helper, a return value or a `when` branch. Developer messages are not copy.
        for (lit in scan.literals) {
            if (lit.start !in hits && isProse(lit.text) && !isDeveloperCall(enclosingCall(m, lit.start))) hits[lit.start] = lit
        }
        return hits.values.toList()
    }

    /** A call whose text is for developers: `error`, `Log.w`, and `scope.launchGuarded` (the receiver does not matter). */
    private fun isDeveloperCall(name: String?): Boolean =
        name != null && (name in DEVELOPER_CALLS || name.substringAfterLast('.') in DEVELOPER_CALLS)

    /** The name of the call whose argument list holds [index], or null when it is not an argument. */
    private fun enclosingCall(m: String, index: Int): String? {
        var depth = 0
        var i = index - 1
        while (i >= 0) {
            when (m[i]) {
                ')', ']', '}' -> depth++
                '(', '[', '{' -> {
                    if (depth == 0) {
                        if (m[i] == '{') return trailingLambdaOwner(m, i)
                        if (m[i] != '(') return null
                        return IDENT_BEFORE.find(m.substring(maxOf(0, i - 40), i))?.groupValues?.get(1)
                    }
                    depth--
                }
            }
            i--
        }
        return null
    }

    /**
     * The call a trailing lambda `{` at [open] belongs to: `require(ok) { "message" }` is owned by `require`, so a
     * developer message in its lambda is exempt like one in its argument list. Null for a lambda that follows no
     * argument list (`Column { ... }`, `if (x) { ... }` bodies are not calls).
     */
    private fun trailingLambdaOwner(m: String, open: Int): String? {
        var i = open - 1
        while (i >= 0 && m[i].isWhitespace()) i--
        if (i < 0 || m[i] != ')') return null
        var depth = 0
        while (i >= 0) {
            when (m[i]) {
                ')' -> depth++
                '(' -> { depth--; if (depth == 0) return IDENT_BEFORE.find(m.substring(maxOf(0, i - 40), i))?.groupValues?.get(1) }
            }
            i--
        }
        return null
    }

    /** The `(`-opened argument list at [open] as ranges, one for each top-level argument, whitespace trimmed. */
    private fun arguments(m: String, open: Int, angle: Boolean = false): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var depth = 0
        var start = open + 1
        var i = open + 1
        while (i < m.length) {
            when (m[i]) {
                '(', '[', '{' -> depth++
                '<' -> if (angle) depth++
                '>' -> if (angle && m[i - 1] != '-') depth--
                ')', ']', '}' -> {
                    if (depth == 0) { addTrimmed(m, start, i, out); return out }
                    depth--
                }
                ',' -> if (depth == 0) { addTrimmed(m, start, i, out); start = i + 1 }
            }
            i++
        }
        return out
    }

    /** The expression that starts at [from]: up to the next top-level comma or closing bracket. */
    private fun argumentFrom(m: String, from: Int): IntRange {
        var depth = 0
        var i = from
        while (i < m.length) {
            when (m[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> { if (depth == 0) break; depth-- }
                ',' -> if (depth == 0) break
            }
            i++
        }
        val out = mutableListOf<IntRange>()
        addTrimmed(m, from, i, out)
        return out.firstOrNull() ?: IntRange.EMPTY
    }

    private fun addTrimmed(m: String, from: Int, to: Int, out: MutableList<IntRange>) {
        var a = from
        var b = to
        while (a < b && m[a].isWhitespace()) a++
        while (b > a && m[b - 1].isWhitespace()) b--
        if (b > a) out += a until b
    }

    /** `fun Name(a: String, b: Int, c: String? = null)` in [m] to Name -> {"a", 0, "c", 2}: the text parameters by name and position. */
    private fun declaredStringParams(m: String): Map<String, Set<Any>> {
        val out = mutableMapOf<String, MutableSet<Any>>()
        for (decl in DECLARATION.findAll(m)) {
            val open = decl.range.last
            val params = arguments(m, open, angle = true)
            params.forEachIndexed { i, p ->
                val text = m.substring(p.first, p.last + 1)
                val match = STRING_PARAM.find(text) ?: return@forEachIndexed
                out.getOrPut(decl.groupValues[1]) { mutableSetOf() }.addAll(listOf(i, match.groupValues[1]))
            }
        }
        return out
    }

    private companion object {
        /** Arguments with these names are shown to the user wherever they appear. */
        val NAMED = listOf(
            "label", "title", "placeholder", "contentDescription", "supportingText", "caption", "text", "subtitle",
            "hint", "message", "description", "blockedText",
        )
        val BUILDER_CALLS = setOf("setContentTitle", "setContentText", "setSubText", "setTicker", "setBigContentTitle")
        val CALL = Regex("""(?<!\w)([A-Za-z]\w*)\s*\(""")
        val NAMED_ARG = Regex("""^([A-Za-z]\w*)\s*=(?!=)""")
        val NAMED_ANYWHERE = Regex("""(?<![\w.])(?:${NAMED.joinToString("|")})\s*=(?!=)""")
        val DECLARATION = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?([A-Z]\w*)\s*\(""")
        val STRING_PARAM = Regex("""^(?:vararg\s+)?(\w+)\s*:\s*String\??\s*(?:=.*)?$""", RegexOption.DOT_MATCHES_ALL)

        val IDENT_BEFORE = Regex("""([A-Za-z_][\w.]*)\s*$""")

        /** Calls whose string arguments are for developers: logs, assertions, thrown errors. */
        val DEVELOPER_CALLS = setOf(
            "error", "require", "check", "requireNotNull", "checkNotNull", "assert", "println", "TODO", "Log.d", "Log.i",
            "Log.w", "Log.e", "Log.v", "IllegalStateException", "IllegalArgumentException", "UnsupportedOperationException",
            "NoSuchElementException", "AssertionError", "RuntimeException", "Exception",
            // SessionScreen.launchGuarded(label) { ... }: the string is the log label of a failed action, never shown.
            "launchGuarded",
        )
        val DATE_PATTERN = Regex("""[dMyEHhmsaLwSZzXx'.:,/ \-]+""")

        /** Two or more words once `${}` and escapes are removed, and not a date pattern. */
        fun isProse(literal: String): Boolean {
            val words = SourceScan.TEMPLATE.replace(literal, " ").replace(Regex("""\\[ntr"'$\\]"""), " ").trim()
            if (!words.contains(' ') || !Regex("""[A-Za-z]{2,}""").containsMatchIn(words)) return false
            return !DATE_PATTERN.matches(words)
        }

        fun HAS_TEXT(literal: String): Boolean =
            SourceScan.TEMPLATE.replace(literal, " ").replace(Regex("""\\[ntr"'$\\]"""), " ").any { it in 'A'..'Z' || it in 'a'..'z' }
    }
}
