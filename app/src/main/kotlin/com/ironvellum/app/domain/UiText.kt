package com.ironvellum.app.domain

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Text for a screen, described rather than built: a string resource (or a pair of them) plus its arguments. Domain
 * code returns this instead of a `String`, so the words stay in `res/values/strings.xml`, follow the language and
 * the Themed names switch, and still run on a plain JVM: it holds `R.string.x` ints (generated constants, no
 * `android.*`) and is turned into text at the edge, by `asString()` in `ui/UiTextAndroid.kt` on a device and by
 * `DiskStrings` in unit tests. docs/STRINGS.md is the developer guide.
 *
 * The plain and themed wording of one sentence are two whole strings (`key` and `key__t`), never one string with the
 * concept word swapped in, so a translator writes each as a sentence. [Themed] holds the pair; it shows the themed
 * side only when the Themed names switch is on and the language's themed strings are complete ([StringSource.themedComplete]).
 * User data (a routine's name, a typed note) goes in as [Raw] or as an argument, and is never translated.
 */
sealed interface UiText {
    /** `getString(id, *args)`. An argument that is itself a [UiText] is resolved first. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** `getQuantityString(id, count, *args)`; [args] defaults to the count, for `%d` in the text. */
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /** One sentence in two wordings: [plain] always, [themed] when the themed names are showing. */
    data class Themed(val plain: UiText, val themed: UiText) : UiText

    /** Text that is data, not copy: shown as it is. */
    data class Raw(val value: String) : UiText

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())

        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText =
            Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())

        /** `key` and `key__t` with the same arguments. */
        fun themed(@StringRes plain: Int, @StringRes themed: Int, vararg args: Any): UiText =
            Themed(Res(plain, args.toList()), Res(themed, args.toList()))

        /** The plural form of [themed]: both sides take the same count and arguments. */
        fun themedPlural(@PluralsRes plain: Int, @PluralsRes themed: Int, count: Int, vararg args: Any): UiText {
            val all = if (args.isEmpty()) listOf<Any>(count) else args.toList()
            return Themed(Plural(plain, count, all), Plural(themed, count, all))
        }
    }
}

/** Where strings come from: a device's resources, or `strings.xml` read from disk in a JVM test. */
interface StringSource {
    fun string(@StringRes id: Int, args: List<Any>): String

    fun plural(@PluralsRes id: Int, count: Int, args: List<Any>): String

    /**
     * True when this language writes every `key__t` the default does (res/values/bools.xml, one override for each
     * translation that is not finished). False hides the Themed names switch and keeps the plain strings.
     */
    val themedComplete: Boolean
}

/** The text for [source]; [themed] is the Themed names switch, which a language with incomplete themed strings ignores. */
fun UiText.resolve(source: StringSource, themed: Boolean): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> source.string(id, args.map { it.resolveArg(source, themed) })
    is UiText.Plural -> source.plural(id, count, args.map { it.resolveArg(source, themed) })
    is UiText.Themed -> (if (themed && source.themedComplete) this.themed else plain).resolve(source, themed)
}

private fun Any.resolveArg(source: StringSource, themed: Boolean): Any =
    if (this is UiText) resolve(source, themed) else this
