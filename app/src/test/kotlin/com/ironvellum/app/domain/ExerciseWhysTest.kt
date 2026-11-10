package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The why-do-it entries cover the real catalogue without orphaned keys. */
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

}
