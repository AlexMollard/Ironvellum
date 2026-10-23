package com.ironvellum.app.ui.titles

import com.ironvellum.app.R
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.plainText
import com.ironvellum.app.themedText
import com.ironvellum.app.data.Words
import org.junit.Assert.assertEquals
import org.junit.Test

class TitlesTabLabelTest {

    private val label = UiText.themed(R.string.titles_tab_journal, R.string.titles_tab_journal__t)

    @Test
    fun `the third Progress tab reads Skill practice in plain words and Journal themed`() {
        assertEquals("SKILL PRACTICE", label.plainText())
        assertEquals("JOURNAL", label.themedText())
    }

    @Test
    fun `the journal forms that sentences are built from are untouched`() {
        assertEquals("Practice log", UiText.themed(R.string.concept_journal_c, R.string.concept_journal_c__t).plainText())
        assertEquals("Journal", UiText.themed(R.string.concept_journal_c, R.string.concept_journal_c__t).themedText())
        // Words.journal.u is still the old upper-case word for any other reader.
        assertEquals("PRACTICE LOG", UiText.themed(R.string.concept_journal_u, R.string.concept_journal_u__t).plainText())
        assertEquals(Words.journal.u.isNotEmpty(), true)
    }
}
