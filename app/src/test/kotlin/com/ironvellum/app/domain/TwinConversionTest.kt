package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which past blocks move onto dedicated exercises, shaped like a lifter who logged everything as a base plus modifiers. */
class TwinConversionTest {

    private val pullUp = Exercise(1, "Pull-up", MuscleGroup.PULL, isWeighted = false)
    private val chinUp = Exercise(2, "Chin-up", MuscleGroup.PULL, isWeighted = false)
    private val pushUp = Exercise(3, "Push-up", MuscleGroup.PUSH, isWeighted = false)
    private val archerPushUp = Exercise(4, "Archer Push-up", MuscleGroup.PUSH, isWeighted = false)
    private val pistol = Exercise(5, "Pistol Squat", MuscleGroup.LEGS, isWeighted = false)
    private val weightedPullUp = Exercise(6, "Weighted Pull-up", MuscleGroup.PULL, isWeighted = true)
    private val weightedChinUp = Exercise(7, "Weighted Chin-up", MuscleGroup.PULL, isWeighted = true)
    private val inclinePushUp = Exercise(8, "Incline Push-up", MuscleGroup.PUSH, isWeighted = false)
    private val declinePushUp = Exercise(9, "Decline Push-up", MuscleGroup.PUSH, isWeighted = false)
    private val weightedPushUp = Exercise(10, "Weighted Push-up", MuscleGroup.PUSH, isWeighted = true)
    private val catalogue = listOf(
        pullUp, chinUp, pushUp, archerPushUp, pistol, weightedPullUp, weightedChinUp, inclinePushUp, declinePushUp, weightedPushUp,
    )

    private fun block(session: Long, position: Int, exercise: Exercise, sets: Int, kg: Double?, modifiers: String) =
        (0 until sets).map { TwinConversion.Row(session, position, exercise.id, kg, modifiers) }

    /** Pull-up weighted x14, Chin-up weighted x3, Push-up "weighted, deficit, elevated" x10, "weighted, incline" x2, an Archer Push-up already, a Pistol Squat weighted. */
    private val rows: List<TwinConversion.Row> =
        block(1, 0, pullUp, 5, 10.0, "weighted") + block(1, 1, pushUp, 4, 10.0, "weighted, deficit, elevated") + block(1, 2, archerPushUp, 3, null, "") +
            block(2, 0, pullUp, 5, 12.5, "weighted") + block(2, 1, chinUp, 3, 10.0, "weighted") + block(2, 2, pushUp, 3, 10.0, "weighted, deficit, elevated") +
            block(3, 0, pullUp, 4, 15.0, "weighted") + block(3, 1, pushUp, 3, 10.0, "weighted, deficit, elevated") +
            block(3, 2, pushUp, 2, 5.0, "weighted, incline") + block(3, 3, pistol, 2, 10.0, "weighted")

    @Test
    fun `every pair is grouped with its set count and the dedicated ones are left alone`() {
        val groups = TwinConversion.groups(TwinConversion.plan(rows, catalogue))
        assertEquals(
            listOf(
                TwinConversion.Group("Pull-up", "Weighted Pull-up", sets = 14, sessions = 3),
                TwinConversion.Group("Push-up", "Decline Push-up", sets = 10, sessions = 3),
                TwinConversion.Group("Chin-up", "Weighted Chin-up", sets = 3, sessions = 1),
                TwinConversion.Group("Push-up", "Incline Push-up", sets = 2, sessions = 1),
            ),
            groups,
        )
    }

    @Test
    fun `the other modifiers stay and the shape wins over weighted`() {
        val moves = TwinConversion.plan(rows, catalogue)
        assertEquals("weighted, deficit", moves.first { it.to == "Decline Push-up" }.modifiers)
        assertEquals("weighted", moves.first { it.to == "Incline Push-up" }.modifiers)
        assertEquals("", moves.first { it.to == "Weighted Pull-up" }.modifiers)
        assertTrue(moves.none { it.from == "Archer Push-up" || it.from == "Pistol Squat" })
    }

    @Test
    fun `a load with no tag moves too, an assisted one and a barbell lift do not`() {
        val untagged = TwinConversion.plan(block(1, 0, pullUp, 3, 10.0, ""), catalogue)
        assertEquals("Weighted Pull-up", untagged.single().to)
        assertTrue(TwinConversion.plan(block(1, 0, pullUp, 3, 20.0, "assisted"), catalogue).isEmpty())
        val bench = Exercise(11, "Bench Press", MuscleGroup.PUSH, isWeighted = true)
        assertTrue(TwinConversion.plan(block(1, 0, bench, 3, 60.0, "paused, weighted"), catalogue + bench).isEmpty())
    }

    @Test
    fun `a second look at what was written finds nothing`() {
        val moves = TwinConversion.plan(rows, catalogue)
        val written = rows.map { row ->
            val move = moves.firstOrNull { it.block == TwinConversion.BlockRef(row.sessionId, row.position, row.exerciseId) }
            if (move == null) row else row.copy(exerciseId = move.toId, modifiers = move.modifiers)
        }
        assertTrue(TwinConversion.plan(written, catalogue).isEmpty())
    }

    @Test
    fun `a twin missing from the catalogue is not a target`() {
        val without = catalogue.filterNot { it.name == "Weighted Pull-up" }
        assertTrue(TwinConversion.plan(block(1, 0, pullUp, 3, 10.0, "weighted"), without).isEmpty())
    }
}
