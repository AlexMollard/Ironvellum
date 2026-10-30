package com.ironvellum.app.domain

import java.util.Locale

/**
 * The one rule for typed numbers, so a field behaves the same on every screen
 * and on every device locale.
 *
 * The convention is the Australian one: "." is the decimal point, "," is only
 * ever a display thousands separator. Some keyboards offer a comma and no point
 * (their numpad follows the phone's region), so a typed "," is read as ".".
 *
 * Nothing here consults [Locale.getDefault]: "1,5" and "1.5" mean the same
 * thing on a German phone as on an Australian one. Typed text is cleaned to a "."; showing a
 * number is [NumberText]'s job, in the app's language.
 *
 * Pair with `decimalKeyboard()` / `wholeKeyboard()` in `ui/components`, which
 * pick the soft keyboard; this object cleans what arrives and reads it back.
 */
object DecimalInput {

    /** Room for "9999999.99"-sized entries unless a field says otherwise. */
    const val DEFAULT_MAX_LENGTH = 8

    /**
     * Cleans a decimal field's text as it is typed or pasted. Keeps ASCII digits
     * and at most one separator, written as ".", with at most [maxDecimals]
     * places and [maxLength] characters overall.
     *
     * A second separator ends the entry ("1..2" becomes "1."), a leading
     * separator gains its zero (".5" becomes "0.5"), and leading zeros collapse
     * ("007" becomes "7"). Stray characters, spaces included, are dropped.
     */
    fun sanitize(raw: String, maxDecimals: Int, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        val whole = StringBuilder()
        val fraction = StringBuilder()
        var pointSeen = false
        for (c in raw) {
            when {
                c in '0'..'9' ->
                    if (!pointSeen) whole.append(c) else if (fraction.length < maxDecimals) fraction.append(c)
                c == '.' || c == ',' -> {
                    if (pointSeen || maxDecimals <= 0) break
                    pointSeen = true
                }
            }
        }
        val integer = if (whole.isEmpty()) (if (pointSeen) "0" else "") else whole.toString().trimStart('0').ifEmpty { "0" }
        return (if (pointSeen) "$integer.$fraction" else integer).take(maxLength)
    }

    /** Cleans a whole-number field's text: ASCII digits only, at most [maxDigits], no leading zeros. */
    fun sanitizeWhole(raw: String, maxDigits: Int): String =
        raw.filter { it in '0'..'9' }.take(maxDigits).let { digits ->
            if (digits.length > 1) digits.trimStart('0').ifEmpty { "0" } else digits
        }

    /**
     * Cleans a one-time code: ASCII digits only, at most [maxDigits], and leading
     * zeros kept. A code is an identifier, not a number, so "012345" stays
     * "012345" and is never parsed.
     */
    fun sanitizeCode(raw: String, maxDigits: Int): String =
        raw.filter { it in '0'..'9' }.take(maxDigits)

    private val DECIMAL = Regex("""([0-9]+[.,]?[0-9]*|[.,][0-9]+)""")
    private val WHOLE = Regex("""[0-9]{1,9}""")

    /**
     * Reads a typed decimal strictly: digits with at most one "." or ",",
     * nothing else (no sign, no exponent, no "NaN"). "5." and ".5" read as 5.0
     * and 0.5 so a half-typed entry still has a value; "." alone, "" and
     * "1..2" are null.
     */
    fun parse(raw: String): Double? {
        val text = raw.trim()
        if (!DECIMAL.matches(text)) return null
        return text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Reads a typed whole number: 1 to 9 ASCII digits, nothing else. */
    fun parseWhole(raw: String): Int? {
        val text = raw.trim()
        return if (WHOLE.matches(text)) text.toInt() else null
    }
}

/**
 * [String.format] in the app's language, for screen text: "%.1f" prints "82.5" in English and "82,5" in German, "%,d"
 * prints "1,234", "1.234" or "1 234" (narrow no-break space, French). The locale is read at the call, so it is the
 * per-app language ([Locale.getDefault] follows it on Android) and a screen formatted after a language switch is right.
 *
 * Only for text a person reads. A number that is stored, exported, synced or parsed back uses [fmtRoot].
 */
fun String.fmt(vararg args: Any?): String = String.format(Locale.getDefault(), this, *args)

/** [fmt] for a given [locale] (a shared text already carries the one it was built for). */
fun String.fmtIn(locale: Locale, vararg args: Any?): String = String.format(locale, this, *args)

/** [String.format] pinned to [Locale.ROOT]: "82.5" and "1,234" whatever the language. For data, never for screen text. */
fun String.fmtRoot(vararg args: Any?): String = String.format(Locale.ROOT, this, *args)

/** The numbers a person reads, in the app's language. Field text and stored values keep a ".": see [DecimalInput]. */
object NumberText {
    /** [value] to [places] decimals: "82.5" or "82,5". */
    fun decimal(value: Double, places: Int, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.${places}f", value)

    /** [decimal] with trailing zeros and a bare separator dropped: "8.75", "225", never "225.00" or "225,". */
    fun trimmed(value: Double, maxPlaces: Int, locale: Locale = Locale.getDefault()): String {
        val text = decimal(value, maxPlaces, locale)
        val separator = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return if (separator in text) text.trimEnd('0').trimEnd(separator) else text
    }

    /** A whole count with the language's thousands separator: "2,000", "2.000", "2 000" (French, narrow no-break space). */
    fun grouped(value: Long, locale: Locale = Locale.getDefault()): String = String.format(locale, "%,d", value)
}
