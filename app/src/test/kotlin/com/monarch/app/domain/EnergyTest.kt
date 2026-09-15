package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyTest {
    @Test
    fun `katch-mcardle matches hand-computed value`() {
        // 80 kg at 20% bf = 64 kg lean; 370 + 21.6 x 64 = 1752.4
        assertEquals(64.0, Energy.leanMassKg(80.0, 20.0)!!, 1e-9)
        val est = Energy.restingKcalPerDay(80.0, 20.0)!!
        assertEquals(1752, est.kcal)
        assertTrue(est.basis.contains("Katch-McArdle"))
    }

    @Test
    fun `resting burn is null and names body fat when body fat is absent`() {
        assertNull(Energy.restingKcalPerDay(80.0, null))
        assertNull(Energy.restingKcalPerDay(null, 20.0))
        assertNull(Energy.leanMassKg(80.0, null))
    }

    @Test
    fun `met equation matches hand-computed kcal`() {
        // 8.3 x 3.5 x 72.5 / 200 x 31 min = 326.4
        val est = Energy.setKcal(
            ExerciseMetric.DURATION, "Running", "Cardio",
            durationSec = 31 * 60, distanceM = null, reps = 0,
            weightKg = null, bodyKg = 72.5,
        )!!
        assertEquals(326, est.kcal)
        assertEquals(EnergyConfidence.ESTIMATED, est.confidence)
    }

    @Test
    fun `faster run of the same distance burns more kcal`() {
        fun run(durationSec: Int) = Energy.setKcal(
            ExerciseMetric.DISTANCE_TIME, "Run", "Cardio",
            durationSec = durationSec, distanceM = 10_000.0, reps = 0,
            weightKg = null, bodyKg = 80.0,
        )!!.kcal
        // 12 km/h = 50 min at MET 11.8 vs 7 km/h = ~86 min at MET 6.0
        assertTrue(run(50 * 60) > run(85 * 60 + 42))
        assertEquals(826, run(50 * 60))
    }

    @Test
    fun `unknown exercise name still gets its category met`() {
        assertTrue(Energy.metFor("Ultimate Frisbee", "Sport", ExerciseMetric.DURATION, null, 1800) > 0.0)
        assertEquals(7.0, Energy.metFor("Ultimate Frisbee", "Sport", ExerciseMetric.DURATION, null, 1800), 1e-9)
        // name known even with unknown category
        assertEquals(11.0, Energy.metFor("Jump Rope Session", "", ExerciseMetric.DURATION, null, 600), 1e-9)
        // fully unknown: sane nonzero default
        assertEquals(3.5, Energy.metFor("Underwater Basket Weaving", "", ExerciseMetric.DURATION, null, 600), 1e-9)
    }

    @Test
    fun `setKcal returns null for unknown body weight`() {
        assertNull(
            Energy.setKcal(
                ExerciseMetric.DURATION, "Run", "Cardio",
                durationSec = 1800, distanceM = null, reps = 0,
                weightKg = null, bodyKg = null,
            ),
        )
    }

    @Test
    fun `dayKcal measured value wins and is never summed with estimates`() {
        val steps = Energy.stepsKcal(10_000, 7.0, 80.0, 180.0)!!
        val session = Energy.sessionKcal(
            sets = listOf(
                SessionSet(exerciseId = 1, setIndex = 0, reps = 10, durationSec = 1800),
            ),
            exercises = emptyMap(),
            bodyKg = 80.0,
            sessionMinutes = 30,
        )!!
        val day = Energy.dayKcal(450, steps, listOf(session))!!
        assertEquals(450, day.kcal)
        assertEquals(EnergyConfidence.MEASURED, day.confidence)
        assertTrue(day.basis.contains("measured"))
    }

    @Test
    fun `stepsKcal uses measured distance when present`() {
        // 5 km walk at MET 3.5, 80 kg: 3.5 x 3.5 x 80 / 200 x 60 min = 294
        assertEquals(294, Energy.stepsKcal(8_000, 5.0, 80.0, null)!!.kcal)
    }

    @Test
    fun `stepsKcal stride differs between 150cm and 190cm person`() {
        val short = Energy.stepsKcal(10_000, null, 80.0, 150.0)!!.kcal
        val tall = Energy.stepsKcal(10_000, null, 80.0, 190.0)!!.kcal
        assertTrue(tall > short)
    }

    @Test
    fun `stepsKcal zero steps yields zero not null`() {
        assertEquals(0, Energy.stepsKcal(0, null, 80.0, 180.0)!!.kcal)
        assertEquals(0, Energy.stepsKcal(0, 5.0, 80.0, 180.0)!!.kcal)
    }

    @Test
    fun `stepsKcal missing height and distance is null`() {
        assertNull(Energy.stepsKcal(5_000, null, 80.0, null))
        assertNull(Energy.stepsKcal(5_000, null, null, 180.0))
    }

    @Test
    fun `sessionKcal prefers real session minutes over set-count model`() {
        val set = SessionSet(exerciseId = 1, setIndex = 0, reps = 10)
        val est = Energy.sessionKcal(listOf(set, set.copy(setIndex = 1), set.copy(setIndex = 2)), emptyMap(), 80.0, 45)!!
        assertEquals(EnergyConfidence.COARSE, est.confidence)
        assertTrue(est.basis.contains("45 min"))
        // MET 6.0 x 3.5 x 80 / 200 x 45 = 378
        assertEquals(378, est.kcal)
    }

    @Test
    fun `sessionKcal set-count model without session minutes is coarse`() {
        val set = SessionSet(exerciseId = 1, setIndex = 0, reps = 10)
        // MET 3.5 x 3.5 x 80 / 200 x (4 sets x 1.5 min = 6) = 29.4
        val est = Energy.sessionKcal(listOf(set, set.copy(setIndex = 1), set.copy(setIndex = 2), set.copy(setIndex = 3)), emptyMap(), 80.0, null)!!
        assertEquals(EnergyConfidence.COARSE, est.confidence)
        assertEquals(29, est.kcal)
    }

    @Test
    fun `sessionKcal timed activity sets are estimated not coarse`() {
        val set = SessionSet(exerciseId = 1, setIndex = 0, reps = 0, durationSec = 1800)
        // MET 8.3 (run anchor, flat without distance) x 3.5 x 80 / 200 x 30 min = 348
        val est = Energy.sessionKcal(listOf(set.copy(exerciseName = "Run")), emptyMap(), 80.0, null)!!
        assertEquals(EnergyConfidence.ESTIMATED, est.confidence)
        assertEquals(348, est.kcal)
    }

    @Test
    fun `sessionKcal null without body weight or sets`() {
        assertNull(Energy.sessionKcal(emptyList(), emptyMap(), 80.0, 30))
        assertNull(Energy.sessionKcal(listOf(SessionSet(exerciseId = 1, setIndex = 0, reps = 10)), emptyMap(), null, 30))
    }

    @Test
    fun `dayKcal null when nothing is known`() {
        assertNull(Energy.dayKcal(null, null, emptyList()))
    }

    // ---- added later: cases the original 16 did not reach ----

    private fun ex(id: Long, name: String, category: String, metric: ExerciseMetric) =
        Exercise(
            id = id,
            name = name,
            muscleGroup = MuscleGroup.CARDIO,
            isWeighted = false,
            category = category,
            metric = metric,
        )

    private fun set(
        exerciseId: Long,
        name: String,
        durationSec: Int? = null,
        distanceM: Double? = null,
        reps: Int = 0,
    ) = SessionSet(
        id = 0,
        exerciseId = exerciseId,
        exerciseName = name,
        setIndex = 0,
        reps = reps,
        weightKg = null,
        done = true,
        durationSec = durationSec,
        distanceM = distanceM,
    )

    @Test
    fun `speed banding needs both distance and duration`() {
        // With only one of them the flat name MET must be used, never a band
        // derived from a division by zero or a missing operand.
        assertEquals(
            8.3,
            Energy.metFor("Run", "Cardio", ExerciseMetric.DISTANCE_TIME, null, 1800),
            1e-9,
        )
        assertEquals(
            8.3,
            Energy.metFor("Run", "Cardio", ExerciseMetric.DISTANCE_TIME, 4000.0, null),
            1e-9,
        )
        assertEquals(
            8.3,
            Energy.metFor("Run", "Cardio", ExerciseMetric.DISTANCE_TIME, 4000.0, 0),
            1e-9,
        )
    }

    @Test
    fun `a fast run is not costed as a jog`() {
        fun run(distanceM: Double) = Energy.metFor(
            "Run", "Cardio", ExerciseMetric.DISTANCE_TIME, distanceM, 1800,
        )
        assertEquals(6.0, run(2_000.0), 1e-9)   // 4 km/h  -> slow
        assertEquals(8.3, run(4_000.0), 1e-9)   // 8 km/h  -> moderate
        assertEquals(11.8, run(6_000.0), 1e-9)  // 12 km/h -> vigorous
    }

    @Test
    fun `an unknown name falls back to its category, then to moderate work`() {
        assertEquals(7.5, Energy.metFor("Unlisted Thing", "Climbing", ExerciseMetric.DURATION, null, null), 1e-9)
        assertEquals(3.5, Energy.metFor("Unlisted Thing", "Nonsense", ExerciseMetric.DURATION, null, null), 1e-9)
    }

    @Test
    fun `a lifting set is not costed on its own`() {
        // REPS sets have no duration of their own; they are covered by the
        // session-level set-count model instead.
        assertNull(
            Energy.setKcal(
                ExerciseMetric.REPS, "Pull-up", "Strength", 600, null, 8, 20.0, bodyKg = 80.0,
            ),
        )
    }

    @Test
    fun `elapsed time already spent on timed sets is not spent twice on lifting`() {
        val exercises = mapOf(
            1L to ex(1, "Pull-up", "Strength", ExerciseMetric.REPS),
            2L to ex(2, "Skipping", "Cardio", ExerciseMetric.DURATION),
        )
        val sets = listOf(set(1, "Pull-up", reps = 8), set(2, "Skipping", durationSec = 600))
        val est = Energy.sessionKcal(sets, exercises, 70.0, sessionMinutes = 30)!!
        // Skipping 10 min at MET 11 = 134; the remaining 20 min of lifting at
        // MET 6 = 6 * 3.5 * 70 / 200 * 20 = 147. Charging the full 30 min to
        // lifting as well would give 220 on top.
        assertEquals(134 + 147, est.kcal)
        assertEquals(EnergyConfidence.COARSE, est.confidence)
    }

    @Test
    fun `without measured distance the stride comes from height`() {
        // 10000 steps * (0.415 * 180 / 100) = 7470 m; at 5 km/h = 89.64 min;
        // 3.5 * 3.5 * 70 / 200 * 89.64 = 384.3
        val est = Energy.stepsKcal(steps = 10_000, distanceKm = null, bodyKg = 70.0, heightCm = 180.0)!!
        assertEquals(384, est.kcal)
        assertTrue("basis must disclose the derivation", est.basis.contains("stride from height"))
        assertTrue(
            "height was present and used, so nothing must be reported as missing",
            est.missing.isEmpty(),
        )
    }

    @Test
    fun `without a measured value the day is the sum of its estimates`() {
        val steps = EnergyEstimate(100, EnergyConfidence.ESTIMATED, "steps")
        val coarse = EnergyEstimate(200, EnergyConfidence.COARSE, "lift")
        val day = Energy.dayKcal(null, steps, listOf(coarse))!!
        assertEquals(300, day.kcal)
        assertEquals(
            "one coarse part makes the whole day coarse",
            EnergyConfidence.COARSE,
            day.confidence,
        )
    }
}
