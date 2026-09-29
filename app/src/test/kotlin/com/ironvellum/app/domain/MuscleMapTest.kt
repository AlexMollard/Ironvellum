package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Walks the MuscleMap against the REAL seed catalogue - the rows that
 * actually ship - so an unseeded profile key or an unprofiled generator
 * movement breaks these tests instead of a stranger's first week.
 */
class MuscleMapTest {

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    /** Exactly what the generator may prescribe: lifting rows, not holds or activities. */
    private fun generatorEligible(exercise: Exercise): Boolean =
        exercise.category.isBlank() &&
            exercise.metric == ExerciseMetric.REPS &&
            !MovementDifficulty.isLoadPriced(exercise.name) &&
            Skills.ALL.firstOrNull { it.name.equals(exercise.name, ignoreCase = true) }
                ?.metric != Skills.Metric.METRES

    @Test
    fun `every generator-eligible catalogue movement has a profile`() {
        val missing = catalogue.filter { generatorEligible(it) && MuscleMap.profile(it.name) == null }
        assertTrue(
            "movements the generator may prescribe but MuscleMap does not know: " +
                missing.joinToString { it.name },
            missing.isEmpty(),
        )
    }

    @Test
    fun `every MuscleMap key exists in the catalogue case-insensitively`() {
        val catalogueKeys = catalogue.map { it.name.trim().lowercase() }.toSet()
        val offenders = MuscleMap.keys.filter { it !in catalogueKeys }
        assertTrue(
            "MuscleMap keys with no catalogue row: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `every contribution sits within the 0 to 1 scale`() {
        for (key in MuscleMap.keys) {
            val profile = MuscleMap.profile(key)!!
            profile.muscles.forEach { (muscle, share) ->
                assertTrue(
                    "$key credits $muscle with $share outside the 0..1 scale",
                    share in 0.0..1.0,
                )
            }
        }
    }

    @Test
    fun `squats never credit hamstrings`() {
        // Kubo 2019: squat training grew quads, glutes and adductors but not
        // the hamstrings - a squat-pattern movement must not sneak hamstring
        // volume into the plan.
        for (key in MuscleMap.keys) {
            val profile = MuscleMap.profile(key)!!
            if (profile.pattern == MovementPattern.SQUAT) {
                assertEquals(
                    "$key credits hamstrings from a squat pattern",
                    0.0,
                    profile.muscles[Muscle.HAMSTRINGS] ?: 0.0,
                    1e-9,
                )
            }
        }
    }

    @Test
    fun `stretch bias marks exactly the long-length twins`() {
        // Maeo 2021: seated (long) beats lying (short) leg curl.
        assertTrue(MuscleMap.profile("Seated Leg Curl")!!.stretchBias)
        assertFalse(MuscleMap.profile("Lying Leg Curl")!!.stretchBias)
        // Maeo 2022: overhead beats neutral elbow extension.
        assertTrue(MuscleMap.profile("Overhead Cable Extension")!!.stretchBias)
        assertFalse(MuscleMap.profile("Triceps Pushdown")!!.stretchBias)
    }

    @Test
    fun `raises peak torque at short length and are not stretch biased`() {
        // Resistance-curve mechanics: for these the torque peaks with the
        // muscle SHORT, so lengthened-position evidence does not apply.
        listOf(
            "Lateral Raise", "Reverse Fly", "Front Raise", "Triceps Kickback",
            "Face Pull", "Hip Abduction", "Cable Curl",
        ).forEach { name ->
            assertFalse("$name must not claim the stretch position", MuscleMap.profile(name)!!.stretchBias)
        }
        // The genuinely long-length picks keep the flag.
        listOf(
            "Cable Lateral Raise", "Dumbbell Fly", "Cable Fly", "Preacher Curl",
            "Overhead Cable Extension", "Seated Leg Curl", "Romanian Deadlift",
            "Back Squat", "Standing Calf Raise", "Leg Extension", "Dumbbell Pullover",
        ).forEach { name ->
            assertTrue("$name lost its stretch bias", MuscleMap.profile(name)!!.stretchBias)
        }
    }

    @Test
    fun `seated calf raise half-credits calves against the standing raise`() {
        // Kinoshita 2023: gastroc grew 1.7%/0.6% seated vs 12.4%/9.2%
        // standing; soleus grew the same either way.
        assertEquals(0.5, MuscleMap.profile("Seated Calf Raise")!!.muscles[Muscle.CALVES]!!, 1e-9)
        assertEquals(1.0, MuscleMap.profile("Standing Calf Raise")!!.muscles[Muscle.CALVES]!!, 1e-9)
    }

    @Test
    fun `press family carries the measured Lanza sub-levels`() {
        val bench = MuscleMap.profile("Bench Press")
        assertNotNull(bench)
        assertEquals(1.0, bench!!.muscles[Muscle.MID_CHEST]!!, 1e-9)
        assertEquals(0.7, bench.muscles[Muscle.FRONT_DELTS]!!, 1e-9)
        assertEquals(0.6, bench.muscles[Muscle.TRICEPS]!!, 1e-9)
        assertEquals(0.3, bench.muscles[Muscle.SIDE_DELTS]!!, 1e-9)
    }

    /** The muscle a profile is chosen for: its largest share, first in map order on a tie. */
    private fun dominant(name: String): Muscle =
        MuscleMap.profile(name)!!.muscles.entries.maxWithOrNull(compareBy { it.value })!!.key

    @Test
    fun `the chest is credited by region, not as one muscle`() {
        // Flat presses and flyes: the sternal chest leads, both neighbours assist.
        listOf(
            "Bench Press", "Dumbbell Bench Press", "Machine Chest Press", "Smith Machine Bench Press",
            "Push-up", "Dumbbell Fly", "Cable Fly", "Pec Deck",
        ).forEach { name ->
            val muscles = MuscleMap.profile(name)!!.muscles
            assertEquals("$name must lead with the mid chest", Muscle.MID_CHEST, dominant(name))
            assertTrue("$name credits no upper chest", (muscles[Muscle.UPPER_CHEST] ?: 0.0) > 0.0)
            assertTrue("$name credits no lower chest", (muscles[Muscle.LOWER_CHEST] ?: 0.0) > 0.0)
        }
        // Incline presses: the clavicular chest leads and the mid chest keeps
        // the flat press's credit - the incline grew the other sites as much
        // as the flat press did (Chaves 2020).
        listOf("Incline Bench Press", "Incline Dumbbell Press").forEach { name ->
            val muscles = MuscleMap.profile(name)!!.muscles
            assertEquals("$name must lead with the upper chest", Muscle.UPPER_CHEST, dominant(name))
            assertEquals(1.0, muscles[Muscle.MID_CHEST] ?: 0.0, 1e-9)
        }
        // Dips: the costal chest leads.
        listOf("Dip", "Parallel Bar Dip", "Ring Dip", "Assisted Dip").forEach { name ->
            assertEquals("$name must lead with the lower chest", Muscle.LOWER_CHEST, dominant(name))
        }
    }

    @Test
    fun `angle and range modifiers move credit, load modifiers do not`() {
        fun lead(name: String, modifiers: String) =
            MuscleMap.profile(name, modifiers)!!.muscles.entries.first { it.value == 1.0 }.key
        // A push-up moves the body, so feet elevated is the incline and hands up the decline.
        assertEquals(Muscle.UPPER_CHEST, lead("Push-up", "weighted, deficit, elevated"))
        assertEquals(Muscle.LOWER_CHEST, lead("Push-up", "incline"))
        assertEquals(Muscle.LOWER_CHEST, lead("Incline Push-up", ""))
        // A bench moves the implement, so the words keep their bench meaning.
        assertEquals(Muscle.UPPER_CHEST, lead("Bench Press", "incline"))
        assertEquals(Muscle.LOWER_CHEST, lead("Dumbbell Fly", "decline"))
        // Opposite angles cancel; load changes nothing.
        assertEquals(MuscleMap.profile("Push-up"), MuscleMap.profile("Push-up", "elevated, incline"))
        assertEquals(MuscleMap.profile("Bench Press"), MuscleMap.profile("Bench Press", "weighted, paused, tempo"))
        // Angling keeps the other regions' credit rather than moving it.
        val elevated = MuscleMap.profile("Push-up", "elevated")!!.muscles
        assertEquals(1.0, elevated[Muscle.MID_CHEST] ?: 0.0, 1e-9)
        assertEquals(0.5, elevated[Muscle.LOWER_CHEST] ?: 0.0, 1e-9)
        // A deficit is long-length work even where the plain movement is not.
        assertFalse(MuscleMap.profile("Glute Bridge")!!.stretchBias)
        assertTrue(MuscleMap.profile("Glute Bridge", "deficit")!!.stretchBias)
        // The weekly count reads the modifiers.
        val week = listOf(PlannedPreset("Push", "", 5, listOf(PlannedEntry("Push-up", 3, 12, null, modifiers = "elevated"))))
        assertEquals(3.0, ProgramRules.weeklyVolume(week)[Muscle.UPPER_CHEST] ?: 0.0, 1e-9)
    }

    @Test
    fun `profile lookup is case and whitespace insensitive`() {
        assertEquals(MuscleMap.profile("bench press"), MuscleMap.profile("  BENCH PRESS "))
        assertEquals(MuscleMap.profile("Bench Press"), MuscleMap.profile(" Bench Press "))
    }

    @Test
    fun `archer and l-sit pull-ups credit the back and shoulder helpers a pull-up does`() {
        // A weekly coverage of archer or L-sit pull-ups read rear delts, traps
        // and cuff as untrained while the same sets of plain pull-ups did not.
        val pullUp = MuscleMap.profile("Pull-up")!!.muscles
        for (variant in listOf("Archer Pull-up", "L-sit Pull-up")) {
            val muscles = MuscleMap.profile(variant)!!.muscles
            for (muscle in listOf(Muscle.LATS, Muscle.RHOMBOIDS, Muscle.REAR_DELTS, Muscle.TRAPS, Muscle.ROTATOR_CUFF)) {
                assertEquals("$variant $muscle", pullUp[muscle] ?: 0.0, muscles[muscle] ?: 0.0, 1e-9)
            }
        }
        val lSit = MuscleMap.profile("L-sit Pull-up")!!.muscles
        assertEquals(0.5, lSit[Muscle.ABS] ?: 0.0, 1e-9)
        assertEquals(0.5, lSit[Muscle.HIP_FLEXORS] ?: 0.0, 1e-9)
        val week = listOf(PlannedPreset("Pull", "", 3, listOf(PlannedEntry("Archer Pull-up", 4, 6, null))))
        assertEquals(2.0, ProgramRules.weeklyVolume(week)[Muscle.REAR_DELTS] ?: 0.0, 1e-9)
    }
}
