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

    /**
     * Names are the identity here: `requires` points at a name, and `forName`
     * resolves by it. Two skills sharing one name makes the tree's shape depend
     * on list order, and one of them unreachable.
     */
    @Test
    fun `skill names are unique`() {
        val duplicates = Skills.ALL.groupingBy { it.name }.eachCount().filter { it.value > 1 }
        assertEquals(emptyMap<String, Int>(), duplicates)
    }

    /**
     * A prerequisite loop locks every skill in it forever: each waits on the
     * other, `unlocked()` is false for both, and no amount of training opens
     * them. It cannot be noticed by playing — only by walking the tree.
     */
    @Test
    fun `no skill waits on itself, directly or through its line`() {
        val parentOf = Skills.ALL.associate { it.name to it.requires }
        Skills.ALL.forEach { skill ->
            val seen = linkedSetOf(skill.name)
            var cursor = skill.requires
            while (cursor != null) {
                assertTrue(
                    "prerequisite loop: ${seen.joinToString(" -> ")} -> $cursor",
                    cursor !in seen,
                )
                seen += cursor
                cursor = parentOf[cursor]
            }
        }
    }

    /**
     * The tree is walked by `name.trim().lowercase()`, so two rows differing
     * only by case are the same identity: one of them silently shadows the
     * other and it can never be claimed.
     */
    @Test
    fun `skill names are unique case-insensitively`() {
        val duplicates = Skills.ALL
            .groupingBy { it.name.trim().lowercase() }
            .eachCount()
            .filter { it.value > 1 }
            .keys
        assertEquals("skills that shadow an earlier row by case", emptySet<String>(), duplicates)
    }

    /**
     * A tier is a difficulty BAND, not a single rung: a band legitimately
     * holds an ordered chain, so Muscle-up (III) requiring Pull-up (III) is
     * the tree working as designed, and the acyclicity test above already
     * stops such a chain closing on itself. What must never happen is a
     * prerequisite ABOVE its unlock — that asks you to master the harder
     * movement first, and progress would run backwards along the line.
     */
    @Test
    fun `a prerequisite is never a harder tier than what it unlocks`() {
        val bad = Skills.ALL.mapNotNull { skill ->
            val prereq = skill.requires ?: return@mapNotNull null
            // A prerequisite can be another skill or a catalogue movement;
            // both carry a tier through the difficulty tables.
            val prereqTier = Skills.forName(prereq)?.tier ?: MovementDifficulty.tier(prereq)
            (prereqTier > skill.tier).takeIf { it }?.let {
                "${skill.name} (tier ${skill.tier}) requires $prereq (tier $prereqTier)"
            }
        }
        assertEquals("prerequisites harder than their unlock", emptyList<String>(), bad)
    }

    /**
     * A standard denominated in minutes is chased in seconds — every consumer
     * of a SECONDS target treats the number as seconds, so the conversion has
     * to happen at the inference, not at each call site. `metre` is one regex
     * slip away from `minute`, so the distance skill is asserted alongside.
     */
    @Test
    fun `a minute-denominated standard resolves to seconds`() {
        val squatHold = Skills.forName("Deep Squat Hold")!!
        assertEquals(Skills.Metric.SECONDS, squatHold.metric)
        assertEquals("Deep Squat Hold target in seconds", 180, squatHold.target)

        val wristPrep = Skills.forName("Wrist Prep")!!
        assertEquals(Skills.Metric.SECONDS, wristPrep.metric)
        assertEquals("Wrist Prep target in seconds", 600, wristPrep.target)

        val walk = Skills.forName("Handstand Walk")!!
        assertEquals(Skills.Metric.METRES, walk.metric)
    }
}