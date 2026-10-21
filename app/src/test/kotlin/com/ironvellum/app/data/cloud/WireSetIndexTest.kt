package com.ironvellum.app.data.cloud

import com.ironvellum.app.domain.SessionSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cloud keys a set on (trial, movement name, set_index). A rite that names a movement twice has two blocks whose sets
 * both count from 0; the push numbers them apart, and leaves every other trial exactly as it was.
 */
class WireSetIndexTest {

    private fun set(name: String, position: Int, index: Int) =
        SessionSet(exerciseId = name.length.toLong(), exerciseName = name, exercisePosition = position, setIndex = index, reps = 8, done = true)

    @Test
    fun `a trial with no repeated movement keeps every set index`() {
        val sets = listOf(set("Bench", 0, 0), set("Bench", 0, 1), set("Row", 1, 0), set("Row", 1, 1), set("Row", 1, 2))
        assertEquals(listOf(0, 1, 0, 1, 2), CloudSync.wireSetIndexes(sets))
    }

    @Test
    fun `the later block of a repeated movement is numbered on from the earlier one`() {
        val sets = listOf(
            set("Push-up", 3, 0), set("Push-up", 3, 1), set("Push-up", 3, 2),
            set("Push-up", 4, 0), set("Push-up", 4, 1), set("Push-up", 4, 2),
            set("Bulgarian Split Squat", 5, 0),
        )
        val wire = CloudSync.wireSetIndexes(sets)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 0), wire)
        val keys = sets.map { it.exerciseName }.zip(wire)
        assertEquals("one upsert must never carry two rows with one key", keys.size, keys.toSet().size)
    }

    @Test
    fun `blocks already numbered on, as an imported workout stores them, are not shifted again`() {
        val sets = listOf(set("Bench", 0, 0), set("Bench", 0, 1), set("Row", 1, 0), set("Bench", 2, 2), set("Bench", 2, 3))
        assertEquals(listOf(0, 1, 0, 2, 3), CloudSync.wireSetIndexes(sets))
    }

    @Test
    fun `the order of the list does not matter, the position does`() {
        val sets = listOf(set("Push-up", 4, 0), set("Push-up", 3, 0), set("Push-up", 4, 1), set("Push-up", 3, 1))
        assertEquals(listOf(2, 0, 3, 1), CloudSync.wireSetIndexes(sets))
    }

    @Test
    fun `stale rows are judged on the wire keys, so the second block's rows are kept`() {
        val sets = listOf(set("Push-up", 3, 0), set("Push-up", 3, 1), set("Push-up", 4, 0), set("Push-up", 4, 1))
        val onCloud = (0..4).map { SessionSetKeyDto("r$it", "cloud-a", "Push-up", it) }
        val stale = CloudSync.staleSetRowIds(mapOf("cloud-a" to sets), onCloud)
        assertEquals(listOf("r4"), stale)
        assertTrue("r2 and r3 are the second block's", "r2" !in stale && "r3" !in stale)
    }
}
