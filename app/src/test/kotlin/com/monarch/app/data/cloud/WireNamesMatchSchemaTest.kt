package com.monarch.app.data.cloud

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import org.junit.Assert.assertEquals
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

    /** Parameter names of a `create function` signature in the migrations. */
    private fun functionParams(fn: String): Set<String> {
        val re = Regex("""create (?:or replace )?function $fn\s*\(([\s\S]*?)\)\s*returns""")
        val sig = migrations.mapNotNull { re.find(it)?.groupValues?.get(1) }.lastOrNull()
        requireNotNull(sig) { "no definition of function $fn in the migrations" }
        return sig.split(',')
            .mapNotNull { it.trim().split(Regex("""\s+""")).firstOrNull() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    @Test
    fun `rpc argument names match the function signatures`() {
        // A renamed parameter is a 404 from PostgREST on a user's device: the
        // aggregates push is wrapped in runCatching, so the failure is a cloud
        // row that silently stops updating rather than a crash.
        for ((fn, dto) in listOf(
            RPC_PUSH_AGGREGATES to PushAggregatesArgs.serializer().descriptor,
            RPC_FIND_HUNTER to FindHunterArgs.serializer().descriptor,
        )) {
            val params = functionParams(fn)
            assertTrue("no parameters parsed for $fn — the parser is broken", params.isNotEmpty())
            val unknown = dto.elementNames.toSet() - params
            assertTrue(
                "$fn() is called with argument(s) it does not declare: $unknown (declared: ${params.sorted()})",
                unknown.isEmpty(),
            )
            assertEquals(
                "$fn() declares parameters the client never sends, so they take SQL defaults or fail",
                params.sorted(),
                dto.elementNames.sorted(),
            )
        }
    }

    @Test
    fun `the encoded rpc payload carries the declared names`() {
        // rpcArgs() is what actually crosses the wire; a correct descriptor with
        // a broken encoder would still send the wrong body.
        val body = rpcArgs(
            PushAggregatesArgs(
                totalXp = 1_234,
                lifetimeStrength = 99,
                streakDays = 7,
                shadowEssence = 4_096,
                shadowCount = 3,
                shadowRate = 12.5,
            ),
        )
        assertEquals(functionParams(RPC_PUSH_AGGREGATES).sorted(), body.keys.sorted())
        assertEquals("1234", body["p_total_xp"].toString())
        assertEquals("12.5", body["p_shadow_rate"].toString())
        assertEquals("""{"name":"Kaisel"}""", rpcArgs(FindHunterArgs("Kaisel")).toString())
    }

    /** `check (char_length(col) <= N)` ceilings declared in the migrations. */
    private fun lengthCeiling(column: String): Int {
        val re = Regex("""char_length\((?:trim\()?$column\)?\)\s*<=\s*(\d+)""")
        val found = migrations.mapNotNull { re.find(it)?.groupValues?.get(1) }.lastOrNull()
        requireNotNull(found) { "no char_length ceiling for $column in the migrations" }
        return found.toInt()
    }

    /** `between LOW and HIGH` bounds declared in the migrations. */
    private fun betweenBounds(column: String): Pair<Int, Int> {
        val re = Regex("""char_length\(trim\($column\)\)\s*between\s*(\d+)\s*and\s*(\d+)""")
        val m = migrations.firstNotNullOfOrNull { re.find(it) }
        requireNotNull(m) { "no between-bounds for $column in the migrations" }
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    @Test
    fun `the client's field limits are the server's field limits`() {
        // These are duplicated by necessity — one copy in SQL, one in Kotlin —
        // so the pair gets a guard. A tightened constraint would otherwise
        // surface as a sync that fails forever on one row, swallowed by the
        // runCatching around the push.
        assertEquals(
            "sessions.title ceiling drifted from WireLimits",
            lengthCeiling("title"),
            WireLimits.SESSION_TITLE_MAX,
        )
        assertEquals(
            "sessions.note ceiling drifted from WireLimits",
            lengthCeiling("note"),
            WireLimits.SESSION_NOTE_MAX,
        )
        assertEquals(
            "session_sets.grade ceiling drifted from WireLimits",
            lengthCeiling("grade"),
            WireLimits.GRADE_MAX,
        )
        val (low, high) = betweenBounds("display_name")
        assertEquals("profiles.display_name minimum drifted", low, WireLimits.DISPLAY_NAME_MIN)
        assertEquals("profiles.display_name maximum drifted", high, WireLimits.DISPLAY_NAME_MAX)
    }
}
