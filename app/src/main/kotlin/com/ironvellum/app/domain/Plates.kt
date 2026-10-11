package com.ironvellum.app.domain

import kotlin.math.roundToInt

/**
 * What a barbell can be loaded with: a bar and pairs of plates, in one unit. All sums are kept in
 * hundredths of that unit as integers, so 1.25 steps never meet float noise.
 *
 * A load is "loadable" when the greedy fill (heaviest plate first, up to the pairs owned) lands on
 * it exactly. That is what the drawing shows, so the sheet only ever offers builds it can draw.
 * Greedy can miss a build a person would find by hand (25 x1 and 15 x2 reach 30 a side only as
 * 15 + 15), which is why loadability asks the greedy fill rather than the plate list.
 */
class PlateRack(val unit: LoadUnit, val bar: Double, pairs: Map<Double, Int>) {

    /** Plate size to pairs owned, only sizes with at least one pair, heaviest first. */
    val pairs: Map<Double, Int> = pairs.filterValues { it > 0 }.toSortedMap(compareByDescending { it })

    /** One side of a loaded bar, heaviest plate first. */
    class Build(val perSide: List<Double>, val total: Double) {
        val sideTotal: Double get() = perSide.sum()
    }

    enum class Kind { EXACT, BETWEEN, UNDER_BAR, OVER }

    /**
     * What a requested load comes to: [build] is the exact build, or the closest one below (the
     * bar alone under the bar, the full rack over it); [above] is the nearest build over it.
     */
    class Reading(val kind: Kind, val build: Build, val above: Build?)

    private val barH = hundredths(bar)

    private val sizesH: List<Int> = this.pairs.keys.map(::hundredths)

    /** Pairs owned per size, parallel to [sizesH]. */
    private val countsH: List<Int> = this.pairs.values.toList()

    /** Every plate size the unit makes, as the grid loads are built on. */
    private val gridH: Int = hundredths(PlateSizes.sizes(unit).last())

    /** The most the bar can hold. */
    val heaviest: Double get() = unhundred(barH + 2 * sizesH.indices.sumOf { sizesH[it] * countsH[it] })

    /** The smallest plate owned, or null with no plates. */
    val smallest: Double? get() = sizesH.lastOrNull()?.let(::unhundred)

    /** How far apart neighbouring loads sit at the light end: a pair of the smallest plate. */
    val step: Double? get() = smallest?.let { it * 2 }

    /** Every load the greedy fill reaches exactly, bar first, in the unit. */
    val loadable: List<Double> by lazy {
        val maxSide = (heaviestH() - barH) / 2
        (0..maxSide / gridH).mapNotNull { j ->
            val side = j * gridH
            if (fill(side).second == 0) unhundred(barH + 2 * side) else null
        }
    }

    /** The exact build for [total] (in the unit), or null when plates cannot make it. */
    fun build(total: Double): Build? {
        val sideH = hundredths(total) - barH
        if (sideH < 0 || sideH % 2 != 0) return null
        val (plates, rest) = fill(sideH / 2)
        return if (rest == 0) Build(plates.map(::unhundred), total) else null
    }

    /** The largest loadable at or below [total], or null under the bar. */
    fun nearestBelow(total: Double): Double? = loadable.lastOrNull { hundredths(it) <= hundredths(total) }

    /** The smallest loadable at or above [total], or null over the rack. */
    fun nearestAbove(total: Double): Double? = loadable.firstOrNull { hundredths(it) >= hundredths(total) }

    fun read(total: Double): Reading {
        val barOnly = Build(emptyList(), bar)
        build(total)?.let { return Reading(Kind.EXACT, it, null) }
        if (hundredths(total) < barH) return Reading(Kind.UNDER_BAR, barOnly, stepFrom(bar, up = true)?.let(::buildOf))
        if (hundredths(total) > heaviestH()) return Reading(Kind.OVER, buildOf(heaviest), null)
        val below = nearestBelow(total) ?: bar
        return Reading(Kind.BETWEEN, buildOf(below), nearestAbove(total)?.let(::buildOf))
    }

    /** The load that steps one build away from [total]: the next loadable up or down, or null at the end. */
    fun stepFrom(total: Double, up: Boolean): Double? =
        if (up) loadable.firstOrNull { hundredths(it) > hundredths(total) }
        else loadable.lastOrNull { hundredths(it) < hundredths(total) }

    private fun buildOf(total: Double): Build = build(total) ?: Build(emptyList(), bar)

    private fun heaviestH() = hundredths(heaviest)

    /** Greedy fill of [sideH] hundredths: the plates taken and what is left over. */
    private fun fill(sideH: Int): Pair<List<Int>, Int> {
        var rest = sideH
        val taken = ArrayList<Int>()
        for (i in sizesH.indices) {
            val n = minOf(countsH[i], rest / sizesH[i])
            repeat(n) { taken += sizesH[i] }
            rest -= n * sizesH[i]
        }
        return taken to rest
    }

    companion object {
        internal fun hundredths(x: Double): Int = (x * 100.0).roundToInt()
        internal fun unhundred(h: Int): Double = h / 100.0

        /** A plate or bar figure as printed: "25", "1.25". */
        fun number(x: Double): String =
            String.format(java.util.Locale.US, "%.2f", x).trimEnd('0').trimEnd('.')
    }
}

/** The plate sizes, bar choices and starting sets for each unit. */
object PlateSizes {
    private val KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    private val LB = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

    /** Every size the unit makes, heaviest first. */
    fun sizes(unit: LoadUnit): List<Double> = if (unit == LoadUnit.LB) LB else KG

    /** The bars the chooser offers before "Other", heaviest first. */
    fun bars(unit: LoadUnit): List<Double> = if (unit == LoadUnit.LB) listOf(45.0, 35.0, 25.0) else listOf(20.0, 15.0, 10.0)

    fun defaultBar(unit: LoadUnit): Double = bars(unit).first()

    /** A home and commercial-gym set: 297.5 kg or 490 lb at the most. */
    fun defaultPairs(unit: LoadUnit): Map<Double, Int> =
        if (unit == LoadUnit.LB) {
            mapOf(45.0 to 3, 35.0 to 1, 25.0 to 1, 10.0 to 2, 5.0 to 1, 2.5 to 1)
        } else {
            mapOf(25.0 to 2, 20.0 to 2, 15.0 to 1, 10.0 to 2, 5.0 to 2, 2.5 to 1, 1.25 to 1)
        }

    fun defaults(unit: LoadUnit) = PlateRack(unit, defaultBar(unit), defaultPairs(unit))
}
