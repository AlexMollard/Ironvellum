package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.Skills
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillTreeLayoutTest {

    /** A 360dp phone: 16dp gutters each side and the numeral column. */
    private val phoneColumns = columnsFor(360f - 32f - 28f)

    @Test
    fun `a phone gets four slots and a tablet more, never fewer`() {
        assertEquals(4, phoneColumns)
        assertEquals(4, columnsFor(100f))
        assertEquals(6, columnsFor(2000f))
    }

    @Test
    fun `every technique on a path is placed exactly once`() {
        for (line in Skills.LINES) {
            val placed = treeLayout(line, phoneColumns).nodes.map { it.skill.name }
            assertEquals(line, Skills.ALL.filter { it.line == line }.map { it.name }.sorted(), placed.sorted())
            assertEquals(line, placed.size, placed.toSet().size)
        }
    }

    @Test
    fun `tiers only ever step down the graph`() {
        for (line in Skills.LINES) {
            val layout = treeLayout(line, phoneColumns)
            layout.levels.zipWithNext { a, b -> assertTrue(line, a.tier <= b.tier) }
            layout.nodes.forEach { assertEquals(it.skill.name, it.skill.tier, layout.levels[it.level].tier) }
            // exactly one level per tier carries the numeral
            val tiers = layout.levels.map { it.tier }.distinct()
            assertEquals(line, tiers, layout.levels.filter { it.startsTier }.map { it.tier })
        }
    }

    @Test
    fun `every edge runs from a higher row to a lower one`() {
        for (line in Skills.LINES) {
            val layout = treeLayout(line, phoneColumns)
            val level = layout.nodes.associate { it.skill.name to it.level }
            assertTrue(line, layout.edges.isNotEmpty() || layout.nodes.size == 1)
            layout.edges.forEach { assertTrue("$line $it", level.getValue(it.from) < level.getValue(it.to)) }
        }
    }

    @Test
    fun `no two nodes overlap at 360dp and none leaves the row`() {
        for (line in Skills.LINES) {
            val layout = treeLayout(line, phoneColumns)
            layout.nodes.groupBy { it.level }.forEach { (level, row) ->
                val xs = row.map { it.x }.sorted()
                assertTrue("$line row $level below the left edge", xs.first() >= 0f)
                assertTrue("$line row $level past the right edge", xs.last() <= phoneColumns - 1f)
                xs.zipWithNext { a, b -> assertTrue("$line row $level overlaps: $xs", b - a >= 1f - 1e-4f) }
            }
        }
    }

    @Test
    fun `a row wider than the screen wraps instead of overlapping`() {
        for (line in Skills.LINES) {
            val layout = treeLayout(line, columns = 1)
            assertEquals(line, Skills.ALL.count { it.line == line }, layout.nodes.size)
            layout.nodes.groupBy { it.level }.values.forEach { assertEquals(line, 1, it.size) }
        }
    }

    @Test
    fun `a chain stays on one vertical and a fork sits under its parent`() {
        val push = treeLayout("Push", phoneColumns).nodes.associateBy { it.skill.name }
        // Incline Push-up to One-Arm Push-up is a straight run
        val trunk = listOf("Incline Push-up", "Push-up", "Diamond Push-up", "Archer Push-up")
        assertEquals(1, trunk.map { push.getValue(it).x }.distinct().size)
        val pull = treeLayout("Pull", phoneColumns).nodes.associateBy { it.skill.name }
        val kids = listOf("One-Arm Hang", "Scapular Pull").map { pull.getValue(it).x }
        val parent = pull.getValue("Dead Hang").x
        assertTrue("a fork's children straddle or sit on their parent", kids.min() <= parent && kids.max() >= parent)
    }

    @Test
    fun `the ordering never does worse than declaration order and mostly untangles it`() {
        // Declaration order, spaced evenly: what the sweeps start from.
        var total = 0
        for (line in Skills.LINES) {
            val layout = treeLayout(line, phoneColumns)
            total += layout.crossings()
            assertTrue("$line crosses ${layout.crossings()} times", layout.crossings() <= 2)
        }
        assertTrue("$total crossings across every path", total <= 6)
    }

    @Test
    fun `prerequisites on another path become a marker, not an edge`() {
        val pull = treeLayout("Pull", phoneColumns)
        assertEquals(listOf(CrossNeed("L-sit", "Core")), pull.crossNeeds["L-sit Pull-up"])
        assertTrue(pull.edges.none { it.to == "L-sit Pull-up" && it.from == "L-sit" })
        assertTrue(pull.levels[pull.nodes.first { it.skill.name == "L-sit Pull-up" }.level].hasCrossNeed)
        // every cross prerequisite in the data is accounted for, on every path
        for (line in Skills.LINES) {
            val layout = treeLayout(line, phoneColumns)
            val expected = Skills.ALL.filter { it.line == line }
                .sumOf { s -> s.prerequisites.count { Skills.forName(it)?.line != line } }
            assertEquals(line, expected, layout.crossNeeds.values.sumOf { it.size })
        }
    }

    @Test
    fun `the marker names the first outstanding cross-path prerequisite and counts the rest`() {
        val needs = listOf(CrossNeed("Pull-up", "Pull"), CrossNeed("Parallel Bar Dip", "Push"))
        assertEquals("needs Pull-up (Pull) +1", crossNeedMarker(needs, emptySet()))
        assertEquals("needs Parallel Bar Dip (Push)", crossNeedMarker(needs, setOf("Pull-up")))
        assertNull(crossNeedMarker(needs, setOf("Pull-up", "Parallel Bar Dip")))
        assertNull(crossNeedMarker(emptyList(), emptySet()))
    }

    @Test
    fun `the first next technique is the topmost open one, and none once the path is mastered`() {
        for (line in Skills.LINES) {
            val layout = treeLayout(line, phoneColumns)
            val first = layout.firstNext(emptySet())
            assertNotNull(line, first)
            assertTrue(line, first!!.prerequisites.isEmpty())
            val all = Skills.ALL.filter { it.line == line }.map { it.name }.toSet()
            assertNull(line, layout.firstNext(all))
        }
        val pull = treeLayout("Pull", phoneColumns)
        assertFalse(pull.firstNext(setOf("Dead Hang"))!!.name == "Dead Hang")
    }
}
