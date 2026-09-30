package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSearchTest {
    private fun hit(name: String, q: String) = ExerciseSearch.rank(name, q) != null

    @Test fun blankMatchesEverythingWithZero() {
        assertEquals(0, ExerciseSearch.rank("Pull-up", ""))
        assertEquals(0, ExerciseSearch.rank("Pull-up", " - "))
    }

    @Test fun punctuationAndCaseInsensitive() {
        for (q in listOf("pullup", "pull up", "Pull-up", "PULL-UP")) assertTrue(q, hit("Pull-up", q))
        assertTrue(hit("Muscle-up", "muscleup"))
    }

    @Test fun tokensMatchInAnyOrderAndAsPrefixes() {
        assertTrue(hit("Leg Extension", "ext leg"))
        assertTrue(hit("Leg Extension", "extension leg"))
        assertNull(ExerciseSearch.rank("Leg Extension", "ext arm"))
    }

    @Test fun aliasesHitCatalogueNames() {
        assertNotNull(ExerciseSearch.rank("Handstand Push-up", "hspu"))
        assertNotNull(ExerciseSearch.rank("Overhead Press", "ohp"))
        assertNotNull(ExerciseSearch.rank("Romanian Deadlift", "rdl"))
        assertNotNull(ExerciseSearch.rank("Tuck Front Lever", "fl"))
        assertNotNull(ExerciseSearch.rank("Toes-to-Bar", "t2b"))
        assertNotNull(ExerciseSearch.rank("L-sit", "l sit"))
        assertNull(ExerciseSearch.rank("Tuck Back Lever", "fl"))
        assertNull(ExerciseSearch.rank("Bench Press", "hspu"))
    }

    @Test fun typoWithinToleranceMatches() {
        assertTrue(hit("Deadlift", "deadlfit"))     // transposition
        assertTrue(hit("Deadlift", "deadlft"))      // deletion
        assertTrue(hit("Romanian Deadlift", "romainan"))
        assertTrue(hit("Bench Press", "bnch"))
    }

    @Test fun typoBeyondToleranceDoesNotMatch() {
        assertNull(ExerciseSearch.rank("Deadlift", "dxxdlxft")) // 3 edits
        assertTrue(hit("Deadlift", "dxadlxft")) // 2 edits allowed at 8 letters
        assertNull(ExerciseSearch.rank("Bench Press", "bxnxh"))
        assertNull(ExerciseSearch.rank("Deadlift", "dexdlxfx"))
    }

    @Test fun shortTokensNeverFuzzyMatch() {
        assertNull(ExerciseSearch.rank("Dip", "dop"))
        assertNull(ExerciseSearch.rank("Row", "rew"))
        assertNull(ExerciseSearch.rank("Curl", "cxr"))
    }

    @Test fun rankingExactThenPrefixThenWordsThenFuzzy() {
        val exact = ExerciseSearch.rank("Deadlift", "deadlift")!!
        val prefix = ExerciseSearch.rank("Deadlift Lockout", "deadlift")!!
        val words = ExerciseSearch.rank("Romanian Deadlift", "deadlift")!!
        val fuzzy = ExerciseSearch.rank("Deadlift", "deadlfit")!!
        assertTrue(exact < prefix)
        assertTrue(prefix < words)
        assertTrue(words < fuzzy)
    }

    @Test fun aliasHitsLeadLiteralLetterMatches() {
        val alias = ExerciseSearch.rank("Tuck Front Lever", "fl")!!
        val prefix = ExerciseSearch.rank("Flag Raise", "fl")!!
        val words = ExerciseSearch.rank("Cable Fly", "fl")!!
        assertTrue(alias < prefix)
        assertTrue(alias < words)
        // An exact name still beats any nickname reading of the same text.
        assertTrue(ExerciseSearch.rank("Press", "press")!! < ExerciseSearch.rank("Overhead Press", "press")!!)
    }

    @Test fun nonMatchReturnsNull() {
        assertNull(ExerciseSearch.rank("Bench Press", "zzzz"))
    }
}
