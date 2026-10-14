package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SchemeSuggestionTest {

    @Test
    fun aHeavyCompoundIsSuggestedATopSetWithBackOffs() {
        assertEquals(SchemeType.TOP_BACKOFF, SchemeSuggestion.forLift("Bench Press", inSuperset = false).type)
        assertEquals(SchemeType.TOP_BACKOFF, SchemeSuggestion.forLift("Back Squat", inSuperset = true).type)
    }

    @Test
    fun anIsolationLiftIsSuggestedADropSetUnlessItIsInASuperset() {
        assertEquals(SchemeType.DROPS, SchemeSuggestion.forLift("Lateral Raise", inSuperset = false).type)
        assertEquals(SchemeType.STRAIGHT, SchemeSuggestion.forLift("Lateral Raise", inSuperset = true).type)
    }

    @Test
    fun anUnknownMovementStaysStraight() {
        assertEquals(SchemeType.STRAIGHT, SchemeSuggestion.forLift("Something Custom", inSuperset = false).type)
    }
}
