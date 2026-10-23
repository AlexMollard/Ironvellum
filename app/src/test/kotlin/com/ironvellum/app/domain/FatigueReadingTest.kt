package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueReadingTest {

    private val H = 3_600_000L
    private val UTC = ZoneId.of("UTC")
    private val NOW = 1_000L * 24 * H
    private val SLOW = 24.0

    private fun at(zone: String, y: Int, mo: Int, d: Int, h: Int, min: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, min, 0, 0, ZoneId.of(zone)).toInstant().toEpochMilli()

    private fun contribution(
        muscle: Muscle = Muscle.QUADS,
        dose: Double,
        ageMs: Long = 0,
        qualifying: Double = 0.0,
        novel: Boolean = false,
        stretched: Boolean = false,
        halfLife: Double = SLOW,
        name: String = "Back Squat",
        id: Long = 1,
        now: Long = NOW,
    ) = FatigueContribution(
        muscle, id, now - ageMs, name, "", name.lowercase(), dose, qualifying, stretched, novel, halfLife,
    )

    private fun report(vararg contributions: FatigueContribution, now: Long = NOW) =
        FatigueReport(now, false, Muscle.entries.associateWith { m -> contributions.filter { it.muscle == m } })

    /** A QUADS report whose residual reaches the Light line [hours] hours after [now]. */
    private fun lightIn(hours: Double, now: Long = NOW) =
        report(contribution(dose = 2.1 * Math.pow(2.0, hours / SLOW), now = now), now = now)

    // ---------------------------------------------------------------- levels

    @Test
    fun `level boundaries`() {
        assertEquals(FatigueLevel.FRESH, FatigueLevel.of(0.0))
        assertEquals(FatigueLevel.FRESH, FatigueLevel.of(0.89))
        assertEquals(FatigueLevel.LIGHT, FatigueLevel.of(0.90))
        assertEquals(FatigueLevel.LIGHT, FatigueLevel.of(2.09))
        assertEquals(FatigueLevel.MODERATE, FatigueLevel.of(2.10))
        assertEquals(FatigueLevel.HIGH, FatigueLevel.of(3.6))
        assertEquals(FatigueLevel.VERY_HIGH, FatigueLevel.of(5.1))
        assertEquals(FatigueLevel.VERY_HIGH, FatigueLevel.of(9.0))
    }

    // ---------------------------------------------------------------- shares

    @Test
    fun `shares round to five, top three, and other`() {
        val four = report(
            contribution(dose = 62.0, name = "A", id = 1), contribution(dose = 25.0, name = "B", id = 2),
            contribution(dose = 8.0, name = "C", id = 3), contribution(dose = 5.0, name = "D", id = 4),
        ).reading(Muscle.QUADS, UTC)
        assertEquals(listOf(60, 25, 10), four.causes.map { it.percent })
        assertEquals(listOf("A", "B", "C"), four.causes.map { it.exerciseName })
        assertEquals(5, four.otherPercent)

        val two = report(contribution(dose = 7.0, name = "A", id = 1), contribution(dose = 3.0, name = "B", id = 2)).reading(Muscle.QUADS, UTC)
        assertEquals(listOf(70, 30), two.causes.map { it.percent })
        assertEquals(0, two.otherPercent)

        val one = report(contribution(dose = 3.0)).reading(Muscle.QUADS, UTC)
        assertEquals(listOf(100), one.causes.map { it.percent })
        assertEquals(0, one.otherPercent)
    }

    @Test
    fun `shares use current residual not dose`() {
        val older = contribution(dose = 10.0, ageMs = 48 * H, name = "Old", id = 1)
        val newer = contribution(dose = 4.0, name = "New", id = 2)
        val reading = report(older, newer).reading(Muscle.QUADS, UTC)
        assertEquals(listOf("New", "Old"), reading.causes.map { it.exerciseName })
        assertEquals(listOf(60, 40), reading.causes.map { it.percent })
    }

    @Test
    fun `no causes when fresh`() {
        val reading = FatigueModel.estimate(emptyList(), NOW, null).reading(Muscle.QUADS, UTC)
        assertTrue(reading.causes.isEmpty())
        assertEquals(0, reading.otherPercent)
        assertEquals(LightBy.None, reading.lightBy)
        assertEquals(LightBy.None, reading.freshBy)
        assertNull(reading.soreness)
        assertEquals(FatigueLevel.FRESH, reading.level)
    }

    // ---------------------------------------------------------------- light by

    @Test
    fun `light by is the solved crossing`() {
        val one = report(contribution(dose = 6.0))
        val expected = NOW + (24.0 * Math.log(6.0 / 2.1) / Math.log(2.0) * H).toLong()
        assertEquals(expected.toDouble(), one.firstBelowMs(Muscle.QUADS, FatigueTuning.LIGHT_BELOW)!!.toDouble(), 60_000.0)

        val half = contribution(dose = 3.0, id = 1)
        val alone = report(half).firstBelowMs(Muscle.QUADS, FatigueTuning.LIGHT_BELOW)!!
        val both = report(half, contribution(dose = 3.0, id = 2)).firstBelowMs(Muscle.QUADS, FatigueTuning.LIGHT_BELOW)!!
        assertTrue("two sources cross later than either alone", both > alone)
    }

    @Test
    fun `first below is null when already below or still above after a week`() {
        assertNull(report(contribution(dose = 1.0)).firstBelowMs(Muscle.QUADS, FatigueTuning.LIGHT_BELOW))
        assertNull(report(contribution(dose = 400.0)).firstBelowMs(Muscle.QUADS, FatigueTuning.LIGHT_BELOW))
    }

    @Test
    fun `light by uses the local calendar day`() {
        val now = at("UTC", 2026, 3, 4, 10) // a Wednesday
        assertEquals(LightBy.Today, lightIn(3.0, now).reading(Muscle.QUADS, UTC).lightBy)
        assertEquals(LightBy.Tomorrow, lightIn(20.0, now).reading(Muscle.QUADS, UTC).lightBy)
        assertEquals(LightBy.Weekday(DayOfWeek.FRIDAY), lightIn(60.0, now).reading(Muscle.QUADS, UTC).lightBy)
        assertEquals(LightBy.OverAWeek, lightIn(9 * 24.0, now).reading(Muscle.QUADS, UTC).lightBy)
        assertEquals("exactly seven dates ahead is a week away", LightBy.OverAWeek, lightIn(6 * 24.0 + 20.0, now).reading(Muscle.QUADS, UTC).lightBy)
    }

    @Test
    fun `light by at midnight`() {
        val now = at("UTC", 2026, 3, 4, 22)
        assertEquals(LightBy.Today, lightIn(1 + 59 / 60.0, now).reading(Muscle.QUADS, UTC).lightBy)
        assertEquals(LightBy.Tomorrow, lightIn(2 + 1 / 60.0, now).reading(Muscle.QUADS, UTC).lightBy)
    }

    @Test
    fun `light by in a different zone changes the day`() {
        val now = at("UTC", 2026, 6, 10, 12)
        val report = lightIn(20.0, now)
        assertEquals(LightBy.Today, report.reading(Muscle.QUADS, ZoneId.of("Pacific/Auckland")).lightBy)
        assertEquals(LightBy.Tomorrow, report.reading(Muscle.QUADS, ZoneId.of("America/Los_Angeles")).lightBy)
    }

    @Test
    fun `light by across a clock change`() {
        val zone = ZoneId.of("America/New_York")
        val now = at("America/New_York", 2026, 3, 7, 20)
        assertEquals(LightBy.Tomorrow, lightIn(12.0, now).reading(Muscle.QUADS, zone).lightBy)
    }

    @Test
    fun `already light gives no light by line`() {
        val reading = report(contribution(dose = 1.5)).reading(Muscle.QUADS, UTC)
        assertEquals(FatigueLevel.LIGHT, reading.level)
        assertEquals(LightBy.None, reading.lightBy)
        assertNotNull(reading.freshBy)
        assertTrue(reading.freshBy != LightBy.None)
    }

    @Test
    fun `fresh by is later than light by`() {
        val reading = lightIn(10.0).reading(Muscle.QUADS, UTC)
        val light = lightIn(10.0).firstBelowMs(Muscle.QUADS, FatigueTuning.LIGHT_BELOW)!!
        val fresh = lightIn(10.0).firstBelowMs(Muscle.QUADS, FatigueTuning.FRESH_BELOW)!!
        assertTrue(fresh > light)
        assertTrue(reading.freshBy != LightBy.None)
    }

    @Test
    fun `level at a later time follows the decay`() {
        val r = report(contribution(dose = 6.0))
        assertEquals(FatigueLevel.VERY_HIGH, r.levelAt(Muscle.QUADS, NOW))
        assertEquals(FatigueLevel.FRESH, r.levelAt(Muscle.QUADS, NOW + 7 * 24 * H))
    }

    // ---------------------------------------------------------------- soreness

    private fun sore(vararg c: FatigueContribution) = report(*c).reading(Muscle.QUADS, UTC).soreness

    @Test
    fun `soreness rule`() {
        fun one(ageMs: Long, qualifying: Double = 1.5, dose: Double = 5.0) =
            sore(contribution(dose = dose, ageMs = ageMs, qualifying = qualifying, novel = true))
        assertNotNull(one(13 * H))
        assertNull("11.9 h", one((11.9 * H).toLong()))
        assertNotNull("12.0 h", one(12 * H))
        assertNotNull("72.0 h", one(72 * H))
        assertNull("72.1 h", one((72.1 * H).toLong()))
        assertNull("1.4 dose", one(24 * H, qualifying = 1.4))
        assertNull("familiar plain sets do not qualify", one(24 * H, qualifying = 0.0, dose = 5.0))
        assertNull("a live workout never qualifies", one(0))
    }

    @Test
    fun `soreness counts only sessions inside the window`() {
        val tooSoon = contribution(dose = 3.0, ageMs = 6 * H, qualifying = 1.0, novel = true, id = 1)
        val inside = contribution(dose = 3.0, ageMs = 30 * H, qualifying = 1.0, novel = true, id = 2)
        assertNull(sore(tooSoon, inside))
        val insideToo = contribution(dose = 3.0, ageMs = 50 * H, qualifying = 1.0, novel = true, id = 3)
        assertNotNull(sore(tooSoon, inside, insideToo))
    }

    @Test
    fun `soreness names the cause`() {
        val set = LoggedSet("Romanian Deadlift", "", 100.0, 5, 9, SetKind.WORKING, true, 0L)
        val session = FatigueSession(1, NOW - 24 * H, listOf(set, set))
        val report = FatigueModel.estimate(listOf(session), NOW, NOW - 400L * 24 * H)
        val note = report.reading(Muscle.HAMSTRINGS, UTC).soreness!!
        assertEquals(SorenessReason.NEW_AND_STRETCH, note.reason)
        assertEquals(Muscle.HAMSTRINGS, note.muscle)
        assertEquals("Romanian Deadlift", note.exerciseName)
    }

    @Test
    fun `soreness reason follows the flags`() {
        fun reason(novel: Boolean, stretched: Boolean) =
            sore(contribution(dose = 3.0, ageMs = 24 * H, qualifying = 2.0, novel = novel, stretched = stretched))!!.reason
        assertEquals(SorenessReason.NEW, reason(novel = true, stretched = false))
        assertEquals(SorenessReason.STRETCH, reason(novel = false, stretched = true))
        assertEquals(SorenessReason.NEW_AND_STRETCH, reason(novel = true, stretched = true))
    }

    // ---------------------------------------------------------------- most worked

    @Test
    fun `most worked orders and limits`() {
        val r = report(
            contribution(Muscle.QUADS, dose = 3.0), contribution(Muscle.GLUTES, dose = 5.0),
            contribution(Muscle.LATS, dose = 1.0), contribution(Muscle.BICEPS, dose = 2.0),
            contribution(Muscle.CALVES, dose = 4.0), contribution(Muscle.ABS, dose = 0.5),
            contribution(Muscle.TRICEPS, dose = 2.0),
        )
        val worked = r.mostWorked(UTC)
        assertEquals(listOf(Muscle.GLUTES, Muscle.CALVES, Muscle.QUADS, Muscle.BICEPS), worked.map { it.muscle })
        assertEquals(
            "ties go by figure order",
            listOf(Muscle.GLUTES, Muscle.CALVES, Muscle.QUADS, Muscle.BICEPS, Muscle.TRICEPS, Muscle.LATS),
            r.mostWorked(UTC, 10).map { it.muscle },
        )
        assertTrue(r.mostWorked(UTC, 10).none { it.level == FatigueLevel.FRESH })
        assertTrue(FatigueModel.estimate(emptyList(), NOW, null).mostWorked(UTC).isEmpty())
    }
}
