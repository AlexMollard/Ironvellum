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

    @Test
    fun `edges that leave one prerequisite or enter one dependant share lanes`() {
        for (line in Skills.LINES) {
            val layout = treeLayout(line, 4)
            val level = layout.nodes.associate { it.skill.name to it.level }
            val perEdge = layout.lanes.values.sumOf { it.size }
            val drawn = layout.lanes.flatMap { (e, xs) -> xs.mapIndexed { i, x -> level.getValue(e.from) + 1 + i to x } }.toSet()
            assertTrue("$line draws ${drawn.size} lanes for $perEdge crossings", drawn.size <= perEdge)
            // Handstand is the hub-heavy path: it had one full-height lane per long edge
            if (line == "Handstand") assertTrue("Handstand draws ${drawn.size} lanes for $perEdge crossings", drawn.size <= perEdge * 0.6)
        }
    }

    // ---- the real phones: every path at 360dp and 412dp, as drawn

    private val phones = listOf(360f, 412f)

    @Test
    fun `at 360dp and 412dp no node, label or edge overlaps another or leaves the screen`() {
        val bad = buildList {
            for (w in phones) for (line in Skills.LINES) {
                val d = Drawn(line, w)
                (d.overlaps() + d.outOfWidth() + d.edgeHits()).forEach { add("$line @${w.toInt()}dp: $it") }
            }
        }
        assertTrue("${bad.size} problems:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `at 360dp and 412dp the edges of a path do not cross`() {
        // Handstand is a hub: one trunk into Wall HSPU cannot be kept clear of the Crow Pose join, whatever the order
        val allowed = mapOf("Handstand" to 1)
        val bad = buildList {
            for (w in phones) for (line in Skills.LINES) {
                val n = Drawn(line, w).crossings()
                if (n > (allowed[line] ?: 0)) add("$line @${w.toInt()}dp: $n")
            }
        }
        assertTrue("crossings:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `a branching path spreads across the width instead of huddling in the middle`() {
        for (w in phones) for (line in Skills.LINES) {
            val d = Drawn(line, w)
            val xs = d.layout.nodes.map { it.x }
            if (xs.max() - xs.min() < 0.5f) continue // a lone chain stays centred
            // the outermost node sits within a slot of an edge, or the stretch hit its cap
            val reach = (xs.max() - xs.min()) / (d.columns - 1)
            assertTrue("$line @${w.toInt()}dp uses ${"%.0f".format(reach * 100)}% of the width", reach >= 0.55f)
        }
    }

    @Test
    fun `no name or marker needs more than three lines at 360dp`() {
        for (line in Skills.LINES) {
            val d = Drawn(line, 360f)
            d.layout.nodes.forEach { n ->
                val text = d.cellW - 2 * LABEL_PAD_DP
                assertTrue("${n.skill.name} is cut", estimateLines(n.skill.name, text, LABEL_SP, bold = true) <= 3)
                d.layout.crossMarker(n.skill.name)?.let { assertTrue("$it is cut", estimateLines(it, text, CROSS_SP, bold = false) <= 3) }
            }
        }
    }

    @Test
    fun `a wrapped label grows its row and the edge still starts below it`() {
        val layout = treeLayout("Pull", 4)
        val one = treeMetrics(layout, 82f, 12f, linesOf = { 1 })
        val three = treeMetrics(layout, 82f, 12f, linesOf = { 3 })
        assertTrue(three.tops.last() - one.tops.last() >= 2 * 12f * layout.levels.size)
        val n = layout.nodes.first { it.skill.name == "Dead Hang" }
        val start = layout.edgeCurve(layout.edges.first { it.from == "Dead Hang" }, three).first().p0
        assertTrue(start.y >= three.discBottom(n.level) + 3 * 12f)
    }
}

private data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    fun grow(d: Float) = Box(l - d, t - d, r + d, b + d)
    fun strictlyHolds(p: Pt) = p.x > l && p.x < r && p.y > t && p.y < b
    fun meets(o: Box) = l < o.r && o.l < r && t < o.b && o.t < b
}

/** One path laid out for a phone [width] wide (16dp gutters), with its nodes, captions and edges as boxes and lines. */
private class Drawn(val line: String, width: Float) {
    val avail = width - 32f
    val columns = columnsFor(avail)
    val cellW = avail / columns
    val layout = treeLayout(line, columns)
    val m = treeMetrics(layout, cellW, 12f)

    fun disc(n: PlacedSkill): Box {
        val c = m.discCentre(n)
        return Box(c.x - NODE_DP / 2, c.y - NODE_DP / 2, c.x + NODE_DP / 2, c.y + NODE_DP / 2)
    }

    /** The caption block: the cell less its plate padding, from under the disc to where the node's own line leaves. */
    fun caption(n: PlacedSkill): Box {
        val cx = m.centreX(n.x)
        return Box(cx - cellW / 2 + LABEL_PAD_DP, m.discBottom(n.level) + TEXT_GAP_DP, cx + cellW / 2 - LABEL_PAD_DP, layout.nodeBottom(n, m))
    }

    fun points(e: TreeEdge): List<Pt> = layout.edgeCurve(e, m).flatMap { c -> (0..24).map { c.at(it / 24f) } }

    /** The edge sampled at a dp or finer, so a short box cannot slip between samples. */
    private fun dense(e: TreeEdge): List<Pt> {
        val p = points(e)
        return p.zipWithNext().flatMap { (a, b) ->
            val steps = maxOf(1, kotlin.math.ceil(hypot(b.x - a.x, b.y - a.y)).toInt())
            (0 until steps).map { Pt(a.x + (b.x - a.x) * it / steps, a.y + (b.y - a.y) * it / steps) }
        } + p.last()
    }

    fun overlaps(): List<String> = buildList {
        val ns = layout.nodes
        for (i in ns.indices) for (j in ns.indices) {
            if (i == j) continue
            val a = ns[i].skill.name
            val b = ns[j].skill.name
            if (i < j && disc(ns[i]).meets(disc(ns[j]))) add("disc $a overlaps disc $b")
            if (i < j && caption(ns[i]).meets(caption(ns[j]))) add("label $a overlaps label $b")
            if (caption(ns[i]).meets(disc(ns[j]))) add("label $a overlaps disc $b")
        }
    }

    fun outOfWidth(): List<String> = buildList {
        layout.nodes.forEach { n ->
            val d = disc(n)
            val c = caption(n)
            if (minOf(d.l, c.l) < -0.01f || maxOf(d.r, c.r) > avail + 0.01f) add("${n.skill.name} leaves the width")
        }
        layout.edges.forEach { e -> if (dense(e).any { it.x < 0f || it.x > avail }) add("edge ${e.from} to ${e.to} leaves the width") }
    }

    /** An edge inside any disc (a margin clear) or caption other than the ends it joins. */
    fun edgeHits(): List<String> = buildList {
        layout.edges.forEach { e ->
            val pts = dense(e)
            layout.nodes.forEach { n ->
                val own = n.skill.name == e.from || n.skill.name == e.to
                if (pts.any { disc(n).grow(if (own) -0.5f else 4f).strictlyHolds(it) }) add("${e.from} to ${e.to} runs through the disc of ${n.skill.name}")
                if (pts.any { caption(n).grow(if (own) -0.5f else 2f).strictlyHolds(it) }) add("${e.from} to ${e.to} runs through the caption of ${n.skill.name}")
            }
        }
    }

    /** Pairs of edges, not sharing an end, that meet or run within 1.5dp of each other. */
    fun crossings(): Int {
        val polys = layout.edges.associateWith { points(it) }
        var count = 0
        for (i in layout.edges.indices) for (j in i + 1 until layout.edges.size) {
            val a = layout.edges[i]
            val b = layout.edges[j]
            if (a.from == b.from || a.to == b.to || a.from == b.to || a.to == b.from) continue
            if (polylineGap(polys.getValue(a), polys.getValue(b)) < 1.5f) count++
        }
        return count
    }
}

private fun segmentGap(a: Pt, b: Pt, c: Pt, d: Pt): Float {
    fun side(o: Pt, p: Pt, q: Pt) = (p.x - o.x) * (q.y - o.y) - (p.y - o.y) * (q.x - o.x)
    if (side(c, d, a) * side(c, d, b) < 0 && side(a, b, c) * side(a, b, d) < 0) return 0f
    fun toSegment(p: Pt, s: Pt, e: Pt): Float {
        val dx = e.x - s.x
        val dy = e.y - s.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((p.x - s.x) * dx + (p.y - s.y) * dy) / len2).coerceIn(0f, 1f)
        return hypot(p.x - (s.x + t * dx), p.y - (s.y + t * dy))
    }
    return minOf(toSegment(a, c, d), toSegment(b, c, d), toSegment(c, a, b), toSegment(d, a, b))
}

private fun polylineGap(p: List<Pt>, q: List<Pt>): Float {
    var best = Float.MAX_VALUE
    for (i in 0 until p.size - 1) for (j in 0 until q.size - 1) best = minOf(best, segmentGap(p[i], p[i + 1], q[j], q[j + 1]))
    return best
}
