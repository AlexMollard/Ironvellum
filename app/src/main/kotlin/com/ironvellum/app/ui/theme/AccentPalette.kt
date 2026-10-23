package com.ironvellum.app.ui.theme

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import kotlin.math.roundToInt

// The Vellum presets (option B), each hex the sRGB of the OKLCH in its comment.
private const val LIGHT_FOREST = 0xFF208046.toInt() // oklch(.53 .125 152)
private const val LIGHT_OXBLOOD = 0xFFA03E43.toInt() // oklch(.50 .13 20)
private const val LIGHT_INK_BLUE = 0xFF2E64A6.toInt() // oklch(.50 .12 255)
private const val LIGHT_COPPER = 0xFFA34D16.toInt() // oklch(.52 .13 48)

// shortcut: the approved gold (oklch .56 .13 68, #A56300) reads 3.95:1 on the page and gold is text as well as a
// fill; the same hue and chroma at L .528 is the smallest drop that reaches 4.5:1. Back to .56 for a fill-only gold.
private const val LIGHT_GOLD = 0xFF9B5900.toInt() // oklch(.528 .13 68)
private const val LIGHT_TEAL = 0xFF007176.toInt() // oklch(.50 .085 200), Copper's reward: copper sits too near gold

/** The colours the Vellum presets saved before the rebuild, and what replaced each. */
private val LEGACY_LIGHT = mapOf(
    0xFF2F6E4F.toInt() to LIGHT_FOREST,
    0xFF8E2F2B.toInt() to LIGHT_OXBLOOD,
    0xFF2F4C7A.toInt() to LIGHT_INK_BLUE,
    0xFFA9542A.toInt() to LIGHT_COPPER,
    0xFF8A5F00.toInt() to LIGHT_GOLD,
    0xFF1B6A72.toInt() to LIGHT_TEAL,
)

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
        val LightDefault = AccentPalette(LIGHT_FOREST, LIGHT_GOLD)

        /**
         * A Vellum colour saved before the 2026-10-23 rebuild, as the preset colour that replaced it; anything else
         * (a custom hex) is returned as it was. Keeps a saved preset selected after the update.
         */
        fun fromLegacyLight(colour: Int): Int = LEGACY_LIGHT[colour] ?: colour
    }
}

data class AccentPreset(@StringRes private val nameRes: Int, val palette: AccentPalette) {
    /** The preset's name as shown. */
    val name: String get() = Words.text(UiText.res(nameRes))
}

object AccentPresets {
    /** The dark presets. */
    val entries = listOf(
        AccentPreset(R.string.accent_emerald_gold, AccentPalette.Default),
        AccentPreset(R.string.accent_sapphire_amber, AccentPalette(0xFF60A5FA.toInt(), 0xFFFBBF24.toInt())),
        AccentPreset(R.string.accent_violet_rose, AccentPalette(0xFFA78BFA.toInt(), 0xFFFB7185.toInt())),
        AccentPreset(R.string.accent_ice_copper, AccentPalette(0xFF67E8F9.toInt(), 0xFFE6A07C.toInt())),
    )

    /**
     * The Vellum (light) presets, rebuilt as option B. Each reward colour reads 4.5:1 on the page: gold for the three
     * that sit apart from it, and a teal for Copper, whose orange-brown (hue 48) sits 20 degrees from the gold's
     * (hue 68) and would read as the same colour.
     */
    val light = listOf(
        AccentPreset(R.string.accent_forest_gold, AccentPalette.LightDefault),
        AccentPreset(R.string.accent_oxblood_gold, AccentPalette(LIGHT_OXBLOOD, LIGHT_GOLD)),
        AccentPreset(R.string.accent_ink_gold, AccentPalette(LIGHT_INK_BLUE, LIGHT_GOLD)),
        AccentPreset(R.string.accent_copper_teal, AccentPalette(LIGHT_COPPER, LIGHT_TEAL)),
    )

    fun forTheme(light: Boolean) = if (light) this.light else entries
}

/** The roles one accent pair plays on Vellum paper: the button fill, the text shade, the today-box tint and its border. */
internal class LightAccent(val fill: Int, val text: Int, val tint: Int, val border: Int, val reward: Int)

private val LIGHT_PAGE = LightPaper.Abyss.toArgb()
private val LIGHT_PANEL = LightPaper.Vault.toArgb()
private val LIGHT_INK = LightPaper.Ink.toArgb()

/**
 * Text shade, tint and tint border of each light preset, by the preset's fill, in the accent's own hue: the text
 * shade is about 0.07 darker than the fill (forest's is the approved green, oklch .46 .11), the tint is oklch
 * (.92 .04 h) and the border (.74 .08 h).
 */
private val LIGHT_SHADES = mapOf(
    LIGHT_FOREST to intArrayOf(0xFF176938.toInt(), 0xFFCFEED6.toInt(), 0xFF85BA92.toInt()),
    LIGHT_OXBLOOD to intArrayOf(0xFF833035.toInt(), 0xFFFEDBDA.toInt(), 0xFFD99796.toInt()),
    LIGHT_INK_BLUE to intArrayOf(0xFF225088.toInt(), 0xFFD3E7FF.toInt(), 0xFF89AEDD.toInt()),
    LIGHT_COPPER to intArrayOf(0xFF863D0E.toInt(), 0xFFFCDDCE.toInt(), 0xFFD59C7F.toInt()),
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
    // A preset's fill is exact (forest's reads 4.08:1 on the page by design: it is a fill, and its text is the shade
    // below); only a custom colour is deepened until it reads.
    val fill = if (shades != null) primary else deepenForLight(primary)
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

/**
 * The accent a notification tints its icon with, resolved from the stored [mode] and the phone's dark state as it is
 * now, not from the process-global theme flag, which stays stale when the phone flips with no activity open.
 */
fun notificationAccent(mode: ThemeMode, systemDark: Boolean, dark: AccentPalette, light: AccentPalette): Int =
    if (mode.isLight(systemDark)) light.lightAccent().fill else dark.primary
