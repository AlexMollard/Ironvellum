package com.ironvellum.app.data.cloud

import com.ironvellum.app.data.Words
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cloud.explain is the LAST translation step before a sentence reaches the
 * lifter; every cloud path in AccountRepository/CloudSync wraps errors through
 * it. A regression here shows raw SDK text (or leaked API internals) in the UI.
 */
class CloudErrorExplainTest {

    @Test
    fun `an already human-readable message is passed through, not double-wrapped`() {
        // Failure paths wrap their own precise message (e.g. sign-in wording)
        // in IllegalStateException before explain() sees it; re-wording it
        // would erase the one actionable sentence the player gets.
        assertEquals(
            "Wrong email or password",
            Words.text(Cloud.explain(IllegalStateException("Wrong email or password"))),
        )
    }

    @Test
    fun `the codes for a missing table, column or function mean the cloud is not set up`() {
        for (code in listOf("42703", "42P01", "PGRST202", "PGRST204", "PGRST205")) assertTrue(code, Cloud.isNotSetUpCode(code))
        assertFalse(Cloud.isNotSetUpCode("23505"))
        assertFalse(Cloud.isNotSetUpCode(null))
        // The wording of a message is not a signal: only the code is.
        assertFalse(Cloud.isNotSetUp(IllegalStateException("The shadow board is not live yet")))
    }

    @Test
    fun `a network failure reads as an offline problem`() {
        assertEquals(
            "Could not reach the cloud — check your connection",
            Words.text(Cloud.explain(HttpRequestException("connect timed out", HttpRequestBuilder()))),
        )
    }

    @Test
    fun `an unknown error surfaces the generic message and never the raw text`() {
        // Credential Manager and the auth SDK leak API internals in exception
        // text; the generic branch must drop them wholesale, not interpolate.
        val explained = Words.text(Cloud.explain(RuntimeException("Bearer eyJhbGciOiJIUzI1NiJ9.secret-token")))
        assertEquals("The Ledger stumbled — try again in a moment", explained)
        assertFalse(explained.contains("Bearer"))
    }
}
