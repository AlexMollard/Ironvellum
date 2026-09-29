package com.ironvellum.app.ui.dashboard

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The step count carries the age of the Health Connect read behind it. */
class StepsAsOfCaptionTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 29)
    private fun at(date: LocalDate, hour: Int, minute: Int) =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a read today shows its time, an older one its date`() {
        assertEquals("as of 14:05", stepsAsOfCaption(at(today, 14, 5), today, zone))
        assertEquals("as of Sep 28", stepsAsOfCaption(at(today.minusDays(1), 23, 50), today, zone))
    }

    @Test
    fun `no read yet shows nothing`() {
        assertNull(stepsAsOfCaption(null, today, zone))
    }
}
