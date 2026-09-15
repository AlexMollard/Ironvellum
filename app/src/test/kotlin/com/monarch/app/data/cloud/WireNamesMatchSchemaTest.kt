package com.monarch.app.data.cloud

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Dtos.kt states the rule that holds the whole cloud read path together: every
 * @SerialName must match its snake_case column exactly. Nothing enforced it.
 *
 * kotlinx does not fail on a mismatch — it decodes the default. A renamed
 * column or a typo therefore shows a leaderboard where every hunter has 0
 * essence and 0 XP: a wrong ranking presented as fact, with no error anywhere.
 *
 * This reads the migrations that actually define those relations and checks the
 * serial names against them, so drift fails here instead of on a user's screen.
 */
class WireNamesMatchSchemaTest {

    private val migrations: List<String> = run {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "supabase/migrations").isDirectory) dir = dir.parentFile
        val found = dir?.let { File(it, "supabase/migrations") }
        requireNotNull(found) { "supabase/migrations not found from ${File("").absolutePath}" }
        found.listFiles { f -> f.extension == "sql" }!!.sortedBy { it.name }.map { it.readText() }
    }

    /** Columns of a base table: the create, plus every later `add column`. */
    private fun tableColumns(table: String): Set<String> {
        val cols = mutableSetOf<String>()
        val create = Regex("""create table (?:if not exists )?$table\s*\(([\s\S]*?)\n\);""")
        // One `alter table` may carry several `add column` clauses (0002 adds
        // title and note in a single statement), so scan the whole statement.
        val alter = Regex("""alter table $table\b([\s\S]*?);""")
        val addColumn = Regex("""add column (?:if not exists )?(\w+)""")
        for (sql in migrations) {
            create.find(sql)?.groupValues?.get(1)?.lines()?.forEach { line ->
                val name = line.trim().substringBefore(' ').trim(',', '(')
                if (name.isNotEmpty() && name.first().isLetter() &&
                    name !in setOf("primary", "foreign", "unique", "check", "constraint")
                ) {
                    cols += name
                }
            }
            alter.findAll(sql).forEach { stmt ->
                addColumn.findAll(stmt.groupValues[1]).forEach { cols += it.groupValues[1] }
            }
        }
        return cols
    }

    /** Output columns of the LAST definition of a view (later migrations redefine it). */
    private fun viewColumns(view: String): Set<String> {
        val re = Regex("""create (?:or replace )?view $view\b[^;]*?\bas\s*select([\s\S]*?);""")
        val raw = migrations.mapNotNull { re.find(it)?.groupValues?.get(1) }.lastOrNull()
        requireNotNull(raw) { "no definition of view $view in the migrations" }
        // The select list ends at the view's OWN `from`, which is the first one
        // at paren depth 0 — a correlated sub-select carries its own `from`
        // (sessions_last_7d) and must not terminate the scan.
        val body = run {
            var depth = 0
            var end = raw.length
            var i = 0
            while (i < raw.length) {
                when (raw[i]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                if (depth == 0 && raw.startsWith("from", i) &&
                    (i == 0 || !raw[i - 1].isLetterOrDigit())
                ) {
                    end = i
                    break
                }
                i++
            }
            raw.substring(0, end)
        }
        // Split on commas that are not inside a sub-select's parentheses.
        val parts = mutableListOf<StringBuilder>().apply { add(StringBuilder()) }
        var depth = 0
        for (ch in body) {
            when (ch) {
                '(' -> depth++
                ')' -> depth--
            }
            if (ch == ',' && depth == 0) parts += StringBuilder() else parts.last().append(ch)
        }
        return parts.map { part ->
            val expr = part.toString().trim()
            // "... as sessions_last_7d" -> the alias; "p.level" -> the column.
            val alias = Regex("""\bas\s+(\w+)$""").find(expr)?.groupValues?.get(1)
            alias ?: expr.substringAfterLast('.').trim()
        }.filter { it.isNotEmpty() }.toSet()
    }

    private fun serialNames(descriptor: SerialDescriptor): Set<String> =
        descriptor.elementNames.toSet()

    private fun check(what: String, dto: SerialDescriptor, columns: Set<String>) {
        assertTrue("no columns parsed for $what — the parser, not the schema, is broken", columns.size > 2)
        val unknown = serialNames(dto) - columns
        assertTrue(
            "$what sends/reads column(s) that $what's relation does not have: $unknown " +
                "(available: ${columns.sorted()})",
            unknown.isEmpty(),
        )
    }

    @Test
    fun `profile columns exist on the profiles table`() {
        check("ProfileDto", ProfileDto.serializer().descriptor, tableColumns("profiles"))
    }

    @Test
    fun `leaderboard columns exist on the leaderboard view`() {
        check("LeaderboardDto", LeaderboardDto.serializer().descriptor, viewColumns("leaderboard"))
    }

    @Test
    fun `shadow board columns exist on the shadow_board view`() {
        check("ShadowBoardDto", ShadowBoardDto.serializer().descriptor, viewColumns("shadow_board"))
    }

    @Test
    fun `session and set rows match their tables`() {
        check("SessionDto", SessionDto.serializer().descriptor, tableColumns("sessions"))
        check("SessionSetDto", SessionSetDto.serializer().descriptor, tableColumns("session_sets"))
    }

    @Test
    fun `earned titles and friendships match their tables`() {
        check("EarnedTitleDto", EarnedTitleDto.serializer().descriptor, tableColumns("earned_titles"))
        check("FriendshipDto", FriendshipDto.serializer().descriptor, tableColumns("friendships"))
    }

    @Test
    fun `the ranked numbers the boards order by are really on the profiles table`() {
        // These are the columns push_aggregates() writes and the boards sort on.
        // If one is renamed server-side the board silently ranks everyone at 0.
        val profiles = tableColumns("profiles")
        for (col in listOf("level", "total_xp", "streak_days", "titles_count", "lifetime_strength", "shadow_essence")) {
            assertTrue("profiles is missing the ranked column $col", col in profiles)
        }
    }
}
