package com.ironvellum.app

/** One string literal in Kotlin source: [start] is the opening quote, [end] the index after the closing one. */
internal data class Lit(val line: Int, val start: Int, val end: Int, val text: String)

/** [literals] in source order, and [masked]: the source with comments blanked and literal bodies filled with `_`, same offsets. */
internal class ScanResult(val literals: List<Lit>, val masked: String)

/**
 * The Kotlin source scanner shared by the source-scanning tests (GlossaryTest, HardcodedUiLiteralTest): string
 * literals with `${…}` kept verbatim, comments and char literals skipped.
 */
internal object SourceScan {
    val TEMPLATE = Regex("""\$\{[^{}]*(\{[^{}]*\}[^{}]*)*\}|\$\w+""")

    /** Every string literal in [src] with its starting line; comments and char literals skipped. */
    fun literals(src: String): List<Pair<Int, String>> = scan(src).literals.map { it.line to it.text }

    fun scan(src: String): ScanResult {
        val out = mutableListOf<Lit>()
        val masked = src.toCharArray()
        var i = 0
        var line = 1
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\n' -> { line++; i++ }
                src.startsWith("//", i) -> {
                    val end = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                    for (k in i until end) if (masked[k] != '\r') masked[k] = ' '
                    i = end
                }
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                    line += src.substring(i, end).count { it == '\n' }
                    for (k in i until end) if (masked[k] != '\n' && masked[k] != '\r') masked[k] = ' '
                    i = end
                }
                c == '\'' -> {
                    val close = if (src.getOrNull(i + 1) == '\\') i + 3 else i + 2
                    val end = if (src.getOrNull(close) == '\'') close + 1 else i + 1
                    for (k in i + 1 until end - 1) masked[k] = '_'
                    i = end
                }
                c == '"' -> {
                    val triple = src.startsWith("\"\"\"", i)
                    val open = if (triple) 3 else 1
                    val (end, text) = readString(src, i + open, triple)
                    out += Lit(line, i, end, text)
                    for (k in i + open until end - open) if (masked[k] != '\n' && masked[k] != '\r') masked[k] = '_'
                    line += text.count { it == '\n' }
                    i = end
                }
                else -> i++
            }
        }
        return ScanResult(out, String(masked))
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
