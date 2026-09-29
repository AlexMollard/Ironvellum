package com.ironvellum.app.ui.titles

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatLoadTest {

    @Test
    fun `whole plate loads drop the decimal`() {
        assertEquals("20", formatLoad(20.0))
    }

    @Test
    fun `a widened float never prints its raw digits`() {
        // 0.1 + 0.2 is 0.30000000000000004 as a Double; the journal used to
        // print the raw value after "@".
        assertEquals("0.3", formatLoad(0.1 + 0.2))
        assertEquals("12.5", formatLoad(12.5))
    }
}
