package com.ironvellum.app.ui.train

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TrainTabMemoryTest {

    @Before
    @After
    fun reset() = TrainTabMemory.reset()

    @Test
    fun `defaults are Plan and Fatigue`() {
        assertEquals(TrainTab.PLAN, TrainTabMemory.tab)
        assertEquals(MusclesView.FATIGUE, TrainTabMemory.view)
    }

    @Test
    fun `a coverage link opens Muscles on Coverage`() {
        TrainTabMemory.openCoverage()
        assertEquals(TrainTab.MUSCLES, TrainTabMemory.tab)
        assertEquals(MusclesView.COVERAGE, TrainTabMemory.view)
    }

    @Test
    fun `a link wins over a remembered Fatigue`() {
        TrainTabMemory.tab = TrainTab.MUSCLES
        TrainTabMemory.view = MusclesView.FATIGUE
        TrainTabMemory.tab = TrainTab.PLAN
        TrainTabMemory.openCoverage()
        assertEquals(MusclesView.COVERAGE, TrainTabMemory.view)
    }

    @Test
    fun `the switch is remembered across a visit to Plan`() {
        TrainTabMemory.tab = TrainTab.MUSCLES
        TrainTabMemory.view = MusclesView.COVERAGE
        TrainTabMemory.tab = TrainTab.PLAN
        TrainTabMemory.tab = TrainTab.MUSCLES
        assertEquals(MusclesView.COVERAGE, TrainTabMemory.view)
    }

    @Test
    fun `the fatigue link opens Muscles on Fatigue`() {
        TrainTabMemory.openCoverage()
        TrainTabMemory.openFatigue()
        assertEquals(TrainTab.MUSCLES, TrainTabMemory.tab)
        assertEquals(MusclesView.FATIGUE, TrainTabMemory.view)
    }
}
