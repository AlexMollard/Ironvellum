package com.ironvellum.app.ui.settings

import com.ironvellum.app.data.Words
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportSummariesTest {
    @Test
    fun `one of each reads singular`() {
        assertEquals(
            "restored 1 rite · 1 sealed trial · 1 set · 1 reading · 1 deed · 1 Journal attempt · 1 health day",
            Words.text(SettingsSummaries.archiveRestored(1, 1, 1, 1, 1, 1, 1)),
        )
    }
}