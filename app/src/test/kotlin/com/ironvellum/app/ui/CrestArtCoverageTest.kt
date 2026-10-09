package com.ironvellum.app.ui

import androidx.compose.ui.graphics.Color
import com.ironvellum.app.domain.CrestGroup
import com.ironvellum.app.domain.CrestTone
import com.ironvellum.app.domain.Crests
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.ui.components.CREST_SHAPES
import com.ironvellum.app.ui.components.MAX_WASH
import com.ironvellum.app.ui.components.crestLook
import com.ironvellum.app.ui.components.hasCrestMark
import com.ironvellum.app.ui.theme.Metal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crest catalogue and the drawn marks are two lists that must agree. A crest drawn without a mark is
 * an empty badge, so all 28 are checked here, and an id the catalogue does not know draws nothing and
 * wears no metal: there is no `else` that paints a new crest as the prism.
 */
class CrestArtCoverageTest {

    @Test
    fun `all 28 crests have a mark and a look`() {
        assertEquals(28, Crests.ALL.size)
        assertEquals("ids are unique", 28, Crests.ALL.map { it.id }.toSet().size)
        Crests.ALL.forEach { crest ->
            assertTrue("crest ${crest.id} has no drawn mark", hasCrestMark(crest.id))
            assertNotNull("crest ${crest.id} has no look", crestLook(crest.id))
        }
        assertEquals("no mark without a crest", Crests.ALL.map { it.id }.toSet(), CREST_SHAPES.keys)
    }

    @Test
    fun `no two crests share a mark`() {
        val drawn = Crests.ALL.map { c -> CREST_SHAPES.getValue(c.id).joinToString("|") { it.d } }
        assertEquals("two crests draw the same mark", drawn.size, drawn.toSet().size)
    }

    @Test
    fun `every mark uses a bounded wash and only Void has a dark centre`() {
        CREST_SHAPES.forEach { (id, shapes) ->
            assertTrue("$id has no shapes", shapes.isNotEmpty())
            shapes.forEach {
                assertTrue("$id: wash ${it.wash} is over $MAX_WASH, a solid fill", it.wash in 0f..MAX_WASH)
                assertTrue("$id: a stroke with no width", it.width > 0f)
                assertTrue("$id: invalid stroke alpha", it.strokeAlpha in 0f..1f)
                if (it.darkGround) assertEquals("void", id)
            }
        }
    }

    @Test
    fun `an unknown crest has no mark, no look and no stand-in`() {
        assertFalse(hasCrestMark("not-a-crest"))
        assertNull(crestLook("not-a-crest"))
        assertNull(crestLook(""))
    }

    @Test
    fun `every tone has its own ramp and the extra ones are fixed colours`() {
        CrestTone.entries.forEach { assertNotNull(Metal.of(it)) }
        assertSame(Metal.Common, Metal.of(CrestTone.Iron))
        assertSame(Metal.Rare, Metal.of(CrestTone.Bronze))
        assertSame(Metal.Fabled, Metal.of(CrestTone.Gold))
        assertSame(Metal.Masterwork, Metal.of(CrestTone.Prism))
        assertNotSame(Metal.Fabled, Metal.of(CrestTone.Emerald))
        // Fixed hex, not the accent: Emerald, Red and Ink read the same for every lifter.
        assertEquals(Color(0xFF34D399), Metal.Emerald.tone)
        assertEquals(Color(0xFFEF5350), Metal.Red.tone)
        assertEquals(Color(0xFFA3A099), Metal.Ink.tone)
        assertFalse(Metal.Emerald.prism || Metal.Red.prism || Metal.Ink.prism)
    }

    @Test
    fun `only the prism crests are prisms and the ladder never wears one`() {
        Crests.ALL.forEach { crest ->
            val look = crestLook(crest.id)!!
            assertEquals(crest.id, crest.art == CrestTone.Prism, look.art.prism)
            assertEquals(crest.id, crest.ring == CrestTone.Prism, look.ring.prism)
        }
        Veil.CREST_LADDER.forEach { assertFalse(it, crestLook(it)!!.art.prism) }
        assertEquals(setOf("sovereign", "ironvellum", "masterwork"), Crests.ALL.filter { it.double }.map { it.id }.toSet())
    }

    @Test
    fun `the roller can draw only the six chance crests`() {
        assertEquals(Crests.CHANCE.map { it.id }, Gacha.CREST_FRAMES.map { it.id })
        assertEquals(setOf("aurora", "void", "masterwork", "ledger", "margin", "ashenking"), Gacha.CREST_FRAMES.map { it.id }.toSet())
        assertTrue(Crests.CHANCE.all { it.group == CrestGroup.Veil })
    }
}
