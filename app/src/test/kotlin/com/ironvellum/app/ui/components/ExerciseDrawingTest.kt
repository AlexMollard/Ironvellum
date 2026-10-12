package com.ironvellum.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseDrawingTest {

    @Test
    fun `a drawing fits inside any slot, wide or tall, and is centred`() {
        // a landscape push-up (3:1) and a portrait pull-up (1:2) in square, wide and narrow slots
        val shapes = listOf(300f to 100f, 100f to 200f)
        val slots = listOf(36f to 36f, 200f to 120f, 80f to 200f, 400f to 40f)
        for ((aw, ah) in shapes) for ((sw, sh) in slots) {
            val fit = fitArt(aw, ah, sw, sh)
            assertTrue("$aw x $ah in $sw x $sh overflows width", fit.left >= 0f && fit.left + aw * fit.scale <= sw + 0.01f)
            assertTrue("$aw x $ah in $sw x $sh overflows height", fit.top >= 0f && fit.top + ah * fit.scale <= sh + 0.01f)
            assertEquals("centred horizontally", sw - (fit.left + aw * fit.scale), fit.left, 0.01f)
            assertEquals("centred vertically", sh - (fit.top + ah * fit.scale), fit.top, 0.01f)
        }
    }

    @Test
    fun `the art leaves the same share of the slot empty on every edge`() {
        val fit = fitArt(100f, 100f, 100f, 100f)
        assertEquals(100f * ART_INSET, fit.left, 0.01f)
    }

    @Test
    fun `a file parses into a window and registered frames`() {
        val art = ExerciseArtFile.parse("v1 10 20 300 400\r\n0 0 M0 0L1 1Z\r\n-5.5 7 M2 2L3 3Z\r\n")
        assertEquals(300f, art.width, 0f)
        assertEquals(2, art.frames.size)
        // frames are moved into the window: the window's own origin is subtracted
        assertEquals(-10f, art.frames[0].dx, 0f)
        assertEquals(-15.5f, art.frames[1].dx, 0f)
        assertEquals(-13f, art.frames[1].dy, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a file that is not the format is rejected`() {
        ExerciseArtFile.parse("P4 1 1\n")
    }
}
