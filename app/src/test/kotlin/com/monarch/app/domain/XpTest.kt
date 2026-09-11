package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XpTest {

    @Test
    fun `award sums sets reps and bonus`() {
        assertEquals(15 * 4 + 40 + 25, Xp.award(sets = 4, reps = 40))
    }

    @Test
    fun `award clamps negative input`() {
        assertEquals(25, Xp.award(sets = -3, reps = -10))
    }

    @Test
    fun `level one at zero and just under threshold`() {
        assertEquals(1, Xp.levelFor(0))
        assertEquals(1, Xp.levelFor(99))
    }

    @Test
    fun `level two exactly at threshold`() {
        assertEquals(2, Xp.levelFor(100))
    }

    @Test
    fun `level three after accumulating level one and two costs`() {
        // 100 (L1->L2) + 200 (L2->L3) = 300
        assertEquals(3, Xp.levelFor(300))
        assertEquals(2, Xp.levelFor(299))
    }

    @Test
    fun `progress reports remainder into current level`() {
        val progress = Xp.progress(350)
        assertEquals(3, progress.level)
        assertEquals(50L, progress.intoLevel)
        assertEquals(300L, progress.needed)
    }

    @Test
    fun `negative xp treated as zero`() {
        assertEquals(1, Xp.levelFor(-50))
    }
}
