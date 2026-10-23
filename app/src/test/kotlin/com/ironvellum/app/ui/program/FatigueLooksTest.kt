package com.ironvellum.app.ui.program

import com.ironvellum.app.domain.CauseShare
import com.ironvellum.app.domain.FatigueLevel
import com.ironvellum.app.domain.LightBy
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueLooksTest {

    private val strengths = listOf(FatigueLevel.LIGHT, FatigueLevel.MODERATE, FatigueLevel.HIGH, FatigueLevel.VERY_HIGH)

    @Test
    fun `fresh is bare paper and every other level fills more than the one before`() {
        assertEquals(IronvellumColors.Rune, fatigueLook(FatigueLevel.FRESH).fill)
        val alphas = strengths.map { fatigueLook(it).fill.alpha }
        assertEquals(alphas.sorted(), alphas)
        assertEquals(alphas.size, alphas.toSet().size)
    }

    @Test
    fun `light and moderate are clearly apart`() {
        val gap = fatigueLook(FatigueLevel.MODERATE).fill.alpha - fatigueLook(FatigueLevel.LIGHT).fill.alpha
        assertTrue("gap $gap", gap >= 0.25f)
    }

    @Test
    fun `hatch on high and cross-hatch on very high, nothing below`() {
        assertEquals(RegionDecor.HATCH, fatigueLook(FatigueLevel.HIGH).decor)
        assertEquals(RegionDecor.CROSS_HATCH, fatigueLook(FatigueLevel.VERY_HIGH).decor)
        listOf(FatigueLevel.FRESH, FatigueLevel.LIGHT, FatigueLevel.MODERATE).forEach {
            assertEquals(RegionDecor.NONE, fatigueLook(it).decor)
        }
    }

    @Test
    fun `a strong level is never colour alone`() {
        FatigueLevel.entries.filter { it >= FatigueLevel.HIGH }.forEach {
            assertTrue(fatigueLook(it).decor != RegionDecor.NONE)
        }
    }

    @Test
    fun `the figure is described by level, strongest first, and says when nothing is worked`() {
        val levels = Muscle.entries.associateWith { FatigueLevel.FRESH } +
            mapOf(Muscle.QUADS to FatigueLevel.VERY_HIGH, Muscle.HAMSTRINGS to FatigueLevel.HIGH, Muscle.CALVES to FatigueLevel.LIGHT)
        val text = fatigueSummary(levels)
        assertTrue(text, text.indexOf("quads") in 0 until text.indexOf("hamstrings"))
        assertTrue("light muscles stay out of the summary: $text", !text.contains("calves"))
        assertTrue(fatigueSummary(Muscle.entries.associateWith { FatigueLevel.FRESH }).contains("no muscle", ignoreCase = true))
    }

    // ---------------------------------------------------------------- the words

    private val utc = ZoneId.of("UTC")
    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, utc).toInstant().toEpochMilli()

    private fun cause(percent: Int, modifiers: String = "") =
        CauseShare("Romanian Deadlift", modifiers, "romanian deadlift", 1L, at(20, 18), percent)

    @Test
    fun `a cause reads as a share of a weekday's lift`() {
        // 2026-10-20 is a Tuesday, the 22nd a Thursday.
        assertEquals("about 60% from Tuesday's Romanian Deadlift", causeLine(cause(60), at(22, 12), utc, Locale.ENGLISH))
    }

    @Test
    fun `a sole cause and a cause from today read plainly`() {
        assertEquals("from Tuesday's Romanian Deadlift", causeLine(cause(100), at(22, 12), utc, Locale.ENGLISH))
        assertEquals("about 45% from today's Romanian Deadlift", causeLine(cause(45), at(20, 22), utc, Locale.ENGLISH))
    }

    @Test
    fun `a variation is named in the cause`() {
        assertEquals(
            "about 50% from Tuesday's Romanian Deadlift · deficit",
            causeLine(cause(50, "deficit, tempo"), at(22, 12), utc, Locale.ENGLISH),
        )
    }

    @Test
    fun `fresh by names the day, and nothing when already fresh`() {
        assertNull(freshByLine(LightBy.None))
        assertEquals("Fresh by Mon", freshByLine(LightBy.Weekday(DayOfWeek.MONDAY), Locale.ENGLISH))
        assertTrue(freshByLine(LightBy.Today)!!.contains("today", ignoreCase = true))
        assertTrue(freshByLine(LightBy.Tomorrow)!!.contains("tomorrow", ignoreCase = true))
        assertTrue(freshByLine(LightBy.OverAWeek)!!.contains("week", ignoreCase = true))
    }
}
