package com.ironvellum.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text token on the page and the panel, in both themes: 4.5:1 for body text and the accent text roles.
 * Bracket and Rune are structure lines, not text, and are held to nothing here.
 */
class ThemeContrastTest {
    private val savedLight = IronvellumColors.light
    private val savedDark = IronvellumColors.darkAccents
    private val savedLightAccents = IronvellumColors.lightAccents

    @After fun restore() {
        IronvellumColors.light = savedLight
        IronvellumColors.darkAccents = savedDark
        IronvellumColors.lightAccents = savedLightAccents
    }

    private fun ratio(fg: Color, bg: Color): Double = contrastRatio(fg.compositeOver(bg).toArgb(), bg.toArgb())

    private fun assertText(name: String, fg: Color, bg: Color, bgName: String, min: Double = 4.5) {
        val r = ratio(fg, bg)
        assertTrue("$name on $bgName is $r:1, needs $min", r >= min)
    }

    private fun checkPaper(label: String) {
        val c = IronvellumColors
        listOf("page" to c.Abyss, "panel" to c.Vault).forEach { (bgName, bg) ->
            assertText("$label Ink", c.Ink, bg, bgName)
            assertText("$label InkMuted", c.InkMuted, bg, bgName)
            assertText("$label DangerRed", c.DangerRed, bg, bgName)
            assertText("$label Caution", c.Caution, bg, bgName)
            assertText("$label Emerald", c.Emerald, bg, bgName)
            assertText("$label EmeraldBright", c.EmeraldBright, bg, bgName)
            assertText("$label SystemGreen", c.SystemGreen, bg, bgName)
            assertText("$label SovereignGold", c.SovereignGold, bg, bgName)
            RARITIES.forEach { (name, tint) -> assertText("$label rarity $name", tint, bg, bgName) }
        }
        assertText("$label InkMuted", c.InkMuted, c.VaultHigh, "raised paper")
        assertText("$label Ink", c.Ink, c.VaultHigh, "raised paper")
        assertText("$label SystemGreen", c.SystemGreen, c.VaultHigh, "raised paper", 4.0)
        assertText("$label UnheldName", c.UnheldName, c.PlateGround, "plate")
        assertText("$label UnheldNameBright", c.UnheldNameBright, c.Vault, "panel")
        assertText("$label OnEmerald", c.OnEmerald, c.Emerald, "accent fill")
        assertText("$label OnGold", c.OnGold, c.SovereignGold, "reward fill")
        assertText("$label OnGoldFill", c.OnGoldFill, Metal.Fabled.tone, "gold button", 4.5)
        // Text on the Today box's own ground, and on the accent button when it is disabled.
        assertText("$label SystemGreen", c.SystemGreen, c.AccentTint.compositeOver(c.Vault), "today box")
        assertText("$label Ink", c.Ink, c.AccentTint.compositeOver(c.Vault), "today box")
        // A disabled control is exempt from WCAG text contrast; it must still be visibly there.
        assertText("$label InkMuted", c.InkMuted, c.Rune, "disabled button", 3.0)
    }

    private val RARITIES get() = listOf(
        "Iron" to RarityTint.Iron, "Bronze" to RarityTint.Bronze, "Gold" to RarityTint.Gold, "Prismatic" to RarityTint.Prismatic,
    )

    @Test fun `dark theme text tokens clear 4_5 to 1 on page and panel`() {
        IronvellumColors.light = false
        AccentPresets.entries.forEach { preset ->
            IronvellumColors.darkAccents = preset.palette
            checkPaper("dark ${preset.name}")
        }
    }

    @Test fun `light theme text tokens clear 4_5 to 1 on page and panel for every preset`() {
        IronvellumColors.light = true
        AccentPresets.light.forEach { preset ->
            IronvellumColors.lightAccents = preset.palette
            checkPaper("light ${preset.name}")
        }
    }

    @Test fun `a custom hex is deepened until it reads on the page and its button text reads on it`() {
        IronvellumColors.light = true
        for (hue in 0 until 360 step 10) {
            for ((s, v) in listOf(1f to 1f, 0.5f to 1f, 1f to 0.6f, 0.2f to 0.95f, 0f to 1f, 0f to 0.5f)) {
                val colour = hsv(hue.toFloat(), s, v)
                IronvellumColors.lightAccents = AccentPalette(colour, colour)
                val fill = IronvellumColors.Emerald
                assertText("custom $hue/$s/$v as text on page", fill, IronvellumColors.Abyss, "page")
                assertText("custom $hue/$s/$v as text on panel", fill, IronvellumColors.Vault, "panel")
                assertText("custom $hue/$s/$v on-fill text", IronvellumColors.OnEmerald, fill, "fill")
                assertText("custom $hue/$s/$v SystemGreen", IronvellumColors.SystemGreen, IronvellumColors.Vault, "panel")
                assertText("custom $hue/$s/$v reward", IronvellumColors.SovereignGold, IronvellumColors.Vault, "panel")
                assertText("custom $hue/$s/$v SystemGreen on tint", IronvellumColors.SystemGreen, IronvellumColors.AccentTint, "tint")
            }
        }
    }

    @Test fun `a colour that already reads is not deepened`() {
        AccentPresets.light.forEach { assertEquals(it.palette.primary, deepenForLight(it.palette.primary)) }
        assertNotEquals(0xFFF2C14E.toInt(), deepenForLight(0xFFF2C14E.toInt()))
    }

    @Test fun `each light preset keeps its reward apart from its primary`() {
        AccentPresets.light.forEach { preset ->
            val p = hueOf(preset.palette.primary)
            val s = hueOf(preset.palette.secondary)
            val gap = minOf(Math.abs(p - s), 360 - Math.abs(p - s))
            assertTrue("${preset.name}: hue gap $gap", gap >= 35)
        }
        assertEquals(AccentPalette.LightDefault, AccentPresets.light[0].palette)
    }

    @Test fun `dark values are exactly what they were`() {
        IronvellumColors.light = false
        IronvellumColors.darkAccents = AccentPalette.Default
        val c = IronvellumColors
        assertEquals(0xFF0C0C0B.toInt(), c.Abyss.toArgb())
        assertEquals(0xFF171715.toInt(), c.Vault.toArgb())
        assertEquals(0xFF1E1E1B.toInt(), c.VaultHigh.toArgb())
        assertEquals(0xFFE8E8E4.toInt(), c.Ink.toArgb())
        assertEquals(0xFFA3A099.toInt(), c.InkMuted.toArgb())
        assertEquals(0xFF32302B.toInt(), c.Rune.toArgb())
        assertEquals(0xFF4A473F.toInt(), c.Bracket.toArgb())
        assertEquals(0xFFEF5350.toInt(), c.DangerRed.toArgb())
        assertEquals(0xFFE0B25A.toInt(), c.Caution.toArgb())
        assertEquals(0xFF34D399.toInt(), c.Emerald.toArgb())
        assertEquals(0xFF6EE7B7.toInt(), c.EmeraldBright.toArgb())
        assertEquals(0xFF25986E.toInt(), c.EmeraldDeep.toArgb())
        assertEquals(0xFF6FAE8C.toInt(), c.SystemGreen.toArgb())
        assertEquals(0xFFF2C14E.toInt(), c.SovereignGold.toArgb())
        assertEquals(0xFF8A8F96.toInt(), RarityTint.Iron.toArgb())
        assertEquals(0xFFC98B5B.toInt(), RarityTint.Bronze.toArgb())
        assertEquals(0xFFD6A94A.toInt(), RarityTint.Gold.toArgb())
        assertEquals(0xFFBFB0F7.toInt(), RarityTint.Prismatic.toArgb())
        assertEquals(0xFF121211.toInt(), c.PlateGround.toArgb())
        assertEquals(0xFF3A3935.toInt(), c.Silhouette.toArgb())
        assertEquals(0xFF151C19.toInt(), c.Wells.top.toArgb())
    }

    @Test fun `the art tool still finds the ink it tints with`() {
        // tools/art.py reads the first `val Ink = Color(0xFF......)` in Theme.kt: it must stay the dark ink.
        val theme = java.io.File("src/main/kotlin/com/ironvellum/app/ui/theme/Theme.kt").readText()
        val match = Regex("val Ink = Color\\(0xFF([0-9A-Fa-f]{6})\\)").find(theme)
        assertEquals("E8E8E4", match?.groupValues?.get(1))
    }

    private fun hsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - Math.abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return 0xFF000000.toInt() or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    private fun hueOf(colour: Int): Double {
        val r = ((colour shr 16) and 255) / 255.0
        val g = ((colour shr 8) and 255) / 255.0
        val b = (colour and 255) / 255.0
        val max = maxOf(r, g, b)
        val d = max - minOf(r, g, b)
        val h = when {
            d == 0.0 -> 0.0
            max == r -> ((g - b) / d) % 6
            max == g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        }
        return (h * 60 + 360) % 360
    }
}
