package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementsTest {

    private val day = 24L * 60 * 60 * 1000

    private fun waistGoal(target: Double, start: Double, achieved: Long? = null) = MeasurementGoal(
        site = MeasurementSite.WAIST,
        targetCm = target,
        setAtMs = 0,
        startCm = start,
        achievedAtMs = achieved,
    )

    @Test
    fun `shrinking goal progresses fractionally`() {
        val goal = waistGoal(target = 80.0, start = 85.0)
        val entries = listOf(
            MeasurementEntry(site = MeasurementSite.WAIST, takenAtMs = 1, valueCm = 85.0),
            MeasurementEntry(site = MeasurementSite.WAIST, takenAtMs = 2 * day, valueCm = 82.5),
        )

        val progress = Measurements.progress(goal, entries)

        assertEquals(82.5, progress.currentCm!!, 1e-9)
        assertEquals(0.5f, progress.fraction)
        assertEquals(2.5, progress.remainingCm, 1e-9)
        assertTrue(progress.shrinking)
        assertFalse(progress.achieved)
    }

    @Test
    fun `growing goal progresses fractionally`() {
        val goal = MeasurementGoal(
            site = MeasurementSite.UPPER_ARM, targetCm = 38.0, setAtMs = 0, startCm = 35.0,
        )
        val entries = listOf(
            MeasurementEntry(site = MeasurementSite.UPPER_ARM, takenAtMs = 1, valueCm = 35.0),
            MeasurementEntry(site = MeasurementSite.UPPER_ARM, takenAtMs = 2 * day, valueCm = 36.5),
        )

        val progress = Measurements.progress(goal, entries)

        assertEquals(0.5f, progress.fraction)
        assertEquals(1.5, progress.remainingCm, 1e-9)
        assertFalse(progress.shrinking)
        assertFalse(progress.achieved)
    }

    @Test
    fun `passing the target clamps to full and reports achieved`() {
        val goal = MeasurementGoal(
            site = MeasurementSite.UPPER_ARM, targetCm = 38.0, setAtMs = 0, startCm = 35.0,
        )
        val entries = listOf(
            MeasurementEntry(site = MeasurementSite.UPPER_ARM, takenAtMs = 2 * day, valueCm = 39.0),
        )

        val progress = Measurements.progress(goal, entries)

        assertEquals(1f, progress.fraction)
        assertTrue(progress.achieved)
    }

    @Test
    fun `moving away from the target clamps at zero and never achieves`() {
        // Arm goal but the arm shrank: raw fraction is negative and must clamp.
        val goal = MeasurementGoal(
            site = MeasurementSite.UPPER_ARM, targetCm = 38.0, setAtMs = 0, startCm = 35.0,
        )
        val entries = listOf(
            MeasurementEntry(site = MeasurementSite.UPPER_ARM, takenAtMs = 2 * day, valueCm = 34.0),
        )

        val progress = Measurements.progress(goal, entries)

        assertEquals(0f, progress.fraction)
        assertFalse(progress.achieved)
    }

    @Test
    fun `goal equal to baseline never divides by zero`() {
        val goal = waistGoal(target = 85.0, start = 85.0)
        val at = listOf(MeasurementEntry(site = MeasurementSite.WAIST, takenAtMs = 1, valueCm = 85.0))
        val past = listOf(MeasurementEntry(site = MeasurementSite.WAIST, takenAtMs = 1, valueCm = 84.0))

        val atProgress = Measurements.progress(goal, at)
        val pastProgress = Measurements.progress(goal, past)

        assertEquals(1f, atProgress.fraction)
        assertTrue(atProgress.achieved)
        assertEquals(0f, pastProgress.fraction)
        assertEquals(1.0, pastProgress.remainingCm, 1e-9)
    }

    @Test
    fun `no readings yet gives null current and zero fraction`() {
        val goal = MeasurementGoal(
            site = MeasurementSite.CHEST, targetCm = 110.0, setAtMs = 0, startCm = 110.0,
        )

        val progress = Measurements.progress(goal, emptyList())

        assertNull(progress.currentCm)
        assertEquals(0f, progress.fraction)
        assertFalse(progress.achieved)
    }

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
