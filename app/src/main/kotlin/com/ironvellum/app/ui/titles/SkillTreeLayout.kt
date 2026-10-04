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

/** Levels up to this many entries (nodes and waypoints) have every order tried when settling the crossings. */
private const val MAX_PERMUTED = 7

/** The most the drawing may be stretched sideways to fill the row; past this a fork reads as a flat bar. */
private const val MAX_SPREAD = 2.2

/**
 * How far toward the edge slot the outermost node may be stretched, as a share of the half-width. All the way
 * to the edge left two-branch paths (Push) hugging the screen sides with an empty channel between them.
 */
private const val EDGE_REACH = 0.7

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

    // An edge whose prerequisite is already an ancestor of another prerequisite of the same technique says
    // nothing the path does not already say (Wall Handstand leads to Wall HSPU through the negative), so it
    // is not drawn; every prerequisite is still listed on the technique itself.
    val ancestors = HashMap<String, Set<String>>()
    fun ancestorsOf(n: String): Set<String> = ancestors.getOrPut(n) {
        parents.getValue(n).flatMap { setOf(it) + ancestorsOf(it) }.toSet()
    }
    val edges = onLine.flatMap { s ->
        val direct = parents.getValue(s.name)
        direct.filter { p -> direct.none { q -> q != p && p in ancestorsOf(q) } }.map { TreeEdge(it, s.name) }
    }
    val groups = onLine.groupBy { it.tier to depthOf(it) }
        .toSortedMap(compareBy({ it.first }, { it.second }))
    val limit = groups.keys.associateWith { columns }.toMutableMap()

    val order = mutableListOf<MutableList<String>>()
    val levelTier = mutableListOf<Int>()
    val levelOf = HashMap<String, Int>()

    // One waypoint name per level an edge passes. Edges leaving the same
    // prerequisite share their waypoints for as long as they run together,
    // and so do edges entering the same dependant, so a hub draws one trunk
    // instead of a lane per edge; an edge on its own keeps its own lane.
    fun chainsFor(): Map<TreeEdge, List<String>> {
        fun crossing(e: TreeEdge) = levelOf.getValue(e.from) + 1 until levelOf.getValue(e.to)
        val leaving = HashMap<Pair<String, Int>, Int>()
        val entering = HashMap<Pair<String, Int>, Int>()
        edges.forEach { e ->
            crossing(e).forEach { l ->
                leaving.merge(e.from to l, 1, Int::plus)
                entering.merge(e.to to l, 1, Int::plus)
            }
        }
        return edges.associateWith { e ->
            val top = levelOf.getValue(e.from)
            val bottom = levelOf.getValue(e.to)
            crossing(e).map { l ->
                val out = leaving.getValue(e.from to l) > 1
                val into = entering.getValue(e.to to l) > 1
                when {
                    out && (!into || l - top <= bottom - l) -> "${LANE_MARK}out:${e.from}@$l"
                    into -> "${LANE_MARK}in:${e.to}@$l"
                    else -> "${LANE_MARK}${e.from}>${e.to}@$l"
                }
            }
        }
    }
    var chainOf: Map<TreeEdge, List<String>> = emptyMap()

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
        chainOf = chainsFor()
        val passing = IntArray(order.size)
        chainOf.values.flatten().toSet().forEach { passing[it.substringAfterLast('@').toInt()]++ }
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
    val links = LinkedHashSet<Pair<String, String>>()
    // how many edges ride each link: a crossing of two shared trunks is every pair of their edges crossing
    val riders = HashMap<Pair<String, String>, Int>()
    chainOf.values.flatten().toSet().forEach { order[it.substringAfterLast('@').toInt()] += it }
    edges.forEach { e ->
        (listOf(e.from) + chainOf.getValue(e) + e.to).zipWithNext().forEach { (a, b) ->
            riders.merge(a to b, 1, Int::plus)
            if (links.add(a to b)) {
                upstream.getOrPut(b) { mutableListOf() } += a
                downstream.getOrPut(a) { mutableListOf() } += b
            }
        }
    }
    order.forEachIndexed { l, names -> names.forEach { levelOf[it] = l } }
    val linkList = links.toList()
    fun parentsOf(n: String): List<String> = upstream[n].orEmpty()
    fun childrenOf(n: String): List<String> = downstream[n].orEmpty()

    // ---- ordering within levels

    fun norm(): Map<String, Double> = buildMap {
        order.forEach { names -> names.forEachIndexed { i, n -> put(n, (i + 0.5) / names.size) } }
    }

    fun orderCrossings(): Int {
        val p = norm()
        var count = 0
        for (i in linkList.indices) for (j in i + 1 until linkList.size) {
            val a = linkList[i]
            val b = linkList[j]
            if (levelOf[a.first] != levelOf[b.first]) continue
            val top = p.getValue(a.first) - p.getValue(b.first)
            val bottom = p.getValue(a.second) - p.getValue(b.second)
            if (top * bottom < 0) count += riders.getValue(a) * riders.getValue(b)
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

    val declared = order.map { it.toList() }
    var best = declared
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
    // The barycentre order is often a swap or two short of the best. Settle each level in turn: try every
    // order of a level small enough to try and keep the one that crosses least against its neighbours.
    fun crossingsAround(l: Int): Int {
        val index = HashMap<String, Int>()
        for (k in maxOf(0, l - 1)..minOf(order.lastIndex, l + 1)) order[k].forEachIndexed { i, n -> index[n] = i }
        val near = linkList.filter { levelOf[it.first] == l - 1 || levelOf[it.first] == l }
        var count = 0
        for (i in near.indices) for (j in i + 1 until near.size) {
            val a = near[i]
            val b = near[j]
            if (levelOf[a.first] != levelOf[b.first]) continue
            val top = index.getValue(a.first) - index.getValue(b.first)
            val bottom = index.getValue(a.second) - index.getValue(b.second)
            if (top * bottom < 0) count += riders.getValue(a) * riders.getValue(b)
        }
        return count
    }
    fun permutations(items: List<String>): Sequence<List<String>> =
        if (items.size <= 1) sequenceOf(items)
        else items.asSequence().flatMap { head -> permutations(items - head).map { listOf(head) + it } }
    fun settle() {
    var rounds = 0
    do {
        var improved = false
        for (l in order.indices) {
            if (order[l].size !in 2..MAX_PERMUTED) continue
            var least = crossingsAround(l)
            var keep = order[l].toList()
            for (candidate in permutations(keep)) {
                order[l] = candidate.toMutableList()
                val c = crossingsAround(l)
                if (c < least) {
                    least = c
                    keep = candidate
                    improved = true
                }
            }
            order[l] = keep.toMutableList()
        }
    } while (improved && ++rounds < 4)
    }
    // From the swept order, from declaration order and from the swept order mirrored: they settle in
    // different valleys, and the least-crossed one wins (the swept one on a tie).
    var chosen = best
    var chosenCrossings = Int.MAX_VALUE
    listOf(best, declared, best.map { it.reversed() }).forEach { start ->
        start.forEachIndexed { l, names -> order[l] = names.toMutableList() }
        settle()
        val c = orderCrossings()
        if (c < chosenCrossings) {
            chosenCrossings = c
            chosen = order.map { it.toList() }
        }
    }
    chosen.forEachIndexed { l, names -> order[l] = names.toMutableList() }

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

    // Spread: a branchy path packs into the middle of the row, leaving the margins empty. Stretch the whole
    // drawing about its centre until its outermost node (or lane) reaches [EDGE_REACH] of the way to the edge.
    // Only gaps grow, so nothing that was clear of anything else closes up, and a lone chain (nothing
    // off-centre) stays where it is.
    val mid = (columns - 1) / 2.0
    var spread = MAX_SPREAD
    xOf.forEach { (n, x) ->
        val off = kotlin.math.abs(x - mid)
        if (off > 1e-9) spread = minOf(spread, (mid * EDGE_REACH + if (isLane(n)) LANE_MARGIN else 0.0) / off)
    }
    if (spread > 1.0) xOf.keys.toList().forEach { xOf[it] = mid + (xOf.getValue(it) - mid) * spread * (1 - 1e-6) }

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

/**
 * The first prerequisite on another path that holds this path back: the first
 * unmastered technique, in reading order, whose cross-path need is not yet
 * mastered. Null when nothing here waits on another path.
 */
internal fun TreeLayout.firstBlocker(mastered: Set<String>): CrossNeed? =
    nodes.firstNotNullOfOrNull { n ->
        if (n.skill.name in mastered) null
        else crossNeeds[n.skill.name]?.firstOrNull { it.skill !in mastered }
    }

/**
 * The first available, unmastered technique in the graph's reading order; null
 * when none is open - the path is done, or what is left waits on another path
 * (see [firstBlocker]).
 */
internal fun TreeLayout.firstNext(mastered: Set<String>): Skills.SkillDef? =
    nodes.firstOrNull { SkillGuidance.isFrontier(it.skill, mastered) }?.skill

/**
 * The technique a path should open on: the first available, unmastered one;
 * failing that the furthest mastered one in reading order (the lifter's most
 * recent ground, there being no claim dates here). Null for an empty path.
 */
internal fun TreeLayout.openTarget(mastered: Set<String>): String? =
    firstNext(mastered)?.name ?: nodes.lastOrNull { it.skill.name in mastered }?.skill?.name
