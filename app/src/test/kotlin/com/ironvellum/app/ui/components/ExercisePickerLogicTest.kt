package com.ironvellum.app.ui.components

import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.LastLogged
import com.ironvellum.app.domain.MuscleGroup
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(70.0, top.getValue(1).weightKg!!, 0.0)
        assertEquals(6, top.getValue(1).reps)
        assertEquals(12, top.getValue(2).reps)
        assertEquals(2, top.size)
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
}
