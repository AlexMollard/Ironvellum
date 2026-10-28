package com.ironvellum.app.data.cloud

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * supabase/hosted/ holds the patches the owner pastes into the LIVE project by
 * hand, and the baseline is the source of truth they copy from. Nothing else
 * connects the two: a definition changed in the baseline and left out of the
 * next patch ships green and breaks on the project (edited_at and a gen_random_bytes
 * search_path slip both did). So this compares them mechanically.
 *
 * Every function and view a patch defines must match the baseline's text
 * (comments and whitespace aside), judged by the LAST patch to define it. The
 * backend gate applies the newest patch on top of the baseline for real.
 */
class HostedPatchTest {

    /** The order the owner applies them in. A patch not listed here fails the test below. */
    private val applied = listOf(
        "2026-10-01-inbox.sql",
        "2026-10-01-circles.sql",
        "2026-10-02-release.sql",
        "2026-10-09-crests.sql",
        "2026-10-23-web-viewer.sql",
        "2026-10-28-web-snapshot-cap.sql",
        "2026-10-28-web-snapshot-hardening.sql",
    )

    private val root: File = run {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "supabase/migrations").isDirectory) dir = dir.parentFile
        requireNotNull(dir) { "supabase/ not found from ${File("").absolutePath}" }
    }

    private fun sql(path: String) = File(root, path).readText()

    /** `name(args)` or `view:name` to its definition, comments stripped and whitespace collapsed. */
    private fun definitions(text: String): Map<String, String> {
        val code = text.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
        val out = linkedMapOf<String, String>()
        val function = Regex(
            """create or replace function\s+(?:public\.)?(\w+)\s*\((.*?)\)(.*?)\$\$;""",
            RegexOption.DOT_MATCHES_ALL,
        )
        for (m in function.findAll(code)) {
            out["${m.groupValues[1]}(${squash(m.groupValues[2])})"] = squash(m.value)
        }
        val view = Regex("""create or replace view\s+(?:public\.)?(\w+)(.*?);""", RegexOption.DOT_MATCHES_ALL)
        for (m in view.findAll(code)) out["view:${m.groupValues[1]}"] = squash(m.value)
        return out
    }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    @Test
    fun `every hosted patch is registered and declares itself idempotent`() {
        val onDisk = File(root, "supabase/hosted").listFiles { f -> f.extension == "sql" }!!.map { it.name }.sorted()
        assertEquals("a patch in supabase/hosted/ is missing from HostedPatchTest.applied", applied.sorted(), onDisk)
        for (name in applied) {
            assertTrue("$name does not say it is idempotent", Regex("idempotent", RegexOption.IGNORE_CASE).containsMatchIn(sql("supabase/hosted/$name")))
        }
    }

    @Test
    fun `what the patches define is what the baseline defines`() {
        val baseline = definitions(sql("supabase/migrations/0001_baseline.sql"))
        assertTrue("the baseline scan found almost nothing; the regex is broken", baseline.size > 40)
        val live = linkedMapOf<String, Pair<String, String>>() // key to (patch, text), last patch wins
        for (name in applied) {
            for ((key, text) in definitions(sql("supabase/hosted/$name"))) live[key] = name to text
        }
        assertTrue("the patch scan found almost nothing; the regex is broken", live.size > 20)
        val drift = live.mapNotNull { (key, patchAndText) ->
            val (patch, text) = patchAndText
            when {
                key !in baseline -> "$key in $patch is not in the baseline"
                baseline.getValue(key) != text -> "$key in $patch differs from the baseline"
                else -> null
            }
        }
        assertEquals("A hosted patch has drifted from supabase/migrations/0001_baseline.sql:\n" + drift.joinToString("\n"), 0, drift.size)
    }

    @Test
    fun `the release patch carries the column and the feed that read edited_at`() {
        val release = sql("supabase/hosted/2026-10-02-release.sql")
        assertTrue("add column if not exists edited_at" in release.lowercase())
        assertTrue("s.edited_at" in release)
    }

    @Test
    fun `the crests patch carries schema 29, the column and every surface that reads it`() {
        val crests = sql("supabase/hosted/2026-10-09-crests.sql")
        val beacon = Regex("""schema_version\(\) returns int\s+language sql stable as \$\$ select (\d+) \$\$""")
        assertEquals(29, beacon.find(crests)!!.groupValues[1].toInt())
        assertTrue("add column if not exists current_crest_id" in crests.lowercase())
        // Four views and the circle's two mentions (the key and the column it reads).
        assertEquals(6, Regex("current_crest_id").findAll(crests.substringAfter("2. the boards")).count())
    }

    @Test
    fun `the web viewer patch carries the beacon and every new table`() {
        val patch = sql("supabase/hosted/2026-10-23-web-viewer.sql")
        val baseline = sql("supabase/migrations/0001_baseline.sql")
        val beacon = Regex("""schema_version\(\) returns int\s+language sql stable as \$\$ select (\d+) \$\$""")
        assertEquals(30, beacon.find(patch)!!.groupValues[1].toInt())
        assertEquals(NEEDED_SCHEMA_VERSION, beacon.find(baseline)!!.groupValues[1].toInt())
        val tables = listOf("session_set_details", "imported_sessions", "imported_session_sets", "web_snapshots")
        val create = Regex("""create table if not exists\s+(?:public\.)?(\w+)""", RegexOption.IGNORE_CASE)
        assertEquals(tables.toSet(), create.findAll(patch).map { it.groupValues[1] }.toSet())
        assertEquals(4, create.findAll(patch).count())
        for (table in tables) {
            assertTrue("baseline missing $table", create.findAll(baseline).any { it.groupValues[1] == table })
        }
    }

    @Test
    fun `the snapshot cap patch carries the current schema beacon`() {
        val patch = sql("supabase/hosted/2026-10-28-web-snapshot-cap.sql")
        val beacon = Regex("""schema_version\(\) returns int\s+language sql stable as \$\$ select (\d+) \$\$""")
        assertEquals(NEEDED_SCHEMA_VERSION, beacon.find(patch)!!.groupValues[1].toInt())
        assertTrue("web_snapshots_size_cap" in patch)
    }

    @Test
    fun `the hardening patch is the baseline's snapshot cap, delete path and init-plan policies`() {
        val patch = sql("supabase/hosted/2026-10-28-web-snapshot-hardening.sql")
        val baseline = sql("supabase/migrations/0001_baseline.sql")
        val code = patch.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
        // No beacon, no function: the build that expects schema 31 works before and after it.
        assertTrue("create or replace function" !in code.lowercase())
        // The cap is added NOT VALID, validated, and only when the definition is not already this one.
        assertTrue("not valid" in code.lowercase() && "validate constraint web_snapshots_size_cap" in code)
        assertTrue("octet_length(snapshot::text) <= 8 * 1024 * 1024" in code && "octet_length(snapshot::text) <= 8 * 1024 * 1024" in baseline)
        assertTrue("like '%(8 * 1024)%'" in code && "like '%(8 * 1024)%'" in baseline)
        // The owner-only delete exists in both, and every policy of the four tables is the one-evaluation form.
        for (text in listOf(code, baseline)) {
            assertTrue("create policy web_snapshots_delete" in text)
            assertTrue("grant select, insert, update, delete on web_snapshots to authenticated" in text)
        }
        val policies = Regex("""create policy (session_set_details_owner|imported_sessions_owner|imported_session_sets_owner|web_snapshots_\w+)[\s\S]*?;""")
        val webPolicies = policies.findAll(code).toList()
        assertEquals(7, webPolicies.size)
        val bare = Regex("""(?<!select )auth\.uid\(\)""")
        for (policy in webPolicies) {
            assertTrue("${policy.groupValues[1]} calls auth.uid() per row", !bare.containsMatchIn(policy.value))
            val inBaseline = policies.findAll(baseline).first { it.groupValues[1] == policy.groupValues[1] }.value
            assertEquals("${policy.groupValues[1]} differs from the baseline", squash(inBaseline), squash(policy.value))
        }
    }
}
