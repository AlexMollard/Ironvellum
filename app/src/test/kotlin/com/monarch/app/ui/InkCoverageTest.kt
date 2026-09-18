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
            // Sigil.kt used to be listed here for the procedural crest's
            // gradient fills and bevel highlights. The crests are drawn art
            // now, that composable is deleted, and the file has no ruled call
            // left - so it is absent rather than exempt.
            // The trend series itself. Its weight and alpha already breathe per
            // segment; the coordinates must stay exact or the chart misreports
            // the user's own measurements.
            "TrendChart.kt" to sortedMapOf("drawLine" to 1),
            // Drifting motes in the idle scene: breathing alpha fills.
            "IdleScreen.kt" to sortedMapOf("drawCircle" to 1),
        )
    }
}
