package com.ironvellum.app.ui.program

import com.ironvellum.app.R
import com.ironvellum.app.StringsXml
import com.ironvellum.app.domain.LightBy
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.SorenessNote
import com.ironvellum.app.domain.SorenessReason
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.plainText
import com.ironvellum.app.themedText
import java.time.DayOfWeek
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueCopyTest {

    @Test
    fun `light-by line per case`() {
        assertNull(lightByLine(LightBy.None))
        assertEquals("Back to light later today", lightByLine(LightBy.Today))
        assertEquals("Back to light by tomorrow", lightByLine(LightBy.Tomorrow))
        assertEquals("Back to light by Thursday", lightByLine(LightBy.Weekday(DayOfWeek.THURSDAY), Locale.ENGLISH))
        assertEquals("Back to light in over a week", lightByLine(LightBy.OverAWeek))
    }

    @Test
    fun `soreness line names the cause for each reason`() {
        fun note(reason: SorenessReason, modifiers: String = "") = SorenessNote(reason, "Romanian Deadlift", modifiers, Muscle.HAMSTRINGS)
        assertEquals("Romanian Deadlift was new to you and loads the stretched hamstrings.", sorenessLine(note(SorenessReason.NEW_AND_STRETCH)))
        sorenessLine(note(SorenessReason.NEW)).let { assertTrue(it, it.startsWith("Romanian Deadlift was new to you") && it.contains("hamstrings")) }
        sorenessLine(note(SorenessReason.STRETCH)).let { assertTrue(it, it.startsWith("Romanian Deadlift loads the hamstrings")) }
        // A variation is part of the lift the lifter was new to.
        assertTrue(sorenessLine(note(SorenessReason.NEW, "deficit")).startsWith("Romanian Deadlift · deficit"))
    }

    @Test
    fun `the explainer says soreness is not a quality signal in both wordings`() {
        val sentence = UiText.themed(R.string.fatigue_explainer_soreness, R.string.fatigue_explainer_soreness__t)
        assertEquals("Soreness isn't proof a workout worked, and no soreness isn't proof it didn't.", sentence.plainText())
        assertEquals("Soreness isn't proof a trial worked, and no soreness isn't proof it didn't.", sentence.themedText())
    }

    @Test
    fun `the explainer never changes the plan and says so in both wordings`() {
        val intro = UiText.themed(R.string.fatigue_explainer_intro, R.string.fatigue_explainer_intro__t)
        assertTrue(intro.plainText().endsWith("never changes your plan."))
        assertTrue(intro.themedText().endsWith("never changes your cycle."))
    }

    @Test
    fun `no fatigue string prints a decimal`() {
        val decimals = Regex("""\d\.\d""")
        val offenders = StringsXml.read(StringsXml.MAIN).filter { it.name.startsWith("fatigue_") && it.text != null && decimals.containsMatchIn(it.text!!) }
        assertFalse("decimals in ${offenders.map { it.name }}", offenders.isNotEmpty())
    }
}
