package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.Skills

/** One technique placed on a path's graph: a level (row) and a horizontal slot. */
internal data class PlacedSkill(
    val skill: Skills.SkillDef,
    val level: Int,
    /** Slot of the node's centre, 0 to columns - 1; fractional so a child can sit under its parent. */
    val x: Float,
)

/** A row of the graph. A tier spans one level, or several when its techniques chain or wrap. */
internal data class TreeLevel(
    val tier: Int,
    /** First level of its tier: the one that carries the numeral. */
    val startsTier: Boolean,
    /** Some node here has a prerequisite on another path, so the row reserves a line for the marker. */
    val hasCrossNeed: Boolean,
)

internal data class TreeEdge(val from: String, val to: String)

/** A prerequisite that lives on another path. */
internal data class CrossNeed(val skill: String, val line: String)

internal data class TreeLayout(
    val columns: Int,
    val levels: List<TreeLevel>,
    /** In reading order: level by level, left to right. */
    val nodes: List<PlacedSkill>,
    /** Prerequisite to dependant, both on this path. */
    val edges: List<TreeEdge>,
    /** Prerequisites held on another path, by the skill that needs them. */
    val crossNeeds: Map<String, List<CrossNeed>>,
    /**
     * For an edge spanning more than one level, the slot it passes through on
     * each level in between, top to bottom. Short edges have no entry.
     */
    val lanes: Map<TreeEdge, List<Float>>,
)

private const val MIN_CELL_DP = 72f
private const val MIN_COLUMNS = 4
private const val MAX_COLUMNS = 6

/** Marks a waypoint's name so it can never clash with a technique. */
private const val LANE_MARK = "~"

/** Slots between two waypoints, and between a waypoint and a node's centre. */
private const val LANE_GAP = 0.35
private const val LANE_NODE_GAP = 0.7

/** How far past an end column's centre a waypoint may sit. */
private const val LANE_MARGIN = 0.4

/**
 * How many node slots fit across [availableDp]. A path's widest row is three
 * techniques, so four slots on a 360dp phone never wrap; a wider screen gets
 * more slots rather than stretched ones.
 */
internal fun columnsFor(availableDp: Float): Int =
    (availableDp / MIN_CELL_DP).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)

/**
 * Lays one path out as a layered graph.
 *
 * Levels: a technique sits in its tier's band; a prerequisite in the same
 * tier pushes its dependant down a sub-level, so every edge runs strictly
 * downward. A level wider than [columns] wraps onto the next.
 *
 * Lanes: an edge that spans more than one level gets a virtual waypoint on
 * every level it crosses (the standard layered-graph treatment). A waypoint
 * is ordered and placed like a node, only narrower, so the edge ends up in a
 * lane of its own between the real nodes of that row instead of running
 * through them. A row that has no room for its waypoints wraps like a wide one.
 *
 * Order: a few down-and-up barycentre sweeps (each node sorts by the mean
 * position of its parents, then of its children), keeping the arrangement
 * with the fewest crossings.
 *
 * Slots: each level is placed top-down with every node as near the mean of its
 * parents as the minimum gaps allow, so a chain stays vertical and a fork
 * spreads evenly under its parent. Waypoints give way before nodes do.
 */
internal fun treeLayout(line: String, columns: Int): TreeLayout {
    require(columns >= 1) { "columns must be at least 1" }
    val onLine = Skills.ALL.filter { it.line == line }
    val inLine = onLine.map { it.name }.toSet()
    val parents: Map<String, List<String>> =
        onLine.associate { s -> s.name to s.prerequisites.filter { it in inLine } }

    // sub-level inside a tier: how many same-tier prerequisites deep
    val depth = HashMap<String, Int>()
    fun depthOf(s: Skills.SkillDef): Int = depth.getOrPut(s.name) {
        parents.getValue(s.name)
            .mapNotNull { Skills.forName(it) }
            .filter { it.tier == s.tier }
            .maxOfOrNull { depthOf(it) + 1 } ?: 0
    }

    val edges = onLine.flatMap { s -> parents.getValue(s.name).map { TreeEdge(it, s.name) } }
    val groups = onLine.groupBy { it.tier to depthOf(it) }
        .toSortedMap(compareBy({ it.first }, { it.second }))
    val limit = groups.keys.associateWith { columns }.toMutableMap()

    val order = mutableListOf<MutableList<String>>()
    val levelTier = mutableListOf<Int>()
    val levelOf = HashMap<String, Int>()
    val chainOf = HashMap<TreeEdge, List<String>>()

    // Wrap each group at its limit, then narrow the limit of any row whose
    // waypoints do not fit beside its nodes, until every row has room.
    while (true) {
        order.clear()
        levelTier.clear()
        val levelGroup = mutableListOf<Pair<Int, Int>>()
        groups.forEach { (key, members) ->
            members.map { it.name }.chunked(limit.getValue(key)).forEach {
                order += it.toMutableList()
                levelTier += key.first
                levelGroup += key
            }
        }
        levelOf.clear()
        order.forEachIndexed { l, names -> names.forEach { levelOf[it] = l } }
        val passing = IntArray(order.size)
        edges.forEach { e ->
            for (l in levelOf.getValue(e.from) + 1 until levelOf.getValue(e.to)) passing[l]++
        }
        val tight = order.indices.firstOrNull { l ->
            val n = order[l].size
            n > 1 && (n - 1) + LANE_NODE_GAP * passing[l] > columns - 1 + 2 * LANE_MARGIN &&
                limit.getValue(levelGroup[l]) > 1
        } ?: break
        limit[levelGroup[tight]] = order[tight].size - 1
    }

    // waypoints: one per level an edge passes, joined into the row's order list
    val upstream = HashMap<String, MutableList<String>>()
    val downstream = HashMap<String, MutableList<String>>()
    val links = mutableListOf<Pair<String, String>>()
    edges.forEach { e ->
        val from = levelOf.getValue(e.from)
        val to = levelOf.getValue(e.to)
        val chain = (from + 1 until to).map { l -> "$LANE_MARK${e.from}>${e.to}@$l".also { order[l] += it } }
        chainOf[e] = chain
        (listOf(e.from) + chain + e.to).zipWithNext().forEach { (a, b) ->
            upstream.getOrPut(b) { mutableListOf() } += a
            downstream.getOrPut(a) { mutableListOf() } += b
            links += a to b
        }
    }
    order.forEachIndexed { l, names -> names.forEach { levelOf[it] = l } }
    fun parentsOf(n: String): List<String> = upstream[n].orEmpty()
    fun childrenOf(n: String): List<String> = downstream[n].orEmpty()

    // ---- ordering within levels

    fun norm(): Map<String, Double> = buildMap {
        order.forEach { names -> names.forEachIndexed { i, n -> put(n, (i + 0.5) / names.size) } }
    }

    fun orderCrossings(): Int {
        val p = norm()
        var count = 0
        for (i in links.indices) for (j in i + 1 until links.size) {
            val a = links[i]
            val b = links[j]
            if (levelOf[a.first] != levelOf[b.first]) continue
            val top = p.getValue(a.first) - p.getValue(b.first)
            val bottom = p.getValue(a.second) - p.getValue(b.second)
            if (top * bottom < 0) count++
        }
        return count
    }

    fun sweep(l: Int, neighbours: (String) -> List<String>) {
        val p = norm()
        val key = order[l].associateWith { n ->
            neighbours(n).map { p.getValue(it) }.takeIf { it.isNotEmpty() }?.average() ?: p.getValue(n)
        }
        order[l] = order[l].sortedBy { key.getValue(it) }.toMutableList()
    }

    var best = order.map { it.toList() }
    var bestCrossings = orderCrossings()
    repeat(4) {
        for (l in 1 until order.size) sweep(l, ::parentsOf)
        for (l in order.size - 2 downTo 0) sweep(l, ::childrenOf)
        val now = orderCrossings()
        if (now < bestCrossings) {
            bestCrossings = now
            best = order.map { it.toList() }
        }
    }
    best.forEachIndexed { l, names -> order[l] = names.toMutableList() }

    // ---- slots

    val xOf = HashMap<String, Double>()

    fun isLane(n: String) = n.startsWith(LANE_MARK)
    fun gap(a: String, b: String): Double = when {
        isLane(a) && isLane(b) -> LANE_GAP
        isLane(a) || isLane(b) -> LANE_NODE_GAP
        else -> 1.0
    }

    // Puts one level as near its desired slots as the gaps and the row allow.
    fun place(names: List<String>, desired: List<Double>) {
        val m = names.size
        val off = DoubleArray(m)
        for (k in 1 until m) off[k] = off[k - 1] + gap(names[k - 1], names[k])
        // Colliding runs merge at their weighted mean; a waypoint weighs less than a node, so it gives way.
        class Run(val sum: Double, val weight: Double, val count: Int) {
            val at get() = sum / weight
        }
        val runs = ArrayList<Run>()
        desired.forEachIndexed { k, d ->
            val w = if (isLane(names[k])) 1.0 else 4.0
            var cur = Run(w * (d - off[k]), w, 1)
            while (runs.isNotEmpty() && runs.last().at > cur.at) {
                val prev = runs.removeAt(runs.lastIndex)
                cur = Run(prev.sum + cur.sum, prev.weight + cur.weight, prev.count + cur.count)
            }
            runs += cur
        }
        val xs = DoubleArray(m)
        var i = 0
        runs.forEach { r -> repeat(r.count) { xs[i] = r.at + off[i]; i++ } }
        // keep inside the row, still a gap apart; a waypoint may sit a little past the end columns
        fun lo(k: Int) = if (isLane(names[k])) -LANE_MARGIN else 0.0
        fun hi(k: Int) = columns - 1.0 + if (isLane(names[k])) LANE_MARGIN else 0.0
        for (k in 0 until m) xs[k] = maxOf(xs[k], lo(k), if (k == 0) lo(k) else xs[k - 1] + gap(names[k - 1], names[k]))
        var limitHi = Double.MAX_VALUE
        for (k in m - 1 downTo 0) {
            xs[k] = minOf(xs[k], hi(k), limitHi)
            if (k > 0) limitHi = xs[k] - gap(names[k - 1], names[k])
        }
        names.forEachIndexed { k, n -> xOf[n] = xs[k] }
    }

    fun mean(of: List<String>): Double? = of.mapNotNull { xOf[it] }.takeIf { it.isNotEmpty() }?.average()

    // down: under the parents, unrelated roots spread evenly
    order.forEach { names ->
        place(names, names.mapIndexed { i, n -> mean(parentsOf(n)) ?: ((i + 0.5) * columns / names.size - 0.5) })
    }
    // up: over the children, so a chain rises straight from its end
    for (l in order.size - 2 downTo 0) {
        order[l].let { names -> place(names, names.map { n -> mean(childrenOf(n)) ?: xOf.getValue(n) }) }
    }
    // down again: whatever the lift moved, its dependants follow
    for (l in 1 until order.size) {
        order[l].let { names -> place(names, names.map { n -> mean(parentsOf(n)) ?: xOf.getValue(n) }) }
    }

    // Centre the whole drawing, waypoints included, in the width it was given.
    val shift = (columns - 1) / 2.0 - (xOf.values.min() + xOf.values.max()) / 2.0
    xOf.keys.toList().forEach { xOf[it] = xOf.getValue(it) + shift }

    val nodes = order.flatMapIndexed { l, names ->
        names.filterNot(::isLane).map { n -> PlacedSkill(Skills.forName(n)!!, l, xOf.getValue(n).toFloat()) }
    }
    val cross = onLine.mapNotNull { s ->
        s.prerequisites.filter { it !in inLine }
            .mapNotNull { p -> Skills.forName(p)?.let { CrossNeed(it.name, it.line) } }
            .takeIf { it.isNotEmpty() }
            ?.let { s.name to it }
    }.toMap()
    val levels = order.mapIndexed { l, names ->
        TreeLevel(
            tier = levelTier[l],
            startsTier = l == 0 || levelTier[l - 1] != levelTier[l],
            hasCrossNeed = names.any { it in cross },
        )
    }
    val lanes = chainOf.filterValues { it.isNotEmpty() }
        .mapValues { (_, chain) -> chain.map { xOf.getValue(it).toFloat() } }
    return TreeLayout(columns, levels, nodes, edges, cross, lanes)
}

/**
 * Edge crossings between adjacent levels in a placed layout - what the
 * ordering sweeps try to drive down, exposed so a test can hold them to it.
 */
internal fun TreeLayout.crossings(): Int {
    val at = nodes.associate { it.skill.name to it }
    val adjacent = edges.filter { at.getValue(it.to).level == at.getValue(it.from).level + 1 }
    var count = 0
    for (i in adjacent.indices) for (j in i + 1 until adjacent.size) {
        val a = adjacent[i]
        val b = adjacent[j]
        if (at.getValue(a.from).level != at.getValue(b.from).level) continue
        val top = at.getValue(a.from).x - at.getValue(b.from).x
        val bottom = at.getValue(a.to).x - at.getValue(b.to).x
        if (top * bottom < 0) count++
    }
    return count
}

/** The first available, unmastered technique in the graph's reading order; null when the path is done. */
internal fun TreeLayout.firstNext(mastered: Set<String>): Skills.SkillDef? =
    nodes.firstOrNull { SkillGuidance.isFrontier(it.skill, mastered) }?.skill
