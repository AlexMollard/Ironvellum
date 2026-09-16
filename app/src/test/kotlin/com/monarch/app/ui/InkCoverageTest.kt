package com.monarch.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hand-drawn look is a product requirement, and it has regressed five times
 * — always the same way: a new screen reaches for `CircleShape` or `.border()`,
 * or a stock Material component brings its own rounded container. None of that
 * fails a build and none of it fails a behavioural test, so it only ever
 * surfaced by eyeballing screenshots on a phone.
 *
 * These are source scans, which is unusual for a test, and they are justified
 * narrowly: the contract being defended is "no machined geometry reaches the
 * screen", the violation is textual, and the alternative is a human noticing a
 * stray rectangle. Every exception is listed with the reason it stays.
 */
class InkCoverageTest {

    private val uiRoot = File("src/main/kotlin/com/monarch/app/ui")

    private val sources: List<File> =
        uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** A scan that silently finds nothing is worse than no scan at all. */
    @Test
    fun `the scan actually sees the ui tree`() {
        assertTrue(
            "ui sources not found from ${File(".").absolutePath}; the scans below would pass vacuously",
            sources.size > 20,
        )
    }

    /**
     * Every dialog must re-pin the text scale.
     *
     * A Compose `Dialog` hosts its content in its own window and composition,
     * which re-provides the platform `LocalDensity` — so the fixed scale that
     * `MonarchTheme` installs for the activity does NOT reach it. Measured:
     * with the app pinned, the weigh-in dialog still grew from 409px to 756px
     * between system 1.0x and 2.0x. Same justification as the scans above: the
     * violation is textual and otherwise only visible by changing a system
     * setting and looking.
     */
    @Test
    fun `every dialog re-pins the text scale`() {
        val offenders = sources.filter { file ->
            val text = file.readText()
            val opens = Regex("""\n\s*Dialog\(""").findAll(text).count()
            opens > 0 && !text.contains("FixedTextScale")
        }
        assertEquals(
            "these files open a Dialog without FixedTextScale, so its content " +
                "follows the system font while every screen behind it does not: " +
                offenders.map { it.name },
            emptyList<String>(),
            offenders.map { it.name },
        )
    }

    @Test
    fun `no geometric shape classes reach the ui`() {
        // These are the exact classes the ink shapes replaced. `MaterialTheme.shapes`
        // is already wired to InkEdgeShape, so a bare one here is always a regression.
        val banned = listOf(
            Regex("""RoundedCornerShape\("""),
            Regex("""CutCornerShape\("""),
            Regex("""(?<!\w)RectangleShape"""),
            // InkCircleShape is the drawn one; the bare Material class is not.
            Regex("""(?<!Ink)(?<!\w)CircleShape"""),
        )
        assertEquals(emptyList<String>(), offenders(banned, exempt = emptyMap()))
    }

    @Test
    fun `no stock borders reach the ui`() {
        // Modifier.border traces the shape with a constant-width stroke: a
        // perfectly even outline around an otherwise hand-drawn panel.
        assertEquals(
            emptyList<String>(),
            offenders(listOf(Regex("""\.border\(""")), exempt = emptyMap()),
        )
    }

    @Test
    fun `ruled drawing stays inside the ink primitives`() {
        val tokens = listOf("drawLine", "drawArc", "drawCircle")
        val actual = sortedMapOf<String, Map<String, Int>>()
        for (file in sources) {
            val counts = mutableMapOf<String, Int>()
            file.readTextLines().forEach { line ->
                if (line.isComment()) return@forEach
                tokens.forEach { token ->
                    if (line.contains("$token(")) counts[token] = (counts[token] ?: 0) + 1
                }
            }
            if (counts.isNotEmpty()) actual[file.name] = counts.toSortedMap()
        }
        // Exact counts, not a blanket file exemption: a NEW ruled call in an
        // already-exempt file still moves the number and fails here.
        assertEquals(RULED_EXEMPTIONS, actual)
    }

    @Test
    fun `material components that ignore the theme pass an explicit shape`() {
        // Each of these carries its own *Defaults shape and silently ignores
        // MaterialTheme.shapes — the class that hid five separate regressions.
        val components = listOf(
            "AlertDialog", "OutlinedTextField", "TextField", "OutlinedButton",
            "Button", "ExtendedFloatingActionButton", "FloatingActionButton", "Card",
        )
        val missing = mutableListOf<String>()
        for (file in sources) {
            val lines = file.readTextLines()
            lines.forEachIndexed { index, line ->
                val component = components.firstOrNull { name ->
                    Regex("""(?<!\w)$name\(""").containsMatchIn(line) && !line.isComment()
                } ?: return@forEachIndexed
                if (!callPassesShape(lines, index)) {
                    missing += "${file.name}:${index + 1} $component without shape ="
                }
            }
        }
        assertEquals(emptyList<String>(), missing)
    }

    /**
     * True when the call opening at [start] names a `shape` argument before it
     * closes. Bounded by the call's own closing line so a `shape =` belonging to
     * a later sibling call can never satisfy an earlier one.
     */
    private fun callPassesShape(lines: List<String>, start: Int): Boolean {
        var depth = 0
        var index = start
        while (index < lines.size) {
            val line = lines[index]
            if (!line.isComment()) {
                if (Regex("""(?<!\w)shape\s*=""").containsMatchIn(line)) return true
                depth += line.count { it == '(' } - line.count { it == ')' }
            }
            // The opening line always pushes at least one level, so a return to
            // zero means the argument list is finished.
            if (index > start && depth <= 0) return false
            if (index == start && depth <= 0) return false
            index++
        }
        return false
    }

    private fun offenders(banned: List<Regex>, exempt: Map<String, Set<String>>): List<String> {
        val found = mutableListOf<String>()
        for (file in sources) {
            val allowed = exempt[file.name].orEmpty()
            file.readTextLines().forEachIndexed { index, line ->
                if (line.isComment()) return@forEachIndexed
                for (pattern in banned) {
                    val match = pattern.find(line) ?: continue
                    if (match.value.trimEnd('(') in allowed) continue
                    found += "${file.name}:${index + 1} ${match.value}"
                }
            }
        }
        return found
    }

    private fun File.readTextLines(): List<String> = readText().lines()

    private fun String.isComment(): Boolean =
        trimStart().startsWith("//") || trimStart().startsWith("*") || trimStart().startsWith("/*")

    private companion object {
        /**
         * Every ruled draw call left in the UI, with the reason it stays.
         * Counts are exact: adding a ruled line anywhere — even in a file that
         * already appears here — fails the scan and has to be justified.
         */
        val RULED_EXEMPTIONS: Map<String, Map<String, Int>> = sortedMapOf(
            // Ink.kt IS the brush: these calls are how every other surface is drawn.
            "Ink.kt" to sortedMapOf("drawArc" to 1, "drawCircle" to 2, "drawLine" to 8),
            // Ambient washes behind the essence counter: low-alpha gradient fills,
            // not geometry. Brushed, they read as dirt on the screen.
            "ShadowBackdrop.kt" to sortedMapOf("drawCircle" to 2),
            // Crest art: gradient body fills plus sub-pixel bevel highlights
            // (alpha under 0.3, width under 0.02 of the unit). They are shading;
            // the rings, spokes and dots around them are all ink.
            "Sigil.kt" to sortedMapOf("drawCircle" to 5, "drawLine" to 4),
            // The trend series itself. Its weight and alpha already breathe per
            // segment; the coordinates must stay exact or the chart misreports
            // the user's own measurements.
            "TrendChart.kt" to sortedMapOf("drawLine" to 1),
            // Drifting motes in the idle scene: breathing alpha fills.
            "IdleScreen.kt" to sortedMapOf("drawCircle" to 1),
        )
    }
}
