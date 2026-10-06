package com.ironvellum.app.ui.theme

import org.junit.Assert.*
import org.junit.Test

class AccentPaletteTest {
    @Test fun `hex accepts opaque RGB and rejects malformed input`() {
        assertEquals(0xFF34D399.toInt(), parseAccentHex(" #34d399 "))
        assertEquals(0xFF000000.toInt(), parseAccentHex("000000"))
        listOf("", "#abc", "#12345678", "GG0000", "12 3456").forEach {
            assertNull(parseAccentHex(it))
        }
        assertEquals("#34D399", accentHex(0xFF34D399.toInt()))
    }
    @Test fun `default palette retains original shades`() {
        assertEquals(0xFF34D399.toInt(), AccentPalette.Default.primary)
        assertEquals(0xFFF2C14E.toInt(), AccentPalette.Default.secondary)
        assertEquals(0xFF6EE7B7.toInt(), AccentPalette.Default.bright)
        assertEquals(0xFF6FAE8C.toInt(), AccentPalette.Default.muted)
    }
    @Test fun `presets can be restored and custom shades follow the primary`() {
        val sapphire = AccentPresets.entries[1].palette
        assertNotEquals(AccentPalette.Default, sapphire)
        assertNotEquals(AccentPalette.Default.bright, sapphire.bright)
        assertNotEquals(AccentPalette.Default.muted, sapphire.muted)
        assertEquals(4, AccentPresets.entries.map { it.palette }.distinct().size)
    }
    @Test fun `foreground picks readable ink for dark and light accents`() {
        assertEquals(0xFFFFFFFF.toInt(), accentForeground(0xFF000000.toInt()))
        assertEquals(0xFF000000.toInt(), accentForeground(0xFFFFFFFF.toInt()))
        assertEquals(0xFF000000.toInt(), accentForeground(AccentPalette.Default.primary))
    }
}