package com.ironvellum.app.ui.settings

import com.ironvellum.app.data.Words
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ImportSummariesTest {
    @Test
    fun `one of each reads singular`() {
        assertEquals(
            "restored 1 rite · 1 sealed trial · 1 set · 1 reading · 1 deed · 1 Journal attempt · 1 health day",
            Words.text(SettingsSummaries.archiveRestored(1, 1, 1, 1, 1, 1, 1)),
        )
    }

    @Test
    fun `picked provider failures are reported without swallowing cancellation`() = runBlocking {
        assertEquals("", readPickedText { throw SecurityException("permission revoked") })
        assertEquals("", readPickedText { throw IOException("provider failed") })
        assertEquals("", readPickedText { null })
        assertEquals("archive", readPickedText { "archive".byteInputStream() })
        val cancelled = CancellationException("cancelled")
        try {
            readPickedText { throw cancelled }
            fail("cancellation must propagate")
        } catch (actual: CancellationException) {
            assertEquals(cancelled.message, actual.message)
        }
    }
}