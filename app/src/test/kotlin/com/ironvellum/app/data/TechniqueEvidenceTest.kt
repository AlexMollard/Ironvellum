package com.ironvellum.app.data

import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.Skills
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which logged set stands as a technique's training evidence. */
class TechniqueEvidenceTest {

    private fun record(reps: Int, weightKg: Double?, score: Double) =
        SetRecords.Record("x", setIndex = 0, score = score, reps = reps, weightKg = weightKg, achievedAtMs = 0, sessionId = 0)

    private val loadFree = Skills.ALL.first {
        it.target > 0 && !it.standard.contains("bodyweight", true) && Skills.loadBar(it.name) == null
    }
    private val addedLoad = Skills.forName("Weighted Pull-up")!!
    private val bodyweightBar = Skills.ALL.first { it.standard.contains("bodyweight", true) }

    private val heavyShort = record(reps = 5, weightKg = 25.0, score = 900.0)
    private val lightLong = record(reps = 12, weightKg = null, score = 700.0)

    @Test
    fun `a count-only standard takes the set with the most reps or seconds`() {
        assertEquals(lightLong, techniqueEvidence(listOf(heavyShort, lightLong), loadFree))
    }

    @Test
    fun `a standard with a load keeps the strongest set`() {
        assertEquals(heavyShort, techniqueEvidence(listOf(lightLong, heavyShort), addedLoad))
        assertEquals(heavyShort, techniqueEvidence(listOf(lightLong, heavyShort), bodyweightBar))
    }

    @Test
    fun `a movement that is no technique keeps the strongest set`() {
        assertEquals(heavyShort, techniqueEvidence(listOf(lightLong, heavyShort), null))
    }
}
