package com.monarch.app.ui

import com.monarch.app.domain.Gacha
import com.monarch.app.ui.components.crestArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The crest catalogue and the drawn art are two hand-maintained lists that must
 * agree. `CrestMark` draws NOTHING for an id it has no art for, so a frame added
 * to `Gacha.CREST_FRAMES` without art would ship as an empty plate — a cosmetic
 * the gacha can award and which renders as a blank badge.
 *
 * Distinct ids are asserted too: two frames pointing at one drawable is the
 * other way this pair drifts, and it reads as a duplicate prize.
 */
class CrestArtCoverageTest {

    @Test
    fun `every catalogue frame has drawn art`() {
        Gacha.CREST_FRAMES.forEach { frame ->
            assertNotNull("crest ${frame.id} has no drawn mark", crestArt(frame.id))
        }
    }

    @Test
    fun `no two frames share a mark`() {
        val arts = Gacha.CREST_FRAMES.mapNotNull { crestArt(it.id) }
        assertEquals("two crests draw the same mark", arts.size, arts.toSet().size)
    }

    @Test
    fun `an unknown frame has no art rather than a stand-in`() {
        assertEquals(null, crestArt("not-a-crest"))
    }
}
