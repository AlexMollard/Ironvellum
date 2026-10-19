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
    /**
     * The accent tint and its border as opaque colours over the dark panel (#171715), for RemoteViews, which cannot
     * blend. The app draws them as the primary at 10% / 35% over the panel; the default keeps the widget's
     * hand-tuned #10241C / #1F5C46.
     */
    val panelTint: Int get() = if (primary == Default.primary) 0xFF10241C.toInt() else mixAccent(DARK_PANEL, primary, 0.10)
    val panelTintBorder: Int get() = if (primary == Default.primary) 0xFF1F5C46.toInt() else mixAccent(DARK_PANEL, primary, 0.35)
    companion object {
        private const val DARK_PANEL = 0xFF171715.toInt()
        val Default = AccentPalette()
        /** Forest and gold, the Vellum default. */
        val LightDefault = AccentPalette(0xFF2F6E4F.toInt(), 0xFF8A5F00.toInt())
    }
}

data class AccentPreset(val name: String, val palette: AccentPalette)

object AccentPresets {
    /** The dark presets. */
    val entries = listOf(
        AccentPreset("Emerald & Gold", AccentPalette.Default),
        AccentPreset("Sapphire & Amber", AccentPalette(0xFF60A5FA.toInt(), 0xFFFBBF24.toInt())),
        AccentPreset("Violet & Rose", AccentPalette(0xFFA78BFA.toInt(), 0xFFFB7185.toInt())),
        AccentPreset("Ice & Copper", AccentPalette(0xFF67E8F9.toInt(), 0xFFE6A07C.toInt())),
    )

    /**
     * The Vellum (light) presets, from the approved mockups. Each reward colour is a deep shade that reads 4.5:1 on
     * the page: gold for the three that sit apart from it, and a teal for Copper, whose orange-brown (hue 20) sits
     * 21 degrees from the gold's (hue 41) and would read as the same colour.
     */
    val light = listOf(
        AccentPreset("Forest & Gold", AccentPalette.LightDefault),
        AccentPreset("Oxblood & Gold", AccentPalette(0xFF8E2F2B.toInt(), 0xFF8A5F00.toInt())),
        AccentPreset("Ink & Gold", AccentPalette(0xFF2F4C7A.toInt(), 0xFF8A5F00.toInt())),
        AccentPreset("Copper & Teal", AccentPalette(0xFFA9542A.toInt(), 0xFF1B6A72.toInt())),
    )

    fun forTheme(light: Boolean) = if (light) this.light else entries
}

/** The roles one accent pair plays on Vellum paper: the button fill, the text shade, the today-box tint and its border. */
internal class LightAccent(val fill: Int, val text: Int, val tint: Int, val border: Int, val reward: Int)

private val LIGHT_PAGE = 0xFFF3EEE3.toInt()
private val LIGHT_PANEL = 0xFFFBF8F2.toInt()
private val LIGHT_INK = 0xFF1F1C17.toInt()

/** Deep text shade, tint and tint border the mockups give each light preset, by the preset's fill. */
private val LIGHT_SHADES = mapOf(
    0xFF2F6E4F.toInt() to intArrayOf(0xFF255A40.toInt(), 0xFFDCE8DF.toInt(), 0xFF8FB39E.toInt()),
    0xFF8E2F2B.toInt() to intArrayOf(0xFF7A2622.toInt(), 0xFFF0DCD6.toInt(), 0xFFC99A92.toInt()),
    0xFF2F4C7A.toInt() to intArrayOf(0xFF263F66.toInt(), 0xFFDCE3EE.toInt(), 0xFF95A8C6.toInt()),
    0xFFA9542A.toInt() to intArrayOf(0xFF8C4220.toInt(), 0xFFF3E0D2.toInt(), 0xFFD3A487.toInt()),
)

/** WCAG contrast of two opaque colours. */
fun contrastRatio(a: Int, b: Int): Double {
    val la = relativeLuminance(a) + 0.05
    val lb = relativeLuminance(b) + 0.05
    return maxOf(la, lb) / minOf(la, lb)
}

private fun relativeLuminance(colour: Int): Double {
    fun linear(shift: Int): Double {
        val channel = ((colour ushr shift) and 255) / 255.0
        return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
}

/**
 * A custom colour made usable as text on Vellum: mixed toward black until it reaches 4.5:1 on the page (the darker
 * of the two paper tones, so the panel, and the light button text on a fill of it, clear it too). A colour that
 * already does is returned as it is.
 */
fun deepenForLight(colour: Int): Int {
    var out = colour or 0xFF000000.toInt()
    var step = 0
    while (contrastRatio(out, LIGHT_PAGE) < 4.5 && step < 40) {
        out = mixAccent(out, 0xFF000000.toInt(), 0.06)
        step++
    }
    return out
}

internal fun AccentPalette.lightAccent(): LightAccent {
    val shades = LIGHT_SHADES[primary]
    val fill = deepenForLight(primary)
    val text = shades?.get(0) ?: mixAccent(fill, 0xFF000000.toInt(), 0.2)
    return LightAccent(
        fill = fill,
        text = text,
        tint = shades?.get(1) ?: mixAccent(fill, LIGHT_PANEL, 0.85),
        border = shades?.get(2) ?: mixAccent(fill, LIGHT_PANEL, 0.5),
        reward = deepenForLight(secondary),
    )
}

fun parseAccentHex(input: String): Int? {
    val hex = input.trim().removePrefix("#")
    if (!hex.matches(Regex("[0-9a-fA-F]{6}"))) return null
    return hex.toInt(16) or 0xFF000000.toInt()
}

fun accentHex(colour: Int): String = "#%06X".format(colour and 0xFFFFFF)

/**
 * Pick whichever foreground has the higher WCAG contrast. Dark paper uses black and white; Vellum passes its own ink
 * and panel tones, so text on a fill is never a pure white or black.
 */
fun accentForeground(colour: Int, darkText: Int = 0xFF000000.toInt(), lightText: Int = 0xFFFFFFFF.toInt()): Int =
    if (contrastRatio(colour, darkText) >= contrastRatio(colour, lightText)) darkText else lightText

/** Text on a Vellum accent fill: the panel tone, or the ink when a pale custom fill needs it. */
fun lightAccentForeground(colour: Int): Int = accentForeground(colour, LIGHT_INK, LIGHT_PANEL)

internal fun mixAccent(a: Int, b: Int, amount: Double): Int {
    fun channel(shift: Int): Int = ((((a ushr shift) and 255) * (1 - amount) + ((b ushr shift) and 255) * amount).roundToInt()) shl shift
    return 0xFF000000.toInt() or channel(16) or channel(8) or channel(0)
}
