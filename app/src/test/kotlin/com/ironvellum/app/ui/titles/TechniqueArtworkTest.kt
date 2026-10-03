package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.Skills
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniqueArtworkTest {
    @Test
    fun everyPublishedTechniqueHasItsOwnIllustration() {
        val illustrations = Skills.ALL.map { skill ->
            requireNotNull(techniqueArtwork(skill.name)) { "Missing artwork for ${skill.name}" }
        }
        assertTrue(illustrations.all { it != 0 })
        assertEquals("Techniques must not silently share an illustration", illustrations.size, illustrations.toSet().size)
    }
}
