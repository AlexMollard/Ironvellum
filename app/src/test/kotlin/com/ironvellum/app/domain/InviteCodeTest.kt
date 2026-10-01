package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `a circle named Strength does not steal the code from the share text`() {
        // "STRENGTH" is eight glyphs of the alphabet, so a bare scan reads the
        // NAME as the code. The share text puts the name before the code.
        val share = "Join my Ironvellum circle Strength — code K7M2PQ4X"
        assertEquals("K7M2PQ4X", extractInviteCode(share))
        assertEquals("K7M2PQ4X", extractInviteCode("code: k7m2pq4x, circle Strength"))
        assertEquals("STRENGTH", extractInviteCode("Join my circle X — code Strength"))
    }

    @Test
    fun `without the word code only a token with a digit is taken`() {
        assertEquals("K7M2PQ4X", extractInviteCode("here you go K7M2PQ4X thanks"))
        // A bare eight-letter word is a name far more often than a code.
        assertNull(extractInviteCode("Strength"))
        assertNull(extractInviteCode("see you at the gym"))
        assertNull(extractInviteCode(""))
    }

    @Test
    fun `a code is never sliced out of a longer token`() {
        assertNull(extractInviteCode("K7M2PQ4XYZ"))
        assertNull(extractInviteCode("AK7M2PQ4X"))
        assertNull(extractInviteCode("K7M2PQ0X")) // 0 is not drawable
    }

    @Test
    fun `join statuses read as a success or a refusal in the app's words`() {
        assertEquals("joined", JoinStatus.parse("\"joined\"\n"))
        assertNull(JoinStatus.refusal("joined"))
        assertEquals("No circle answers to that code", JoinStatus.refusal("no_such_code"))
        assertEquals("That circle is full", JoinStatus.refusal("full"))
        assertTrue(JoinStatus.refusal("throttled")!!.startsWith("Too many code attempts"))
        // A status this build does not know is a refusal, never a quiet success.
        assertTrue(JoinStatus.refusal("something-new") != null)
    }

    @Test
    fun `the join statuses the client reads are the ones the server returns`() {
        // Every status string the client maps must be spelled in the join function.
        val join = Regex("""create or replace function public\.join_warband\(.*?\n\$\$;""", RegexOption.DOT_MATCHES_ALL)
            .find(baseline)?.value
        requireNotNull(join) { "join_warband not found in the baseline" }
        for (status in listOf("joined", "no_such_code", "full", "throttled")) {
            assertTrue("join_warband never returns '$status'", join.contains("return '$status'"))
        }
    }
}
