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
 * thing on a German phone as on an Australian one.
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
 * [String.format] pinned to [Locale.ROOT], so "%.1f" prints "82.5" and "%,d"
 * prints "1,234" whatever the phone's region. Plain `"%.1f".format(x)` uses the
 * device locale and prints "82,5" on a European one.
 */
fun String.fmt(vararg args: Any?): String = String.format(Locale.ROOT, this, *args)
