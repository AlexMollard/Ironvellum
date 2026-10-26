package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** What "Lighten" and "Swap" make of a routine entry for one workout, and that the override reads back from its string. */
class SessionOverrideTest {

    private fun entry(id: Long = 1, sets: Int = 3, reps: Int = 8, scheme: SetScheme = SetScheme(), kg: Double? = 60.0) =
        PresetEntry(id = id, exerciseId = 10, exerciseName = "Weighted Dip", targetSets = sets, targetReps = reps, targetWeightKg = kg, modifiers = "weighted", scheme = scheme)

    // ------------------------------------------------------------------------------------- lighten

    @Test
    fun `lighten takes one set and one rep off at the same load, never below one set`() {
        val light = Lighten.apply(entry(), repsCounted = true)
        assertEquals(2, light.targetSets)
        assertEquals(7, light.targetReps)
        assertEquals(60.0, light.targetWeightKg!!, 0.0)

        val one = Lighten.apply(entry(sets = 1), repsCounted = true)
        assertEquals("a single set stays", 1, one.targetSets)
        assertEquals(7, one.targetReps)
        assertFalse(Lighten.canTrim(entry(sets = 1)))
        assertEquals(1, Lighten.apply(entry(sets = 1, reps = 1), repsCounted = true).targetReps)
    }

    @Test
    fun `a hold loses the set but not the clock`() {
        val held = Lighten.apply(entry(reps = 30), repsCounted = false)
        assertEquals(2, held.targetSets)
        assertEquals(30, held.targetReps)
    }

    @Test
    fun `a top set scheme loses its last back-off, and the top set asks for a rep less and a rep more in reserve`() {
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF, topReps = 4..6, topRir = 0..1, backoffSets = 2, backoffReps = 6..8)
        val light = Lighten.apply(entry(scheme = scheme), repsCounted = true)
        assertEquals(SchemeType.TOP_BACKOFF, light.scheme.type)
        assertEquals(1, light.scheme.backoffSets)
        assertEquals(2, Lighten.workingSets(light))
        assertEquals(3..6, light.scheme.topReps)
        assertEquals(5..8, light.scheme.backoffReps)
        assertEquals(1..2, light.scheme.topRir)
    }

    @Test
    fun `a top set with one back-off lightens to the top set alone`() {
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF, backoffSets = 1)
        val light = Lighten.apply(entry(scheme = scheme), repsCounted = true)
        assertEquals(1, Lighten.workingSets(light))
        assertEquals(1, light.targetSets)
    }

    @Test
    fun `a drop scheme and a custom one lose a working set, a custom one its last back-off first`() {
        val drops = SetScheme(type = SchemeType.DROPS, dropSets = 3)
        val lighterDrops = Lighten.apply(entry(scheme = drops), repsCounted = true)
        assertEquals(2, lighterDrops.targetSets)
        assertTrue(lighterDrops.scheme.dropSets <= 2)

        val custom = SetScheme(type = SchemeType.CUSTOM, custom = listOf(SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF, SetKind.DROP))
        val lighterCustom = Lighten.apply(entry(scheme = custom), repsCounted = true)
        assertEquals(listOf(SetKind.TOP, SetKind.BACKOFF, SetKind.DROP), lighterCustom.scheme.custom)
    }

    // ---------------------------------------------------------------------------------- the override

    private val swap = EntryAdjust.Swap(exerciseId = 77, exerciseName = "Pike Push-up", modifiers = "", sets = 3, reps = 6, weightKg = null)

    @Test
    fun `a swap puts the stand-in in the entry with its own reps and load, never the old lift's`() {
        val out = SessionOverride(mapOf(1L to EntryAdjust(swap = swap))).applyTo(entry(scheme = SetScheme(type = SchemeType.TOP_BACKOFF)))
        assertEquals(77, out.exerciseId)
        assertEquals("Pike Push-up", out.exerciseName)
        assertEquals("", out.modifiers)
        assertEquals(6, out.targetReps)
        assertEquals(null, out.targetWeightKg)
        assertEquals(3, out.targetSets)
        assertTrue(out.scheme.isStraight)
    }

    @Test
    fun `an entry the override does not name is handed back as it is`() {
        val original = entry(id = 5)
        assertSame(original, SessionOverride(mapOf(1L to EntryAdjust(lighten = true))).applyTo(original))
        assertSame(original, SessionOverride.NONE.applyTo(original))
    }

    @Test
    fun `applying an override never touches the entry it was given`() {
        val original = entry()
        val before = original.copy()
        SessionOverride(mapOf(1L to EntryAdjust(swap = swap, lighten = true))).applyTo(original)
        assertEquals(before, original)
    }

    @Test
    fun `an override reads back from its string, and a bad one reads as none`() {
        val override = SessionOverride(
            mapOf(
                1L to EntryAdjust(lighten = true),
                2L to EntryAdjust(swap = swap.copy(weightKg = 12.5)),
                3L to EntryAdjust(),
            ),
        )
        val back = SessionOverride.decode(override.encode())
        assertEquals(mapOf(1L to EntryAdjust(lighten = true), 2L to EntryAdjust(swap = swap.copy(weightKg = 12.5))), back.entries)
        assertTrue(SessionOverride.decode(null).isEmpty)
        assertTrue(SessionOverride.decode("not an override").isEmpty)
        assertTrue(SessionOverride.NONE.isEmpty)
        assertFalse(back.isEmpty)
    }
}
