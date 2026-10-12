package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupersetsTest {

    // ----------------------------------------------------------- groups in a list

    @Test
    fun `only neighbours sharing a number are a superset`() {
        val raw = listOf(1, 1, null, 2, null, 2, 3)
        assertEquals(listOf(0..1), Supersets.runs(raw))
        // A number left behind by a removal or a reorder is not a superset: 2 is split, 3 is alone.
        assertEquals(listOf(1, 1, null, null, null, null, null), Supersets.effective(raw))
        assertEquals(listOf(1, 1, null, null, null, null, null), Supersets.numbers(raw))
    }

    @Test
    fun `supersets are numbered in the order the trial reaches them`() {
        val raw = listOf(null, 7, 7, null, 4, 4, 4)
        assertEquals(listOf(null, 1, 1, null, 2, 2, 2), Supersets.numbers(raw))
    }

    // ----------------------------------------------------------- linking

    @Test
    fun `linking an exercise with the next one makes a pair and moves nothing`() {
        val layout = Supersets.link(listOf(null, null, null), a = 1, b = 2)!!
        assertEquals(listOf(0, 1, 2), layout.order)
        assertEquals(listOf(null, 1, 1), layout.groups)
    }

    @Test
    fun `a pair takes a third to make a triple, and a fourth is refused`() {
        val pair = listOf(1, 1, null, null)
        val triple = Supersets.link(pair, a = 1, b = 2)!!
        assertEquals(listOf(1, 1, 1, null), triple.groups)
        assertFalse("never more than three", Supersets.canLink(triple.groups, 2, 3))
        // Two pairs would be four.
        assertNull(Supersets.link(listOf(1, 1, 2, 2), a = 1, b = 2))
        // Already together.
        assertNull(Supersets.link(pair, a = 0, b = 1))
    }

    @Test
    fun `linking a distant partner moves it next to the group and keeps the rest in order`() {
        // Bench(0) Row(1) Press(2) Curl(3): link Bench with Curl.
        val after = Supersets.link(listOf(null, null, null, null), a = 0, b = 3)!!
        assertEquals(listOf(0, 3, 1, 2), after.order)
        assertEquals(listOf(1, 1, null, null), after.groups)
        // An earlier partner goes in front.
        val before = Supersets.link(listOf(null, null, null, null), a = 2, b = 0)!!
        assertEquals(listOf(1, 0, 2, 3), before.order)
        assertEquals(listOf(null, 1, 1, null), before.groups)
    }

    @Test
    fun `linking brings a whole group along and joins the numbers`() {
        // Curl(0) alone, then Bench+Row as group 5: Curl links with Row, so the pair comes with it.
        val layout = Supersets.link(listOf(null, null, 5, 5, null), a = 0, b = 3)!!
        assertEquals(listOf(0, 2, 3, 1, 4), layout.order)
        assertEquals(listOf(5, 5, 5, null, null), layout.groups)
    }

    @Test
    fun `a new group gets a number nobody uses`() {
        val layout = Supersets.link(listOf(4, 4, null, null), a = 2, b = 3)!!
        assertEquals(listOf(4, 4, 5, 5), layout.groups)
    }

    @Test
    fun `unlink ends the whole group and leaves the order`() {
        assertEquals(listOf(null, null, null, null), Supersets.unlink(listOf(1, 1, 1, 2), 1))
        assertEquals(listOf(null, null, 2, 2), Supersets.unlink(listOf(1, 1, 2, 2), 0))
        // Not in a superset: nothing to end.
        assertEquals(listOf(3, 3, null), Supersets.unlink(listOf(3, 3, null), 2))
    }

    // ----------------------------------------------------------- moving

    @Test
    fun `members swap places inside their group`() {
        val move = Supersets.move(listOf(null, 1, 1, null), index = 1, up = false)!!
        assertEquals(listOf(0, 2, 1, 3), move.order)
        assertEquals(listOf(null, 1, 1, null), move.groups)
    }

    @Test
    fun `a lone exercise steps over a whole group, never into it`() {
        val down = Supersets.move(listOf(null, 1, 1, 1), index = 0, up = false)!!
        assertEquals(listOf(1, 2, 3, 0), down.order)
        assertEquals(listOf(1, 1, 1, null), down.groups)
        val up = Supersets.move(listOf(1, 1, null), index = 2, up = true)!!
        assertEquals(listOf(2, 0, 1), up.order)
        assertEquals(listOf(null, 1, 1), up.groups)
    }

    @Test
    fun `a member that moves out past the edge leaves, and a stranded partner is unlinked too`() {
        val move = Supersets.move(listOf(null, 1, 1), index = 1, up = true)!!
        assertEquals(listOf(1, 0, 2), move.order)
        assertEquals("the pair is broken, so neither is a superset", listOf(null, null, null), move.groups)
        // Out of a triple, the other two stay linked.
        val triple = Supersets.move(listOf(2, 2, 2, null), index = 2, up = false)!!
        assertEquals(listOf(0, 1, 3, 2), triple.order)
        assertEquals(listOf(2, 2, null, null), triple.groups)
    }

    @Test
    fun `the ends of the list refuse`() {
        assertNull(Supersets.move(listOf(null, null), 0, up = true))
        assertNull(Supersets.move(listOf(null, null), 1, up = false))
        assertNull(Supersets.move(listOf(1, 1), 0, up = true))
    }

    // ----------------------------------------------------------- the live flow

    private var nextId = 1L

    /** [counts] sets for each exercise (id 1, 2, ...), position by place, grouped by [groups]. */
    private fun trial(counts: List<Int>, groups: List<Int?>, positions: List<Int> = counts.indices.toList()): List<SessionSet> =
        counts.flatMapIndexed { e, n ->
            (0 until n).map { i ->
                SessionSet(
                    id = nextId++, exerciseId = e + 1L, exerciseName = "Lift ${e + 1}", exercisePosition = positions[e],
                    setIndex = i, reps = 8, supersetGroup = groups[e],
                )
            }
        }

    private fun List<SessionSet>.tick(id: Long): List<SessionSet> = map { if (it.id == id) it.copy(done = true) else it }

    private fun List<SessionSet>.label(set: SessionSet?): String =
        set?.let { "${'A' + (it.exerciseId - 1).toInt()}${it.workingNumber(this)}" } ?: "-"

    @Test
    fun `ticking a set in one exercise opens the other, round after round`() {
        var sets = trial(counts = listOf(3, 3), groups = listOf(1, 1))
        val order = buildList {
            while (true) {
                val next = Supersets.nextSet(sets) ?: break
                add(sets.label(next))
                sets = sets.tick(next.id)
            }
        }
        assertEquals(listOf("A1", "B1", "A2", "B2", "A3", "B3"), order)
    }

    @Test
    fun `a triple cycles in order and a shorter member drops out of the later rounds`() {
        var sets = trial(counts = listOf(2, 3, 1), groups = listOf(9, 9, 9))
        val order = buildList {
            while (true) {
                val next = Supersets.nextSet(sets) ?: break
                add(sets.label(next))
                sets = sets.tick(next.id)
            }
        }
        assertEquals(listOf("A1", "B1", "C1", "A2", "B2", "B3"), order)
    }

    @Test
    fun `without supersets it is the first set waiting, as before`() {
        var sets = trial(counts = listOf(2, 2), groups = listOf(null, null))
        val order = buildList {
            while (true) {
                val next = Supersets.nextSet(sets) ?: break
                add(sets.label(next))
                sets = sets.tick(next.id)
            }
        }
        assertEquals(listOf("A1", "A2", "B1", "B2"), order)
    }

    @Test
    fun `a superset sits in the middle of the trial and the rest follow it`() {
        var sets = trial(counts = listOf(1, 2, 2, 1), groups = listOf(null, 4, 4, null))
        val order = buildList {
            while (true) {
                val next = Supersets.nextSet(sets) ?: break
                add(sets.label(next))
                sets = sets.tick(next.id)
            }
        }
        assertEquals(listOf("A1", "B1", "C1", "B2", "C2", "D1"), order)
    }

    @Test
    fun `warm-ups are never next and do not count as a round`() {
        val sets = trial(counts = listOf(3, 2), groups = listOf(1, 1)).mapIndexed { i, s ->
            if (i == 0) s.copy(kind = SetKind.WARMUP) else s
        }
        // A's first row is a warm-up: A's working sets are its two others.
        val first = Supersets.nextSet(sets)!!
        assertEquals("A1", sets.label(first))
        assertEquals(1, first.setIndex)
    }

    @Test
    fun `the round is read from the sets alone, so a restart in the middle picks up where it was`() {
        var sets = trial(counts = listOf(2, 2), groups = listOf(1, 1))
        sets = sets.tick(sets[0].id)
        // Nothing else is remembered: A1 is done, so B1 is next.
        assertEquals("B1", sets.label(Supersets.nextSet(sets)))
    }

    @Test
    fun `groups that are no longer neighbours flow as separate exercises`() {
        // A and C share a number but B sits between them.
        var sets = trial(counts = listOf(2, 2, 2), groups = listOf(3, null, 3))
        val order = buildList {
            while (true) {
                val next = Supersets.nextSet(sets) ?: break
                add(sets.label(next))
                sets = sets.tick(next.id)
            }
        }
        assertEquals(listOf("A1", "A2", "B1", "B2", "C1", "C2"), order)
    }

    @Test
    fun `gaps in the stored positions do not break the group`() {
        // A removed exercise left a hole: positions 0, 2, 5 are still next to each other in order.
        var sets = trial(counts = listOf(2, 2), groups = listOf(1, 1), positions = listOf(2, 5))
        assertEquals("A1", sets.label(Supersets.nextSet(sets)))
        sets = sets.tick(Supersets.nextSet(sets)!!.id)
        assertEquals("B1", sets.label(Supersets.nextSet(sets)))
    }

    // ----------------------------------------------------------- rest

    private fun restsAlongTheWay(counts: List<Int>, groups: List<Int?>): List<String> {
        var sets = trial(counts, groups)
        val out = mutableListOf<String>()
        while (true) {
            val next = Supersets.nextSet(sets) ?: break
            // The screen's log button and the lock-screen "Set done" both ask this.
            if (RestTimer.startsRest(sets, next.id, nowDone = true)) out += "rest after ${sets.label(next)}"
            sets = sets.tick(next.id)
        }
        return out
    }

    @Test
    fun `rest starts only after the last exercise of the round`() {
        // Three rounds, so two rests: after B1 and B2; B3 is the last set of the trial.
        assertEquals(listOf("rest after B1", "rest after B2"), restsAlongTheWay(listOf(3, 3), listOf(1, 1)))
    }

    @Test
    fun `a triple rests once per round, after its third`() {
        assertEquals(listOf("rest after C1"), restsAlongTheWay(listOf(2, 2, 2), listOf(1, 1, 1)))
    }

    @Test
    fun `sets outside a superset rest after every set, as before`() {
        assertEquals(
            listOf("rest after A1", "rest after A2", "rest after B1"),
            restsAlongTheWay(listOf(2, 2), listOf(null, null)).take(3),
        )
    }

    @Test
    fun `a superset between two lifts rests after its round and after the lift before it`() {
        val rests = restsAlongTheWay(listOf(1, 1, 1, 1), listOf(null, 2, 2, null))
        assertEquals(listOf("rest after A1", "rest after C1"), rests)
    }

    @Test
    fun `a member with fewer sets does not hold the round up`() {
        // A has two sets, B has one: round two is A alone, and ticking it ends that round.
        val rests = restsAlongTheWay(listOf(2, 1, 1), listOf(1, 1, null))
        assertEquals(listOf("rest after B1", "rest after A2"), rests)
    }

    @Test
    fun `completing the round is judged from the member's own round, not the trial's`() {
        val sets = trial(counts = listOf(2, 2), groups = listOf(1, 1))
        // A1 first: B has not logged round one yet.
        assertFalse(Supersets.completesRound(sets, sets[0].id))
        // B1 first (out of order): A has not either.
        assertFalse(Supersets.completesRound(sets, sets[2].id))
        // With A1 done, B1 closes the round.
        assertTrue(Supersets.completesRound(sets.tick(sets[0].id), sets[2].id))
        // A2 opens round two while B is still on round one: not complete.
        assertFalse(Supersets.completesRound(sets.tick(sets[0].id).tick(sets[2].id), sets[1].id))
    }

    @Test
    fun `an unticked or unknown set starts no rest and a set outside the trial is its own round`() {
        val sets = trial(counts = listOf(2, 2), groups = listOf(1, 1))
        assertFalse(RestTimer.startsRest(sets, sets[0].id, nowDone = false))
        assertFalse(RestTimer.startsRest(sets, 999, nowDone = true))
        assertTrue(Supersets.completesRound(sets, 999))
    }

    // ----------------------------------------------------------- the footer

    @Test
    fun `the footer says no rest yet only mid-round`() {
        var sets = trial(counts = listOf(2, 2), groups = listOf(1, 1))
        assertFalse("A1 opens the round", Supersets.midRound(sets, Supersets.nextSet(sets)!!))
        sets = sets.tick(Supersets.nextSet(sets)!!.id)
        assertTrue("B1 comes straight after A1", Supersets.midRound(sets, Supersets.nextSet(sets)!!))
        sets = sets.tick(Supersets.nextSet(sets)!!.id)
        assertFalse("A2 begins a fresh round, after the rest", Supersets.midRound(sets, Supersets.nextSet(sets)!!))
        val solo = trial(counts = listOf(2), groups = listOf(null))
        assertFalse(Supersets.midRound(solo, solo[0]))
    }
}
