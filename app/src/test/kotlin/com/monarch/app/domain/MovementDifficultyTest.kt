package com.monarch.app.domain

import com.monarch.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

class MovementDifficultyTest {

    /**
     * The tree is the difficulty ordering XP reads. If the catalogue and the
     * tree ever disagree on a name's casing, every set of that movement
     * silently drops to the default tier and the rebalance quietly undoes
     * itself.
     */
    @Test
    fun `catalogued movements that exist in the skill tree read their tier from it`() {
        val tree = Skills.ALL.associateBy { it.name.lowercase() }
        Seed.exercises
            .mapNotNull { tree[it.name.lowercase()] }
            // "Weighted X" and tiers II-V of the gym lines are tiered by their
            // standard's load, which XP counts separately - those are
            // deliberately overridden below.
            .filterNot { MovementDifficulty.isLoadPriced(it.name) }
            .forEach { skill ->
                assertEquals(
                    "${skill.name} must score at its tree tier",
                    skill.tier,
                    MovementDifficulty.tier(skill.name),
                )
            }
    }

    /**
     * "Weighted Pull-up" is a tier V standard because of the +25 kg in it.
     * XP already multiplies by the kilos actually logged, so taking that tier
     * at face value pays for the same plate twice — 855 XP for five sets that
     * a plain pull-up session scores at 249.
     */
    @Test
    fun `a weighted variant is tiered as its unloaded parent`() {
        assertEquals(MovementDifficulty.tier("Pull-up"), MovementDifficulty.tier("Weighted Pull-up"))
        assertEquals(MovementDifficulty.tier("Dip"), MovementDifficulty.tier("Weighted Dip"))
        // The gym progression lines price their multiples the same way: the
        // name stays at the line root's unloaded tier, the barbell pays.
        assertEquals(MovementDifficulty.tier("Back Squat"), MovementDifficulty.tier("Triple-Bodyweight Squat"))
        assertEquals(MovementDifficulty.tier("Bench Press"), MovementDifficulty.tier("Paused Bench Press"))
        assertEquals(MovementDifficulty.tier("Overhead Press"), MovementDifficulty.tier("Half-Again Overhead Press"))
        assertEquals(MovementDifficulty.tier("Deadlift"), MovementDifficulty.tier("Heavy Deadlift"))
        // The load still pays — through the kilos, not through the name.
        assertTrue(
            Xp.setXp(Xp.SetEffort("Weighted Pull-up", 5, weightKg = 25.0), 80.0) >
                Xp.setXp(Xp.SetEffort("Weighted Pull-up", 5), 80.0),
        )
    }

    /**
     * Anything the tree does not know still has to be scored. A lifting
     * movement left at a wrong default is a silent economy bug, so the ones
     * the app ships with are named explicitly.
     */
    @Test
    fun `every seeded lifting movement is classified, not defaulted by accident`() {
        val unclassified = Seed.exercises
            // HOLD as well as REPS: a static hold is strength work, and one
            // left unclassified is the same silent economy bug.
            .filter { runCatching { ExerciseMetric.valueOf(it.metric) }.getOrNull()?.isStrength == true }
            .map { it.name }
            .filterNot { MovementDifficulty.isClassified(it) }
        assertEquals(emptyList<String>(), unclassified)
    }

    /**
     * The reverse direction. `plank` was priced at tier 1 and marked a hold
     * while the catalogue seeded only "Weighted Plank", so the classification
     * sat there unreachable — the forward test above passes happily on a key
     * that names nothing at all.
     */
    @Test
    fun `every classification key names a movement the catalogue actually ships`() {
        val seeded = Seed.exercises.map { it.name.trim().lowercase() }.toSet()
        val orphaned = MovementDifficulty.catalogueOnlyKeys.filterNot { it in seeded }.sorted()
        assertEquals(emptyList<String>(), orphaned)
    }

    /**
     * The same dead-classification bug, third table: a `loadFactors` key that
     * names no shipped movement converts nothing while looking like coverage
     * (the plank case that sat dead for months, one table over). The map is
     * private because scoring goes through [MovementDifficulty.loadFactor], so
     * the keys are read reflectively; if the field is renamed, the readable
     * failure here says to update this accessor rather than silently passing.
     */
    @Test
    fun `every load-factor key names a movement the catalogue actually ships`() {
        val field = MovementDifficulty::class.java.getDeclaredField("loadFactors")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val loadFactors = field.get(null) as Map<String, Double>
        Assume.assumeTrue(
            "no load factors are wired yet: the reverse-coverage check needs the table filled",
            loadFactors.isNotEmpty(),
        )
        val seeded = Seed.exercises.map { it.name.trim().lowercase() }.toSet()
        val orphaned = loadFactors.keys.filterNot { it in seeded }.sorted()
        assertEquals("dead load-factor keys: $orphaned", emptyList<String>(), orphaned)
    }

    @Test
    fun `tier ordering matches the tree's own progression`() {
        assertTrue(MovementDifficulty.tier("Handstand Push-up") > MovementDifficulty.tier("Push-up"))
        assertTrue(MovementDifficulty.tier("One-Arm Pull-up") > MovementDifficulty.tier("Pull-up"))
        assertTrue(MovementDifficulty.intensity("Full Planche") > MovementDifficulty.intensity("Frog Stand"))
        // A dip moves close to full bodyweight; it must never price below a
        // diamond push-up again. Equal is the floor, so assert not-below.
        assertFalse(
            MovementDifficulty.tier("Parallel Bar Dip") < MovementDifficulty.tier("Diamond Push-up"),
        )
        // Three separate tables price the dip: the tree row, the catalogue
        // "dip" row and the "weighted dip" load override. Drift between them
        // is the risk, so pin them together.
        assertEquals(
            MovementDifficulty.tier("Parallel Bar Dip"),
            MovementDifficulty.tier("dip"),
        )
        // The load override must track its unloaded parent, or a weighted dip
        // pays for the belt twice.
        assertEquals(
            MovementDifficulty.tier("Parallel Bar Dip"),
            MovementDifficulty.tier("Weighted Dip"),
        )
        // One leg is half the base; it must out-earn the two-leg version.
        assertTrue(
            MovementDifficulty.tier("single-leg glute bridge") > MovementDifficulty.tier("glute bridge"),
        )
    }

    @Test
    fun `name matching ignores case and surrounding space`() {
        assertEquals(
            MovementDifficulty.tier("Handstand Push-up"),
            MovementDifficulty.tier("  handstand push-up "),
        )
    }

    /**
     * The metric is authoritative once a row carries it; a movement whose
     * metric says REPS must not be re-read as seconds just because a stale
     * name matches.
     */
    @Test
    fun `the stored metric outranks the name`() {
        assertTrue(MovementDifficulty.isHoldSet(ExerciseMetric.HOLD, "Some Custom Move"))
        assertFalse(MovementDifficulty.isHoldSet(ExerciseMetric.DURATION, "Hollow Hold"))
        // No metric at all (an archive from before HOLD existed) falls back to the name.
        assertTrue(MovementDifficulty.isHoldSet(null, "Hollow Hold"))
    }

    @Test
    fun `seconds convert to rep-equivalents at one rate`() {
        assertEquals(
            MovementDifficulty.SECONDS_PER_REP_EQUIVALENT,
            Xp.SECONDS_PER_EFFORT_UNIT,
            0.0,
        )
        assertEquals(12.0, MovementDifficulty.holdRepEquivalents(60), 0.0001)
        assertEquals(0.0, MovementDifficulty.holdRepEquivalents(-5), 0.0001)
    }

    @Test
    fun `holds are recognised from the tree, the catalogue and the modifier`() {
        assertTrue(MovementDifficulty.isHoldByName("Hollow Hold"))
        assertTrue(MovementDifficulty.isHoldByName("L-sit"))
        assertTrue(MovementDifficulty.isHoldByName("Active Bar Hang"))
        assertTrue(MovementDifficulty.isHoldSet(ExerciseMetric.REPS, "Push-up", "hold seconds"))
        assertFalse(MovementDifficulty.isHoldByName("Push-up"))
        assertFalse(MovementDifficulty.isHoldByName("Dragon Flag"))
    }

    /**
     * "5 single-arm negatives per side, each 5s to full hang" is a rep
     * standard that mentions seconds. Reading it as a hold would divide those
     * reps by the hold conversion and underpay the set by five.
     */
    @Test
    fun `a rep standard that mentions seconds is not a hold`() {
        assertFalse(MovementDifficulty.isHoldByName("One-Arm Negative"))
        assertFalse(MovementDifficulty.isHoldByName("One-Arm Negative Push-up"))
        assertFalse(MovementDifficulty.isHoldByName("Nordic Negative"))
        assertEquals(Skills.Metric.REPS, Skills.forName("One-Arm Negative")!!.metric)
    }

    @Test
    fun `modifier factors combine and stay inside their bounds`() {
        assertEquals(1.0, MovementDifficulty.modifierFactor(""), 0.0001)
        assertEquals(1.0, MovementDifficulty.modifierFactor("banded, weighted"), 0.0001)
        assertTrue(MovementDifficulty.modifierFactor("deficit, decline") > 1.0)
        assertTrue(MovementDifficulty.modifierFactor("assisted") < 1.0)
        val piled = MovementDifficulty.modifierFactor("one-arm, archer, deficit, tempo, paused, decline")
        assertTrue("stacking must not run away: $piled", piled <= MovementDifficulty.MAX_MODIFIER_FACTOR)
    }

    /**
     * The strength weight is sqrt of the XP intensity: bodyweight scaling must
     * stay the dominant term of the strength index, so the hardest movement
     * caps at 4x the easiest even though XP pays 16x.
     */
    @Test
    fun `strength weight damps the tier curve to its square root`() {
        assertEquals(1.0, MovementDifficulty.strengthWeight("Bodyweight Squat"), 0.0001)
        assertEquals(
            kotlin.math.sqrt(MovementDifficulty.intensity("One-Arm Pull-up")),
            MovementDifficulty.strengthWeight("One-Arm Pull-up"),
            0.0001,
        )
        assertEquals(4.0, MovementDifficulty.strengthWeight("One-Arm Pull-up"), 0.0001)
        // An unclassified name falls back to DEFAULT_TIER, same as XP does.
        assertEquals(
            MovementDifficulty.strengthWeight("Bodyweight Squat") *
                kotlin.math.sqrt(MovementDifficulty.TIER_BASE),
            MovementDifficulty.strengthWeight("Some Custom Move"),
            0.0001,
        )
    }

    /**
     * Both currencies read ONE taper. If XP's alias ever diverges from the
     * shared body, the strength index and XP would price the same 100-rep
     * set with different diminishing returns — the exact disagreement this
     * move exists to make impossible.
     */
    @Test
    fun `xp's taper alias reads the shared body`() {
        listOf(0.0, 5.0, 10.0, 25.0, 100.0).forEach { units ->
            assertEquals(
                MovementDifficulty.taperedVolume(units),
                Xp.taperedVolume(units),
                0.0,
            )
        }
        assertEquals(10.0 + 90.0 * MovementDifficulty.TAPERED_RATE, Xp.taperedVolume(100.0), 0.0001)
    }
}
