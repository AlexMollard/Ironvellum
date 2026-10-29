package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LifterSummaryTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private fun at(daysAgo: Double, xp: Int = 100, strength: Int = 500) = LifterSummary.Entry(now - (daysAgo * day).toLong(), xp, strength)

    @Test fun emptyListHasNothingToSay() {
        val f = LifterSummary.of(emptyList(), now)
        assertEquals(0, f.count); assertEquals(0L, f.xp); assertEquals(0.0, f.perWeek, 0.0)
        assertNull(f.daysSince); assertNull(f.peak); assertEquals(emptyList<Double>(), f.strengthSeries)
    }

    @Test fun xpIsTheSumOverEverySharedWorkout() {
        assertEquals(600L, LifterSummary.of(listOf(at(1.0, xp = 100), at(2.0, xp = 200), at(40.0, xp = 300)), now).xp)
    }

    @Test fun perWeekCountsTheLastFourWeeksOverFour() {
        val f = LifterSummary.of(listOf(at(1.0), at(7.0), at(27.9), at(28.0), at(29.0), at(60.0)), now)
        assertEquals(1.0, f.perWeek, 0.0)
        assertEquals(0.5, LifterSummary.of(listOf(at(3.0), at(10.0)), now).perWeek, 0.0)
    }

    @Test fun lastWorkoutIsWholeDaysSinceTheNewest() {
        assertEquals(0, LifterSummary.of(listOf(at(0.5), at(9.0)), now).daysSince)
        assertEquals(1, LifterSummary.of(listOf(at(1.2), at(9.0)), now).daysSince)
        assertEquals(9, LifterSummary.of(listOf(at(9.9), at(30.0)), now).daysSince)
    }

    @Test fun strengthSeriesIsOldestFirstWithItsPeak() {
        val f = LifterSummary.of(listOf(at(1.0, strength = 900), at(20.0, strength = 700), at(10.0, strength = 950)), now)
        assertEquals(listOf(700.0, 950.0, 900.0), f.strengthSeries)
        assertEquals(950, f.peak)
    }

    @Test fun aWorkoutWithoutATimeSortsLastAndDoesNotCountForRecency() {
        val f = LifterSummary.of(listOf(LifterSummary.Entry(null, 10, 800), at(5.0, strength = 600)), now)
        assertEquals(listOf(600.0, 800.0), f.strengthSeries); assertEquals(5, f.daysSince)
    }
}
