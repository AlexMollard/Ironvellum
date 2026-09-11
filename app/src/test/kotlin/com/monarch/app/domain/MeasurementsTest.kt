package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementsTest {
    private val day = 24L * 60 * 60 * 1000

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
        assertEquals(2, latest.size)
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
}
