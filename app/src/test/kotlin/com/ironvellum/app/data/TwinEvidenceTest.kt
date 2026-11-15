package com.ironvellum.app.data

import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.ui.titles.SkillGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A skill reads the sets logged as its dedicated exercise and the ones logged as its base with the modifier or load. */
class TwinEvidenceTest {

    private val exercises = listOf(
        Exercise(1, "Pull-up", MuscleGroup.PULL, isWeighted = false),
        Exercise(2, "Weighted Pull-up", MuscleGroup.PULL, isWeighted = true),
        Exercise(3, "Push-up", MuscleGroup.PUSH, isWeighted = false),
        Exercise(4, "Archer Push-up", MuscleGroup.PUSH, isWeighted = false),
        Exercise(5, "Incline Push-up", MuscleGroup.PUSH, isWeighted = false),
        Exercise(6, "Dead Hang", MuscleGroup.PULL, isWeighted = false, metric = ExerciseMetric.HOLD),
    )

    private val bodyweightKg = 80.0

    private fun trial(vararg sets: SessionSet) =
        listOf(WorkoutSession(id = 1, label = "t", startedAtMs = 1_000, completedAtMs = 2_000) to sets.toList())

    private fun set(exerciseId: Long, name: String, reps: Int, kg: Double? = null, modifiers: String = "", index: Int = 0) =
        SessionSet(id = index + 1L, exerciseId = exerciseId, exerciseName = name, setIndex = index, reps = reps, weightKg = kg, modifiers = modifiers, done = true)

    private fun evidence(vararg sets: SessionSet) =
        skillTrainingEvidence(trial(*sets), { bodyweightKg }, exercises)

    private fun clearsWeightedPullUp(vararg sets: SessionSet): Boolean {
        val found = evidence(*sets)["weighted pull-up"] ?: return false
        return SkillGuidance.clears(Skills.forName("Weighted Pull-up")!!, SkillGuidance.Effort(found.value, found.weightKg), bodyweightKg)
    }

    @Test
    fun `a pull-up with a third of bodyweight added clears the weighted pull-up bar`() {
        // 80 kg: the bar is 26.7 kg added, with half a kilo of slack.
        assertTrue(clearsWeightedPullUp(set(1, "Pull-up", 5, 27.0, "weighted")))
        assertFalse(clearsWeightedPullUp(set(1, "Pull-up", 5, 18.0, "weighted")))
        assertFalse(clearsWeightedPullUp(set(1, "Pull-up", 5, 5.0, "weighted")))
    }

    @Test
    fun `the load counts without the tag, and too few reps do not clear`() {
        assertTrue(clearsWeightedPullUp(set(1, "Pull-up", 5, 27.0)))
        assertFalse(clearsWeightedPullUp(set(1, "Pull-up", 4, 30.0, "weighted")))
    }

    @Test
    fun `a long bodyweight set does not hide the loaded one`() {
        assertTrue(
            clearsWeightedPullUp(
                set(1, "Pull-up", 15, null, index = 0),
                set(1, "Pull-up", 5, 27.0, "weighted", index = 1),
            ),
        )
    }

    @Test
    fun `sets logged under the dedicated name count the same`() {
        assertTrue(clearsWeightedPullUp(set(2, "Weighted Pull-up", 5, 27.0)))
        assertFalse(clearsWeightedPullUp(set(2, "Weighted Pull-up", 5, 18.0)))
    }

    @Test
    fun `an assisted pull-up with a load is no weighted pull-up`() {
        assertFalse(clearsWeightedPullUp(set(1, "Pull-up", 5, 30.0, "assisted")))
    }

    @Test
    fun `a push-up with archer counts for the archer push-up and still for the push-up`() {
        val found = evidence(set(3, "Push-up", 6, null, "archer"))
        assertEquals(6, found.getValue("archer push-up").value)
        assertEquals(6, found.getValue("push-up").value)
    }

    @Test
    fun `an easier push-up does not show the archer technique`() {
        val found = evidence(set(3, "Push-up", 6, null, "archer, incline"))
        assertNull(found["archer push-up"])
    }

    @Test
    fun `a base without a twin in the catalogue is left alone`() {
        val found = skillTrainingEvidence(
            trial(set(1, "Pull-up", 5, 27.0, "weighted")),
            { bodyweightKg },
            exercises.filterNot { it.name == "Weighted Pull-up" },
        )
        assertNull(found["weighted pull-up"])
        assertNotNull(found["pull-up"])
    }

    @Test
    fun `workout attempts retain repeated and below-best efforts without duplicating plain sets`() {
        val sets = listOf(
            set(1, "Pull-up", 10, index = 0),
            set(1, "Pull-up", 6, index = 1),
            set(1, "Pull-up", 6, index = 2),
        )
        val found = skillTrainingEvidence(trial(*sets.toTypedArray()), { bodyweightKg }, exercises)
        assertEquals(listOf(10, 6, 6), found.getValue("pull-up").attempts.map { it.value })
        assertEquals(10, found.getValue("pull-up").value)
    }

    @Test
    fun `workout attempts exclude pending warmup and eased sets and retain twin identity`() {
        val found = evidence(
            set(3, "Push-up", 12, modifiers = "archer"),
            set(3, "Push-up", 8, modifiers = "archer", index = 1),
            set(3, "Push-up", 99, index = 2).copy(done = false),
            set(3, "Push-up", 99, index = 3).copy(kind = SetKind.WARMUP),
            set(3, "Push-up", 99, modifiers = "assisted", index = 4),
        )
        assertEquals(listOf(12, 8), found.getValue("archer push-up").attempts.map { it.value })
        assertEquals(listOf(12, 8), found.getValue("push-up").attempts.map { it.value })
        assertTrue(found.getValue("push-up").attempts.all { it.variation.contains("archer") })
        assertTrue(found.getValue("archer push-up").attempts.all { it.variation.isEmpty() })
    }

    @Test
    fun `hold attempts use seconds and are newest first while incomplete sessions stay out`() {
        val first = set(6, "Dead Hang", 0).copy(durationSec = 40)
        val later = first.copy(durationSec = 25)
        val history = trial(first) + listOf(
            WorkoutSession(id = 2, label = "later", startedAtMs = 3_000, completedAtMs = 4_000) to listOf(later),
            WorkoutSession(id = 3, label = "live", startedAtMs = 5_000) to listOf(first.copy(durationSec = 90)),
        )
        val attempts = skillTrainingEvidence(history, { bodyweightKg }, exercises).getValue("dead hang").attempts
        assertEquals(listOf(25, 40), attempts.map { it.value })
        assertEquals(listOf(3_000L, 1_000L), attempts.map { it.achievedAtMs })
    }
}
