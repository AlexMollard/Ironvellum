package com.ironvellum.app.ui.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class CelebrationGateTest {

    @After
    fun release() {
        while (CelebrationGate.held.value > 0) CelebrationGate.leave()
    }

    @Test
    fun pagesShowWhenNothingHoldsTheGate() {
        assertEquals(listOf("deed", "crest"), CelebrationGate.visible(listOf("deed", "crest"), CelebrationGate.held.value))
    }

    @Test
    fun pagesWaitWhileASealTellsItsStory() {
        CelebrationGate.enter()
        assertEquals(emptyList<String>(), CelebrationGate.visible(listOf("deed", "crest"), CelebrationGate.held.value))
        CelebrationGate.leave()
        assertEquals(listOf("deed", "crest"), CelebrationGate.visible(listOf("deed", "crest"), CelebrationGate.held.value))
    }

    @Test
    fun theGateOpensOnlyWhenEveryHolderLeft() {
        CelebrationGate.enter()
        CelebrationGate.enter()
        CelebrationGate.leave()
        assertEquals(1, CelebrationGate.held.value)
        CelebrationGate.leave()
        CelebrationGate.leave()
        assertEquals(0, CelebrationGate.held.value)
    }
}