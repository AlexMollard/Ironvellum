package com.ironvellum.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepMathTest {
    private val zone = ZoneId.of("UTC")

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDate.of(2026, 9, day).atTime(hour, minute).atZone(zone).toInstant()

    @Test
    fun `awake stages are not sleep`() {
        val night = SleepSpan(
            start = at(29, 23),
            end = at(30, 7),
            awake = listOf(at(30, 2) to at(30, 2, 30), at(30, 4) to at(30, 4, 20)),
        )
        // 8 h in bed minus 50 min awake.
        assertEquals(480 - 50, SleepMath.asleepMinutes(night))
    }

    @Test
    fun `an awake span is clipped to its session and never makes the night negative`() {
        val span = SleepSpan(at(30, 1), at(30, 2), awake = listOf(at(29, 20) to at(30, 5)))
        assertEquals(0, SleepMath.asleepMinutes(span))
    }

    @Test
    fun `a night is dated by the day you woke up`() {
        val night = SleepSpan(at(29, 23), at(30, 7))
        val byDay = SleepMath.nightMinutesByWakeDay(listOf(night), zone)
        assertEquals(mapOf(LocalDate.of(2026, 9, 30) to 480), byDay)
    }

    @Test
    fun `an afternoon nap is left out of the night`() {
        val night = SleepSpan(at(29, 23), at(30, 6, 30))
        val nap = SleepSpan(at(30, 14), at(30, 14, 40))
        assertEquals(450, SleepMath.nightMinutesByWakeDay(listOf(night, nap), zone)[LocalDate.of(2026, 9, 30)])
    }

    @Test
    fun `a split night counts both halves`() {
        val first = SleepSpan(at(29, 23), at(30, 3))
        val second = SleepSpan(at(30, 4), at(30, 7))
        assertEquals(240 + 180, SleepMath.nightMinutesByWakeDay(listOf(first, second), zone)[LocalDate.of(2026, 9, 30)])
    }

    @Test
    fun `a lone afternoon sleep is still a sleep`() {
        val shift = SleepSpan(at(30, 9), at(30, 15))
        assertEquals(360, SleepMath.nightMinutesByWakeDay(listOf(shift), zone)[LocalDate.of(2026, 9, 30)])
    }

    @Test
    fun `a split night counts the evening half even when it is the shorter one`() {
        val evening = SleepSpan(at(29, 23), at(30, 1))
        val morning = SleepSpan(at(30, 2), at(30, 7))
        assertEquals(120 + 300, SleepMath.nightMinutesByWakeDay(listOf(morning, evening), zone)[LocalDate.of(2026, 9, 30)])
    }

    @Test
    fun `an evening half that ends before midnight joins its night rather than dating one of its own`() {
        val evening = SleepSpan(at(29, 21), at(29, 23, 30))
        val morning = SleepSpan(at(30, 0, 30), at(30, 7))
        val byDay = SleepMath.nightMinutesByWakeDay(listOf(evening, morning), zone)
        assertEquals(mapOf(LocalDate.of(2026, 9, 30) to 150 + 390), byDay)
    }

    @Test
    fun `halves further apart than the gap are separate sleeps`() {
        val night = SleepSpan(at(29, 23), at(30, 6))
        val evening = SleepSpan(at(30, 18), at(30, 19))
        val byDay = SleepMath.nightMinutesByWakeDay(listOf(night, evening), zone)
        assertEquals(420, byDay[LocalDate.of(2026, 9, 30)])
    }
}
