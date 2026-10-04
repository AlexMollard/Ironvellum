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

internal const val NODE_DP = 64f
internal const val TOP_PAD_DP = 5f
internal const val TEXT_GAP_DP = 2f
internal const val EDGE_GAP_DP = 20f

/** Where everything sits on the canvas: the level tops and the slot width. */
internal data class TreeMetrics(
    val cellW: Float,
    val lineH: Float,
    val tops: List<Float>,
    val levelLines: List<Int>,
    /** Per level, the lines its widest "needs X (Path)" marker takes; 0 when no node there carries one. */
    val crossLines: List<Int>,
)

/** Type sizes of the two captions under a disc, for the line estimate; [SkillTree] draws with the same. */
internal const val LABEL_SP = 11f
internal const val CROSS_SP = 10f

/** Side padding inside a label plate; text wraps in the cell less this on each side. */
internal const val LABEL_PAD_DP = 3f

/** The most lines a technique's name, or its marker, may take before it is cut. */
internal const val MAX_LABEL_LINES = 3

/**
 * A word-wrap estimate of how many lines [text] takes in [widthDp], for callers
 * that cannot measure (the unit tests). Break opportunities are spaces and
 * after hyphens, as in the real text layout; the glyph width is a cautious
 * average so the estimate runs long rather than short. The screen measures.
 */
internal fun estimateLines(text: String, widthDp: Float, sp: Float, bold: Boolean): Int {
    val fit = maxOf(1, (widthDp / (sp * if (bold) 0.62f else 0.56f)).toInt())
    var lines = 1
    var used = 0
    // pieces end at a space (dropped) or a hyphen (kept)
    Regex("[^ -]+-?").findAll(text).forEach { piece ->
        var len = piece.value.length
        val joiner = if (used == 0) 0 else 1
        if (used + joiner + len <= fit && !(used == 0 && len > fit)) {
            used += joiner + len
        } else {
            if (used > 0) lines++
            while (len > fit) {
                lines++
                len -= fit
            }
            used = len
        }
    }
    return lines.coerceIn(1, MAX_LABEL_LINES)
}

/** The text of a node's cross-path marker as it reads before anything is mastered: the longest it gets. */
internal fun TreeLayout.crossMarker(name: String): String? = crossNeedMarker(crossNeeds[name].orEmpty(), emptySet())

/**
 * [linesOf] is how many lines a node's label takes. The screen passes the
 * measured count at the real cell width and font scale; the default is the
 * character-count guess [labelLines].
 */
internal fun treeMetrics(
    layout: TreeLayout,
    cellW: Float,
    lineH: Float,
    crossLinesOf: (PlacedSkill) -> Int = { n ->
        layout.crossMarker(n.skill.name)?.let { estimateLines(it, cellW - 2 * LABEL_PAD_DP, CROSS_SP, bold = false) } ?: 0
    },
    linesOf: (PlacedSkill) -> Int = { estimateLines(it.skill.name, cellW - 2 * LABEL_PAD_DP, LABEL_SP, bold = true) },
): TreeMetrics {
    val lines = layout.levels.indices.map { l -> layout.nodes.filter { it.level == l }.maxOf(linesOf) }
    val cross = layout.levels.indices.map { l -> layout.nodes.filter { it.level == l }.maxOf(crossLinesOf) }
    val tops = (0 until layout.levels.size).runningFold(0f) { y, l ->
        y + TOP_PAD_DP + NODE_DP + TEXT_GAP_DP + lineH * (lines[l] + cross[l]) + EDGE_GAP_DP
    }
    return TreeMetrics(cellW, lineH, tops, lines, cross)
}

internal fun TreeMetrics.centreX(x: Float): Float = cellW * (x + 0.5f)
internal fun TreeMetrics.discTop(level: Int): Float = tops[level] + TOP_PAD_DP
internal fun TreeMetrics.discBottom(level: Int): Float = discTop(level) + NODE_DP

internal fun TreeMetrics.discCentre(n: PlacedSkill) = Pt(centreX(n.x), discTop(n.level) + NODE_DP / 2f)

/**
 * Where a node's own caption ends: the line leaves from here. Its name plate
 * spans the row's tallest name, and a node with a marker adds the row's marker
 * height, so the line is hidden behind its own text and emerges clean below it.
 */
internal fun TreeLayout.nodeBottom(n: PlacedSkill, m: TreeMetrics): Float =
    m.discBottom(n.level) + TEXT_GAP_DP + m.lineH * (m.levelLines[n.level] + if (n.skill.name in crossNeeds) m.crossLines[n.level] else 0)

/** y of a technique's disc top, in dp from the top of the tree; null when it is not on this path. */
internal fun TreeLayout.discTopOf(name: String, m: TreeMetrics): Float? =
    nodes.firstOrNull { it.skill.name == name }?.let { m.discTop(it.level) }

/** Where a row's labels end and the gap to the next row begins: the lowest y a lane may bend from. */
internal fun TreeMetrics.rowBottom(level: Int): Float = tops[level + 1] - EDGE_GAP_DP

/**
 * The path of one edge, as drawn. It leaves the bottom of the prerequisite's
 * own caption, drops straight down, and bends only in the gap between rows,
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
    var here = Pt(m.centreX(xs[0]), nodeBottom(parent, m))
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
