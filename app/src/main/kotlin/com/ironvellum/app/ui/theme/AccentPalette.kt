package com.ironvellum.app.ui.theme

import kotlin.math.pow
import kotlin.math.roundToInt

/** Device-local accent roles; paper, ink and danger retain their own colours. */
data class AccentPalette(
    val primary: Int = 0xFF34D399.toInt(),
    val secondary: Int = 0xFFF2C14E.toInt(),
) {
    val bright: Int get() = if (primary == Default.primary) 0xFF6EE7B7.toInt() else mixAccent(primary, 0xFFFFFFFF.toInt(), 0.28)
    /** The deeper end of the accent's ramp: the default's #25986E, else the accent mixed 28% toward black. */
    val deep: Int get() = if (primary == Default.primary) 0xFF25986E.toInt() else mixAccent(primary, 0xFF000000.toInt(), 0.28)
    val muted: Int get() = if (primary == Default.primary) 0xFF6FAE8C.toInt() else mixAccent(primary, 0xFFA3A099.toInt(), 0.5)
    companion object { val Default = AccentPalette() }
}

data class AccentPreset(val name: String, val palette: AccentPalette)

object AccentPresets {
    val entries = listOf(
        AccentPreset("Emerald & Gold", AccentPalette.Default),
        AccentPreset("Sapphire & Amber", AccentPalette(0xFF60A5FA.toInt(), 0xFFFBBF24.toInt())),
        AccentPreset("Violet & Rose", AccentPalette(0xFFA78BFA.toInt(), 0xFFFB7185.toInt())),
        AccentPreset("Ice & Copper", AccentPalette(0xFF67E8F9.toInt(), 0xFFE6A07C.toInt())),
    )
}

fun parseAccentHex(input: String): Int? {
    val hex = input.trim().removePrefix("#")
    if (!hex.matches(Regex("[0-9a-fA-F]{6}"))) return null
    return hex.toInt(16) or 0xFF000000.toInt()
}

fun accentHex(colour: Int): String = "#%06X".format(colour and 0xFFFFFF)

/** Pick whichever foreground has the higher WCAG contrast. */
fun accentForeground(colour: Int): Int {
    fun linear(shift: Int): Double {
        val channel = ((colour ushr shift) and 255) / 255.0
        return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    }
    val luminance = 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    return if ((luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}

private fun mixAccent(a: Int, b: Int, amount: Double): Int {
    fun channel(shift: Int): Int = ((((a ushr shift) and 255) * (1 - amount) + ((b ushr shift) and 255) * amount).roundToInt()) shl shift
    return 0xFF000000.toInt() or channel(16) or channel(8) or channel(0)
}
