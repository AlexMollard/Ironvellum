package com.ironvellum.app

import com.ironvellum.app.domain.StringSource
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.resolve
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** One `<string>` or `<plurals>` of a resource file: [text] for a string, [items] (quantity to text) for plurals. */
internal class ResEntry(val name: String, val text: String?, val items: Map<String, String>?, val translatable: Boolean) {
    val isPlural: Boolean get() = items != null
}

/** Reads an Android values file the way aapt2 does (escapes, quotes, whitespace); [raw] values are unprocessed. */
internal object StringsXml {
    val MAIN = File("src/main/res/values/strings.xml")

    /** The entries of [file] with values unescaped, in file order. */
    fun read(file: File = MAIN): List<ResEntry> = parse(file, unescaped = true)

    /** The entries of [file] with values exactly as written (the lint rules read these). */
    fun readRaw(file: File = MAIN): List<ResEntry> = parse(file, unescaped = false)

    private fun parse(file: File, unescaped: Boolean): List<ResEntry> {
        require(file.isFile) { "$file not found from ${File("").absolutePath}" }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        fun value(e: Element) = e.textContent.let { if (unescaped) unescape(it) else it }
        val out = mutableListOf<ResEntry>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val translatable = e.getAttribute("translatable") != "false"
            when (e.tagName) {
                "string" -> out += ResEntry(e.getAttribute("name"), value(e), null, translatable)
                "plurals" -> {
                    val items = LinkedHashMap<String, String>()
                    val kids = e.getElementsByTagName("item")
                    for (k in 0 until kids.length) {
                        val item = kids.item(k) as Element
                        items[item.getAttribute("quantity")] = value(item)
                    }
                    out += ResEntry(e.getAttribute("name"), null, items, translatable)
                }
            }
        }
        return out
    }

    /** The device's rule: text is formatted only when arguments are given, so `%%` means `%` only then. */
    fun format(text: String, args: List<Any>): String =
        if (args.isEmpty()) text else String.format(Locale.US, text, *args.toTypedArray())

    /** English plural rules: `one` for exactly 1, `other` for the rest. */
    fun quantity(name: String, items: Map<String, String>, count: Int): String =
        (if (count == 1) items["one"] else null) ?: items["other"] ?: error("plurals $name has no `other`")

    /**
     * aapt2's reading of a string value: whitespace collapses and trims unless inside double quotes; a backslash
     * escapes (`\'`, `\"`, `\@`, `\?`, `\\`, `\n`, `\t`, `\uXXXX`).
     */
    fun unescape(raw: String): String {
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\\' && i + 1 < raw.length -> {
                    val n = raw[i + 1]
                    when (n) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'u' -> {
                            sb.append(raw.substring(i + 2, i + 6).toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(n)
                    }
                    i += 2
                }
                c == '"' -> { quoted = !quoted; i++ }
                c.isWhitespace() && !quoted -> {
                    while (i < raw.length && raw[i].isWhitespace()) i++
                    sb.append(' ')
                }
                else -> { sb.append(c); i++ }
            }
        }
        return if (quoted) sb.toString() else sb.toString().trim()
    }
}

/**
 * [StringSource] over `src/main/res/values/strings.xml` on disk, so a plain JUnit test resolves a [UiText] to the
 * same English text the app shows (no Robolectric, no Context). Ids map to names by reflecting over the generated
 * `R.string` and `R.plurals`. Like the device it formats only when arguments are given (so `%%` means `%` only
 * then) and uses `one` for a count of 1, `other` otherwise: English plural rules.
 *
 * Use [DefaultStrings] for the English text and `UiText.plainText()` / `UiText.themedText()`; pass [themedComplete] = false
 * to see what a language without themed strings shows.
 */
internal class DiskStrings(
    file: File = StringsXml.MAIN,
    override val themedComplete: Boolean = true,
) : StringSource {
    private val entries = StringsXml.read(file).associateBy { it.name }
    private val stringNames = idsToNames("string")
    private val pluralNames = idsToNames("plurals")

    override fun string(id: Int, args: List<Any>): String {
        val name = stringNames[id] ?: error("string id $id is not an R.string")
        val text = entries[name]?.text ?: error("R.string.$name is not in strings.xml")
        return StringsXml.format(text, args)
    }

    override fun plural(id: Int, count: Int, args: List<Any>): String {
        val name = pluralNames[id] ?: error("plural id $id is not an R.plurals")
        val items = entries[name]?.items ?: error("R.plurals.$name is not in strings.xml")
        return StringsXml.format(StringsXml.quantity(name, items, count), args)
    }

    /** `R.<type>` fields by id; R.plurals does not exist until the first plurals resource does. */
    private fun idsToNames(type: String): Map<Int, String> {
        val holder = runCatching { Class.forName("com.ironvellum.app.R\$$type") }.getOrNull() ?: return emptyMap()
        return holder.fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.getInt(null) to it.name }
    }
}

/** The English `strings.xml` as the app shows it. */
internal val DefaultStrings: DiskStrings by lazy { DiskStrings() }

/** The plain wording of this text, as the app shows it with the Themed names switch off. */
internal fun UiText.plainText(source: StringSource = DefaultStrings): String = resolve(source, themed = false)

/** The wording with the Themed names switch on. */
internal fun UiText.themedText(source: StringSource = DefaultStrings): String = resolve(source, themed = true)
