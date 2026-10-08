package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.Xp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InscriptionNoteTest {

    @Test
    fun `a level-up that banked one says so`() {
        assertEquals("+1 inscription waiting in the Veil", inscriptionNote(1))
    }

    @Test
    fun `several are counted`() {
        assertEquals("+3 inscriptions waiting in the Veil", inscriptionNote(3))
    }

    @Test
    fun `nothing is said when nothing was banked`() {
        assertNull(inscriptionNote(0))
    }

    @Test
    fun `a first level-up banks one, and a level that already paid banks none`() {
        // The count handed to the page is Xp.rollsDue, so a level paid before (a refund and a climb back) reads none.
        assertEquals(1, Xp.rollsDue(levelBefore = 1, levelAfter = 2, paidThrough = 1))
        assertNull(inscriptionNote(Xp.rollsDue(levelBefore = 1, levelAfter = 2, paidThrough = 2)))
    }
}
