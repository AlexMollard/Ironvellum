package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeedLaddersTest {

    private val ladders = DeedLadders.ALL

    private fun def(id: String, rule: TitleRule) =
        TitleDef(id, id, "d", rule, TitleRarity.Common)

    @Test
    fun `every deed belongs to exactly one ladder`() {
        val ids = ladders.flatMap { l -> l.rungs.map { it.id } }
        assertEquals("a deed appears in two ladders or none", Titles.ALL.map { it.id }.sorted(), ids.sorted())
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `rungs are sorted easiest first and share one category`() {
        val shipped = ladders.associateBy { it.key }
        val trials = shipped.getValue("trials")
        assertEquals(
            listOf("awakened", "iron_discipline", "relentless", "unbroken", "hundred_battles", "eternal_grinder"),
            trials.rungs.map { it.id },
        )
        ladders.forEach { l ->
            assertTrue("${l.key} mixes categories", l.rungs.all { Titles.category(it.rule) == l.category })
            assertTrue("${l.key} category is not listed", DeedLadders.CATEGORIES.any { it.name == l.category })
        }
    }

    @Test
    fun `every category lists at least one ladder`() {
        DeedLadders.CATEGORIES.forEach { c ->
            assertTrue("${c.name} has no ladders", ladders.any { it.category == c.name })
        }
    }

    @Test
    fun `lifts with different movements are different ladders`() {
        val squat = ladders.single { it.key.startsWith("lift:back squat") }
        assertEquals(listOf("iron_standard", "throne_of_iron"), squat.rungs.map { it.id })
        val deadlift = ladders.single { it.key.startsWith("lift:deadlift") }
        assertEquals(listOf("titans_pull", "atlas"), deadlift.rungs.map { it.id })
        assertTrue(ladders.single { it.key == "hold" }.rungs.size == 1)
    }

    @Test
    fun `the next rung is the easiest one not earned`() {
        val trials = ladders.single { it.key == "trials" }
        assertEquals("awakened", trials.next(emptySet())?.id)
        assertEquals("relentless", trials.next(setOf("awakened", "iron_discipline"))?.id)
        assertNull(trials.next(trials.rungs.map { it.id }.toSet()))
    }

    @Test
    fun `a rung earned out of order does not move the next marker past an open one`() {
        val trials = ladders.single { it.key == "trials" }
        val states = trials.states(setOf("awakened", "relentless"))
        assertEquals(
            listOf(RungState.Earned, RungState.Next, RungState.Earned, RungState.Locked, RungState.Locked, RungState.Locked),
            states,
        )
    }

    @Test
    fun `derive sorts shuffled input and merges the first workout into the trial ladder`() {
        val derived = DeedLadders.derive(
            listOf(
                def("b", TitleRule.Workouts(50)),
                def("x", TitleRule.StepsInDay(10_000)),
                def("a", TitleRule.FirstWorkout),
                def("c", TitleRule.Workouts(25)),
            ),
        )
        assertEquals(listOf("trials", "steps-day"), derived.map { it.key })
        assertEquals(listOf("a", "c", "b"), derived[0].rungs.map { it.id })
        assertEquals("a ladder of one carries its deed's name", "x", derived[1].title)
    }

    @Test
    fun `the shipped catalogue forms the ladders the codex shows`() {
        assertEquals(Titles.ALL.size, ladders.sumOf { it.rungs.size })
        assertEquals("ladder count changed; if the catalogue moved on purpose, update this number", EXPECTED_LADDERS, ladders.size)
    }

    private companion object {
        const val EXPECTED_LADDERS = 31
    }
}
