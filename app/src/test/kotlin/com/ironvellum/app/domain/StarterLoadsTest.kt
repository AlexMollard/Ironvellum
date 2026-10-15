package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A generated rite gives every loaded lift a first load, from bodyweight and level, and never touches logged ones. */
class StarterLoadsTest {

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    private val gym = Equipment(fullGym = true)
    private fun byName(name: String) = catalogue.first { it.name == name }
    private fun basis(level: VolumeLevel = VolumeLevel.LOW, kg: Double = 80.0, sex: Sex = Sex.MALE) =
        StrengthProfile(starter = StarterBasis(kg, sex, level))

    private fun fill(name: String, strength: StrengthProfile, reps: Int = 8) =
        ProgramGenerator.fillLoad(byName(name), strength, reps, 2, gym)

    @Test
    fun `no starter basis leaves a loaded lift unloaded as before`() {
        assertNull(fill("Barbell Row", StrengthProfile()))
    }

    @Test
    fun `every weighted gym lift gets a positive load that is a multiple of its step`() {
        for (level in VolumeLevel.entries) {
            for (e in catalogue.filter { it.isWeighted && it.metric == ExerciseMetric.REPS }) {
                val f = fill(e.name, basis(level))
                assertNotNull("${e.name} at $level has no load", f)
                assertTrue("${e.name} at $level is ${f!!.kg}", f.kg > 0.0)
                val step = Progression.weightStepKg(e.muscleGroup.name, e.name)
                val steps = f.kg / step
                assertEquals("${e.name} ${f.kg} is off its $step step", Math.rint(steps), steps, 1e-6)
            }
        }
    }

    @Test
    fun `a barbell lift never starts under the empty bar`() {
        val tiny = basis(VolumeLevel.LOW, kg = 40.0, sex = Sex.FEMALE)
        for (name in listOf("Back Squat", "Bench Press", "Barbell Row", "Overhead Press", "Deadlift")) {
            assertTrue(name, fill(name, tiny, 5)!!.kg >= StarterLoads.EMPTY_BAR_KG)
        }
    }

    @Test
    fun `loads scale with bodyweight and level and a barbell row is not the empty bar for an 80 kg novice`() {
        val novice = fill("Barbell Row", basis(VolumeLevel.LOW))!!.kg
        assertTrue("row $novice", novice > StarterLoads.EMPTY_BAR_KG)
        assertTrue(fill("Barbell Row", basis(VolumeLevel.HIGH))!!.kg > novice)
        assertTrue(fill("Barbell Row", basis(kg = 100.0))!!.kg > novice)
        assertTrue(fill("Barbell Row", basis(sex = Sex.FEMALE))!!.kg < novice)
        assertTrue(fill("Standing Calf Raise", basis())!!.kg > 0.0)
    }

    @Test
    fun `a logged lift wins over the starting estimate`() {
        val logged = ProgramRules.strengthProfile(listOf(LoggedLift("Barbell Row", 100.0, 5))).copy(
            starter = StarterBasis(80.0, Sex.MALE, VolumeLevel.LOW),
        )
        val withStarter = fill("Barbell Row", logged)!!
        val without = fill("Barbell Row", logged.copy(starter = null))!!
        assertEquals(without.kg, withStarter.kg, 1e-9)
        assertEquals(without.note, withStarter.note)
        // A related logged lift still estimates the unlogged one, rather than the starter.
        val relative = logged.copy(bestE1rmKg = mapOf("barbell row" to 150.0))
        assertTrue(fill("Dumbbell Row", relative)!!.note.orEmpty().startsWith("estimate from"))
    }

    @Test
    fun `bodyweight movements stay unloaded and the note says where the load came from`() {
        assertNull(fill("Pull-up", basis()))
        assertTrue(fill("Barbell Row", basis())!!.note.orEmpty().contains("bodyweight and level"))
    }

    @Test
    fun `a first-run week with no history loads every weighted entry`() {
        val plan = ProgramGenerator.week(
            ProgramRequest(TrainingFocus.GENERAL, VolumeLevel.LOW, gym, daysPerWeek = 3),
            catalogue, basis(),
        )
        val weighted = plan.presets.flatMap { it.entries }.filter { e -> byName(e.exerciseName).isWeighted }
        assertTrue(weighted.isNotEmpty())
        assertTrue(weighted.map { it.exerciseName }.filter { n -> weighted.first { it.exerciseName == n }.targetWeightKg == null }.toString(),
            weighted.all { it.targetWeightKg != null && it.targetWeightKg!! > 0.0 })
    }
}
