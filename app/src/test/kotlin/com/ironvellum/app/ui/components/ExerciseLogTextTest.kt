package com.ironvellum.app.ui.components

import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseLogBlock
import com.ironvellum.app.domain.ExerciseLogSet
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.SetKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** The line a History row shows for one block: compact, grouped by load, the way the lifter reads a set. */
class ExerciseLogTextTest {

    private val pullUp = Exercise(id = 1, name = "Pull-up", muscleGroup = MuscleGroup.PULL, isWeighted = false)
    private val bench = Exercise(id = 2, name = "Bench Press", muscleGroup = MuscleGroup.PUSH, isWeighted = true)
    private val plank = Exercise(id = 3, name = "Plank", muscleGroup = MuscleGroup.CORE, isWeighted = false, metric = ExerciseMetric.HOLD)

    private fun set(figure: Int, kg: Double? = null) = ExerciseLogSet(figure, kg, null, null, null, SetKind.WORKING, null)

    private fun block(vararg sets: ExerciseLogSet, best: Int? = null) = ExerciseLogBlock(0, "", sets.toList(), best, false)

    @Test
    fun `sets at one load share it`() {
        assertEquals("+18 kg × 12, 10, 8", blockLine(pullUp, block(set(12, 18.0), set(10, 18.0), set(8, 18.0))).text)
    }

    @Test
    fun `a change of load starts a new run`() {
        assertEquals("+20 kg × 5 · +18 kg × 8, 8", blockLine(pullUp, block(set(5, 20.0), set(8, 18.0), set(8, 18.0))).text)
    }

    @Test
    fun `an unloaded bodyweight set reads BW`() {
        assertEquals("BW × 12, 10", blockLine(pullUp, block(set(12), set(10))).text)
    }

    @Test
    fun `a barbell lift shows the whole load without a plus`() {
        assertEquals("60 kg × 5, 5", blockLine(bench, block(set(5, 60.0), set(5, 60.0))).text)
    }

    @Test
    fun `a hold lists seconds and a load only when it had one`() {
        assertEquals("45 s, 40 s", blockLine(plank, block(set(45), set(40))).text)
        assertEquals("+10 kg × 30 s", blockLine(plank, block(set(30, 10.0))).text)
    }

    @Test
    fun `only the best of several sets is bold`() {
        val line = blockLine(pullUp, block(set(12, 18.0), set(10, 18.0), best = 1))
        assertEquals(1, line.spanStyles.size)
        assertEquals("10", line.text.substring(line.spanStyles.single().start, line.spanStyles.single().end))
    }

    @Test
    fun `a lone set carries no bold`() {
        assertEquals(0, blockLine(pullUp, block(set(12, 18.0), best = 0)).spanStyles.size)
    }

    @Test
    fun `modifiers read as the lifter picked them`() {
        assertEquals("weighted · incline", modifierText("weighted, incline"))
        assertEquals("", modifierText(""))
    }
}
