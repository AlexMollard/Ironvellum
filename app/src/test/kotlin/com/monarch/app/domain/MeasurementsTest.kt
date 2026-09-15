package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Body measurements are tracked as measured values, and the trailing delta is
 * what the measurements screen shows. Both readings below are the kind of thing
 * a silent sign flip or an off-by-a-window would misreport forever.
 */
class MeasurementsTest {

    private val day = 24L * 60 * 60 * 1000

    private fun reading(site: MeasurementSite, daysAgo: Long, cm: Double) =
        MeasurementEntry(site = site, takenAtMs = NOW - daysAgo * day, valueCm = cm)

    @Test
    fun `a shrinking site reports a negative change`() {
        val waist = MeasurementSite.entries.first()
        val delta = Measurements.deltaCm(
            listOf(reading(waist, 20, 86.0), reading(waist, 0, 82.5)),
            waist,
        )
        assertEquals(-3.5, delta!!, 0.0001)
    }

    @Test
    fun `the change is anchored to the newest reading, not to today`() {
        // Someone who stopped measuring a year ago still sees the change they
        // made, because the window hangs off their last reading.
        val site = MeasurementSite.entries.first()
        val delta = Measurements.deltaCm(
            listOf(reading(site, 400, 100.0), reading(site, 380, 95.0)),
            site,
        )
        assertEquals(-5.0, delta!!, 0.0001)
    }

    @Test
    fun `readings older than the window are excluded from the comparison`() {
        val site = MeasurementSite.entries.first()
        val entries = listOf(
            reading(site, 200, 120.0), // ancient: must not become the baseline
            reading(site, 25, 90.0),
            reading(site, 0, 88.0),
        )
        assertEquals(-2.0, Measurements.deltaCm(entries, site, days = 30)!!, 0.0001)
        // Widen the window and the ancient reading legitimately becomes the baseline.
        assertEquals(-32.0, Measurements.deltaCm(entries, site, days = 365)!!, 0.0001)
    }

    @Test
    fun `nothing to compare against reads as no change rather than zero`() {
        val site = MeasurementSite.entries.first()
        assertNull("no readings", Measurements.deltaCm(emptyList(), site))
        assertNull("one reading is not a trend", Measurements.deltaCm(listOf(reading(site, 0, 90.0)), site))
        assertNull(
            "the only other reading predates the window",
            Measurements.deltaCm(listOf(reading(site, 90, 95.0), reading(site, 0, 90.0)), site, days = 30),
        )
    }

    @Test
    fun `sites do not contaminate each other`() {
        val sites = MeasurementSite.entries
        val a = sites.first()
        val b = sites.last()
        val entries = listOf(
            reading(a, 10, 90.0), reading(a, 0, 88.0),
            reading(b, 10, 40.0), reading(b, 0, 42.0),
        )
        assertEquals(-2.0, Measurements.deltaCm(entries, a)!!, 0.0001)
        assertEquals(2.0, Measurements.deltaCm(entries, b)!!, 0.0001)
    }

    @Test
    fun `latest keeps the newest reading per site regardless of input order`() {
        // The implementation sorts before associateBy because associateBy keeps
        // the LAST match: fed newest-first, an unsorted version returns the
        // oldest value and every site reads stale.
        val site = MeasurementSite.entries.first()
        val newestFirst = listOf(reading(site, 0, 88.0), reading(site, 30, 95.0))
        assertEquals(88.0, Measurements.latest(newestFirst)[site]!!.valueCm, 0.0001)
        assertEquals(88.0, Measurements.latest(newestFirst.reversed())[site]!!.valueCm, 0.0001)
    }

    @Test
    fun `history is newest first and only the asked-for site`() {
        val sites = MeasurementSite.entries
        val a = sites.first()
        val entries = listOf(
            reading(a, 5, 89.0),
            reading(sites.last(), 1, 40.0),
            reading(a, 0, 88.0),
            reading(a, 10, 90.0),
        )
        assertEquals(listOf(88.0, 89.0, 90.0), Measurements.history(entries, a).map { it.valueCm })
    }

    private companion object {
        /** Fixed instant so the window arithmetic cannot drift with the wall clock. */
        const val NOW = 1_800_000_000_000L
    }
}
