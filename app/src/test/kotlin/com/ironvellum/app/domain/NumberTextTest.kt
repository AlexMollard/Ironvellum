package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Numbers a person reads follow the app's language; numbers typed in accept either separator everywhere. */
class NumberTextTest {

    private val de = Locale.GERMAN
    private val fr = Locale.FRENCH
    private val es = Locale.forLanguageTag("es")
    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val ja = Locale.JAPANESE
    private val ko = Locale.KOREAN
    private val zh = Locale.forLanguageTag("zh-Hans")
    private val en = Locale.ENGLISH
    private val narrowNbsp = " "

    @Test
    fun `decimals use the language's separator`() {
        assertEquals("80.5", NumberText.decimal(80.5, 1, en))
        assertEquals("80,5", NumberText.decimal(80.5, 1, de))
        assertEquals("80,5", NumberText.decimal(80.5, 1, fr))
        assertEquals("80,5", NumberText.decimal(80.5, 1, es))
        assertEquals("80,5", NumberText.decimal(80.5, 1, ptBr))
        assertEquals("80.5", NumberText.decimal(80.5, 1, ja))
        assertEquals("80.5", NumberText.decimal(80.5, 1, ko))
        assertEquals("80.5", NumberText.decimal(80.5, 1, zh))
        assertEquals("80,0", NumberText.decimal(80.0, 1, de))
    }

    @Test
    fun `thousands use the language's separator`() {
        assertEquals("2,000", NumberText.grouped(2000, en))
        assertEquals("2.000", NumberText.grouped(2000, de))
        assertEquals("2${narrowNbsp}000", NumberText.grouped(2000, fr))
        assertEquals("2.000", NumberText.grouped(2000, es))
        assertEquals("2.000", NumberText.grouped(2000, ptBr))
        assertEquals("2,000", NumberText.grouped(2000, ja))
        assertEquals("2,000", NumberText.grouped(2000, ko))
        assertEquals("2,000", NumberText.grouped(2000, zh))
        assertEquals("1.234.567", NumberText.grouped(1234567, de))
        assertEquals("999", NumberText.grouped(999, de))
    }

    @Test
    fun `trimmed drops trailing zeros and a bare separator in every language`() {
        assertEquals("225", NumberText.trimmed(225.0, 2, en))
        assertEquals("225", NumberText.trimmed(225.0, 2, de))
        assertEquals("8.75", NumberText.trimmed(8.75, 2, en))
        assertEquals("8,75", NumberText.trimmed(8.75, 2, de))
        assertEquals("8,5", NumberText.trimmed(8.5, 2, fr))
        assertEquals("0", NumberText.trimmed(0.0, 2, de))
        assertEquals("100", NumberText.trimmed(100.0, 0, de))
    }

    @Test
    fun `fmt follows the default locale and fmtRoot never does`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(de)
            assertEquals("80,5", "%.1f".fmt(80.5))
            assertEquals("2.000", "%,d".fmt(2000))
            assertEquals("80.5", "%.1f".fmtRoot(80.5))
            assertEquals("2,000", "%,d".fmtRoot(2000))
            Locale.setDefault(en)
            assertEquals("80.5", "%.1f".fmt(80.5))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `a field prefill keeps its point whatever the language`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(de)
            assertEquals("80.5", Loads.fieldText(80.5, LoadUnit.KG))
            assertEquals("80,5", Loads.format(80.5, LoadUnit.KG))
            assertEquals("80,5 kg", Loads.withUnit(80.5, LoadUnit.KG))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `a typed weight reads the same with a comma or a point`() {
        listOf(en, de, fr, es, ptBr, ja, ko, zh).forEach { locale ->
            val saved = Locale.getDefault()
            try {
                Locale.setDefault(locale)
                assertEquals(80.5, DecimalInput.parse("80,5")!!, 1e-9)
                assertEquals(80.5, DecimalInput.parse("80.5")!!, 1e-9)
                assertEquals(80.5, Loads.parse("80,5", LoadUnit.KG, 1000.0).getOrThrow(), 1e-9)
                assertEquals(80.5, Loads.parse("80.5", LoadUnit.KG, 1000.0).getOrThrow(), 1e-9)
                assertEquals("80.5", DecimalInput.sanitize("80,5", 2))
            } finally {
                Locale.setDefault(saved)
            }
        }
    }
}
