package com.ironvellum.app.ui.components

import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.FatigueAdvice
import com.ironvellum.app.domain.FatigueLevel
import com.ironvellum.app.domain.LastLogged
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleGroup
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePickerLogicTest {

    private fun ex(id: Long, name: String, group: MuscleGroup = MuscleGroup.PULL, weighted: Boolean = false, category: String = "") =
        Exercise(id, name, group, weighted, category = category)

    // The owner's real answers: a pull-up bar and ONE dumbbell, no full gym.
    private val gear = Equipment(fullGym = false, gear = setOf(Gear.PULL_UP_BAR, Gear.DUMBBELLS), dumbbellPair = false)

    @Test
    fun `saved gear hides what it cannot do and never hides activities or unknown needs`() {
        assertTrue(gearFits(ex(1, "Pull-up"), gear))
        assertTrue(gearFits(ex(2, "Goblet Squat", weighted = true), gear))
        assertFalse("no barbell", gearFits(ex(3, "Back Squat", weighted = true), gear))
        assertFalse("needs two dumbbells", gearFits(ex(4, "Dumbbell Bench Press", weighted = true), gear))
        assertFalse("assisted machine", gearFits(ex(5, "Assisted Pull-up", weighted = true), gear))
        assertTrue("activity", gearFits(ex(6, "Running", weighted = false, category = "Cardio"), Equipment.NOTHING))
        assertTrue("weighted, no requirements row: unknown", gearFits(ex(7, "Sandbag Zercher Carry", weighted = true), gear))
        assertTrue("nothing saved", gearFits(ex(3, "Back Squat", weighted = true), null))
        assertTrue("full gym", gearFits(ex(3, "Back Squat", weighted = true), Equipment.FULL_GYM))
    }

    @Test
    fun `my gear only filters when it is on and gear is saved`() {
        val all = listOf(ex(1, "Pull-up"), ex(3, "Back Squat", weighted = true))
        fun names(filters: PickerFilters, equipment: Equipment?) =
            buildPickerView(all, "", filters, equipment, emptyList(), emptySet()).grouped.flatMap { it.second }.map { it.name }
        assertEquals(listOf("Pull-up"), names(PickerFilters(myGear = true), gear))
        assertEquals(listOf("Back Squat", "Pull-up"), names(PickerFilters(myGear = false), gear))
        assertEquals(listOf("Back Squat", "Pull-up"), names(PickerFilters(myGear = true), null))
    }

    @Test
    fun `favourites sort first in results and pin only when nothing narrows the list`() {
        val list = listOf(
            ex(1, "Chin-up"),
            ex(2, "Pull-up"),
            ex(3, "Push-up", MuscleGroup.PUSH),
        )
        val unfiltered = buildPickerView(list, "", PickerFilters(), null, emptyList(), setOf(3L))
        assertEquals(listOf(3L), unfiltered.favourites.map { it.id })
        assertEquals("a pinned favourite is not repeated below", listOf(1L, 2L), unfiltered.grouped.single().second.map { it.id })
        val withRecent = buildPickerView(list, "", PickerFilters(), null, listOf(3L, 1L), setOf(3L))
        assertEquals("recent skips what FAVOURITES already shows", listOf(1L), withRecent.recents.map { it.id })
        assertEquals(listOf(2L), withRecent.grouped.single().second.map { it.id })
        assertEquals("count stays the number of distinct matches", 3, withRecent.count)

        val searched = buildPickerView(list, "up", PickerFilters(), null, emptyList(), setOf(3L))
        assertTrue("no pinned section under a query", searched.favourites.isEmpty())
        assertEquals(3L, searched.grouped.single().second.first().id)

        val chipped = buildPickerView(list, "", PickerFilters(group = MuscleGroup.PULL), null, emptyList(), setOf(3L))
        assertTrue(chipped.favourites.isEmpty())
        assertEquals(2, chipped.count)
    }

    @Test
    fun `recents keep their order, drop unknown ids, and still respect gear`() {
        val list = listOf(ex(1, "Pull-up"), ex(2, "Chin-up"), ex(3, "Back Squat", weighted = true))
        val view = buildPickerView(list, "", PickerFilters(myGear = true), gear, listOf(3L, 2L, 99L, 1L, 2L), emptySet())
        assertEquals("most recent first, gear-blocked and unknown dropped, no duplicates", listOf(2L, 1L), view.recents.map { it.id })
    }

    @Test
    fun `last logged is the top set of the most recent workout, not the heaviest ever`() {
        fun set(id: Long, at: Long, kg: Double?, reps: Int) = LastLogged(id, reps, kg, null, null, null, at)
        val top = LastLogged.topSets(
            listOf(
                set(1, at = 1_000, kg = 100.0, reps = 5), // older, heavier
                set(1, at = 2_000, kg = 60.0, reps = 8),
                set(1, at = 2_000, kg = 70.0, reps = 5),
                set(1, at = 2_000, kg = 70.0, reps = 6),
                set(2, at = 500, kg = null, reps = 12),
            ),
        )
        assertEquals(70.0, top[1, ""]!!.weightKg!!, 0.0)
        assertEquals(6, top[1, ""]!!.reps)
        assertEquals(12, top[2, ""]!!.reps)
        assertEquals(2, top.size)
    }

    @Test
    fun `last logged is kept per variation and a variation never logged shows nothing`() {
        fun set(at: Long, kg: Double?, reps: Int, modifiers: String) = LastLogged(1, reps, kg, null, null, null, at, modifiers = modifiers)
        val top = LastLogged.topSets(
            listOf(
                set(3_000, null, 20, ""), // the flat push-up is the more recent
                set(2_000, null, 12, "deficit"),
                set(2_000, null, 10, "deficit, tempo"), // tempo does not split: same lift as the deficit above
                set(1_000, null, 15, "tempo"), // older plain set under another modifier string
            ),
        )
        assertEquals(20, top[1, ""]!!.reps)
        assertEquals(12, top[1, "deficit"]!!.reps)
        assertNull("never logged: nothing, not the flat set", top[1, "archer"])
        assertEquals("the picker line names the most recent variation", 20, top.latest(1)!!.reps)
        assertNull(top.latest(2))
    }

    @Test
    fun `last logged matches the whole combination, in any order`() {
        fun set(at: Long, reps: Int, modifiers: String) = LastLogged(1, reps, null, null, null, null, at, modifiers = modifiers)
        val top = LastLogged.topSets(
            listOf(set(3_000, 8, "deficit"), set(2_000, 6, "deficit, archer"), set(1_000, 5, "archer,tempo,deficit"), set(500, 9, "archer")),
        )
        assertEquals(6, top[1, "archer,deficit"]!!.reps)
        assertEquals("the sorted key is what lookups use", 6, top[1, com.ironvellum.app.domain.Variation.key("deficit, archer")]!!.reps)
        assertEquals(8, top[1, "deficit"]!!.reps)
        assertEquals(9, top[1, "archer"]!!.reps)
        val day = 86_400_000L
        assertEquals("archer, deficit · 6 × BW · today", lastLoggedLine(top[1, "archer,deficit"]!!.copy(atMs = 100 * day), ex(1, "Push-up"), 100 * day + 1000, ZoneOffset.UTC, withVariation = true))
    }

    @Test
    fun `the picker line names a variation but a plain set stays as it was`() {
        val day = 86_400_000L
        val now = 100 * day + 12 * 3_600_000L
        val pushup = ex(1, "Push-up")
        val deficit = LastLogged(1, 12, null, null, null, null, now - 3 * day, modifiers = "deficit")
        assertEquals("deficit · 12 × BW · 3d ago", lastLoggedLine(deficit, pushup, now, ZoneOffset.UTC, withVariation = true))
        assertEquals("12 × BW · 3d ago", lastLoggedLine(deficit, pushup, now, ZoneOffset.UTC))
        val plain = deficit.copy(modifiers = "")
        assertEquals("12 × BW · 3d ago", lastLoggedLine(plain, pushup, now, ZoneOffset.UTC, withVariation = true))
    }

    @Test
    fun `last logged line reads added load with a plus and holds in seconds`() {
        val day = 86_400_000L
        val now = 100 * day + 12 * 3_600_000L
        val weightedPull = LastLogged(1, 5, 20.0, null, null, null, now - 3 * day)
        assertEquals("5 × +20 kg · 3d ago", lastLoggedLine(weightedPull, ex(1, "Pull-up"), now, ZoneOffset.UTC))

        val row = LastLogged(2, 8, 7.5, null, null, null, now - 30 * day)
        val line = lastLoggedLine(row, ex(2, "Goblet Squat", weighted = true), now, ZoneOffset.UTC)
        assertTrue(line, line.startsWith("8 × 7.5 kg · "))
        assertFalse("old workouts read as a date", line.contains("ago"))

        val hold = LastLogged(3, 0, null, 45, null, null, now - day)
        val plank = Exercise(3, "Plank", MuscleGroup.CORE, false, ExerciseMetric.HOLD)
        assertEquals("45s · yesterday", lastLoggedLine(hold, plank, now, ZoneOffset.UTC))
    }

    @Test
    fun `yours pins at the top, stays in its category, and only shows when nothing narrows the list`() {
        val mine = Exercise(9, "Reverse hyper", MuscleGroup.LEGS, isWeighted = true, custom = true)
        val list = listOf(ex(1, "Pull-up"), mine, Exercise(10, "Zebra stretch", MuscleGroup.CORE, false, custom = true))
        val view = buildPickerView(list, "", PickerFilters(), null, emptyList(), emptySet())
        assertEquals(listOf("Reverse hyper", "Zebra stretch"), view.yours.map { it.name })
        assertTrue("still in its normal category", mine in view.grouped.single().second)
        assertEquals("the count is distinct exercises", 3, view.count)
        assertTrue(buildPickerView(list, "hyper", PickerFilters(), null, emptyList(), emptySet()).yours.isEmpty())
        assertTrue(buildPickerView(list, "", PickerFilters(group = MuscleGroup.LEGS), null, emptyList(), emptySet()).yours.isEmpty())
        assertEquals(
            "a favourite of your own shows in both",
            listOf(9L),
            buildPickerView(list, "", PickerFilters(), null, emptyList(), setOf(9L)).let { v -> v.yours.map { it.id }.intersect(v.favourites.map { it.id }.toSet()).toList() },
        )
    }

    // ------------------------------------------------------------------------------ fatigue (live picker)

    private val push = listOf(
        ex(1, "Bench Press", MuscleGroup.PUSH, weighted = true),
        ex(2, "Overhead Press", MuscleGroup.PUSH, weighted = true),
        ex(3, "Lateral Raise", MuscleGroup.PUSH, weighted = true),
        ex(4, "Back Squat", MuscleGroup.LEGS, weighted = true),
    )

    /** Every muscle fresh, except the chest of the bench press at [level]. */
    private fun tiredBench(level: FatigueLevel): Map<Muscle, FatigueLevel> {
        val chest = FatigueAdvice.mainMuscles("Bench Press")
        val untouched = FatigueAdvice.mainMuscles("Lateral Raise") + FatigueAdvice.mainMuscles("Overhead Press") + FatigueAdvice.mainMuscles("Back Squat")
        return Muscle.entries.associateWith { if (it in chest && it !in untouched) level else FatigueLevel.FRESH }
    }

    private fun order(query: String, levels: Map<Muscle, FatigueLevel>) =
        buildPickerView(push, query, PickerFilters(), null, emptyList(), emptySet(), levels).grouped.flatMap { it.second }.map { it.name }

    @Test
    fun `with no query a tired exercise sinks below fresher ones and the rest keep their order`() {
        val plain = order("", emptyMap())
        val tired = order("", tiredBench(FatigueLevel.HIGH))
        assertEquals("Bench Press", tired.last())
        assertEquals(plain.filter { it != "Bench Press" }, tired.filter { it != "Bench Press" })
        assertTrue("it moved", plain.last() != "Bench Press")
    }

    @Test
    fun `a more tired muscle sinks lower than a less tired one`() {
        val chest = FatigueAdvice.mainMuscles("Bench Press")
        val shoulders = FatigueAdvice.mainMuscles("Lateral Raise")
        val levels = Muscle.entries.associateWith {
            when (it) {
                in shoulders -> FatigueLevel.HIGH
                in chest -> FatigueLevel.VERY_HIGH
                else -> FatigueLevel.FRESH
            }
        }
        val names = order("", levels)
        assertTrue(names.indexOf("Lateral Raise") < names.indexOf("Bench Press"))
    }

    @Test
    fun `with a query the match ranking wins and fatigue does not reorder`() {
        val levels = tiredBench(FatigueLevel.VERY_HIGH)
        assertEquals(order("press", emptyMap()), order("press", levels))
    }

    @Test
    fun `no map, or an empty one, leaves the order exactly as it was`() {
        val levels = tiredBench(FatigueLevel.HIGH)
        assertEquals(order("", emptyMap()), buildPickerView(push, "", PickerFilters(), null, emptyList(), emptySet()).grouped.flatMap { it.second }.map { it.name })
        assertTrue(FatigueAdvice.worstIn("Bench Press", emptyMap()) == null)
        assertTrue(FatigueAdvice.worstIn("Bench Press", levels)!!.second == FatigueLevel.HIGH)
    }

    @Test
    fun `favourites stay pinned above fatigue order`() {
        val view = buildPickerView(push, "", PickerFilters(), null, emptyList(), setOf(1L), tiredBench(FatigueLevel.VERY_HIGH))
        assertEquals(listOf(1L), view.favourites.map { it.id })
    }
}
