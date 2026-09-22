package com.monarch.app.domain

import com.monarch.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

class XpTest {

    private val bodyweight = 80.0

    private fun sets(n: Int, name: String, reps: Int, weightKg: Double? = null, modifiers: String = "") =
        List(n) { Xp.SetEffort(name, reps = reps, weightKg = weightKg, modifiers = modifiers) }

    /** A hold set, stated the way the app now stores one: seconds, zero reps. */
    private fun hold(n: Int, name: String, seconds: Int, weightKg: Double? = null) =
        List(n) {
            Xp.SetEffort(name, reps = 0, holdSeconds = seconds, weightKg = weightKg, metric = ExerciseMetric.HOLD)
        }

    // ---------------------------------------------------------------- difficulty

    /**
     * The reported bug: five handstand push-ups paid less than twenty
     * push-ups, because every rep was worth one XP whatever it cost. Per rep,
     * the tier IV press must now clearly beat the tier II one.
     */
    @Test
    fun `a harder movement pays more per rep than an easier one`() {
        val hspu = Xp.setXp(Xp.SetEffort("Handstand Push-up", reps = 5), bodyweight)
        val pushUp = Xp.setXp(Xp.SetEffort("Push-up", reps = 5), bodyweight)
        // Two tiers apart, so four times the rate once the flat per-set bonus
        // both sets earn is taken out.
        val hardRate = (hspu - Xp.BASE_PER_SET).toDouble()
        val easyRate = (pushUp - Xp.BASE_PER_SET).toDouble()
        assertEquals("hspu=$hspu pushUp=$pushUp", 4.0, hardRate / easyRate, 0.05)
    }

    /**
     * The owner's actual comparison: 3x5 handstand push-ups against 3x20
     * weighted deficit decline push-ups. Volume may not simply win — the two
     * must land within a quarter of each other.
     */
    @Test
    fun `five hard reps stand against twenty loaded easy ones`() {
        val hard = Xp.award(sets(3, "Handstand Push-up", 5), bodyweight)
        val easy = Xp.award(
            sets(3, "Push-up", 20, weightKg = 20.0, modifiers = "weighted, decline, deficit"),
            bodyweight,
        )
        assertTrue("hard=$hard easy=$easy", hard > easy * 0.75 && hard < easy * 1.25)
    }

    /** A user-invented name cannot mint XP: it is scored as ordinary work. */
    @Test
    fun `an unknown movement is scored at the default tier`() {
        assertEquals(
            Xp.setXp(Xp.SetEffort("Push-up", reps = 8), bodyweight),
            Xp.setXp(Xp.SetEffort("Interdimensional Thrust", reps = 8), bodyweight),
        )
    }

    // ---------------------------------------------------------------- holds

    /**
     * The second reported bug: a hold's seconds go into the reps field, so a
     * 60-second hollow hold was paid as sixty reps — the best rate in the app.
     */
    @Test
    fun `a minute of holding no longer beats a hard set`() {
        val hold = Xp.award(hold(3, "Hollow Hold", 60), bodyweight)
        val hard = Xp.award(sets(3, "Handstand Push-up", 5), bodyweight)
        assertTrue("3x60s hold=$hold must not out-earn 3x5 HSPU=$hard", hold < hard)
    }

    /** Same tier, same figure typed in: sixty seconds is not sixty reps. */
    @Test
    fun `sixty seconds of a hold pays well under sixty reps`() {
        val hold = Xp.setXp(hold(1, "Hollow Hold", 60).first(), bodyweight)
        val reps = Xp.setXp(Xp.SetEffort("Hanging Knee Raise", reps = 60), bodyweight)
        assertTrue("hold $hold vs reps $reps", hold < reps * 0.6)
    }

    /** The "hold seconds" modifier makes any movement timed, not just catalogued holds. */
    @Test
    fun `the hold modifier converts seconds for any movement`() {
        val timed = Xp.setXp(Xp.SetEffort("Push-up", reps = 60, modifiers = "hold seconds"), bodyweight)
        val counted = Xp.setXp(Xp.SetEffort("Push-up", reps = 60), bodyweight)
        assertTrue("timed $timed vs counted $counted", timed < counted)
    }

    /** A three-minute hold still beats a one-minute one. */
    @Test
    fun `longer holds still pay more`() {
        assertTrue(
            Xp.setXp(hold(1, "L-sit", 180).first(), bodyweight) >
                Xp.setXp(hold(1, "L-sit", 60).first(), bodyweight),
        )
    }

    // ---------------------------------------------------------------- volume

    /** Reps past the band are endurance: doubling a long set must not double its XP. */
    @Test
    fun `volume beyond the full-value band has diminishing returns`() {
        val twenty = Xp.setXp(Xp.SetEffort("Push-up", reps = 20), bodyweight)
        val forty = Xp.setXp(Xp.SetEffort("Push-up", reps = 40), bodyweight)
        assertTrue("20=$twenty 40=$forty", forty > twenty && forty < twenty * 2)
    }

    /** Splitting volume across sets beats grinding it into one — as it should. */
    @Test
    fun `the first reps of a set are the valuable ones`() {
        val oneLongSet = Xp.award(sets(1, "Push-up", 30), bodyweight)
        val threeShortSets = Xp.award(sets(3, "Push-up", 10), bodyweight)
        assertTrue("$oneLongSet vs $threeShortSets", threeShortSets > oneLongSet)
    }

    // ---------------------------------------------------------------- load

    @Test
    fun `added load raises a set and is capped`() {
        val bare = Xp.setXp(Xp.SetEffort("Pull-up", reps = 5), bodyweight)
        val loaded = Xp.setXp(Xp.SetEffort("Pull-up", reps = 5, weightKg = 20.0), bodyweight)
        val absurd = Xp.setXp(Xp.SetEffort("Pull-up", reps = 5, weightKg = 500.0), bodyweight)
        assertTrue("loaded $loaded > bare $bare", loaded > bare)
        assertTrue("absurd $absurd must be capped", absurd <= (bare * Xp.MAX_LOAD_MULTIPLIER).toInt() + Xp.BASE_PER_SET)
    }

    /** No bodyweight on record must not crash or zero the load bonus. */
    @Test
    fun `load still counts with no bodyweight recorded`() {
        assertTrue(
            Xp.setXp(Xp.SetEffort("Pull-up", reps = 5, weightKg = 20.0), null) >
                Xp.setXp(Xp.SetEffort("Pull-up", reps = 5), null),
        )
    }

    // ------------------------------------------------------- machine implements

    /**
     * The first catalogue movement whose implement transmits less than a
     * free-weight kilogram (sled, Smith, stack, dual pulley). Discovered from
     * the catalogue rather than named, so the test tracks whatever the gym
     * rows actually ship.
     */
    private fun machineMovement(): String =
        Seed.exercises
            .map { it.name }
            .firstOrNull { MovementDifficulty.loadFactor(it) < MovementDifficulty.FREE_WEIGHT_LOAD }
            ?: run {
                Assume.assumeTrue(
                    "no machine movement seeded yet: the load-factor tests need one",
                    false,
                )
                error("unreachable")
            }

    /**
     * The defect [MovementDifficulty.loadFactor] exists to prevent: before the
     * transmission ratio, an 80 kg hunter's 10-rep leg press at 200 kg paid
     * MORE XP and MORE strength than a 10-rep front squat, making a
     * plate-hungry machine the best-value movement in the app. The sled is not
     * harder than the squat; its NUMBER is bigger. The invariant the ratio
     * actually guarantees: the same marked number pays strictly less on a
     * machine than on a barbell, in both currencies. With the factor gone
     * (table empty or unwired) both multipliers return 3.0 and these
     * assertions flip to equality, so the test bites.
     *
     * The barbell reference is the front squat: a catalogue-only lift whose
     * tier still sits beside the machines. The back squat is now tier I of the
     * gym progression tree, and its scoring tier is deliberately below the
     * machines' - comparing against it would pit a tier change against a load
     * factor and prove nothing.
     */
    @Test
    fun `the same marked kilos pay less on a machine than on a barbell`() {
        val machine = machineMovement()
        val barbell = "front squat"
        // Below the barbell's cap threshold (added = 2x bodyweight): at 200 kg
        // BOTH implements peg the 3.0 ceiling and tie, which would prove
        // nothing about the transmission ratio.
        val marked = 100.0
        Assume.assumeTrue(
            "machine $machine must share the barbell's tier for the set comparison to be fair",
            MovementDifficulty.intensity(barbell) == MovementDifficulty.intensity(machine),
        )
        val squat = Xp.setXp(Xp.SetEffort(barbell, reps = 10, weightKg = marked), bodyweight)
        val machineXp = Xp.setXp(Xp.SetEffort(machine, reps = 10, weightKg = marked), bodyweight)
        assertTrue("squat $squat must out-earn ${machine.lowercase()} $machineXp", squat > machineXp)

        // Same comparison through the strength score, tier-free via multipliers.
        val squatStrength =
            StrengthIndex.repScore(barbell, reps = 10, addedKg = marked, bodyweightKg = bodyweight)
        val machineStrength =
            StrengthIndex.repScore(machine, reps = 10, addedKg = marked, bodyweightKg = bodyweight)
        assertTrue(
            "squat $squatStrength must out-score machine $machineStrength",
            squatStrength > machineStrength,
        )
        assertTrue(
            "machine multiplier must sit below the barbell's",
            Xp.loadMultiplier(machine, marked, bodyweight) <
                Xp.loadMultiplier(barbell, marked, bodyweight),
        )
    }

    /**
     * A barbell's kilos hang vertically off the body: the factor is 1.0 and
     * the marked value must pass through untouched. If it did not, this change
     * would have silently restated every existing user's score.
     */
    @Test
    fun `a free-weight movement's load passes through at its marked value`() {
        assertEquals(
            (bodyweight + 100.0) / bodyweight,
            Xp.loadMultiplier("back squat", 100.0, bodyweight),
            0.0,
        )
        // Same through the strength score: identical ratio, so the two
        // currencies restate nobody's history.
        val bare = StrengthIndex.repScore("back squat", 10, null, bodyweight)
        val loaded = StrengthIndex.repScore("back squat", 10, 100.0, bodyweight)
        assertEquals((bodyweight + 100.0) / bodyweight, loaded / bare, 1e-9)
    }

    /**
     * Both currencies must apply the SAME factor to the same implement - the
     * whole reason [MovementDifficulty.loadFactor] lives in the shared body.
     * Each ratio below is computed through its own currency's public path; if
     * a future edit converts the kilos in only one of them, the ratios diverge
     * and this fails. The marked kilos stay small enough that neither ratio
     * reaches the XP cap, where a cap on one side only would also mask the
     * divergence.
     */
    @Test
    fun `xp and the strength score transmit the same implement identically`() {
        val machine = machineMovement()
        val marked = 40.0
        val xpRatio = Xp.loadMultiplier(machine, marked, bodyweight)
        val bare = StrengthIndex.repScore(machine, 10, null, bodyweight)
        val loaded = StrengthIndex.repScore(machine, 10, marked, bodyweight)
        assertEquals(
            "machine=$machine marked=$marked: xp ratio $xpRatio vs strength ratio ${loaded / bare}",
            xpRatio,
            loaded / bare,
            1e-9,
        )
    }

    /**
     * The cap binds AFTER the transmission ratio, and the ratio therefore
     * moves where the cap is reached: a barbell's marked kilos hit
     * [Xp.MAX_LOAD_MULTIPLIER] at twice bodyweight, a sled's marked number
     * has to climb further before its transmitted load gets there. A huge
     * marked number still cannot run away on either implement.
     */
    @Test
    fun `the load cap binds after the transmission ratio`() {
        val machine = machineMovement()
        val factor = MovementDifficulty.loadFactor(machine)
        // An absurd marked number caps on both implements.
        assertEquals(
            Xp.MAX_LOAD_MULTIPLIER,
            Xp.loadMultiplier("back squat", 10_000.0, bodyweight),
            0.0,
        )
        assertEquals(
            Xp.MAX_LOAD_MULTIPLIER,
            Xp.loadMultiplier(machine, 10_000.0, bodyweight),
            0.0,
        )
        // The barbell caps exactly at twice bodyweight.
        assertEquals(Xp.MAX_LOAD_MULTIPLIER, Xp.loadMultiplier("back squat", 2 * bodyweight, bodyweight), 0.0)
        // The machine's transmitted load at the same marked number is below
        // the cap, and it caps exactly where bodyweight + marked * factor
        // reaches three bodyweights.
        assertTrue(
            "machine factor $factor must delay the cap",
            Xp.loadMultiplier(machine, 2 * bodyweight, bodyweight) < Xp.MAX_LOAD_MULTIPLIER,
        )
        assertEquals(
            Xp.MAX_LOAD_MULTIPLIER,
            Xp.loadMultiplier(machine, 2 * bodyweight / factor, bodyweight),
            1e-9,
        )
    }

    // ---------------------------------------------------------------- modifiers

    @Test
    fun `assistance lowers and one-arm work raises the same movement`() {
        val plain = Xp.setXp(Xp.SetEffort("Push-up", reps = 8), bodyweight)
        assertTrue(Xp.setXp(Xp.SetEffort("Push-up", reps = 8, modifiers = "assisted"), bodyweight) < plain)
        assertTrue(Xp.setXp(Xp.SetEffort("Push-up", reps = 8, modifiers = "one-arm"), bodyweight) > plain)
    }

    /**
     * "weighted" must not multiply on top of the kilos it describes, or the
     * same plate is paid for twice.
     */
    @Test
    fun `the weighted modifier does not double-count its own load`() {
        assertEquals(
            Xp.setXp(Xp.SetEffort("Dip", reps = 6, weightKg = 20.0), bodyweight),
            Xp.setXp(Xp.SetEffort("Dip", reps = 6, weightKg = 20.0, modifiers = "weighted"), bodyweight),
        )
    }

    // ---------------------------------------------------------------- awards

    @Test
    fun `an empty set pays nothing, not a base rate`() {
        assertEquals(0, Xp.setXp(Xp.SetEffort("Push-up", reps = 0), bodyweight))
        assertEquals(0, Xp.setXp(Xp.SetEffort("Push-up", reps = -5), bodyweight))
    }

    @Test
    fun `finishing with nothing logged still pays only the completion bonus`() {
        assertEquals(Xp.COMPLETION_BONUS, Xp.award(emptyList(), bodyweight))
    }

    // ---------------------------------------------------------------- levels

    @Test
    fun `level one at zero and just under threshold`() {
        assertEquals(1, Xp.levelFor(0))
        assertEquals(1, Xp.levelFor(99))
    }

    @Test
    fun `level two exactly at threshold`() {
        assertEquals(2, Xp.levelFor(100))
    }

    @Test
    fun `level three after accumulating level one and two costs`() {
        // 100 (L1->L2) + 200 (L2->L3) = 300
        assertEquals(3, Xp.levelFor(300))
        assertEquals(2, Xp.levelFor(299))
    }

    @Test
    fun `progress reports remainder into current level`() {
        val progress = Xp.progress(350)
        assertEquals(3, progress.level)
        assertEquals(50L, progress.intoLevel)
        assertEquals(300L, progress.needed)
    }

    @Test
    fun `negative xp treated as zero`() {
        assertEquals(1, Xp.levelFor(-50))
    }
}
