package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class XpRollsDueTest {

    /** Mirrors Repository.bankLevelRolls: pay what is due, then raise the mark. */
    private class Bank(var mark: Int = 0, var rolls: Int = 0) {
        fun levelUp(before: Int, after: Int) {
            rolls += Xp.rollsDue(before, after, mark)
            mark = maxOf(mark, after)
        }
    }

    @Test
    fun `claim unclaim claim pays the level once`() {
        val bank = Bank(mark = 5)
        bank.levelUp(5, 6) // claim crosses into 6
        bank.levelUp(5, 6) // unclaim dropped to 5, reclaim crosses again
        bank.levelUp(5, 6)
        assertEquals(1, bank.rolls)
    }

    @Test
    fun `a claim that crosses several levels pays each of them`() {
        val bank = Bank(mark = 2)
        bank.levelUp(2, 5)
        assertEquals(3, bank.rolls)
    }

    @Test
    fun `a refund then a bigger climb pays only the new levels`() {
        val bank = Bank(mark = 6)
        bank.levelUp(4, 7) // back from 4 over 5 and 6 (paid) to 7 (new)
        assertEquals(1, bank.rolls)
    }

    @Test
    fun `no rise and no paid history pays nothing negative`() {
        assertEquals(0, Xp.rollsDue(3, 3, 0))
        assertEquals(0, Xp.rollsDue(4, 3, 0))
        assertEquals(0, Xp.rollsDue(3, 6, 9))
    }
}
