package com.ironvellum.app.ui.settings

import com.ironvellum.app.data.cloud.ProbeResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudUiTest {
    private val tested = CloudUi(probe = ProbeResult.Ready(3), testedUrl = "https://a.supabase.co", testedKey = "key-a")

    @Test
    fun `save is allowed only for the exact pair that tested ready`() {
        assertTrue(tested.canSave("https://a.supabase.co", "key-a"))
        assertTrue(tested.canSave("  https://a.supabase.co ", " key-a "))
    }

    @Test
    fun `editing the url or key after a test makes the result stale`() {
        assertFalse(tested.canSave("https://b.supabase.co", "key-a"))
        assertFalse(tested.canSave("https://a.supabase.co", "key-b"))
        assertNull(tested.probeFor("https://b.supabase.co", "key-a"))
    }

    @Test
    fun `a result that is not ready never enables save, and no test means no save`() {
        val bad = CloudUi(probe = ProbeResult.BadKey, testedUrl = "u", testedKey = "k")
        assertFalse(bad.canSave("u", "k"))
        assertFalse(CloudUi().canSave("", ""))
    }
}