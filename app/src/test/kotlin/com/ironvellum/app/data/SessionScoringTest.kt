package com.ironvellum.app.data

import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.domain.ActivityScore
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.StrengthIndex
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.isStrength
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SessionScoring was lifted out of completeSession and mergeImported. These
 * pin that the move changed nothing: the fixture is priced by a verbatim copy
 * of the inline code completeSession ran before the extraction, and by a
 * literal recorded from it, so a later edit to either side shows up here.
 */
class SessionScoringTest {

    private val catalogue = listOf(
        ExerciseEntity(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.PUSH.name, isWeighted = true),
        ExerciseEntity(id = 2, name = "Plank", muscleGroup = MuscleGroup.CORE.name, isWeighted = false, metric = "HOLD"),
        ExerciseEntity(id = 3, name = "Bouldering", muscleGroup = "CLIMBING", isWeighted = false, metric = "ATTEMPTS_GRADE", category = "Climbing"),
        ExerciseEntity(id = 4, name = "Running", muscleGroup = "CARDIO", isWeighted = false, metric = "DISTANCE_TIME", category = "Cardio"),
        ExerciseEntity(id = 5, name = "Pull-up", muscleGroup = MuscleGroup.PULL.name, isWeighted = false),
    )

    private fun set(exerciseId: Long, index: Int, reps: Int, kg: Double? = null, done: Boolean = true, seconds: Int? = null, metres: Double? = null, grade: String? = null, mods: String = "") =
        SetLogEntity(
            sessionId = 1, exerciseId = exerciseId, exercisePosition = exerciseId.toInt(), setIndex = index,
            reps = reps, weightKg = kg, modifiers = mods, done = done, durationSec = seconds, distanceM = metres, grade = grade,
        )

    private val fixture = listOf(
        set(1, 0, 8, 80.0),
        set(1, 1, 6, 90.0),
        set(1, 2, 5, 95.0, done = false),
        set(2, 0, 0, seconds = 60),
        set(2, 1, 0, seconds = 45),
        set(3, 0, 4, grade = "V4"),
        set(4, 0, 1, seconds = 1500, metres = 5000.0),
        set(5, 0, 10, 10.0, mods = "weighted"),
    )

    /** completeSession's XP block exactly as it stood before the extraction. */
    private fun inlineXp(doneSets: List<SetLogEntity>, latestBodyweight: Double?): Int {
        val metrics = catalogue.associate { row ->
            row.id to runCatching { ExerciseMetric.valueOf(row.metric) }.getOrDefault(ExerciseMetric.REPS)
        }
        val names = catalogue.associate { it.id to it.name }
        val categories = catalogue.associate { it.id to it.category }
        fun metricOf(exerciseId: Long) = metrics[exerciseId] ?: ExerciseMetric.REPS
        fun holdSecondsOf(set: SetLogEntity): Int? =
            if (metricOf(set.exerciseId) == ExerciseMetric.HOLD) set.durationSec else null
        val liftingSets = doneSets.filter { metricOf(it.exerciseId).isStrength }
        val activitySets = doneSets.filterNot { metricOf(it.exerciseId).isStrength }
        val liftingXp = Xp.award(
            liftingSets.map { set ->
                Xp.SetEffort(
                    exerciseName = names[set.exerciseId] ?: "",
                    reps = set.reps,
                    holdSeconds = holdSecondsOf(set),
                    weightKg = set.weightKg,
                    modifiers = set.modifiers,
                    metric = metricOf(set.exerciseId),
                )
            },
            latestBodyweight,
            doneSetCount = doneSets.size,
        )
        val activityXp = activitySets.sumOf { set ->
            val metric = metricOf(set.exerciseId)
            val per = ActivityScore.xp(
                exerciseName = names[set.exerciseId] ?: "",
                category = categories[set.exerciseId] ?: "",
                metric = metric,
                durationSec = set.durationSec,
                distanceM = set.distanceM,
                addedKg = set.weightKg,
                bodyweightKg = latestBodyweight ?: 0.0,
            )
            if (metric == ExerciseMetric.ATTEMPTS_GRADE) per * set.reps else per
        }
        return liftingXp + activityXp
    }

    @Test
    fun `the extracted xp matches the inline block it replaced`() {
        val scoring = SessionScoring(catalogue)
        for (bw in listOf(null, 60.0, 82.5)) {
            assertEquals("bodyweight $bw", inlineXp(fixture.filter { it.done }, bw), scoring.xp(fixture, bw))
        }
    }

    @Test
    fun `the fixture prices at the figure recorded before the extraction`() {
        assertEquals(GOLDEN_XP_AT_80, SessionScoring(catalogue).xp(fixture, 80.0))
    }

    @Test
    fun `unticked sets earn nothing and an empty trial earns nothing`() {
        val scoring = SessionScoring(catalogue)
        assertEquals(0, scoring.xp(fixture.map { it.copy(done = false) }, 80.0))
        assertEquals(0, scoring.xp(emptyList(), 80.0))
    }

    @Test
    fun `strength counts only ticked strength work and needs a bodyweight`() {
        val scoring = SessionScoring(catalogue)
        assertEquals(0, scoring.strength(fixture, null, Sex.MALE))
        val activitiesOnly = fixture.filter { it.exerciseId == 3L || it.exerciseId == 4L }
        assertEquals(0, scoring.strength(activitiesOnly, 80.0, Sex.MALE))
        val strengthDone = fixture.filter { it.done && it.exerciseId in setOf(1L, 2L, 5L) }
        val expected = StrengthIndex.sessionScore(
            strengthDone.map { s ->
                StrengthIndex.Effort(
                    exerciseName = catalogue.first { it.id == s.exerciseId }.name,
                    reps = s.reps,
                    holdSeconds = s.durationSec.takeIf { s.exerciseId == 2L },
                    addedKg = s.weightKg,
                    muscleGroup = MuscleGroup.valueOf(catalogue.first { it.id == s.exerciseId }.muscleGroup),
                )
            },
            80.0,
            Sex.MALE,
        )
        assertEquals(expected, scoring.strength(fixture, 80.0, Sex.MALE))
    }

    @Test
    fun `a warm-up is stored unticked and adds nothing to xp or strength`() {
        val scoring = SessionScoring(catalogue)
        // The exclusion is the stored shape, not a filter: a warm-up never carries done = true
        // (the live screen's writers and the amend editor both keep it unticked), so the
        // ticked-sets rule above leaves it out. A heavy one must change nothing.
        val warmup = set(1, 3, 12, 140.0, done = false).copy(warmup = true)
        assertEquals(scoring.xp(fixture, 80.0), scoring.xp(fixture + warmup, 80.0))
        assertEquals(scoring.strength(fixture, 80.0, Sex.MALE), scoring.strength(fixture + warmup, 80.0, Sex.MALE))
    }

    private companion object {
        /** Recorded from the pre-extraction inline code for [fixture] at 80 kg. */
        const val GOLDEN_XP_AT_80 = 406
    }
}
