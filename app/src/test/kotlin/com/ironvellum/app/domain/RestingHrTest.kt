package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RestingHrTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 20)

    private fun window(date: LocalDate, halfHour: Int, bpm: Double, samples: Long = 6) =
        RestingHr.Window(date.atStartOfDay(zone).toInstant().plusSeconds(halfHour * 1800L), bpm, samples)

    private fun derive(vararg w: RestingHr.Window) = RestingHr.derive(w.toList(), zone)

    @Test
    fun `samples are averaged into half-hour windows`() {
        val t = Instant.parse("2026-10-20T02:00:00Z")
        val ws = RestingHr.windows(
            listOf(
                RestingHr.Sample(t, 50), RestingHr.Sample(t.plusSeconds(600), 54), RestingHr.Sample(t.plusSeconds(1200), 55),
                RestingHr.Sample(t.plusSeconds(1800), 70),
            ),
        ).sortedBy { it.start }
        assertEquals(listOf(t, t.plusSeconds(1800)), ws.map { it.start })
        assertEquals(listOf(3L, 1L), ws.map { it.samples })
        assertEquals(53.0, ws[0].avgBpm, 0.01)
    }

    @Test
    fun `no windows gives no days`() {
        assertEquals(emptyMap<LocalDate, Int>(), derive())
    }

    @Test
    fun `the lowest window average is the resting rate`() {
        assertEquals(
            mapOf(day to 54),
            derive(window(day, 2, 55.0), window(day, 3, 53.6), window(day, 20, 72.0), window(day, 30, 90.0)),
        )
    }

    @Test
    fun `sparse wear gives null`() {
        assertEquals(emptyMap<LocalDate, Int>(), derive(window(day, 2, 55.0), window(day, 3, 56.0)))
    }

    @Test
    fun `a window with too few samples does not count or set the minimum`() {
        // The 40 bpm window has one sample (a blip), so only three real windows remain and the minimum is 58.
        assertEquals(
            mapOf(day to 58),
            derive(window(day, 2, 58.0), window(day, 3, 60.0), window(day, 4, 61.0), window(day, 5, 40.0, samples = 1)),
        )
    }

    @Test
    fun `a day recorded only during a workout is not a resting day`() {
        assertEquals(
            emptyMap<LocalDate, Int>(),
            derive(window(day, 30, 128.0), window(day, 31, 141.0), window(day, 32, 150.0)),
        )
    }

    @Test
    fun `a workout on a day with quiet windows does not raise the figure`() {
        assertEquals(
            mapOf(day to 57),
            derive(window(day, 4, 57.0), window(day, 5, 58.0), window(day, 6, 59.0), window(day, 30, 150.0)),
        )
    }

    @Test
    fun `an implausibly low average is dropped`() {
        assertEquals(
            emptyMap<LocalDate, Int>(),
            derive(window(day, 1, 22.0), window(day, 2, 24.0), window(day, 3, 25.0)),
        )
    }

    @Test
    fun `windows are grouped by local day`() {
        val plus10 = ZoneOffset.ofHours(10)
        // 15:30 UTC is 01:30 the next day at +10.
        val late = RestingHr.Window(Instant.parse("2026-10-20T15:30:00Z"), 50.0, 6)
        val result = RestingHr.derive(listOf(late, late, late), plus10)
        assertEquals(mapOf(LocalDate.of(2026, 10, 21) to 50), result)
    }

    @Test
    fun `a recorded resting rate wins over a derived one`() {
        val other = day.plusDays(1)
        assertEquals(
            mapOf(day to 61, other to 55),
            RestingHr.merge(recorded = mapOf(day to 61), derived = mapOf(day to 52, other to 55)),
        )
    }
}
