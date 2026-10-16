package com.ironvellum.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lint for the string resources (docs/STRINGS.md): the plain/themed pairing contract, placeholder discipline and
 * escaping, checked on every strings.xml under res (default and translations) so a translator's file is held to the same rules as the English one.
 * (GlossaryTest checks the vocabulary: plain values hold no themed words, themed values no retired ones.)
 */
class StringsXmlTest {

    private val res = File("src/main/res")

    private val files: List<File> =
        res.listFiles { f -> f.isDirectory && f.name == "values" || f.name.startsWith("values-") }.orEmpty()
            .map { File(it, "strings.xml") }.filter { it.isFile }

    private val english = StringsXml.readRaw()

    private fun allEntries(): List<Pair<File, ResEntry>> = files.flatMap { f -> StringsXml.readRaw(f).map { f to it } }

    private fun values(e: ResEntry): List<String> = e.text?.let { listOf(it) } ?: e.items!!.values.toList()

    /** `%1$s` -> ("1", 's'), `%s` -> (null, 's'); the `%%` escape is not a placeholder. */
    private fun placeholders(text: String): List<Pair<String?, Char>> =
        PLACEHOLDER.findAll(text.replace("%%", "")).map { it.groupValues[1].ifEmpty { null } to it.groupValues[2][0] }.toList()

    @Test
    fun `the scan sees the string files`() {
        assertTrue("no strings.xml under $res from ${File("").absolutePath}", files.isNotEmpty() && english.size >= 5)
    }

    @Test
    fun `key names are lower snake case and only a final __t marks a themed wording`() {
        val bad = allEntries().map { it.second.name }.filterNot { KEY.matches(it) }
        assertEquals("keys must be [a-z][a-z0-9_]* with at most one trailing __t: $bad", 0, bad.size)
    }

    @Test
    fun `every themed key has a plain twin of the same kind`() {
        val byName = english.associateBy { it.name }
        val bad = english.filter { it.name.endsWith("__t") }.filter {
            val plain = byName[it.name.removeSuffix("__t")]
            plain == null || plain.isPlural != it.isPlural
        }.map { it.name }
        assertEquals("themed keys without a plain twin of the same kind (string or plurals): $bad", 0, bad.size)
    }

    @Test
    fun `plain and themed twins take the same arguments`() {
        val byName = english.associateBy { it.name }
        val bad = mutableListOf<String>()
        for (themed in english.filter { it.name.endsWith("__t") }) {
            val plain = byName[themed.name.removeSuffix("__t")] ?: continue
            val a = signature(plain)
            val b = signature(themed)
            if (a != b) bad += "${themed.name}: plain takes $a, themed takes $b"
        }
        assertEquals("twins must carry the same placeholders (type and position):\n" + bad.joinToString("\n"), 0, bad.size)
    }

    private fun signature(e: ResEntry): List<Pair<String?, Char>> = values(e).flatMap { placeholders(it) }.distinct().sortedBy { "${it.first}${it.second}" }

    @Test
    fun `a concept word is never an interpolated placeholder`() {
        val bad = allEntries().filter { (_, e) -> values(e).any { '{' in it || '}' in it || "\${" in it } }
            .map { (f, e) -> "${f.parentFile.name}/${e.name}" }
        assertEquals(
            "no {concept} or \${...} placeholders: write plain and themed as two whole sentences (key and key__t): $bad",
            0,
            bad.size,
        )
    }

    @Test
    fun `apostrophes quotes and percent signs are escaped for aapt2`() {
        val bad = mutableListOf<String>()
        for ((f, e) in allEntries()) {
            val where = "${f.parentFile.name}/${e.name}"
            for (raw in values(e)) {
                if (UNESCAPED_APOSTROPHE.containsMatchIn(raw) && !(raw.startsWith("\"") && raw.endsWith("\""))) {
                    bad += "$where: apostrophe must be \\' -> $raw"
                }
                if (UNESCAPED_QUOTE.containsMatchIn(raw) && !(raw.startsWith("\"") && raw.endsWith("\""))) {
                    bad += "$where: double quote must be \\\" -> $raw"
                }
                if (raw.startsWith("@") || raw.startsWith("?")) bad += "$where: a leading @ or ? must be escaped -> $raw"
                val args = placeholders(raw)
                val stripped = raw.replace("%%", "").replace(PLACEHOLDER, "")
                if (args.isEmpty() && raw.contains("%%")) {
                    bad += "$where: no placeholders, so it is shown unformatted: write % not %% -> $raw"
                }
                if (args.isNotEmpty() && '%' in stripped) bad += "$where: a literal % beside placeholders must be %% -> $raw"
                if (args.size > 1 && args.any { it.first == null }) bad += "$where: two or more placeholders must be positional (%1\$s, %2\$s) -> $raw"
            }
        }
        assertEquals("string escaping:\n" + bad.joinToString("\n"), 0, bad.size)
    }

    @Test
    fun `plurals have an other form and only known quantities`() {
        val bad = allEntries().filter { it.second.isPlural }.filter { (_, e) ->
            val q = e.items!!.keys
            "other" !in q || !QUANTITIES.containsAll(q)
        }.map { "${it.first.parentFile.name}/${it.second.name}" }
        assertEquals("plurals need quantity=\"other\" and only zero/one/two/few/many/other: $bad", 0, bad.size)
    }

    @Test
    fun `a translation declares whether its themed strings are complete and means it`() {
        val themedKeys = english.map { it.name }.filter { it.endsWith("__t") }.toSet()
        val bad = mutableListOf<String>()
        for (file in files.filter { it.parentFile.name != "values" }) {
            val dir = file.parentFile
            val bools = File(dir, "bools.xml")
            val declared = if (bools.isFile) BOOL.find(bools.readText())?.groupValues?.get(1) else null
            if (declared == null) {
                bad += "${dir.name}: bools.xml must declare themed_strings_complete (true only when every key__t is translated)"
                continue
            }
            val have = StringsXml.readRaw(file).map { it.name }.toSet()
            val unknown = have - english.map { it.name }.toSet()
            if (unknown.isNotEmpty()) bad += "${dir.name}: keys the default file lacks: $unknown"
            if (declared == "true" && !have.containsAll(themedKeys)) {
                bad += "${dir.name}: themed_strings_complete is true but ${themedKeys - have} are untranslated"
            }
        }
        assertEquals("translations:\n" + bad.joinToString("\n"), 0, bad.size)
    }

    @Test
    fun `the lint rules catch what they claim to`() {
        assertEquals(listOf("1" to 's', "2" to 'd'), placeholders("%1\$s of %2\$d, 100%%").map { it.first to it.second })
        assertTrue(UNESCAPED_APOSTROPHE.containsMatchIn("don't"))
        assertTrue(!UNESCAPED_APOSTROPHE.containsMatchIn("don\\'t"))
        assertTrue(!KEY.matches("Bad_Key") && !KEY.matches("a__b") && KEY.matches("a_b__t"))
    }

    private companion object {
        val PLACEHOLDER = Regex("""%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])""")
        val KEY = Regex("""[a-z][a-z0-9]*(?:_[a-z0-9]+)*(?:__t)?""")
        val UNESCAPED_APOSTROPHE = Regex("""(?<!\\)'""")
        val UNESCAPED_QUOTE = Regex("""(?<!\\)"""")
        val QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")
        val BOOL = Regex("""name="themed_strings_complete"\s*>\s*(true|false)\s*<""")
    }
}
