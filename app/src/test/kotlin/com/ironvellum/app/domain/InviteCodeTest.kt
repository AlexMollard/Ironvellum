package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The invite code is generated in SQL (create_warband) and validated in Kotlin
 * (isValidInviteCode, the join input filter). Two copies of one alphabet, so
 * the pair gets a guard: a client alphabet narrower than the server's would
 * refuse codes the server legitimately drew; a wider one would send a code the
 * server's check bounces as a confusing failure on the lifter's screen.
 */
class InviteCodeTest {

    private val baseline: String = run {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "supabase/migrations").isDirectory) dir = dir.parentFile
        val found = dir?.let { File(it, "supabase/migrations/0001_baseline.sql") }
        requireNotNull(found) { "supabase/migrations/0001_baseline.sql not found from ${File("").absolutePath}" }
        found.readText()
    }

    @Test
    fun `the kotlin alphabet is the server's alphabet`() {
        val sql = Regex("""alphabet text := '([^']+)'""").find(baseline)?.groupValues?.get(1)
        requireNotNull(sql) { "no alphabet literal in create_warband — the parser, not the schema, is broken" }
        assertEquals(
            "InviteCodeAlphabet drifted from create_warband's draw alphabet",
            sql,
            InviteCodeAlphabet,
        )
        // Exactly 31 glyphs: the regex character class on warbands.invite_code
        // must describe the same set. The class is written with ranges
        // (2-9A-HJ-NP-Z), so expand them before comparing.
        val check = Regex("""invite_code ~ '\^\[([^\]]+)\]\{\d+\}\$'""").find(baseline)?.groupValues?.get(1)
        requireNotNull(check) { "no invite_code check in the baseline" }
        val allowed = buildSet {
            var i = 0
            while (i < check.length) {
                if (i + 2 < check.length && check[i + 1] == '-') {
                    for (c in check[i]..check[i + 2]) add(c)
                    i += 3
                } else {
                    add(check[i])
                    i++
                }
            }
        }
        // Every drawable glyph must pass the SQL check; the check itself is
        // the contract's literal class (2-9A-HJ-NP-Z), which admits a stray L
        // the draw alphabet never produces — a subset, not an equality.
        assertTrue(
            "the kotlin alphabet draws glyph(s) the SQL check refuses",
            InviteCodeAlphabet.toSet().intersect(allowed).size == InviteCodeAlphabet.length,
        )
        assertEquals(31, InviteCodeAlphabet.length)
    }

    @Test
    fun `the alphabet excludes the confusable glyphs`() {
        for (c in "0O1IL") {
            assertFalse("ambiguous glyph $c must not be drawable", c in InviteCodeAlphabet)
        }
    }

    @Test
    fun `isValidInviteCode accepts exactly eight alphabet glyphs`() {
        assertTrue(isValidInviteCode("K7M2PQ4X"))
        // Lowercase is folded by the caller; raw lowercase is not a valid code.
        assertFalse(isValidInviteCode("k7m2pq4x"))
        assertFalse(isValidInviteCode("K7M2PQ4")) // too short
        assertFalse(isValidInviteCode("K7M2PQ4XX")) // too long
        assertFalse(isValidInviteCode("K7M2PQ0X")) // 0 is not drawable
        assertFalse(isValidInviteCode("K7M2PQIX")) // I is not drawable
        assertFalse(isValidInviteCode(""))
    }
}
