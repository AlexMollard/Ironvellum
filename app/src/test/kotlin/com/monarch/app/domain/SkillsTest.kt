package com.monarch.app.domain

import com.monarch.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsTest {

    /** A prerequisite nobody can ever master would strand the rest of its line. */
    @Test
    fun `every prerequisite is a masterable movement`() {
        val masterable = Skills.ALL.map { it.name }.toSet() + Seed.exercises.map { it.name }
        Skills.ALL.mapNotNull { it.requires }.forEach { prereq ->
            assertTrue("dangling prerequisite: $prereq", prereq in masterable)
        }
    }

    @Test
    fun `every skill states a claim standard and a reason`() {
        Skills.ALL.forEach { skill ->
            assertTrue("${skill.name} has no standard", skill.standard.isNotBlank())
            assertTrue("${skill.name} has no rationale", skill.why.isNotBlank())
        }
    }

    @Test
    fun `a skill unlocks only once its prerequisite is mastered`() {
        val hspu = Skills.forName("Handstand Push-up")!!
        assertFalse(Skills.unlocked(hspu, emptySet()))
        assertTrue(Skills.unlocked(hspu, setOf("Wall HSPU")))
    }

    @Test
    fun `mastering a skill opens exactly its dependants`() {
        assertEquals(
            setOf(
                "Handstand Walk",
                "One-Arm Handstand",
                "Straddle Press to Handstand",
                // dependants cross progression lines, not just the parent's own line
                "Handstand-to-Bridge",
            ),
            Skills.unlockedBy("Freestanding Handstand").map { it.name }.toSet(),
        )
        assertEquals(emptyList<String>(), Skills.unlockedBy("Manna").map { it.name })
    }

    @Test
    fun `skill xp scales with tier`() {
        assertEquals(120, Skills.forName("Dead Hang")!!.xp)
        assertEquals(600, Skills.forName("Full Planche")!!.xp)
    }
}