package com.ironvellum.app.ui.theme

import org.junit.Assert.*
import org.junit.Test

class WebAppearanceTest {
    private fun checkPreset(preset: AccentPreset, light: Boolean) {
        val palette = preset.palette
        val set = WebAppearance.accentSet(palette, light, AccentPresets.forTheme(light))
        val vellum = palette.lightAccent()
        val fill = if (light) vellum.fill else palette.primary
        val reward = if (light) vellum.reward else palette.secondary
        assertEquals(preset.id, set.preset)
        assertEquals(accentHex(palette.primary), set.primary)
        assertEquals(accentHex(palette.secondary), set.secondary)
        assertEquals(accentHex(palette.bright), set.bright)
        assertEquals(accentHex(palette.deep), set.deep)
        assertEquals(accentHex(palette.muted), set.muted)
        assertEquals(accentHex(palette.panelTint), set.panelTint)
        assertEquals(accentHex(palette.panelTintBorder), set.panelTintBorder)
        assertEquals(accentHex(fill), set.fill)
        assertEquals(accentHex(if (light) vellum.text else palette.primary), set.text)
        assertEquals(accentHex(if (light) vellum.tint else palette.panelTint), set.tint)
        assertEquals(accentHex(if (light) vellum.border else palette.panelTintBorder), set.border)
        assertEquals(accentHex(reward), set.reward)
        assertEquals(accentHex(if (light) lightAccentForeground(fill) else accentForeground(fill)), set.onPrimary)
        assertEquals(accentHex(if (light) lightAccentForeground(reward) else accentForeground(reward)), set.onSecondary)
    }

    @Test fun everyDarkPresetRoundTrips() = AccentPresets.entries.forEach { checkPreset(it, false) }
    @Test fun everyVellumPresetRoundTrips() = AccentPresets.light.forEach { checkPreset(it, true) }

    @Test fun presetIdsAreUniqueAndStable() {
        val ids = (AccentPresets.entries + AccentPresets.light).map { it.id }
        assertEquals(listOf("emerald_gold", "sapphire_amber", "violet_rose", "ice_copper",
            "forest_gold", "oxblood_gold", "ink_gold", "copper_teal"), ids)
        assertEquals(8, ids.toSet().size)
    }

    @Test fun customAccentHasNullPreset() {
        val custom = AccentPalette(0xFF123456.toInt(), 0xFFABCDEF.toInt())
        assertNull(WebAppearance.accentSet(custom, false, AccentPresets.entries).preset)
        assertNull(WebAppearance.accentSet(custom, true, AccentPresets.light).preset)
        // Matching only one colour is not a preset.
        assertNull(WebAppearance.accentSet(AccentPalette(AccentPalette.Default.primary, custom.secondary), false, AccentPresets.entries).preset)
    }

    @Test fun modeAndThemedFlagAreCarried() {
        for ((mode, expected) in listOf(ThemeMode.SYSTEM to "system", ThemeMode.DARK to "dark", ThemeMode.LIGHT to "light")) {
            for (themed in listOf(false, true)) {
                val appearance = WebAppearance.of(mode, AccentPalette.Default, AccentPalette.LightDefault, themed)
                assertEquals(expected, appearance.mode)
                assertEquals(themed, appearance.themed)
                assertEquals("emerald_gold", appearance.dark.preset)
                assertEquals("forest_gold", appearance.light.preset)
            }
        }
    }
}
