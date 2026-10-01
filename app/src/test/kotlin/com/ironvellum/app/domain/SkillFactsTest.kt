package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The technique detail shows ExerciseFacts(skill.name): the muscle figure, HOW TO
 * and gear are all looked up by the skill's name. A skill whose name matches no
 * profile or guide would silently show none of it.
 */
class SkillFactsTest {

    @Test
    fun `every skill resolves a muscle profile and a how-to guide`() {
        val noProfile = Skills.ALL.map { it.name }.filter { MuscleMap.profile(it)?.muscles.isNullOrEmpty() }
        val noGuide = Skills.ALL.map { it.name }.filter { ExerciseGuides.forName(it) == null }
        assertEquals("skills without a muscle profile", emptyList<String>(), noProfile)
        assertEquals("skills without a how-to guide", emptyList<String>(), noGuide)
    }
}
