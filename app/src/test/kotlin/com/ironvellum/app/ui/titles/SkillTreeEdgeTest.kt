package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.Skills
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.min

/** Pure geometry over every real path: an edge must never be drawn through a node or a label. */
class SkillTreeEdgeTest {

    private val margin = 6f // beyond the disc: the breathing ring reaches 7dp out
    private val textPad = 2f

    /** The narrowest slot a screen that yields [columns] slots gives: 360dp less gutters at four, 72dp each beyond. */
    private fun cellWidth(columns: Int) = if (columns == 4) 328f / 4 else 72f

    private data class Rect(val l: Float, val t: Float, val r: Float, val b: Float) {
        fun contains(p: Pt) = p.x in l..r && p.y in t..b
    }

    private fun labelRects(layout: TreeLayout, m: TreeMetrics, n: PlacedSkill): List<Rect> {
        val cx = m.centreX(n.x)
        val top = m.discBottom(n.level) + TEXT_GAP_DP
        val w = min(n.skill.name.length * 6.5f, m.cellW) / 2f + textPad
        val rects = mutableListOf(Rect(cx - w, top - textPad, cx + w, top + m.lineH * m.levelLines[n.level] + textPad))
        if (n.skill.name in layout.crossNeeds) {
            val y = top + m.lineH * m.levelLines[n.level]
            rects += Rect(cx - m.cellW / 2f, y - textPad, cx + m.cellW / 2f, y + m.lineH * 2 + textPad)
        }
        return rects
    }

    private fun violations(line: String, columns: Int, lineH: Float): List<String> {
        val layout = treeLayout(line, columns)
        val m = treeMetrics(layout, cellWidth(columns), lineH)
        val out = mutableListOf<String>()
        for (e in layout.edges) {
            val pts = layout.edgeCurve(e, m).flatMap { c -> (0..32).map { c.at(it / 32f) } }
            for (n in layout.nodes) {
                if (n.skill.name == e.from || n.skill.name == e.to) continue
                val c = m.discCentre(n)
                val d = pts.minOf { hypot(it.x - c.x, it.y - c.y) }
                if (d < NODE_DP / 2f + margin) out += "${e.from}->${e.to} hits disc of ${n.skill.name} (${"%.0f".format(d)}dp)"
                if (labelRects(layout, m, n).any { r -> pts.any(r::contains) }) out += "${e.from}->${e.to} crosses label of ${n.skill.name}"
            }
        }
        return out
    }

    @Test
    fun `no edge is drawn through another node or its label on any path`() {
        val bad = buildList {
            for (columns in listOf(4, 5, 6)) for (lineH in listOf(12f, 18f)) for (line in Skills.LINES) {
                violations(line, columns, lineH).distinct().forEach { add("$line @$columns cols, line ${lineH}dp: $it") }
            }
        }
        assertTrue("${bad.size} violations:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `a long edge gets a lane on every row it crosses, clear of the nodes there`() {
        for (columns in listOf(4, 5, 6)) for (line in Skills.LINES) {
            val layout = treeLayout(line, columns)
            val at = layout.nodes.associateBy { it.skill.name }
            for (e in layout.edges) {
                val span = at.getValue(e.to).level - at.getValue(e.from).level
                val lanes = layout.lanes[e].orEmpty()
                assertTrue("$line $e: $span rows need ${span - 1} lanes, got ${lanes.size}", lanes.size == maxOf(0, span - 1))
                lanes.forEachIndexed { i, x ->
                    val level = at.getValue(e.from).level + 1 + i
                    assertTrue("$line $e lane $x leaves the canvas", x >= -0.5f && x <= columns - 0.5f)
                    layout.nodes.filter { it.level == level }.forEach {
                        assertTrue("$line $e lane $x is ${it.x - x} slots from ${it.skill.name}", kotlin.math.abs(it.x - x) >= 0.69f)
                    }
                }
            }
        }
    }
}
