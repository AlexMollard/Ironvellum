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
)

private const val MIN_CELL_DP = 72f
private const val MIN_COLUMNS = 4
private const val MAX_COLUMNS = 6

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
 * Order: a few down-and-up barycentre sweeps (each node sorts by the mean
 * position of its parents, then of its children), keeping the arrangement
 * with the fewest crossings.
 *
 * Slots: each level is placed top-down with every node as near the mean of its
 * parents as the one-slot minimum gap allows, so a chain stays vertical and a
 * fork spreads evenly under its parent.
 */
internal fun treeLayout(line: String, columns: Int): TreeLayout {
    require(columns >= 1) { "columns must be at least 1" }
    val onLine = Skills.ALL.filter { it.line == line }
    val inLine = onLine.map { it.name }.toSet()
    val parents: Map<String, List<String>> =
        onLine.associate { s -> s.name to s.prerequisites.filter { it in inLine } }
    val children: Map<String, List<String>> =
        onLine.associate { s -> s.name to onLine.filter { s.name in parents.getValue(it.name) }.map { it.name } }

    // sub-level inside a tier: how many same-tier prerequisites deep
    val depth = HashMap<String, Int>()
    fun depthOf(s: Skills.SkillDef): Int = depth.getOrPut(s.name) {
        parents.getValue(s.name)
            .mapNotNull { Skills.forName(it) }
            .filter { it.tier == s.tier }
            .maxOfOrNull { depthOf(it) + 1 } ?: 0
    }

    val order = mutableListOf<MutableList<String>>()
    val levelTier = mutableListOf<Int>()
    onLine.groupBy { it.tier to depthOf(it) }
        .toSortedMap(compareBy({ it.first }, { it.second }))
        .forEach { (key, members) ->
            members.map { it.name }.chunked(columns).forEach {
                order += it.toMutableList()
                levelTier += key.first
            }
        }

    val levelOf = HashMap<String, Int>()
    order.forEachIndexed { l, names -> names.forEach { levelOf[it] = l } }
    val edges = onLine.flatMap { s -> parents.getValue(s.name).map { TreeEdge(it, s.name) } }

    // ---- ordering within levels

    fun norm(): Map<String, Double> = buildMap {
        order.forEach { names -> names.forEachIndexed { i, n -> put(n, (i + 0.5) / names.size) } }
    }

    fun orderCrossings(): Int {
        val p = norm()
        val adjacent = edges.filter { levelOf.getValue(it.to) == levelOf.getValue(it.from) + 1 }
        var count = 0
        for (i in adjacent.indices) for (j in i + 1 until adjacent.size) {
            val a = adjacent[i]
            val b = adjacent[j]
            if (levelOf[a.from] != levelOf[b.from]) continue
            val top = p.getValue(a.from) - p.getValue(b.from)
            val bottom = p.getValue(a.to) - p.getValue(b.to)
            if (top * bottom < 0) count++
        }
        return count
    }

    fun sweep(l: Int, neighbours: Map<String, List<String>>) {
        val p = norm()
        val key = order[l].associateWith { n ->
            neighbours.getValue(n).map { p.getValue(it) }.takeIf { it.isNotEmpty() }?.average() ?: p.getValue(n)
        }
        order[l] = order[l].sortedBy { key.getValue(it) }.toMutableList()
    }

    var best = order.map { it.toList() }
    var bestCrossings = orderCrossings()
    repeat(4) {
        for (l in 1 until order.size) sweep(l, parents)
        for (l in order.size - 2 downTo 0) sweep(l, children)
        val now = orderCrossings()
        if (now < bestCrossings) {
            bestCrossings = now
            best = order.map { it.toList() }
        }
    }
    best.forEachIndexed { l, names -> order[l] = names.toMutableList() }

    // ---- slots

    val xOf = HashMap<String, Double>()

    // Puts one level as near its desired slots as a one-slot gap and the row allow.
    fun place(names: List<String>, desired: List<Double>) {
        val m = names.size
        // colliding runs merge at their mean
        class Run(var start: Double, var count: Int)
        val runs = ArrayList<Run>()
        desired.forEach { d ->
            var cur = Run(d, 1)
            while (runs.isNotEmpty() && runs.last().start + runs.last().count > cur.start) {
                val prev = runs.removeAt(runs.lastIndex)
                cur = Run(
                    (prev.start * prev.count + (cur.start - prev.count) * cur.count) / (prev.count + cur.count),
                    prev.count + cur.count,
                )
            }
            runs += cur
        }
        val xs = DoubleArray(m)
        var i = 0
        runs.forEach { r -> repeat(r.count) { k -> xs[i++] = r.start + k } }
        // keep inside the row, still a slot apart
        for (k in 0 until m) xs[k] = maxOf(xs[k], if (k == 0) 0.0 else xs[k - 1] + 1.0)
        var limit = columns - 1.0
        for (k in m - 1 downTo 0) {
            xs[k] = minOf(xs[k], limit)
            limit = xs[k] - 1.0
        }
        names.forEachIndexed { k, n -> xOf[n] = xs[k] }
    }

    fun mean(of: List<String>): Double? = of.mapNotNull { xOf[it] }.takeIf { it.isNotEmpty() }?.average()

    // down: under the parents, unrelated roots spread evenly
    order.forEach { names ->
        place(names, names.mapIndexed { i, n -> mean(parents.getValue(n)) ?: ((i + 0.5) * columns / names.size - 0.5) })
    }
    // up: over the children, so a chain rises straight from its end
    for (l in order.size - 2 downTo 0) {
        order[l].let { names -> place(names, names.map { n -> mean(children.getValue(n)) ?: xOf.getValue(n) }) }
    }
    // down again: whatever the lift moved, its dependants follow
    for (l in 1 until order.size) {
        order[l].let { names -> place(names, names.map { n -> mean(parents.getValue(n)) ?: xOf.getValue(n) }) }
    }

    val nodes = order.flatMapIndexed { l, names ->
        names.map { n -> PlacedSkill(Skills.forName(n)!!, l, xOf.getValue(n).toFloat()) }
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
    return TreeLayout(columns, levels, nodes, edges, cross)
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
