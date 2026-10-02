package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Taps land on what is drawn. The hit-test runs on the figure's own transform ([FigureGeometry], the one
 * the canvas draws with), so each case below is a canvas pixel, mirrored sides and both views included.
 */
class FigureHitTestTest {

    private val bodies = listOf("male" to BodyFigures.MALE, "female" to BodyFigures.FEMALE)

    /** No slop: a point inside a region must resolve to exactly the muscle drawn there. */
    private val exact = HitSlop(gap = 0f, tiny = 0f, tinyBox = 0f)
    private val width = 800f
    private val height = 600f

    private fun inside(poly: List<Pair<Float, Float>>, x: Float, y: Float) =
        distanceToPolygon(poly, x, y) == 0f || distanceToPolygon(poly, -x, y) == 0f

    private fun centroid(poly: List<Pair<Float, Float>>): Pair<Float, Float> {
        var a = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in poly.indices) {
            val (x0, y0) = poly[i]
            val (x1, y1) = poly[(i + 1) % poly.size]
            val cross = x0.toDouble() * y1 - x1.toDouble() * y0
            a += cross
            cx += (x0 + x1) * cross
            cy += (y0 + y1) * cross
        }
        return (cx / (3 * a)).toFloat() to (cy / (3 * a)).toFloat()
    }

    /**
     * A point in region [i] that a later region does not cover: its centroid when that qualifies,
     * otherwise the deepest point of a grid over its bounds (a concave region's centroid can lie outside
     * it, and a parent's can sit under its inlay).
     */
    private fun visiblePoint(regions: List<Region>, i: Int): Pair<Float, Float>? {
        val poly = regions[i].points
        val later = regions.drop(i + 1).map { it.points }
        fun visible(p: Pair<Float, Float>) = inside(poly, p.first, p.second) && later.none { inside(it, p.first, p.second) }
        centroid(poly).takeIf(::visible)?.let { return it }
        val x0 = poly.minOf { it.first }
        val x1 = poly.maxOf { it.first }
        val y0 = poly.minOf { it.second }
        val y1 = poly.maxOf { it.second }
        var best: Pair<Float, Float>? = null
        var bestDepth = -1f
        val steps = 80
        for (a in 0..steps) for (b in 0..steps) {
            val p = (x0 + (x1 - x0) * a / steps) to (y0 + (y1 - y0) * b / steps)
            if (!visible(p)) continue
            val depth = minOf(
                poly.indices.minOf { k ->
                    val (ax, ay) = poly[k]
                    val (bx, by) = poly[(k + 1) % poly.size]
                    distanceToSegmentForTest(p.first, p.second, ax, ay, bx, by)
                },
                Float.MAX_VALUE,
            )
            if (depth > bestDepth) { bestDepth = depth; best = p }
        }
        return best
    }

    private fun distanceToSegmentForTest(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        // A two-vertex polygon's distance is the distance to its segment.
        return distanceToPolygon(listOf(ax to ay, bx to by), px, py)
    }

    @Test fun everyRegionIsHitAtAVisiblePointOnBothSides() {
        for ((name, figure) in bodies) {
            val g = FigureGeometry(figure, width, height)
            for (view in FigureView.entries) {
                val regions = figure.regions(view)
                regions.indices.forEach { i ->
                    val region = regions[i]
                    val p = visiblePoint(regions, i)
                    assertTrue("$name $view ${region.muscle} #$i has no visible point", p != null)
                    for (side in listOf(1f, -1f)) {
                        val at = g.toCanvas(view, p!!.first, p.second, side)
                        assertEquals(
                            "$name $view ${region.muscle} #$i side $side",
                            region.muscle,
                            hitMuscle(g, at.x, at.y, exact),
                        )
                    }
                }
            }
        }
    }

    @Test fun inlaysWinOverTheirParents() {
        // The rhomboids are laid over the trapezius and the brachialis over the biceps: a tap in the
        // inlay must not fall through to the parent underneath.
        for ((name, figure) in bodies) {
            val g = FigureGeometry(figure, width, height)
            val back = figure.regions(FigureView.BACK)
            val i = back.indexOfLast { it.muscle == Muscle.RHOMBOIDS }
            val p = visiblePoint(back, i)!!
            val at = g.toCanvas(FigureView.BACK, p.first, p.second)
            assertEquals(name, Muscle.RHOMBOIDS, hitMuscle(g, at.x, at.y, exact))
        }
    }

    @Test fun emptySpaceHitsNothing() {
        for ((name, figure) in bodies) {
            val g = FigureGeometry(figure, width, height)
            val slop = HitSlop(gap = 4f, tiny = 6f, tinyBox = 14f)
            assertNull("$name corner", hitMuscle(g, 1f, 1f, slop))
            assertNull("$name far corner", hitMuscle(g, width - 1f, height - 1f, slop))
            assertNull("$name gap between figures", hitMuscle(g, g.slot, height / 2f, slop))
            assertNull("$name beside the front figure", hitMuscle(g, 2f, height / 2f, slop))
        }
    }

    @Test fun aTapJustOutsideARegionStillCountsWithinTheSlop() {
        // Two pixels under the lowest point of the calf: outside it, inside a 4px slop.
        for ((name, figure) in bodies) {
            val g = FigureGeometry(figure, width, height)
            val calf = figure.regionsOf(Muscle.CALVES, FigureView.BACK).flatMap { it.points }
            val lowest = calf.maxBy { it.second }
            val at = g.toCanvas(FigureView.BACK, lowest.first, lowest.second)
            val y = at.y + 2f
            assertNull("$name without slop", hitMuscle(g, at.x, y, exact).takeIf { it == Muscle.CALVES })
            assertEquals("$name with slop", Muscle.CALVES, hitMuscle(g, at.x, y, HitSlop(4f, 6f, 14f)))
        }
    }

    @Test fun everyDrawnMuscleHasBounds() {
        for ((name, figure) in bodies) {
            val g = FigureGeometry(figure, width, height)
            for (muscle in figure.drawn) {
                val r = muscleBounds(g, muscle)
                assertTrue("$name $muscle has no bounds", r != null && r.width >= 1f && r.height >= 1f)
            }
        }
    }

    @Test fun tappingTheLitMuscleOrEmptySpaceClearsIt() {
        assertEquals(Muscle.LATS, nextSelection(null, Muscle.LATS))
        assertEquals(Muscle.QUADS, nextSelection(Muscle.LATS, Muscle.QUADS))
        assertNull(nextSelection(Muscle.LATS, Muscle.LATS))
        assertNull(nextSelection(Muscle.LATS, null))
        assertNull(nextSelection(null, null))
    }

    @Test fun distanceIsZeroInsideAndMeasuredToTheNearestEdgeOutside() {
        val square = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
        assertEquals(0f, distanceToPolygon(square, 0.5f, 0.5f), 0f)
        assertEquals(0.5f, distanceToPolygon(square, 1.5f, 0.5f), 1e-6f)
        assertEquals(Math.sqrt(2.0).toFloat(), distanceToPolygon(square, 2f, 2f), 1e-6f)
    }
}
