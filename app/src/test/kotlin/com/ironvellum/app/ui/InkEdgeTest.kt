package com.ironvellum.app.ui

import com.ironvellum.app.ui.theme.inkEdgePoints
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ink edge's contract is visual, but two parts of it are provable here and
 * both have a plausible failure mode that a screenshot would not catch:
 * a crawling edge (re-rolled randomness) and an edge that wanders far enough to
 * clip content.
 */
class InkEdgeTest {

    @Test
    fun `same surface draws the same edge every time`() {
        // The whole UI visibly crawls if this stops holding, because Compose
        // re-runs createOutline on recomposition.
        val first = inkEdgePoints(width = 1080f, height = 420f, wobble = 4f, salt = 11)
        val second = inkEdgePoints(width = 1080f, height = 420f, wobble = 4f, salt = 11)
        assertArrayEquals(first, second, 0f)
    }

    @Test
    fun `different surfaces do not share one traced outline`() {
        val panel = inkEdgePoints(1080f, 420f, 4f, salt = 11)
        val button = inkEdgePoints(1080f, 420f, 4f, salt = 37)
        assertFalse(
            "distinct salts must produce distinct edges",
            panel.contentEquals(button),
        )

        val tall = inkEdgePoints(1080f, 900f, 4f, salt = 11)
        assertFalse(
            "a differently sized surface must get its own edge",
            panel.take(4).toFloatArray().contentEquals(tall.take(4).toFloatArray()),
        )
    }

    @Test
    fun `no point wanders further than the wobble allowance`() {
        // Past its allowance the edge stops looking drawn and starts clipping
        // the content inside the panel.
        val w = 600f
        val h = 300f
        val wobble = 5f
        val pts = inkEdgePoints(w, h, wobble, salt = 3)
        var i = 0
        while (i < pts.size) {
            val x = pts[i]
            val y = pts[i + 1]
            val dx = minOf(abs(x - 0f), abs(x - w))
            val dy = minOf(abs(y - 0f), abs(y - h))
            // Every point sits on one of the four edges, so at least one axis
            // must be within the allowance of that edge.
            assertTrue(
                "point ($x, $y) is off every edge by more than $wobble",
                dx <= wobble + 0.01f || dy <= wobble + 0.01f,
            )
            i += 2
        }
    }

    @Test
    fun `the ring never repeats a point, least of all the corner it started on`() {
        // The old contract asked the LAST point to land near the first, which
        // is what put two points ~1px apart on the top-left corner. Each point
        // becomes a quadratic control point, so that pair pinched the curve
        // into a kink and every wobbly panel showed an odd top-left corner.
        // The path is closed by Path.close(); the ring must not close itself.
        val w = 400f
        val h = 200f
        val pts = inkEdgePoints(w, h, wobble = 3f, salt = 5)
        val n = pts.size / 2
        // Shortest facet this surface should produce, halved for slack: the
        // seam is a real segment, not a repeat.
        val shortest = minOf(w, h) / 16f / 2f
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = pts[i * 2] - pts[j * 2]
            val dy = pts[i * 2 + 1] - pts[j * 2 + 1]
            val gap = kotlin.math.sqrt(dx * dx + dy * dy)
            assertTrue(
                "points $i and $j are ${gap}px apart, a degenerate segment that kinks the curve",
                gap >= shortest,
            )
        }
    }

    @Test
    fun `the ring walks every edge and comes back around`() {
        // Dropping the duplicate must not drop the edge itself: the outline
        // still has to reach all four extremes.
        val w = 400f
        val h = 200f
        val pts = inkEdgePoints(w, h, wobble = 3f, salt = 5)
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var i = 0
        while (i < pts.size) {
            maxX = maxOf(maxX, pts[i])
            maxY = maxOf(maxY, pts[i + 1])
            i += 2
        }
        assertTrue("the ring never reached the right edge", maxX >= w - 6f)
        assertTrue("the ring never reached the bottom edge", maxY >= h - 6f)
    }
}
