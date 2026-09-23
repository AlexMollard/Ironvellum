package com.ironvellum.app.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backend a lifter's training is sent to is decided by [resolveCloudConfig]
 * and described by [googleConfiguredFor] / [probeResultFor]. A regression in
 * the resolution order silently points real training at the wrong project;
 * a misclassified probe tells a lifter "ready" about a backend that cannot
 * hold their data. All three are pure functions so the whole matrix is
 * checked here, with no Android types.
 */
class CloudConfigTest {

    private val DEFAULT_URL = "https://default.supabase.co"
    private val DEFAULT_KEY = "default-anon-key"

    @Test
    fun `an override beats the build default`() {
        val config = resolveCloudConfig(
            "https://mine.supabase.co", "mine-key", DEFAULT_URL, DEFAULT_KEY,
        )
        assertEquals(CloudConfig("https://mine.supabase.co", "mine-key", isDefault = false), config)
    }

    @Test
    fun `the build default applies when no override is stored`() {
        val config = resolveCloudConfig(null, null, DEFAULT_URL, DEFAULT_KEY)
        assertEquals(CloudConfig(DEFAULT_URL, DEFAULT_KEY, isDefault = true), config)
    }

    @Test
    fun `a build with no credentials resolves to null`() {
        assertNull(resolveCloudConfig(null, null, "", ""))
        assertNull(resolveCloudConfig(null, null, "   ", DEFAULT_KEY))
    }

    @Test
    fun `a blank override half is ignored, not half-applied`() {
        // A half-typed override must fall through to the default, never
        // combine the custom URL with the default key — that mismatch would
        // 401 every call and read as "the cloud is broken".
        assertEquals(
            CloudConfig(DEFAULT_URL, DEFAULT_KEY, isDefault = true),
            resolveCloudConfig("", "mine-key", DEFAULT_URL, DEFAULT_KEY),
        )
        assertEquals(
            CloudConfig(DEFAULT_URL, DEFAULT_KEY, isDefault = true),
            resolveCloudConfig("https://mine.supabase.co", "  ", DEFAULT_URL, DEFAULT_KEY),
        )
    }

    @Test
    fun `override values are trimmed`() {
        assertEquals(
            CloudConfig("https://mine.supabase.co", "mine-key", isDefault = false),
            resolveCloudConfig(" https://mine.supabase.co ", " mine-key ", DEFAULT_URL, DEFAULT_KEY),
        )
    }

    @Test
    fun `google sign-in exists only on the default backend`() {
        val clientId = "apps.googleusercontent.com"
        assertTrue(googleConfiguredFor(CloudConfig(DEFAULT_URL, DEFAULT_KEY, isDefault = true), clientId))
        // The OAuth web client id belongs to the maintainer's Google project,
        // so a custom backend offers email sign-in only.
        assertFalse(googleConfiguredFor(CloudConfig("https://mine.supabase.co", "k", isDefault = false), clientId))
        assertFalse(googleConfiguredFor(null, clientId))
        // No client id compiled in: even the default cannot offer Google.
        assertFalse(googleConfiguredFor(CloudConfig(DEFAULT_URL, DEFAULT_KEY, isDefault = true), ""))
    }

    @Test
    fun `a 401 probe reads as a bad key`() {
        assertEquals(ProbeResult.BadKey, probeResultFor(401, null, null))
    }

    @Test
    fun `a missing function reads as no schema`() {
        assertEquals(ProbeResult.NoSchema, probeResultFor(404, "PGRST202", null))
    }

    @Test
    fun `a request that never completed reads as unreachable`() {
        assertEquals(ProbeResult.Unreachable, probeResultFor(null, null, null))
        assertEquals(ProbeResult.Unreachable, probeResultFor(503, null, null))
    }

    @Test
    fun `the beacon number decides ready versus outdated`() {
        assertEquals(ProbeResult.Ready(14), probeResultFor(200, null, "14"))
        assertEquals(ProbeResult.Ready(15), probeResultFor(200, null, "15"))
        assertEquals(ProbeResult.Outdated(9, 14), probeResultFor(200, null, "9"))
        // PostgREST may quote scalars in the body; whitespace and quotes go.
        assertEquals(ProbeResult.Ready(14), probeResultFor(200, null, "  \"14\" "))
    }

    @Test
    fun `a 200 with an unparseable beacon is not ready`() {
        assertEquals(ProbeResult.NoSchema, probeResultFor(200, null, "not-a-number"))
        assertEquals(ProbeResult.NoSchema, probeResultFor(200, null, ""))
    }
}
