package com.ironvellum.app.domain

import com.ironvellum.app.RETIRED_WORDS
import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The why-do-it entries against the real catalogue: full coverage, no orphans, lengths the About page fits, clean copy. */
class ExerciseWhysTest {

    @Test
    fun `every catalogue movement has a why with a purpose, a pick line and two or three tags`() {
        val thin = Seed.exercises.map { it.name }.filter { name ->
            val why = ExerciseWhys.forName(name)
            why == null || why.purpose.isBlank() || why.pickIt.isBlank() || why.tags.size !in 2..3
        }
        assertEquals("movements without a usable why", emptyList<String>(), thin)
    }

    @Test
    fun `every why belongs to a catalogue movement`() {
        val catalogue = Seed.exercises.map { it.name.trim().lowercase() }.toSet()
        assertEquals("whys for movements not in the catalogue", emptyList<String>(), ExerciseWhys.keys.filter { it !in catalogue })
    }

    @Test
    fun `lookup ignores case and surrounding space`() {
        assertNotNull(ExerciseWhys.forName("  PULL-UP "))
    }

    @Test
    fun `every why stays within the lengths the About page is sized for`() {
        val long = ExerciseWhys.keys.mapNotNull { key ->
            val why = ExerciseWhys.forName(key)!!
            key.takeIf { why.purpose.length > 120 || why.pickIt.length > 140 || why.tags.any { it.length > 18 } }
        }
        assertEquals("whys over the length caps", emptyList<String>(), long)
    }

    @Test
    fun `no why uses a retired glossary word, a themed word, an em dash or a superlative`() {
        val banned = Regex("""—|\bthe best\b|\bultimate\b|\btechniques?\b|\barmoury\b""", RegexOption.IGNORE_CASE)
        val hits = ExerciseWhys.keys.flatMap { key ->
            val why = ExerciseWhys.forName(key)!!
            (listOf(why.purpose, why.pickIt) + why.tags)
                .filter { text -> banned.containsMatchIn(text) || RETIRED_WORDS.any { it.containsMatchIn(text) } }
                .map { "$key: $it" }
        }
        assertEquals("whys with banned wording", emptyList<String>(), hits)
    }
}
