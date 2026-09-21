package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
