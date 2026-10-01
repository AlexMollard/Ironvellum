package com.ironvellum.app.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeighInTest {
    private val zone = ZoneId.of("Australia/Sydney")
    private fun ms(h: Int, d: Int = 10) = ZonedDateTime.of(2026, 9, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `a second weigh-in on the same day updates rather than inserts`() {
        assertTrue(WeighIn.sameDay(ms(7), ms(7), zone))
        assertTrue(WeighIn.sameDay(ms(0), ms(23), zone))
    }

    @Test
    fun `no earlier reading, or an earlier day, inserts`() {
        assertFalse(WeighIn.sameDay(null, ms(7), zone))
        assertFalse(WeighIn.sameDay(ms(23, d = 9), ms(0, d = 10), zone))
    }
}