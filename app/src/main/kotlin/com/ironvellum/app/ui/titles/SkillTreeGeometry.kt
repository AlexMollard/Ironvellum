package com.ironvellum.app.ui.titles

/** A point on the tree canvas, in dp. */
internal data class Pt(val x: Float, val y: Float)

/** One cubic piece of an edge; a straight run is a cubic whose handles lie on it. */
internal data class Cubic(val p0: Pt, val c1: Pt, val c2: Pt, val p3: Pt) {
    fun at(t: Float): Pt {
        val u = 1f - t
        val a = u * u * u
        val b = 3f * u * u * t
        val c = 3f * u * t * t
        val d = t * t * t
        return Pt(
            a * p0.x + b * c1.x + c * c2.x + d * p3.x,
            a * p0.y + b * c1.y + c * c2.y + d * p3.y,
        )
    }
}

internal const val NODE_DP = 54f
internal const val TOP_PAD_DP = 5f
internal const val TEXT_GAP_DP = 2f
internal const val EDGE_GAP_DP = 20f

/** Where everything sits on the canvas: the level tops and the slot width. */
internal data class TreeMetrics(
    val cellW: Float,
    val lineH: Float,
    val tops: List<Float>,
    val levelLines: List<Int>,
)

/**
 * [linesOf] is how many lines a node's label takes. The screen passes the
 * measured count at the real cell width and font scale; the default is the
 * character-count guess [labelLines].
 */
internal fun treeMetrics(
    layout: TreeLayout,
    cellW: Float,
    lineH: Float,
    linesOf: (PlacedSkill) -> Int = { labelLines(it.skill.name) },
): TreeMetrics {
    val lines = layout.levels.indices.map { l -> layout.nodes.filter { it.level == l }.maxOf(linesOf) }
    val tops = layout.levels.runningFold(0f) { y, level ->
        y + TOP_PAD_DP + NODE_DP + TEXT_GAP_DP + lineH * lines[layout.levels.indexOf(level)] +
            (if (level.hasCrossNeed) lineH * 2 else 0f) + EDGE_GAP_DP
    }
    return TreeMetrics(cellW, lineH, tops, lines)
}

internal fun TreeMetrics.centreX(x: Float): Float = cellW * (x + 0.5f)
internal fun TreeMetrics.discTop(level: Int): Float = tops[level] + TOP_PAD_DP
internal fun TreeMetrics.discBottom(level: Int): Float = discTop(level) + NODE_DP

internal fun TreeMetrics.discCentre(n: PlacedSkill) = Pt(centreX(n.x), discTop(n.level) + NODE_DP / 2f)

/** Where a row's labels end and the gap to the next row begins: the lowest y a lane may bend from. */
internal fun TreeMetrics.rowBottom(level: Int): Float = tops[level + 1] - EDGE_GAP_DP

/**
 * The path of one edge, as drawn. It leaves the bottom of the prerequisite's
 * disc, drops straight past its label, and bends only in the gap between rows,
 * so a bend can never meet a label. A long edge repeats that on every row it
 * crosses, dropping straight down its own lane, and finally lands on the top of
 * the dependant's disc.
 */
internal fun TreeLayout.edgeCurve(edge: TreeEdge, m: TreeMetrics): List<Cubic> {
    val at = nodes.associateBy { it.skill.name }
    val parent = at.getValue(edge.from)
    val child = at.getValue(edge.to)
    val xs = listOf(parent.x) + lanes[edge].orEmpty() + child.x
    val out = ArrayList<Cubic>()
    var here = Pt(m.centreX(xs[0]), m.discBottom(parent.level))
    for (i in 0 until xs.size - 1) {
        val level = parent.level + i
        val leave = Pt(m.centreX(xs[i]), m.rowBottom(level))
        val arrive = Pt(m.centreX(xs[i + 1]), m.discTop(level + 1))
        out += Cubic(here, here, leave, leave)
        val ym = (leave.y + arrive.y) / 2f
        out += Cubic(leave, Pt(leave.x, ym), Pt(arrive.x, ym), arrive)
        here = arrive
    }
    return out
}
