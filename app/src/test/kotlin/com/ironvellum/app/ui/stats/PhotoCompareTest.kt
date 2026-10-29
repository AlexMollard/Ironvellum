package com.ironvellum.app.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoCompareTest {
    @Test fun `the divider starts at half`() = assertEquals(0.5f, DIVIDER_START, 0f)

    @Test fun `a nudge moves a tenth each way`() {
        assertEquals(0.6f, nudgedDivider(0.5f, 1), 1e-6f)
        assertEquals(0.4f, nudgedDivider(0.5f, -1), 1e-6f)
        // No float drift after several steps.
        assertEquals(0.8f, nudgedDivider(nudgedDivider(nudgedDivider(0.5f, 1), 1), 1), 1e-6f)
    }

    @Test fun `a nudge stops at either edge`() {
        assertEquals(1f, nudgedDivider(0.95f, 1), 0f)
        assertEquals(0f, nudgedDivider(0.05f, -1), 0f)
        assertEquals(1f, nudgedDivider(1f, 1), 0f)
    }
}
