package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsTest {

    /**
     * A prerequisite nobody can ever master would strand the rest of its line.
     * Only a SKILL can be mastered - claimSkill refuses any other name - so a
     * catalogue-only movement as a prerequisite would lock its dependant
     * forever, however often it is logged.
     */
    @Test
    fun `every prerequisite is a claimable skill`() {
        Skills.ALL.flatMap { skill -> skill.prerequisites.map { skill.name to it } }.forEach { (skill, prereq) ->
            assertTrue("$skill waits on $prereq, which is not a skill", Skills.forName(prereq) != null)
        }
        assertTrue(Seed.exercises.isNotEmpty())
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
        assertFalse("all prerequisites, not any", Skills.unlocked(hspu, setOf("Wall HSPU")))
        assertTrue(Skills.unlocked(hspu, setOf("Wall HSPU", "Freestanding Handstand")))
    }

    /**
     * Gating is for NEW claims. Mastery that predates a prerequisite is never
     * re-judged, so the next step opens on what the lifter actually owns.
     */
    @Test
    fun `a claimed skill missing a newer prerequisite still opens what it leads to`() {
        val hspu = Skills.forName("Handstand Push-up")!!
        // Wall HSPU and Handstand Push-up claimed before Freestanding Handstand
        // was a prerequisite: 90-Degree Push-up still unlocks off the claim.
        assertTrue(Skills.unlocked(Skills.forName("90-Degree Push-up")!!, setOf("Wall HSPU", hspu.name)))
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
                "Handstand Push-up",
                "Inverted Muscle-up",
            ),
            Skills.dependantsOf("Freestanding Handstand").map { it.name }.toSet(),
        )
        assertEquals(emptyList<String>(), Skills.unlockedBy("Manna").map { it.name })
    }

    /** A dependant with a second, unmet prerequisite does not open yet. */
    @Test
    fun `unlocking waits on every prerequisite`() {
        assertFalse(
            "Handstand-to-Bridge also needs Bridge",
            Skills.unlockedBy("Freestanding Handstand").any { it.name == "Handstand-to-Bridge" },
        )
        assertTrue(
            Skills.unlockedBy("Freestanding Handstand", setOf("Bridge")).any { it.name == "Handstand-to-Bridge" },
        )
        assertTrue(
            "already mastered is not newly unlocked",
            Skills.unlockedBy("Dead Hang", setOf("Scapular Pull")).none { it.name == "Scapular Pull" },
        )
    }

    /**
     * An unclaim refunds what the claim PAID. A stamped claim refunds its
     * stamp whatever the tier is now; an unstamped (older) claim refunds the
     * tier it was claimed at, so re-tiering a skill never revokes or mints.
     */
    @Test
    fun `an unclaim refunds what the claim paid, not the current tier`() {
        assertEquals(240, Skills.claimRefund("Dead Hang", stampedXp = 240))
        assertEquals("unstamped, unchanged tier", 120, Skills.claimRefund("Dead Hang", stampedXp = 0))
        assertEquals(0, Skills.claimRefund("No Such Skill", stampedXp = 0))
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
        val done = mutableSetOf<String>()
        fun visit(name: String, path: List<String>) {
            assertTrue("prerequisite loop: ${(path + name).joinToString(" -> ")}", name !in path)
            if (!done.add(name)) return
            Skills.forName(name)?.prerequisites?.forEach { visit(it, path + name) }
        }
        Skills.ALL.forEach { visit(it.name, emptyList()) }
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
        val bad = Skills.ALL.flatMap { skill ->
            skill.prerequisites.mapNotNull { prereq ->
                val prereqTier = Skills.forName(prereq)!!.tier
                (prereqTier > skill.tier).takeIf { it }?.let {
                    "${skill.name} (tier ${skill.tier}) requires $prereq (tier $prereqTier)"
                }
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

    /**
     * The number on the bar is LOAD, not reps. Taking the max digit run read
     * "5 reps with +25 kg" as a 25-rep standard and sent the journal and the
     * BEST line chasing twenty-five reps of a five-rep lift. The same slip
     * read the 90-Degree Push-up's angle as ninety reps.
     */
    @Test
    fun `a loaded standard infers the rep count, not the plate`() {
        val wpu = Skills.forName("Weighted Pull-up")!!
        assertEquals(Skills.Metric.REPS, wpu.metric)
        assertEquals(5, wpu.target)

        val dip = Skills.forName("Weighted Dip")!!
        assertEquals(Skills.Metric.REPS, dip.metric)
        assertEquals(5, dip.target)

        val ninety = Skills.forName("90-Degree Push-up")!!
        assertEquals(Skills.Metric.REPS, ninety.metric)
        assertEquals(1, ninety.target)
    }

    /**
     * A decimal multiple like "1.5x" splits into 1 and 5 under the digit-run
     * scan, so a future loaded standard written in digits would silently
     * inflate its target. Multiples are spelled out; this pins the convention.
     */
    @Test
    fun `standards spell multiples as words, never decimals`() {
        Skills.ALL.forEach { skill ->
            assertFalse(
                "${skill.name} writes a decimal figure; spell it out or it inflates the target",
                Regex("\\d+\\.\\d").containsMatchIn(skill.standard),
            )
        }
    }

    /**
     * The gym progression lines: four full I-V chains, every rung a rep
     * standard, every bar parsed to the rep count the prose means.
     */
    @Test
    fun `gym lines are complete rep chains with sane targets`() {
        val gymLines = listOf("Squat", "Bench", "Press", "Deadlift")
        gymLines.forEach { line ->
            val skills = Skills.ALL.filter { it.line == line }.sortedBy { it.tier }
            assertEquals("$line must run I to V", (1..5).toList(), skills.map { it.tier })
            skills.forEach { skill ->
                assertEquals("${skill.name} is counted, not timed", Skills.Metric.REPS, skill.metric)
                assertTrue(
                    "${skill.name} target must be a rep count, got ${skill.target}",
                    skill.target in 1..20,
                )
            }
            skills.drop(1).zipWithNext().forEach { (lower, higher) ->
                assertEquals(
                    "each rung requires the one below it",
                    listOf(lower.name),
                    higher.prerequisites,
                )
            }
        }
    }

    /**
     * The published female bars exist exactly where the male prose names a
     * male multiple - the whole gym line - and nowhere else. A missing bar
     * would ceiling a female lifter out of a line; a stray one would claim a
     * sex-specific bar for a bodyweight skill.
     */
    @Test
    fun `female bars cover the gym lines and nothing else`() {
        val gymNames = Skills.ALL.filter { it.line in setOf("Squat", "Bench", "Press", "Deadlift") }.map { it.name }
        gymNames.forEach { name ->
            val bar = Skills.femaleStandard(name)
            assertTrue("$name has no female bar", bar != null)
            assertTrue("$name bar must state the multiple", bar!!.endsWith("bodyweight"))
            assertTrue("$name bar must carry a number", Regex("\\d").containsMatchIn(bar))
        }
        assertEquals(null, Skills.femaleStandard("Pull-up"))
        assertEquals(null, Skills.femaleStandard("Full Planche"))
        assertEquals("1.4x bodyweight", Skills.femaleStandard("Double-Bodyweight Bench Press"))
    }

    /**
     * The structural repairs: the dip branch must not skip a tier, and the
     * compression chain must not jump L-sit to V-sit.
     */
    @Test
    fun `repaired lines have no tier gaps on their chains`() {
        assertEquals(2, Skills.forName("Parallel Bar Support Hold")!!.tier)
        assertEquals(
            listOf("Parallel Bar Support Hold"),
            Skills.forName("Parallel Bar Dip")!!.prerequisites,
        )
        assertEquals(3, Skills.forName("Straddle L-sit")!!.tier)
        assertEquals(listOf("Straddle L-sit"), Skills.forName("V-Sit")!!.prerequisites)
    }

    /**
     * Every gym-line rung is a barbell lift. Seeding them unweighted hid them
     * from the weighted picker facet, let the gear filter offer a 2x
     * bodyweight deadlift to a lifter with no bar, and made the last-logged
     * line read the bar as "added" load.
     */
    @Test
    fun `gym-line rungs seed weighted and need the bar`() {
        val rows = Seed.exercises.associateBy { it.name }
        Skills.ALL.filter { it.line in Skills.GYM_LINES }.forEach { skill ->
            assertTrue("${skill.name} must seed weighted", rows.getValue(skill.name).isWeighted)
            assertTrue("${skill.name} needs a gear row", GearRequirements.hasEntry(skill.name))
            assertTrue(
                "${skill.name} needs a barbell",
                GearRequirements.needs(skill.name).all { Gear.BARBELL in it },
            )
        }
        assertFalse(rows.getValue("Pull-up").isWeighted)
        assertTrue(rows.getValue("Weighted Pull-up").isWeighted)
    }
}
