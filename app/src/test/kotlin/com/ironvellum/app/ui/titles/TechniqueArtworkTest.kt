package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.ExerciseArtIndex
import com.ironvellum.app.domain.Skills
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniqueArtworkTest {
    @Test
    fun everyTechniqueHasADrawingOrTheHousePictogramAndNoAiArtRemains() {
        val drawn = Skills.ALL.filter { techniqueDrawing(it.name) != null }
        // Every technique has its own still; none falls back to the glyph, and there is no third source.
        assertEquals("techniques with no drawing", emptyList<String>(), Skills.ALL.filter { it !in drawn }.map { it.name })
        assertTrue(java.io.File("src/main/res/drawable-nodpi").listFiles().orEmpty().none { it.name.startsWith("skill_") })
    }

    @Test
    fun aProxyDrawingIsCaptionedAndAnExactOneIsNot() {
        Skills.ALL.forEach { skill ->
            val shows = ExerciseArtIndex.shows(skill.name)
            if (shows != null) {
                assertEquals("Shown: $shows", techniqueCaption(skill.name))
                assertTrue("${skill.name} proxy needs a drawing", techniqueDrawing(skill.name) != null)
            } else {
                assertNull(techniqueCaption(skill.name))
            }
        }
    }
}
