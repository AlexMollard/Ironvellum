package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Body measurements are tracked as measured values, and the trailing delta is
 * what the measurements screen reports back. A sign flip, an off-by-a-window,
 * or a stale newest-per-site lookup would all misreport silently and forever.
 */
class MeasurementsTest {

    private val day = 24L * 60 * 60 * 1000

    private fun reading(site: MeasurementSite, daysAgo: Long, cm: Double, id: Long = 0) =
        MeasurementEntry(id = id, site = site, takenAtMs = NOW - daysAgo * day, valueCm = cm)

    @Test
    fun `latest picks the newest reading per site not the first`() {
        val entries = listOf(
            MeasurementEntry(id = 1, site = MeasurementSite.WAIST, takenAtMs = 1, valueCm = 85.0),
            MeasurementEntry(id = 2, site = MeasurementSite.WAIST, takenAtMs = 2 * day, valueCm = 82.5),
            MeasurementEntry(id = 3, site = MeasurementSite.NECK, takenAtMs = 3, valueCm = 38.0),
        )

        val latest = Measurements.latest(entries)

        assertEquals(82.5, latest.getValue(MeasurementSite.WAIST).valueCm, 1e-9)
        assertEquals(38.0, latest.getValue(MeasurementSite.NECK).valueCm, 1e-9)
        // Only sites with readings appear: an entry per known site would show
        // the measurements screen rows the lifter never recorded.
        assertEquals(2, latest.size)
    }

    @Test
    fun `latest is independent of input order`() {
        // The implementation sorts before associateBy because associateBy keeps
        // the LAST match: fed newest-first, an unsorted version returns the
        // oldest value and every site reads permanently stale.
        val newestFirst = listOf(
            reading(MeasurementSite.WAIST, 0, 88.0),
            reading(MeasurementSite.WAIST, 30, 95.0),
        )
        assertEquals(88.0, Measurements.latest(newestFirst).getValue(MeasurementSite.WAIST).valueCm, 1e-9)
        assertEquals(88.0, Measurements.latest(newestFirst.reversed()).getValue(MeasurementSite.WAIST).valueCm, 1e-9)
    }

    @Test
    fun `history is newest first and filtered to the site`() {
        val entries = listOf(
            MeasurementEntry(id = 1, site = MeasurementSite.WAIST, takenAtMs = 1, valueCm = 85.0),
            MeasurementEntry(id = 2, site = MeasurementSite.NECK, takenAtMs = 2, valueCm = 38.0),
            MeasurementEntry(id = 3, site = MeasurementSite.WAIST, takenAtMs = 3, valueCm = 82.5),
        )

        val history = Measurements.history(entries, MeasurementSite.WAIST)

        assertEquals(listOf(3L, 1L), history.map { it.id })
    }

    @Test
    fun `a shrinking site reports a negative change`() {
        val delta = Measurements.deltaCm(
            listOf(
                reading(MeasurementSite.WAIST, 20, 86.0),
                reading(MeasurementSite.WAIST, 0, 82.5),
            ),
            MeasurementSite.WAIST,
        )
        assertEquals(-3.5, delta!!, 1e-9)
    }

    @Test
    fun `the change is anchored to the newest reading not to today`() {
        // Someone who stopped measuring a year ago still sees the change they
        // made, because the window hangs off their last reading.
        val delta = Measurements.deltaCm(
            listOf(
                reading(MeasurementSite.WAIST, 400, 100.0),
                reading(MeasurementSite.WAIST, 380, 95.0),
            ),
            MeasurementSite.WAIST,
        )
        assertEquals(-5.0, delta!!, 1e-9)
    }

    @Test
    fun `deltaCm ignores readings outside the window`() {
        val site = MeasurementSite.WAIST
        val entries = listOf(
            MeasurementEntry(site = site, takenAtMs = 1, valueCm = 90.0),
            MeasurementEntry(site = site, takenAtMs = 60 * day, valueCm = 85.0),
            MeasurementEntry(site = site, takenAtMs = 89 * day, valueCm = 84.0),
            MeasurementEntry(site = site, takenAtMs = 90 * day, valueCm = 82.5),
        )

        // Anchored at the newest reading (day 90): the 90.0 reading at day 1 is out.
        assertEquals(-2.5, Measurements.deltaCm(entries, site, days = 30)!!, 1e-9)
        // Widen the window and that ancient reading legitimately becomes the baseline.
        assertEquals(-7.5, Measurements.deltaCm(entries, site, days = 365)!!, 1e-9)
    }

    @Test
    fun `deltaCm is null with fewer than two comparable points`() {
        val site = MeasurementSite.NECK
        assertNull(Measurements.deltaCm(emptyList(), site))
        assertNull(
            Measurements.deltaCm(
                listOf(MeasurementEntry(site = site, takenAtMs = 1, valueCm = 38.0)),
                site,
            ),
        )
        // Second reading is outside every window anchored at the newest one.
        assertNull(
            Measurements.deltaCm(
                listOf(
                    MeasurementEntry(site = site, takenAtMs = 1, valueCm = 38.0),
                    MeasurementEntry(site = site, takenAtMs = 100 * day, valueCm = 38.5),
                ),
                site,
                days = 30,
            ),
        )
    }

    @Test
    fun `sites do not contaminate each other`() {
        val entries = listOf(
            reading(MeasurementSite.WAIST, 10, 90.0), reading(MeasurementSite.WAIST, 0, 88.0),
            reading(MeasurementSite.NECK, 10, 40.0), reading(MeasurementSite.NECK, 0, 42.0),
        )
        assertEquals(-2.0, Measurements.deltaCm(entries, MeasurementSite.WAIST)!!, 1e-9)
        assertEquals(2.0, Measurements.deltaCm(entries, MeasurementSite.NECK)!!, 1e-9)
    }

    private companion object {
        /** Fixed instant so the window arithmetic cannot drift with the wall clock. */
        const val NOW = 1_800_000_000_000L
    }
}
