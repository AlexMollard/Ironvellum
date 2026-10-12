package com.ironvellum.app.ui.titles

import com.ironvellum.app.domain.Skills
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniqueArtworkTest {
    @Test
    fun everyPublishedTechniqueHasItsOwnIllustration() {
        // A technique is a still picture or an animated drawing, never both and never neither.
        val illustrations = Skills.ALL.map { skill ->
            val still = techniqueArtwork(skill.name)
            val moving = techniqueAnimation(skill.name)
            require((still != null) != (moving != null)) { "${skill.name} needs exactly one illustration" }
            still?.toString() ?: moving!!.id
        }
        assertTrue(illustrations.all { it != "0" })
        assertEquals("Techniques must not silently share an illustration", illustrations.size, illustrations.toSet().size)
    }
}
