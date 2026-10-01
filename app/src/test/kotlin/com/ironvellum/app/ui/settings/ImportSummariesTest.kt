package com.ironvellum.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSummariesTest {
    @Test
    fun `one of each reads singular`() {
        assertEquals(
            "restored 1 rite · 1 sealed trial · 1 set · 1 reading · 1 deed · 1 Journal attempt · 1 health day",
            SettingsSummaries.archiveRestored(1, 1, 1, 1, 1, 1, 1),
        )
        assertEquals(
            "Imported 1 trial · 1 set · +20 XP · 0 already in your Chronicle",
            SettingsSummaries.csvImported(1, 1, 20, 0),
        )
    }

    @Test
    fun `zero and many read plural`() {
        assertEquals(
            "Imported 0 trials · 2 sets · +0 XP · 3 already in your Chronicle",
            SettingsSummaries.csvImported(0, 2, 0, 3),
        )
    }
}