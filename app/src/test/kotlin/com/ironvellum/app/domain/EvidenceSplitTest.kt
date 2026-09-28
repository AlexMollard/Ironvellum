package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The builder shows every reason without its citations and lists the papers
 * once under Sources. A split that leaves a citation in the text brings the
 * clutter back; one that drops a paper leaves a claim unsourced; one that
 * eats an ordinary parenthesis deletes the content itself.
 */
class EvidenceSplitTest {
    @Test
    fun `a dash tail and a parenthesised aside both move to the sources`() {
        val (text, cited) = Evidence.split(
            "Rest 3-5 min on main lifts. Keep 2 reps in reserve (Schoenfeld 2016; Refalo 2023). " +
                "Works your lats - Gentil 2015; Pelland 2026",
        )
        assertEquals("Rest 3-5 min on main lifts. Keep 2 reps in reserve. Works your lats", text)
        assertEquals(
            listOf(Evidence.SCHONFELD_REST_2016, Evidence.REFALO_2023, Evidence.GENTIL_2015, Evidence.PELLAND_2026),
            cited,
        )
    }

    @Test
    fun `ordinary parentheses and dashes stay in the text`() {
        val note = "From your logged lifts (e1RM 100 kg, 2 reps in reserve) - Zourdos 2016"
        val (text, cited) = Evidence.split(note)
        assertEquals("From your logged lifts (e1RM 100 kg, 2 reps in reserve)", text)
        assertEquals(listOf(Evidence.ZOURDOS_2016), cited)
        val swap = "No bench here; the Push-up is the closest match - fine for size, not max strength"
        assertEquals(swap, Evidence.split(swap).first)
    }
}

private val PAPER = Regex("""[A-Z][A-Za-z]+ (?:19|20)\d\d""")

/**
 * Every note and reason in [plans] reads clean once split, and every paper it
 * cites is registered in [Evidence] - an unregistered one would vanish from
 * the text without reaching Sources.
 */
internal fun assertCitationsResolve(plans: List<RoutinePlan>) {
    val texts = plans.flatMap { plan ->
        plan.presets.flatMap { p -> listOf(p.note) + p.entries.flatMap { listOfNotNull(it.why, it.loadNote) } }
    } + ProgramRules.SEX_NOTE
    texts.forEach { raw ->
        assertFalse("citation left in: ${Evidence.split(raw).first}", PAPER.containsMatchIn(Evidence.split(raw).first))
        PAPER.findAll(raw).forEach {
            assertTrue("unregistered paper ${it.value} in: $raw", Evidence.of(it.value) != null)
        }
    }
}
