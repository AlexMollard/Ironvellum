package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class DecimalInputTest {

    private fun clean(raw: String, decimals: Int = 2, length: Int = 8) =
        DecimalInput.sanitize(raw, decimals, length)

    @Test
    fun `a typed comma becomes the point`() {
        assertEquals("82.5", clean("82,5"))
        assertEquals("82.5", clean("82.5"))
    }

    @Test
    fun `only one separator survives and a second ends the entry`() {
        assertEquals("1.", clean("1..2"))
        assertEquals("1.2", clean("1.2.3"))
        assertEquals("1.2", clean("1,2,3"))
    }

    @Test
    fun `a lone or leading separator gains its zero`() {
        assertEquals("0.", clean("."))
        assertEquals("0.5", clean(".5"))
        assertEquals("0.", clean(","))
    }

    @Test
    fun `leading zeros collapse but a single zero and 0 point stay`() {
        assertEquals("7", clean("007"))
        assertEquals("0", clean("000"))
        assertEquals("0.5", clean("00.5"))
        assertEquals("100", clean("100"))
    }

    @Test
    fun `places and length are capped`() {
        assertEquals("82.55", clean("82.555"))
        assertEquals("82.5", clean("82.55", decimals = 1))
        assertEquals("1234", clean("1234.5", decimals = 0))
        assertEquals("12345.6", clean("12345.678", length = 7))
    }

    @Test
    fun `non digits are dropped, Arabic-Indic digits included`() {
        assertEquals("8.25", clean(" 8a.2-5 "))
        assertEquals("", clean("١٢"))
        assertEquals("", clean("abc"))
    }

    @Test
    fun `whole numbers keep digits only`() {
        assertEquals("125", DecimalInput.sanitizeWhole("12.5", 3))
        assertEquals("12", DecimalInput.sanitizeWhole("1,2", 3))
        assertEquals("8", DecimalInput.sanitizeWhole("08", 3))
        assertEquals("0", DecimalInput.sanitizeWhole("0", 3))
        assertEquals("0", DecimalInput.sanitizeWhole("000", 3))
        assertEquals("123", DecimalInput.sanitizeWhole("1234", 3))
        assertEquals("", DecimalInput.sanitizeWhole("-,.", 3))
    }

    @Test
    fun `parse reads point and comma the same`() {
        assertEquals(82.5, DecimalInput.parse("82,5")!!, 1e-9)
        assertEquals(82.5, DecimalInput.parse("82.5")!!, 1e-9)
        assertEquals(82.5, DecimalInput.parse(" 82.5 ")!!, 1e-9)
        assertEquals(5.0, DecimalInput.parse("5.")!!, 1e-9)
        assertEquals(0.5, DecimalInput.parse(".5")!!, 1e-9)
        assertEquals(7.0, DecimalInput.parse("007")!!, 1e-9)
    }

    @Test
    fun `parse refuses anything that is not one plain decimal`() {
        listOf("", " ", ".", ",", "1..2", "1.2.3", "1,234.5", "-5", "+5", "1e3", "NaN", "Infinity", "0x10", "1 2", "abc")
            .forEach { assertNull("accepted \"$it\"", DecimalInput.parse(it)) }
        assertNull(DecimalInput.parse("9".repeat(400)))
    }

    @Test
    fun `parseWhole reads digits only`() {
        assertEquals(12, DecimalInput.parseWhole("12"))
        assertEquals(12, DecimalInput.parseWhole(" 012 "))
        listOf("", "1.5", "1,5", "-1", "abc", "1234567890").forEach {
            assertNull("accepted \"$it\"", DecimalInput.parseWhole(it))
        }
    }

    @Test
    fun `parsing and formatting ignore the device locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(1.5, DecimalInput.parse("1,5")!!, 1e-9)
            assertEquals(1.5, DecimalInput.parse("1.5")!!, 1e-9)
            assertEquals("82.5", "%.1f".fmt(82.5))
            assertEquals("1,234", "%,d".fmt(1234))
            assertEquals("2026-W05", "%04d-W%02d".fmt(2026, 5))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `reset codes keep leading zeros and drop non digits`() {
        assertEquals("012345", DecimalInput.sanitizeCode("012345", 10))
        assertEquals("000000", DecimalInput.sanitizeCode("000 000", 10))
        assertEquals("0123", DecimalInput.sanitizeCode("01-2.3x4567", 4))
        assertEquals("", DecimalInput.sanitizeCode("abc", 10))
    }
}