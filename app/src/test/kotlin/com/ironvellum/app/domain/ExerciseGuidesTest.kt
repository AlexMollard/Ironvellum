package com.ironvellum.app.domain

import com.ironvellum.app.RETIRED_WORDS
import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The how-to guides against the real catalogue: full coverage, no orphans, glossary-clean copy. */
class ExerciseGuidesTest {

    @Test
    fun `every catalogue movement has a guide with a setup and at least three steps`() {
        val thin = Seed.exercises.map { it.name }.filter { name ->
            val guide = ExerciseGuides.forName(name)
            guide == null || guide.setup.isBlank() || guide.steps.size < 3 || guide.steps.any { it.isBlank() }
        }
        assertEquals("movements without a usable guide", emptyList<String>(), thin)
    }

    @Test
    fun `every guide belongs to a catalogue movement`() {
        val catalogue = Seed.exercises.map { it.name.trim().lowercase() }.toSet()
        assertEquals("guides for movements not in the catalogue", emptyList<String>(), ExerciseGuides.keys.filter { it !in catalogue })
    }

    @Test
    fun `lookup ignores case and surrounding space`() {
        assertNotNull(ExerciseGuides.forName("  BENCH PRESS "))
        assertNotNull(ExerciseGuides.forName("Crow → Handstand"))
    }

    @Test
    fun `no guide uses a retired glossary word`() {
        val hits = ExerciseGuides.keys.flatMap { key ->
            val guide = ExerciseGuides.forName(key)!!
            (listOf(guide.setup) + guide.steps + guide.cues + guide.commonMistakes)
                .filter { text -> RETIRED_WORDS.any { it.containsMatchIn(text) } }
                .map { "$key: $it" }
        }
        assertEquals("retired words in guide text", emptyList<String>(), hits)
    }
}
