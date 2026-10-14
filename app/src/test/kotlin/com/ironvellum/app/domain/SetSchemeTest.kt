package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The per-lift scheme: what it stores, the sets it makes, and the loads a top set hands its back-offs. */
class SetSchemeTest {

    /** A belt and no vest: the kit does not apply, so the lift's own step (2.5 kg on a pull-up) rounds the load. */
    private val belt = LiftLoading(stepKg = 2.5, worn = null, bodyweightKg = 79.0, bodyweightLift = true)
    private val vest = LiftLoading(stepKg = 2.5, worn = LoadKit(vestMaxKg = 20.0, vestBlockKg = 1.0), bodyweightKg = 79.0, bodyweightLift = true)

    /** The owner's own plan: a weighted pull-up, top set +22 kg x 2-4, then three back-offs at a fixed +15 kg x 4-5. */
    private val owners = SetScheme(
        type = SchemeType.TOP_BACKOFF,
        topReps = 2..4, topRir = 0..1,
        backoffSets = 3, backoffFixedKg = 15.0, backoffReps = 4..5,
    )

    // ---- the stored form

    @Test
    fun `straight stores nothing and the owners plan survives the column`() {
        assertNull(SetScheme().encode())
        assertEquals(SetScheme(), SetScheme.decode(null))
        assertEquals(SetScheme(), SetScheme.decode(""))
        val text = owners.encode()!!
        assertEquals(owners, SetScheme.decode(text))
        assertTrue(text, text.contains("bf=15") && !text.contains("bp="))
        assertEquals(4, owners.workingSets(1))
    }

    @Test
    fun `every scheme round-trips and an unreadable column is straight`() {
        val schemes = listOf(
            SetScheme(type = SchemeType.TOP_BACKOFF, backoffPercent = 15, restAfterTopSec = 240, warmupRamp = true),
            SetScheme(type = SchemeType.DROPS, dropSets = 2, dropCount = 2, dropPercent = 25),
            SetScheme(type = SchemeType.CUSTOM, custom = listOf(SetKind.WORKING, SetKind.TOP, SetKind.BACKOFF, SetKind.FAILURE)),
            SetScheme(warmupRamp = true),
        )
        schemes.forEach { assertEquals(it, SetScheme.decode(it.encode())) }
        assertEquals(SetScheme(), SetScheme.decode("garbage"))
        assertEquals(SetScheme(), SetScheme.decode("t=no_such_scheme"))
        // Out-of-range figures are clamped, never trusted.
        val wild = SetScheme.decode("t=top_backoff;bo=99;bp=1;tr=0-900;rt=5")
        assertEquals(SetScheme.MAX_BACKOFF_SETS, wild.backoffSets)
        assertEquals(SetScheme.PERCENT_RANGE.first, wild.backoffPercent)
        assertEquals(1..100, wild.topReps)
        assertEquals(SetScheme.REST_RANGE_SEC.first, wild.restAfterTopSec)
    }

    @Test
    fun `normalising keeps only the chosen cards settings`() {
        val draft = SetScheme(type = SchemeType.STRAIGHT, topReps = 2..4, backoffFixedKg = 9.0, dropCount = 3)
        assertEquals(SetScheme(), draft.normalised())
        assertNull(draft.normalised().encode())
        val fixed = SetScheme(type = SchemeType.TOP_BACKOFF, backoffPercent = 25, backoffFixedKg = 15.0, dropCount = 3)
        assertEquals(SetScheme.DEFAULT_BACKOFF_PERCENT, fixed.normalised().backoffPercent)
    }

    // ---- the sets a scheme makes

    @Test
    fun `kinds follow the scheme`() {
        assertEquals(List(3) { SetKind.WORKING }, SetScheme().kinds(3))
        assertEquals(listOf(SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF), SetScheme(type = SchemeType.TOP_BACKOFF).kinds(99))
        // The last set drops twice: 3 sets, the last one followed by two drops.
        val drops = SetScheme(type = SchemeType.DROPS, dropSets = 1, dropCount = 2)
        assertEquals(
            listOf(SetKind.WORKING, SetKind.WORKING, SetKind.WORKING, SetKind.DROP, SetKind.DROP),
            drops.kinds(3),
        )
        // The last two sets each get a drop.
        assertEquals(
            listOf(SetKind.WORKING, SetKind.WORKING, SetKind.DROP, SetKind.WORKING, SetKind.DROP),
            SetScheme(type = SchemeType.DROPS, dropSets = 2).kinds(3),
        )
        assertEquals(4, SetScheme(type = SchemeType.TOP_BACKOFF, backoffSets = 3).workingSets(1))
        assertEquals(3, drops.workingSets(3))
    }

    @Test
    fun `the Forge fitting a new set count keeps the scheme honest`() {
        assertEquals(3, SetScheme(type = SchemeType.TOP_BACKOFF).fitTo(4).backoffSets)
        assertEquals(SetScheme(), SetScheme(type = SchemeType.CUSTOM, custom = listOf(SetKind.TOP, SetKind.BACKOFF)).fitTo(3))
        assertEquals(2, SetScheme(type = SchemeType.DROPS, dropSets = 3).fitTo(2).dropSets)
    }

    // ---- back-off loads

    @Test
    fun `a percent back-off comes off the total load including the body and rounds down onto a belt plate`() {
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF)
        val backoff = SchemePlan.backoff(topKg = 18.7, topReps = 4, scheme = scheme, lift = belt)
        // (79 + 18.7) x 0.9 = 87.93, less the body 8.93 added, down to 7.5 on a 2.5 kg plate grid.
        assertEquals(7.5, backoff.weightKg!!, 1e-9)
        assertEquals(7, backoff.reps)
        assertEquals(6..8, backoff.range)
        assertEquals(Rounding.BELT_PLATES, backoff.rounding)
    }

    @Test
    fun `with a vest the back-off lands on its blocks and never above the percent asked for`() {
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF)
        val backoff = SchemePlan.backoff(18.7, 4, scheme, vest)
        assertEquals(8.0, backoff.weightKg!!, 1e-9)
        assertEquals(Rounding.VEST_BLOCKS, backoff.rounding)
        // Past the vest the belt's plates take over, and the note says so.
        val both = vest.copy(worn = LoadKit(vestMaxKg = 10.0, vestBlockKg = 1.0, dipBelt = true))
        val past = SchemePlan.backoff(24.0, 4, scheme, both)
        // (79 + 24) x 0.9 = 92.7, added 13.7: on the belt, in 2.5 kg plates, 12.5.
        assertEquals(12.5, past.weightKg!!, 1e-9)
        assertEquals(Rounding.BELT_PLATES, past.rounding)
    }

    @Test
    fun `a back-off under one block is bodyweight, not a one block load`() {
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF)
        val twoKgBlocks = vest.copy(worn = LoadKit(vestMaxKg = 20.0, vestBlockKg = 2.0))
        // (80 + 10) x 0.9 = 81, added 1.0: less than one 2 kg block.
        val tiny = SchemePlan.backoff(10.0, 4, scheme, twoKgBlocks.copy(bodyweightKg = 80.0))
        assertNull(tiny.weightKg)
        // A top set so light the percent lands under the body itself.
        assertNull(SchemePlan.backoff(5.0, 4, scheme, belt.copy(bodyweightKg = 80.0)).weightKg)
    }

    @Test
    fun `a fixed back-off is the load as given, whatever the top set was`() {
        val backoff = SchemePlan.backoff(topKg = 22.0, topReps = 3, scheme = owners, lift = belt)
        assertEquals(15.0, backoff.weightKg!!, 0.0)
        assertEquals(5, backoff.reps)
        assertEquals(4..5, backoff.range)
        assertNull(backoff.rounding)
        assertEquals(15.0, SchemePlan.backoff(26.0, 2, owners, belt).weightKg!!, 0.0)
    }

    @Test
    fun `an unweighted bodyweight top set gets back-offs two reps fewer instead of less load`() {
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF)
        val backoff = SchemePlan.backoff(topKg = null, topReps = 9, scheme = scheme, lift = belt)
        assertNull(backoff.weightKg)
        assertEquals(7, backoff.reps)
        // Never below one rep.
        assertEquals(1, SchemePlan.backoff(null, 2, scheme, belt).reps)
        // A plain lift with no load keeps its own band.
        val row = LiftLoading(stepKg = 2.5, bodyweightLift = false)
        assertEquals(7, SchemePlan.backoff(null, 9, scheme, row).reps)
    }

    @Test
    fun `a barbell back-off is the percent of the top rounded down to the plate grid`() {
        val squat = LiftLoading(stepKg = 5.0)
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF)
        assertEquals(90.0, SchemePlan.backoff(100.0, 5, scheme, squat).weightKg!!, 1e-9)
        assertEquals(90.0, SchemePlan.backoff(102.5, 5, scheme, squat).weightKg!!, 1e-9)
        assertEquals(80.0, SchemePlan.backoff(100.0, 5, scheme.copy(backoffPercent = 20), squat).weightKg!!, 1e-9)
    }

    @Test
    fun `in pounds the back-off lands on the lb grid of the figure the lifter reads`() {
        val squat = LiftLoading(stepKg = 5.0, unit = LoadUnit.LB)
        val top = Loads.toKg(225.0, LoadUnit.LB)
        val backoff = SchemePlan.backoff(top, 5, SetScheme(type = SchemeType.TOP_BACKOFF), squat)
        // 225 lb less 10% is 202.5 lb, down onto the 5 lb grid: 200 lb.
        assertEquals(Loads.toKg(200.0, LoadUnit.LB), backoff.weightKg!!, 1e-3)
    }

    // ---- the whole plan

    @Test
    fun `the owners plan makes a top set then three fixed back-offs`() {
        val sets = SchemePlan.plan(owners, entrySets = 4, entryReps = 2, entryKg = 22.0, lift = belt)
        assertEquals(listOf(SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF, SetKind.BACKOFF), sets.map { it.kind })
        assertEquals(22.0, sets[0].weightKg!!, 0.0)
        assertEquals(2, sets[0].reps)
        assertEquals(2..4, sets[0].range)
        sets.drop(1).forEach {
            assertEquals(15.0, it.weightKg!!, 0.0)
            assertEquals(5, it.reps)
        }
    }

    @Test
    fun `a suggested top set carries its load into the back-offs`() {
        val scheme = SetScheme(type = SchemeType.TOP_BACKOFF)
        val top = TopSuggestion(reps = 4, weightKg = 18.7, beats = TopMark(4, 17.7))
        val sets = SchemePlan.plan(scheme, 3, 8, 7.7, belt, top)
        assertEquals(18.7, sets[0].weightKg!!, 0.0)
        assertEquals(7.5, sets[1].weightKg!!, 1e-9)
    }

    @Test
    fun `drops step down from the set before them and straight sets stay put`() {
        val drops = SetScheme(type = SchemeType.DROPS, dropSets = 1, dropCount = 2, dropPercent = 20)
        val lift = LiftLoading(stepKg = 1.25, bodyweightLift = false)
        val sets = SchemePlan.plan(drops, 3, 12, 12.5, lift)
        // 10.0 less 20% is 8.0, which the 1.25 kg grid rounds down to 7.5.
        assertEquals(listOf(12.5, 12.5, 12.5, 10.0, 7.5), sets.map { it.weightKg })
        assertEquals(listOf(SetKind.WORKING, SetKind.WORKING, SetKind.WORKING, SetKind.DROP, SetKind.DROP), sets.map { it.kind })
        val straight = SchemePlan.plan(SetScheme(), 3, 8, 60.0, LiftLoading(5.0))
        assertEquals(List(3) { 60.0 }, straight.map { it.weightKg })
    }

    // ---- the top set

    @Test
    fun `a top set climbs a rep then a load`() {
        val range = 4..6
        val next: (Double?) -> Double? = { (it ?: 0.0) + 1.0 }
        val climbing = SchemePlan.suggestTop(TopMark(4, 17.7), range, riteKg = 17.7, nextKg = next)!!
        assertEquals(5, climbing.reps)
        assertEquals(17.7, climbing.weightKg!!, 0.0)
        val full = SchemePlan.suggestTop(TopMark(6, 17.7), range, riteKg = 17.7, nextKg = next)!!
        assertEquals(4, full.reps)
        assertEquals(18.7, full.weightKg!!, 1e-9)
        // The owner's range tops out at 4: a 4 x +22 asks for 2 x +23.
        val owner = SchemePlan.suggestTop(TopMark(4, 22.0), 2..4, riteKg = 22.0, nextKg = next)!!
        assertEquals(2, owner.reps)
        assertEquals(23.0, owner.weightKg!!, 1e-9)
        // A rite load raised above the last top is a bump on purpose.
        val bumped = SchemePlan.suggestTop(TopMark(4, 22.0), 2..4, riteKg = 25.0, nextKg = next)!!
        assertEquals(25.0, bumped.weightKg!!, 0.0)
        assertEquals(2, bumped.reps)
        assertNull(SchemePlan.suggestTop(null, range, 20.0, next))
    }

    @Test
    fun `the rest after a top set is the schemes own, three minutes by default`() {
        assertEquals(180, SchemePlan.restAfterTop(null))
        assertEquals(180, SchemePlan.restAfterTop(SetScheme()))
        assertEquals(240, SchemePlan.restAfterTop(SetScheme(type = SchemeType.TOP_BACKOFF, restAfterTopSec = 240)))
    }

    // ---- the words

    @Test
    fun `the rite row and the offer say what the scheme is`() {
        assertNull(SchemeText.summary(SetScheme(), 3))
        assertEquals("Top set + 2 back-offs", SchemeText.summary(SetScheme(type = SchemeType.TOP_BACKOFF), 3))
        assertEquals("Top set + 1 back-off", SchemeText.summary(SetScheme(type = SchemeType.TOP_BACKOFF, backoffSets = 1), 2))
        assertEquals(
            "3 sets, last set drops twice",
            SchemeText.summary(SetScheme(type = SchemeType.DROPS, dropCount = 2), 3),
        )
        assertEquals("3 straight", SchemeText.clause(SetScheme(), 3))
        assertEquals("top + 2 back-offs", SchemeText.clause(SetScheme(type = SchemeType.TOP_BACKOFF), 3))
        val bars = SchemeText.bars(SetScheme(type = SchemeType.TOP_BACKOFF, warmupRamp = true), 3)
        assertEquals(listOf(SetKind.WARMUP, SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF), bars.map { it.kind })
        assertEquals(1f, bars[1].level, 0f)
        assertEquals(0.9f, bars[2].level, 1e-6f)
    }

    // ---- switching the back-off to a fixed load

    private val percent = SetScheme(type = SchemeType.TOP_BACKOFF, backoffPercent = 10)

    @Test
    fun `fixed starts on the load the percent gives now, on the kit steps`() {
        // +22 kg on a 79 kg body, 10% lighter: 11.9 kg added, down to the vest block below.
        assertEquals(11.0, SchemePlan.seedFixedBackoff(22.0, percent, vest), 1e-9)
        // No vest: the lift's own 2.5 kg grid.
        assertEquals(10.0, SchemePlan.seedFixedBackoff(22.0, percent, belt), 1e-9)
        // A plain lift takes the percent off the load itself.
        assertEquals(90.0, SchemePlan.seedFixedBackoff(100.0, percent, LiftLoading(stepKg = 2.5)), 1e-9)
        // The seed is the figure the preview already shows for the percent option.
        assertEquals(SchemePlan.backoff(22.0, 4, percent, vest).weightKg!!, SchemePlan.seedFixedBackoff(22.0, percent, vest), 1e-9)
    }

    @Test
    fun `the seed ignores a stale fixed load and never leaves nothing to load`() {
        assertEquals(11.0, SchemePlan.seedFixedBackoff(22.0, percent.copy(backoffFixedKg = 15.0), vest), 1e-9)
        // No top load, or a back-off under one block: the smallest step, so Done is never blocked.
        assertEquals(2.5, SchemePlan.seedFixedBackoff(null, percent, belt), 1e-9)
        assertEquals(1.0, SchemePlan.seedFixedBackoff(5.0, percent, vest), 1e-9)
    }

    // ---- the figure a rite row prints

    private val kg: (Double) -> String = { (if (it == Math.floor(it)) it.toLong().toString() else it.toString()) + "kg" }

    @Test
    fun `a top set lift reads its real plan, not sets by top reps`() {
        assertEquals("2–4 @22kg · 3 × 4–5 @15kg", SchemeText.figure(owners, 4, 2, 22.0, kg))
        assertEquals("4–6 @100kg · 2 × 6–8 −10%", SchemeText.figure(percent, 3, 4, 100.0, kg))
        // No loads (the Today card): the percent still says itself, a fixed back-off is just sets by reps.
        assertEquals("2–4 · 3 × 4–5", SchemeText.figure(owners, 4, 2, 22.0, null))
        assertEquals("4–6 · 2 × 6–8 −10%", SchemeText.figure(percent, 3, 4, null, null))
        // A bodyweight top set has no load to print.
        assertEquals("2–4 · 3 × 4–5 @15kg", SchemeText.figure(owners, 4, 2, null, kg))
    }

    @Test
    fun `every other scheme keeps the plain sets by reps figure`() {
        assertEquals("3 × 7 @60kg", SchemeText.figure(SetScheme(), 3, 7, 60.0, kg))
        assertEquals("3 × 30s", SchemeText.figure(SetScheme(), 3, 30, null, kg, hold = true))
        assertEquals("3 × 7", SchemeText.figure(SetScheme(type = SchemeType.DROPS), 3, 7, null, null))
    }
}
