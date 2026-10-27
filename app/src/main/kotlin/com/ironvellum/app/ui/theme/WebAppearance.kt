package com.ironvellum.app.ui.theme

import com.ironvellum.app.domain.WebSnapshot

/** Resolves the same accent roles and button foregrounds used by the phone. */
internal object WebAppearance {
    fun accentSet(palette: AccentPalette, light: Boolean, presets: List<AccentPreset>): WebSnapshot.AccentSet {
        val vellum = palette.lightAccent()
        val fill = if (light) vellum.fill else palette.primary
        val reward = if (light) vellum.reward else palette.secondary
        return WebSnapshot.AccentSet(
            preset = presets.firstOrNull { it.palette == palette }?.id,
            primary = accentHex(palette.primary),
            secondary = accentHex(palette.secondary),
            bright = accentHex(palette.bright),
            deep = accentHex(palette.deep),
            muted = accentHex(palette.muted),
            panelTint = accentHex(palette.panelTint),
            panelTintBorder = accentHex(palette.panelTintBorder),
            fill = accentHex(fill),
            text = accentHex(if (light) vellum.text else palette.primary),
            tint = accentHex(if (light) vellum.tint else palette.panelTint),
            border = accentHex(if (light) vellum.border else palette.panelTintBorder),
            reward = accentHex(reward),
            onPrimary = accentHex(if (light) lightAccentForeground(fill) else accentForeground(fill)),
            onSecondary = accentHex(if (light) lightAccentForeground(reward) else accentForeground(reward)),
        )
    }

    fun of(mode: ThemeMode, dark: AccentPalette, vellum: AccentPalette, themed: Boolean): WebSnapshot.Appearance =
        WebSnapshot.Appearance(
            mode = when (mode) {
                ThemeMode.SYSTEM -> "system"
                ThemeMode.DARK -> "dark"
                ThemeMode.LIGHT -> "light"
            },
            themed = themed,
            dark = accentSet(dark, light = false, presets = AccentPresets.entries),
            light = accentSet(vellum, light = true, presets = AccentPresets.light),
        )
}
