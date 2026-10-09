package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.HouseEffects
import com.ironvellum.app.domain.HouseStanding
import com.ironvellum.app.domain.LiftRecord
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.RelicHouse
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Today day card's state, its movement rows and the Veil's full-strength bar. */
class TodayStateTest {

    private val rite = WorkoutPreset(id = 1, name = "Full Body B", scheduledDay = 3)
    private val trial = WorkoutSession(id = 9, presetId = 1, label = "Full Body B", startedAtMs = 0)

    @Test
    fun `a live trial wins today, a sealed rite wins over its Begin`() {
        assertEquals(DayKind.LIVE, dayKind(true, rite, isToday = true, live = trial, sealed = trial))
        assertEquals(DayKind.SEALED, dayKind(true, rite, isToday = true, live = null, sealed = trial))
        assertEquals(DayKind.BEGIN, dayKind(true, rite, isToday = true, live = null, sealed = null))
    }

    @Test
    fun `another day only shows its rite, even with a trial live`() {
        assertEquals(DayKind.PLANNED, dayKind(true, rite, isToday = false, live = trial, sealed = null))
        assertEquals(DayKind.SEALED, dayKind(true, rite, isToday = false, live = null, sealed = trial))
    }

    @Test
    fun `no rite on the day is respite, and no cycle at all is its own state`() {
        assertEquals(DayKind.RESPITE, dayKind(true, null, isToday = true, live = null, sealed = null))
        assertEquals(DayKind.NO_CYCLE, dayKind(false, null, isToday = true, live = null, sealed = null))
        // An open trial under way still gets the Continue card on today, with no cycle.
        assertEquals(DayKind.LIVE, dayKind(false, null, isToday = true, live = trial, sealed = null))
    }

    private fun set(position: Int, index: Int, done: Boolean, kg: Double = 60.0, reps: Int = 5, warmup: Boolean = false) =
        SessionSet(exerciseId = position + 1L, exerciseName = "Lift $position", exercisePosition = position, setIndex = index, reps = reps, weightKg = kg, done = done, warmup = warmup)

    private val catalogue = (1L..3L).associateWith { Exercise(id = it, name = "Lift ${it - 1}", muscleGroup = MuscleGroup.entries.first(), isWeighted = true) }

    @Test
    fun `planned rows read sets by reps`() {
        val rows = plannedRows(listOf(PresetEntry(exerciseId = 1, exerciseName = "Back squat", targetSets = 3, targetReps = 7)))
        assertEquals(listOf(DayRow("Back squat", "3×7", 0, 3, false)), rows)
    }

    @Test
    fun `a live trial shows done, part way and untouched rows, warm-ups left out`() {
        val sets = listOf(
            set(0, 0, true), set(0, 1, true),
            set(1, 0, true), set(1, 1, false), set(1, 2, false),
            set(2, 0, false, reps = 8), set(2, 1, false, reps = 8),
            set(2, 2, false, warmup = true),
        )
        val rows = trialRows(sets, catalogue, sealed = false)
        assertEquals(listOf("2/2", "1/3", "2×8"), rows.map { it.value })
        assertEquals(listOf(true, false, false), rows.map { it.checked })
        assertEquals(listOf(false, true, false), rows.map { it.partly })
    }

    @Test
    fun `a sealed trial names each movement's best set and ticks what was logged`() {
        val sets = listOf(set(0, 0, true, kg = 80.0, reps = 5), set(0, 1, true, kg = 90.0, reps = 7), set(1, 0, false))
        val rows = trialRows(sets, catalogue, sealed = true)
        assertEquals("2/2 · 90 kg × 7", rows[0].value)
        assertTrue(rows[0].checked)
        assertEquals("0/1", rows[1].value)
        assertFalse(rows[1].checked)
    }

    @Test
    fun `the plan line reads as a sentence`() {
        assertEquals("5 exercises · 19 sets · about 54 min", sentencePlan("5 EXERCISES · 19 SETS · ~54 MIN"))
    }

    private val hour = 3_600_000L

    @Test
    fun `the Veil bar is full after collecting and drains across the full-strength day`() {
        val start = 1_000_000_000L
        val fresh = veilStrength(start, start)!!
        assertEquals(1f, fresh.fraction, 0.0001f)
        assertEquals("24 h left at full strength", fresh.caption)
        val nine = veilStrength(start, start + 9 * hour)!!
        assertEquals(0.625f, nine.fraction, 0.001f)
        assertEquals("15 h left at full strength", nine.caption)
        // The last stretch rounds up, so it never reads "0 h left".
        assertEquals("1 h left at full strength", veilStrength(start, start + 23 * hour + hour / 2)!!.caption)
        assertEquals("1 h left at full strength", veilStrength(start, start + 23 * hour)!!.caption)
    }

    @Test
    fun `past the full-strength day the bar is empty and the caption reports the taper`() {
        val start = 1_000_000_000L
        val edge = veilStrength(start, start + 24 * hour)!!
        assertEquals(0f, edge.fraction, 0.0001f)
        assertEquals("Tapering, at 100% strength", edge.caption)
        // 48 h: 24 h into a 48 h taper, halfway down 1.0 to 0.1.
        val taper = veilStrength(start, start + 48 * hour)!!
        assertEquals(0f, taper.fraction, 0.0001f)
        assertEquals("Tapering, at 55% strength", taper.caption)
        // Past the taper it holds at a tenth.
        assertEquals("Tapering, at 10% strength", veilStrength(start, start + 200 * hour)!!.caption)
    }

    @Test
    fun `the Veil bar is absent with no baseline or a clock set backwards`() {
        assertNull(veilStrength(0L, 5 * hour))
        assertNull(veilStrength(10 * hour, 5 * hour))
        assertNotNull(veilStrength(10 * hour, 10 * hour))
    }

    @Test
    fun `a full Iron house stretches the bar to 26 hours`() {
        val start = 1_000_000_000L
        val iron = HouseEffects(listOf(HouseStanding(RelicHouse.Iron, 4)))
        assertEquals("26 h left at full strength", veilStrength(start, start, iron)!!.caption)
        // 24 h in, the plain bar has tapered; Iron's still has two hours in hand.
        assertEquals("Tapering, at 100% strength", veilStrength(start, start + 24 * hour)!!.caption)
        val held = veilStrength(start, start + 24 * hour, iron)!!
        assertEquals("2 h left at full strength", held.caption)
        assertEquals(2f / 26f, held.fraction, 0.001f)
        // The taper starts at 26 h and runs 48 h: at 50 h it is halfway from 1.0 to the floor.
        assertEquals("Tapering, at 55% strength", veilStrength(start, start + 50 * hour, iron)!!.caption)
    }

    @Test
    fun `the rate drops a trailing zero only when whole`() {
        assertEquals("40", rateLabel(40.0))
        assertEquals("12.5", rateLabel(12.5))
    }

    private fun budget(
        available: Int,
        rowCount: Int = 6,
        veilFull: Int? = null,
        chrome: Int = 400,
        veilCompact: Int = 100,
    ) = TodayBudget(
        available = available, chrome = chrome, rowCount = rowCount, rowsTopPad = 10,
        naturalRow = 52, tightRow = 40, moreLine = 28, veilFull = veilFull, veilCompact = veilCompact,
    )

    @Test
    fun `every row stays at its natural height when there is room`() {
        // 400 + 100 + 10 + 6 * 52 = 822
        assertEquals(TodayFit(6, 52, fullVeil = false), fitToday(budget(available = 1000)))
        assertEquals(TodayFit(6, 52, fullVeil = false), fitToday(budget(available = 822)))
    }

    @Test
    fun `rows tighten toward the minimum before any is dropped`() {
        // 510 + 6 * 45 = 780: each row gets 45
        assertEquals(TodayFit(6, 45, fullVeil = false), fitToday(budget(available = 780)))
        // 510 + 6 * 40 = 750 is the tightest all six go
        assertEquals(TodayFit(6, 40, fullVeil = false), fitToday(budget(available = 750)))
    }

    @Test
    fun `rows fold into the more line once the tight list no longer fits`() {
        // 749: six tight rows do not fit; 510 + 28 + 5 * 40 = 738 does, with 11 spare over 5 rows
        assertEquals(TodayFit(5, 42, fullVeil = false), fitToday(budget(available = 749)))
        // 510 + 28 + 2 * 40 = 618
        assertEquals(TodayFit(2, 40, fullVeil = false), fitToday(budget(available = 618)))
    }

    @Test
    fun `truncation can reach no rows at all, still showing the more line`() {
        // 510 + 28 = 538
        assertEquals(TodayFit(0, 52, fullVeil = false), fitToday(budget(available = 538)))
        assertEquals(TodayFit(0, 52, fullVeil = false), fitToday(budget(available = 570)))
    }

    @Test
    fun `when even the minimum does not fit the caller scrolls`() {
        assertNull(fitToday(budget(available = 537)))
        assertNull(fitToday(budget(available = 100)))
        // No rows at all: the chrome and the compact Veil are the whole page.
        assertNull(fitToday(budget(available = 499, rowCount = 0)))
        assertEquals(TodayFit(0, 0, fullVeil = false), fitToday(budget(available = 500, rowCount = 0)))
    }

    @Test
    fun `the full Veil is kept only while it fits, then the compact one`() {
        val respite = { available: Int -> budget(available, rowCount = 0, veilFull = 220, chrome = 400) }
        assertEquals(TodayFit(0, 0, fullVeil = true), fitToday(respite(620)))
        assertEquals(TodayFit(0, 0, fullVeil = false), fitToday(respite(619)))
        assertEquals(TodayFit(0, 0, fullVeil = false), fitToday(respite(500)))
        assertNull(fitToday(respite(499)))
    }

    @Test
    fun `the full Veil is not worth a dropped row`() {
        // Rows present: the full form is offered only if every row still fits beside it.
        assertEquals(TodayFit(2, 52, fullVeil = true), fitToday(budget(available = 400 + 220 + 10 + 104, rowCount = 2, veilFull = 220)))
        // Tightened to the minimum beside the full form is still the full form.
        assertEquals(TodayFit(2, 40, fullVeil = true), fitToday(budget(available = 400 + 220 + 10 + 80, rowCount = 2, veilFull = 220)))
        // One px short: the compact form takes over and both rows keep their height.
        assertEquals(TodayFit(2, 52, fullVeil = false), fitToday(budget(available = 400 + 220 + 10 + 79, rowCount = 2, veilFull = 220)))
    }

    @Test
    fun `a more line larger than a row still folds only what must go`() {
        val big = TodayBudget(available = 410 + 10 + 3 * 40 - 1, chrome = 410, rowCount = 3, rowsTopPad = 10, naturalRow = 52, tightRow = 40, moreLine = 60, veilFull = null, veilCompact = 0)
        // 3 tight rows do not fit, 2 + a 60px line do not either: 1 row and the line
        assertEquals(TodayFit(1, 52, fullVeil = false), fitToday(big))
    }

    private fun peak(set: SessionSet?, addsToBody: Boolean, series: List<Double> = listOf(100.0, 112.0), best: Double = 112.0) =
        LiftRecord(
            name = "Pull-up", bestE1rmKg = best, bestAtMs = 0, series = series, deltaKg = null, lastAtMs = 0,
            bestSet = set, bestSetAddsToBody = addsToBody,
        )

    @Test
    fun `a peak reads as the set that set it`() {
        val barbell = SessionSet(exerciseId = 1, exerciseName = "Back Squat", setIndex = 0, reps = 5, weightKg = 100.0, done = true)
        assertEquals("100 kg × 5", peakSetText(peak(barbell, addsToBody = false)))
        val loaded = SessionSet(exerciseId = 2, exerciseName = "Pull-up", setIndex = 0, reps = 8, weightKg = 11.0, done = true)
        assertEquals("+11 kg × 8", peakSetText(peak(loaded, addsToBody = true)))
    }

    @Test
    fun `a peak without its set falls back to the estimate`() {
        assertEquals("est. 112 kg", peakSetText(peak(null, addsToBody = false)))
    }

    @Test
    fun `a peak says how far it rose, never zero`() {
        assertEquals("12 kg", peakGainText(peak(null, false)))
        assertEquals("2.5 kg", peakGainText(peak(null, false, series = listOf(109.5, 112.0))))
        assertEquals("0.1 kg", peakGainText(peak(null, false, series = listOf(111.99, 112.0))))
    }

    @Test
    fun `the narrator speaks once per state, and every line is free of retired words`() {
        assertEquals("The page is blank. 4 days of ink behind it.", narratorLine(DayKind.BEGIN, daysKept = 4))
        assertEquals("The page is blank. 1 day of ink behind it.", narratorLine(DayKind.BEGIN, daysKept = 1))
        assertEquals("The page is blank.", narratorLine(DayKind.BEGIN))
        assertEquals("The page is blank. Ink the first set.", narratorLine(DayKind.LIVE, 0, 19))
        assertEquals("1 set inked. The page begins to fill.", narratorLine(DayKind.LIVE, 1, 19))
        assertEquals("7 sets inked. The page is half written.", narratorLine(DayKind.LIVE, 7, 19))
        assertEquals("Every set is inked. Seal the page.", narratorLine(DayKind.LIVE, 19, 19))
        assertEquals("The Ledger gilds its page.", narratorLine(DayKind.SEALED))
        assertEquals("The Ledger rests its pen. Your oath holds.", narratorLine(DayKind.RESPITE, daysKept = 4))
        assertEquals("The Ledger rests its pen.", narratorLine(DayKind.RESPITE))
        assertEquals("The page waits for its day.", narratorLine(DayKind.PLANNED))
        val lines = DayKind.entries.flatMap { kind ->
            listOf(narratorLine(kind), narratorLine(kind, 3, 10, 2), narratorLine(kind, 10, 10, 1))
        }
        val hits = lines.filter { line -> com.ironvellum.app.RETIRED_WORDS.any { it.containsMatchIn(line) } }
        assertEquals(emptyList<String>(), hits)
        // The fit tests look for "more" and "essence" as single nodes: the narrator must not say either.
        assertTrue(lines.none { "more" in it || "essence" in it })
    }

    @Test
    fun `the inscriptions line is always there, loud only when some wait`() {
        assertEquals(InscriptionsLine("3 inscriptions waiting", waiting = true, action = "Inscribe (3)"), inscriptionsLine(3))
        assertEquals(InscriptionsLine("1 inscription waiting", waiting = true, action = "Inscribe (1)"), inscriptionsLine(1))
        assertEquals(InscriptionsLine("No inscriptions waiting", waiting = false), inscriptionsLine(0))
        // Still loading: the slot is held, with nothing claimed in it.
        assertEquals(InscriptionsLine("", waiting = false), inscriptionsLine(null))
        // None waiting and one affordable: the slot offers to buy it, with the price.
        assertEquals(InscriptionsLine("No inscriptions waiting", waiting = false, action = "Buy one \u00b7 5,000"), inscriptionsLine(0, 5_000L))
        // Waiting wins over a price: the action is to inscribe.
        assertEquals("Inscribe (2)", inscriptionsLine(2, 5_000L).action)
        assertNull(inscriptionsLine(0).action)
    }

    @Test
    fun `the stamp thuds only for a rite sealed within the hour`() {
        val now = 10 * STAMP_FRESH_MS
        assertTrue(stampIsFresh(now - 60_000, now))
        assertTrue(stampIsFresh(now - STAMP_FRESH_MS, now))
        assertFalse(stampIsFresh(now - STAMP_FRESH_MS - 1, now))
        assertFalse(stampIsFresh(null, now))
        // A clock set backwards is not fresh news.
        assertFalse(stampIsFresh(now + 1, now))
    }

    @Test
    fun `motes are seeded, in bounds and wrap the shared loop without a jump`() {
        val field = veilMotes(22, seed = 7)
        assertEquals(field, veilMotes(22, seed = 7))
        assertEquals(22, field.size)
        field.forEach { m ->
            assertEquals("period must divide the loop", 0, MOTION_LOOP_S % m.periodS)
            assertTrue(m.x in 0f..1f && m.y in 0f..1f && m.rise in 0.3f..0.85f)
            assertTrue(m.radiusDp in 0.7f..2f)
            // The end of the loop is the start of the next.
            assertEquals(moteProgress(m, 0f), moteProgress(m, 1f - 1e-7f), 1e-3f)
            assertTrue(moteProgress(m, 0.37f) in 0f..1f)
        }
    }

    @Test
    fun `a mote fades in and out and is never lit at either end of its rise`() {
        assertEquals(0f, moteAlpha(0f), 0f)
        assertEquals(0f, moteAlpha(1f), 0f)
        assertEquals(0.75f, moteAlpha(0.15f), 1e-6f)
        assertTrue(moteAlpha(0.5f) in 0.45f..0.75f)
        assertTrue((0..100).all { moteAlpha(it / 100f) in 0f..0.75f })
    }
}
