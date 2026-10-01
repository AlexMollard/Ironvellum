package com.ironvellum.app.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerMathTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 1)

    private fun day(offset: Long, steps: Int = 0, kcal: Int = 0, sleep: Int = 0, hr: Int? = null) =
        HealthDay(today.minusDays(offset), steps = steps, activeKcal = kcal, sleepMinutes = sleep, restingHr = hr)

    private fun ms(date: LocalDate) = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun stat(date: LocalDate, kg: Double, height: Double = 0.0, bf: Double? = null) =
        StatEntry(takenAtMs = ms(date), weightKg = kg, heightCm = height, bodyFatPct = bf)

    // ---- calendar windows

    @Test
    fun `today is never an older day`() {
        val rows = listOf(day(2, steps = 9000), day(1, steps = 4000))
        assertNull("no row for today means not synced", Ledger.todayRow(rows, today))
        assertEquals(10, Ledger.todayRow(rows + day(0, steps = 10), today)!!.steps)
    }

    @Test
    fun `slots are fixed calendar dates with gaps as null`() {
        val rows = listOf(day(0, steps = 100), day(2, steps = 300), day(6, steps = 700), day(30, steps = 5))
        val slots = Ledger.slots(rows, today, 7) { it.steps.toDouble() }
        assertEquals(7, slots.size)
        // Oldest first: index 0 is six days ago, index 6 is today.
        assertEquals(listOf(700.0, null, null, null, 300.0, null, 100.0), slots)
        assertEquals("a row outside the window is not counted", 3, Ledger.tracked(slots))
    }

    @Test
    fun `a row with only sleep is not a steps day`() {
        val slots = Ledger.slots(listOf(day(0, sleep = 400)), today, 3) { it.steps.toDouble() }
        assertEquals(listOf(null, null, null), slots)
    }

    @Test
    fun `seven day average leaves out a partial today and divides by tracked days`() {
        val rows = listOf(day(0, steps = 500)) + (1L..7L).map { day(it, steps = 8000) }.take(3)
        val avg = Ledger.average(rows, today, 7, includeToday = false) { it.steps.toDouble() }!!
        assertEquals(8000.0, avg.value, 1e-9)
        assertEquals(3, avg.tracked)
        assertEquals(7, avg.of)
        val withToday = Ledger.average(rows, today, 7, includeToday = true) { it.steps.toDouble() }!!
        assertEquals(4, withToday.tracked)
        assertNull(Ledger.average(emptyList(), today, 7, false) { it.steps.toDouble() })
    }

    @Test
    fun `window ends on today and rolls over with it`() {
        assertEquals(today, Ledger.window(today, 14).last())
        assertEquals(today.minusDays(13), Ledger.window(today, 14).first())
        val tomorrow = today.plusDays(1)
        assertEquals(tomorrow, Ledger.window(tomorrow, 14).last())
    }

    // ---- height, BMI, FFMI

    @Test
    fun `profile height is used for readings logged before it was set`() {
        val early = stat(today, 80.0, height = 0.0)
        assertNull(Ledger.bmiOf(early, null))
        assertEquals(24.7, Ledger.bmiOf(early, 180.0)!!, 1e-9)
        assertEquals("the profile height wins over a stale row height", 24.7, Ledger.bmiOf(stat(today, 80.0, 170.0), 180.0)!!, 1e-9)
        assertEquals("row height is the fallback", 27.7, Ledger.bmiOf(stat(today, 80.0, 170.0), null)!!, 1e-9)
    }

    @Test
    fun `ffmi comes from the newest reading that has body fat and says when`() {
        val old = stat(today.minusDays(20), 80.0, bf = 15.0)
        val newer = stat(today.minusDays(1), 79.0)
        val reading = Ledger.latestFfmi(listOf(newer, old), 180.0)!!
        assertEquals(ms(today.minusDays(20)), reading.takenAtMs)
        assertNull(Ledger.latestFfmi(listOf(newer), 180.0))
        assertNull("no height at all", Ledger.latestFfmi(listOf(old), null))
    }

    @Test
    fun `ffmi is normalised to 1_8 metres`() {
        // 80 kg, 15% bf, 180 cm: lean 68 / 3.24 = 21.0, and 1.8 m is the pivot.
        assertEquals(21.0, BodyStats.ffmiNormalised(80.0, 180.0, 15.0)!!, 1e-9)
        // 100 kg, 15% at 195 cm: raw 22.4, normalised 22.4 + 6.1 * -0.15 = 21.5
        assertEquals(22.4, BodyStats.ffmi(100.0, 195.0, 15.0)!!, 1e-9)
        assertEquals(21.5, BodyStats.ffmiNormalised(100.0, 195.0, 15.0)!!, 1e-9)
        // a short lifter is lifted, not left low
        assertTrue(BodyStats.ffmiNormalised(60.0, 160.0, 15.0)!! > BodyStats.ffmi(60.0, 160.0, 15.0)!!)
    }

    // ---- weight plot

    @Test
    fun `weight plot places readings by date inside the window`() {
        val stats = listOf(
            stat(today.minusDays(100), 90.0),
            stat(today.minusDays(29), 82.0),
            stat(today.minusDays(15), 81.0),
            stat(today, 80.0),
        )
        val plot = Ledger.weightPlot(stats, LedgerRange.D30, today, zone)!!
        assertEquals(listOf(82.0, 81.0, 80.0), plot.values)
        assertEquals(today.minusDays(29), plot.startDate)
        assertEquals(0.0, plot.positions.first(), 1e-9)
        assertEquals(1.0, plot.positions.last(), 1e-9)
        assertEquals(-2.0, plot.deltaKg!!, 1e-9)
        val all = Ledger.weightPlot(stats, LedgerRange.ALL, today, zone)!!
        assertEquals(4, all.values.size)
        assertEquals(-10.0, all.deltaKg!!, 1e-9)
    }

    @Test
    fun `weight delta needs two readings in the window`() {
        val plot = Ledger.weightPlot(listOf(stat(today.minusDays(60), 85.0), stat(today, 80.0)), LedgerRange.D30, today, zone)!!
        assertEquals(1, plot.values.size)
        assertNull(plot.deltaKg)
        assertNull(Ledger.weightPlot(emptyList(), LedgerRange.D30, today, zone))
    }

    // ---- text

    @Test
    fun `a delta carries one sign`() {
        assertEquals("+1.2 kg", Ledger.signed(1.2, "kg"))
        assertEquals("\u22120.4 kg", Ledger.signed(-0.4, "kg"))
        assertEquals("0.0 kg", Ledger.signed(0.04, "kg"))
        assertEquals("0.0 cm", Ledger.signed(-0.04, "cm"))
    }

    @Test
    fun `a decimal comma is a decimal point`() {
        assertEquals(79.5, Ledger.parseDecimal("79,5")!!, 1e-9)
        assertEquals(79.5, Ledger.parseDecimal(" 79.5 ")!!, 1e-9)
        assertNull(Ledger.parseDecimal("7,9,5"))
        assertNull(Ledger.parseDecimal(""))
        assertNull(Ledger.parseDecimal("NaN"))
    }

    // ---- energy

    @Test
    fun `burn is unknown without a row or a session and measured when synced`() {
        assertNull(Ledger.dayBurn(null, emptyList(), emptyMap(), 80.0, 180.0))
        val measured = Ledger.dayBurn(day(0, kcal = 450), emptyList(), emptyMap(), 80.0, 180.0)!!
        assertEquals(450, measured.kcal)
        assertEquals(EnergyConfidence.MEASURED, measured.confidence)
        val estimated = Ledger.dayBurn(day(0, steps = 8000), emptyList(), emptyMap(), 80.0, 180.0)!!
        assertEquals(EnergyConfidence.ESTIMATED, estimated.confidence)
        assertNotNull(estimated)
    }

    @Test
    fun `burn slots put today last and leave unsynced days null`() {
        val slots = Ledger.burnSlots(
            listOf(day(2, kcal = 300)), today, 4, emptyList(), emptyMap(), emptyMap(), 80.0, 180.0, zone,
        )
        assertEquals(listOf(null, 300, null, null), slots.map { it?.kcal })
    }

    // ---- training

    @Test
    fun `strength trend compares with five scored trials ago and skips unscored`() {
        fun s(i: Int, score: Int) = WorkoutSession(id = i.toLong(), label = "t$i", startedAtMs = i * 1000L, strengthScore = score)
        val sessions = listOf(s(1, 100), s(2, 0), s(3, 110), s(4, 120), s(5, 130), s(6, 140), s(7, 150), s(8, 160))
        val trend = Ledger.strengthTrend(sessions)!!
        assertEquals(160, trend.latest)
        assertEquals(5, trend.trialsBack)
        assertEquals(160 - 110, trend.delta)
        assertNull(Ledger.strengthTrend(listOf(s(1, 0))))
        assertNull(Ledger.strengthTrend(listOf(s(1, 90)))!!.delta)
    }

    @Test
    fun `weekly counts run Monday to Sunday and end with this week`() {
        // 2026-10-01 is a Thursday; this week started Monday 28 Sep.
        val sealed = setOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 27))
        val counts = Ledger.weeklyCounts(sealed, today, weeks = 3)
        assertEquals(listOf(0, 1, 2), counts)
    }

    @Test
    fun `weekly counts follow the week start they are given`() {
        // Sunday 27 Sep belongs to the week of Monday 21 Sep, but starts the next one in a Sunday-start locale.
        val sealed = setOf(LocalDate.of(2026, 9, 27))
        assertEquals(listOf(1, 0), Ledger.weeklyCounts(sealed, today, weeks = 2, weekStart = java.time.DayOfWeek.MONDAY))
        assertEquals(listOf(0, 1), Ledger.weeklyCounts(sealed, today, weeks = 2, weekStart = java.time.DayOfWeek.SUNDAY))
    }

    @Test
    fun `body fat text is valid when blank or a number in range, and invalid when it will not parse`() {
        assertTrue(Ledger.bodyFatTextValid(""))
        assertTrue(Ledger.bodyFatTextValid("  "))
        assertTrue(Ledger.bodyFatTextValid("18,5"))
        assertFalse(Ledger.bodyFatTextValid("."))
        assertFalse(Ledger.bodyFatTextValid("-5"))
        assertFalse(Ledger.bodyFatTextValid("1"))
        assertFalse(Ledger.bodyFatTextValid("80"))
    }

    @Test
    fun `daily summary is the seven day average, leaves out partial today and names only what arrived`() {
        val rows = listOf(day(0, steps = 100, kcal = 5, sleep = 480)) +
            (1L..2L).map { day(it, steps = 8000, kcal = 400, sleep = 420) }
        assertEquals("8.0k steps \u00B7 7h 20m \u00B7 400 kcal", Ledger.dailyAverageSummary(rows, today))
        assertEquals("7h 0m", Ledger.dailyAverageSummary(listOf(day(1, sleep = 420)), today))
        assertNull(Ledger.dailyAverageSummary(emptyList(), today))
        assertNull("only today's partial row", Ledger.dailyAverageSummary(listOf(day(0, steps = 10)), today))
    }

    @Test
    fun `readings are placed by the days between them`() {
        val d = LocalDate.of(2026, 9, 1)
        assertEquals(listOf(0.0, 0.1, 1.0), Ledger.datePositions(listOf(d, d.plusDays(3), d.plusDays(30))))
        assertEquals(listOf(0.5), Ledger.datePositions(listOf(d)))
        assertEquals(listOf(0.5, 0.5), Ledger.datePositions(listOf(d, d)))
        assertEquals(emptyList<Double>(), Ledger.datePositions(emptyList()))
    }
}
