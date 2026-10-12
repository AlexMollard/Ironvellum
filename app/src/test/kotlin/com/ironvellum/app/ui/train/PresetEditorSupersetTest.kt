package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.Supersets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rite editor's superset rules, on the lists the view model holds: linking reorders, moving keeps groups whole. */
class PresetEditorSupersetTest {

    private fun entry(id: Long, name: String, group: Int? = null) =
        EditorEntry(exerciseId = id, exerciseName = name, sets = "3", reps = "8", weight = "", modifiers = "", supersetGroup = group)

    private val rite = listOf(entry(1, "Bench press"), entry(2, "Overhead press"), entry(3, "Lateral raise"), entry(4, "Barbell row"))

    private fun List<EditorEntry>.link(index: Int, partner: Int): List<EditorEntry>? =
        Supersets.link(map { it.supersetGroup }, index, partner)?.let { arranged(it) }

    @Test
    fun linkingADistantPartnerMovesItNextToTheEntry() {
        val linked = rite.link(index = 0, partner = 3)!!
        assertEquals(listOf("Bench press", "Barbell row", "Overhead press", "Lateral raise"), linked.map { it.exerciseName })
        assertEquals(listOf(1, 1, null, null), linked.map { it.supersetGroup })
    }

    @Test
    fun theRowNamesTheOtherMembers() {
        val linked = rite.link(0, 3)!!
        assertEquals("With Barbell row", supersetPartners(linked, 0))
        assertEquals("With Bench press", supersetPartners(linked, 1))
        assertNull(supersetPartners(linked, 2))
        val triple = linked.link(1, 2)!!
        assertEquals("With Bench press and Overhead press", supersetPartners(triple, 1))
        assertEquals("With Bench press and Barbell row", supersetPartners(triple, 2))
    }

    @Test
    fun aFourthMemberIsRefused() {
        val triple = rite.link(0, 1)!!.link(1, 2)!!
        assertEquals(listOf(1, 1, 1, null), triple.map { it.supersetGroup })
        assertNull(triple.link(2, 3))
    }

    @Test
    fun aGroupChangeIsAnEditTheDiscardPromptSees() {
        val linked = rite.link(0, 1)!!
        assertTrue(editorChanged(EditorUi(entries = rite), EditorUi(entries = linked)))
        assertEquals(false, editorChanged(EditorUi(entries = rite), EditorUi(entries = rite)))
    }

    @Test
    fun movingKeepsAGroupWholeAndLeavingBreaksTheNumbers() {
        val linked = rite.link(1, 2)!!
        // Bench steps over the whole pair rather than into it.
        val over = Supersets.move(linked.map { it.supersetGroup }, 0, up = false)!!
        assertEquals(listOf("Overhead press", "Lateral raise", "Bench press", "Barbell row"), linked.arranged(over).map { it.exerciseName })
        assertEquals(listOf(1, 1, null, null), linked.arranged(over).map { it.supersetGroup })
        // Overhead press moving up past the edge leaves the group, and with it the pair.
        val out = Supersets.move(linked.map { it.supersetGroup }, 1, up = true)!!
        assertEquals(listOf(null, null, null, null), linked.arranged(out).map { it.supersetGroup })
    }
}
