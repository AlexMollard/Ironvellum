package com.monarch.app.ui

import com.monarch.app.ui.theme.inkEdgePoints
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
    fun `edge closes back where it started`() {
        val pts = inkEdgePoints(400f, 200f, 3f, salt = 5)
        val startX = pts[0]
        val startY = pts[1]
        val lastX = pts[pts.size - 2]
        val lastY = pts[pts.size - 1]
        // The path is closed by the caller, so the final point must already be
        // near the origin or the closing segment cuts across the surface.
        assertTrue(
            "final point ($lastX, $lastY) is too far from the start ($startX, $startY)",
            abs(lastX - startX) <= 12f && abs(lastY - startY) <= 12f,
        )
    }
}
