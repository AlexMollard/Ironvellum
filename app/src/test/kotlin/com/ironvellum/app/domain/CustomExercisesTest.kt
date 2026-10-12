package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomExercisesTest {

    @After
    fun forget() = CustomExercises.reset()

    private fun register(
        name: String,
        group: MuscleGroup = MuscleGroup.LEGS,
        category: String = "",
        needs: Set<CustomNeed> = emptySet(),
        shares: Map<Muscle, Double> = emptyMap(),
    ) = CustomExercises.sync(listOf(CustomExercises.Row(name, group, category, needs, shares)))

    // ---------------------------------------------------------------- derived weighted flag

    @Test
    fun `weighted follows the gear and is never asked`() {
        assertTrue(CustomExercises.isWeighted(setOf(CustomNeed.MACHINE)))
        assertTrue(CustomExercises.isWeighted(setOf(CustomNeed.BARBELL, CustomNeed.BENCH)))
        assertTrue(CustomExercises.isWeighted(setOf(CustomNeed.DUMBBELLS)))
        assertFalse(CustomExercises.isWeighted(setOf(CustomNeed.PULL_UP_BAR)))
        assertFalse(CustomExercises.isWeighted(setOf(CustomNeed.DIP_BARS, CustomNeed.RINGS, CustomNeed.PARALLETTES, CustomNeed.AB_WHEEL)))
        assertFalse("nothing", CustomExercises.isWeighted(emptySet()))
        assertFalse(CustomExercises.isWeighted(setOf(CustomNeed.NOTHING)))
    }

    // ---------------------------------------------------------------- muscle shares

    @Test
    fun `main is a full set and assist half, main first, main wins an overlap`() {
        val shares = CustomExercises.shares(
            main = listOf(Muscle.GLUTES, Muscle.HAMSTRINGS),
            assist = listOf(Muscle.LOWER_BACK, Muscle.GLUTES),
        )
        assertEquals(listOf(Muscle.GLUTES, Muscle.HAMSTRINGS, Muscle.LOWER_BACK), shares.keys.toList())
        assertEquals(1.0, shares.getValue(Muscle.GLUTES), 0.0)
        assertEquals(1.0, shares.getValue(Muscle.HAMSTRINGS), 0.0)
        assertEquals(0.5, shares.getValue(Muscle.LOWER_BACK), 0.0)
    }

    @Test
    fun `a custom movement is profiled for MuscleMap, coverage and the figure`() {
        val shares = CustomExercises.shares(listOf(Muscle.GLUTES, Muscle.HAMSTRINGS), listOf(Muscle.LOWER_BACK))
        register("Reverse hyper", MuscleGroup.LEGS, shares = shares)
        val profile = MuscleMap.profile("  reverse HYPER ")!!
        assertEquals(shares, profile.muscles)
        assertEquals("the first main muscle leads", Muscle.GLUTES, profile.muscles.keys.first())
        assertEquals(MovementPattern.SQUAT, profile.pattern)
        assertFalse(profile.noLeadByDesign)
        assertFalse("catalogue keys stay catalogue-only", "reverse hyper" in MuscleMap.keys)
    }

    @Test
    fun `no muscles chosen leaves the movement unprofiled, assist-only leads with nothing`() {
        register("Mystery press", MuscleGroup.PUSH)
        assertNull(MuscleMap.profile("Mystery press"))
        register("Wrist rolls", MuscleGroup.PULL, shares = CustomExercises.shares(emptyList(), listOf(Muscle.FOREARMS)))
        val profile = MuscleMap.profile("Wrist rolls")!!
        assertTrue(profile.noLeadByDesign)
        assertEquals(0.5, profile.muscles.getValue(Muscle.FOREARMS), 0.0)
    }

    @Test
    fun `an activity credits assist at most and is never gear gated`() {
        register("Shadow boxing", MuscleGroup.CARDIO, "Cardio", shares = CustomExercises.shares(listOf(Muscle.FRONT_DELTS), emptyList()))
        val profile = MuscleMap.profile("Shadow boxing")!!
        assertEquals(0.5, profile.muscles.getValue(Muscle.FRONT_DELTS), 0.0)
        assertTrue(profile.noLeadByDesign)
        assertTrue(GearRequirements.fits("Shadow boxing", false, "Cardio", Equipment.NOTHING))
    }

    @Test
    fun `a custom movement is never prescribed by the generator`() {
        register("Reverse hyper", shares = CustomExercises.shares(listOf(Muscle.GLUTES), emptyList()))
        val catalogue = listOf(Exercise(1, "Reverse hyper", MuscleGroup.LEGS, isWeighted = false, custom = true))
        assertTrue(ProgramGenerator.eligible(catalogue, Equipment.FULL_GYM, TrainingFocus.GENERAL).isEmpty())
    }

    // ---------------------------------------------------------------- gear

    @Test
    fun `gear rows gate a custom movement by what it needs`() {
        val barbellOnly = Equipment(fullGym = false, gear = setOf(Gear.BARBELL))
        val both = Equipment(fullGym = false, gear = setOf(Gear.BARBELL, Gear.BENCH))
        CustomExercises.sync(
            listOf(
                CustomExercises.Row("Hyper bench", MuscleGroup.LEGS, "", setOf(CustomNeed.BARBELL, CustomNeed.BENCH), emptyMap()),
                CustomExercises.Row("Sled drag", MuscleGroup.LEGS, "", setOf(CustomNeed.MACHINE), emptyMap()),
                CustomExercises.Row("Floor thing", MuscleGroup.CORE, "", emptySet(), emptyMap()),
            ),
        )
        assertFalse(GearRequirements.fits("Hyper bench", true, "", barbellOnly))
        assertTrue(GearRequirements.fits("Hyper bench", true, "", both))
        assertFalse("a machine or cable is a full gym", GearRequirements.fits("Sled drag", true, "", both))
        assertTrue(GearRequirements.fits("Sled drag", true, "", Equipment.FULL_GYM))
        assertTrue("nothing needed", GearRequirements.fits("Floor thing", false, "", Equipment.NOTHING))
        assertEquals(listOf(setOf(Gear.BARBELL, Gear.BENCH)), GearRequirements.needs("hyper bench"))
    }

    // ---------------------------------------------------------------- scoring default

    @Test
    fun `a custom movement is priced at the lowest tier and a machine at the sled band`() {
        val cheapestCatalogue = Seed.exercises.minOf { MovementDifficulty.intensity(it.name) }
        CustomExercises.sync(
            listOf(
                CustomExercises.Row("Reverse hyper", MuscleGroup.LEGS, "", setOf(CustomNeed.MACHINE), emptyMap()),
                CustomExercises.Row("Home curl", MuscleGroup.PULL, "", setOf(CustomNeed.DUMBBELLS), emptyMap()),
            ),
        )
        assertEquals(CustomExercises.TIER, MovementDifficulty.tier("Home curl"))
        assertEquals("never above the cheapest real movement", cheapestCatalogue, MovementDifficulty.intensity("Home curl"), 0.0)
        assertEquals(MovementDifficulty.SLED_LOAD, MovementDifficulty.loadFactor("Reverse hyper"), 0.0)
        assertEquals(MovementDifficulty.FREE_WEIGHT_LOAD, MovementDifficulty.loadFactor("Home curl"), 0.0)

        fun xp(name: String) = Xp.award(
            listOf(Xp.SetEffort(name, reps = 10, weightKg = 40.0, metric = ExerciseMetric.REPS)),
            bodyweightKg = 80.0,
        )
        assertTrue("cheaper per rep than an unclassified name used to be", xp("Home curl") < xp("Some Unlisted Lift"))
    }

    @Test
    fun `a custom row cannot borrow a catalogue name's price because the name is refused`() {
        assertNotNull(CustomExercises.nameProblem("one-arm pull-up", emptyList()))
        assertNotNull(CustomExercises.nameProblem("Back Squat", emptyList()))
    }

    // ---------------------------------------------------------------- duplicate names

    @Test
    fun `a name matching an existing exercise is refused with a pointer to it, case-insensitively`() {
        val existing = listOf("Back Squat", "Reverse hyper")
        assertEquals(
            "\"Back Squat\" is already in the Ledger. Pick it from the list instead.",
            CustomExercises.nameProblem("  back   SQUAT ", existing),
        )
        assertNotNull("one of your own", CustomExercises.nameProblem("REVERSE HYPER", existing))
        assertNull("the row being edited may keep its own name", CustomExercises.nameProblem("reverse hyper", existing, own = "Reverse hyper"))
        assertNull(CustomExercises.nameProblem("Zebra stretch", existing))
    }

    @Test
    fun `library, skill-tree and board names are refused even when the catalogue list lacks them`() {
        assertNotNull(CustomExercises.nameProblem(ExerciseLibrary.rows.first().name, emptyList()))
        assertNotNull(CustomExercises.nameProblem(Skills.ALL.first().name, emptyList()))
    }

    @Test
    fun `blank and long names are refused`() {
        assertNotNull(CustomExercises.nameProblem("   ", emptyList()))
        assertNotNull(CustomExercises.nameProblem("x".repeat(CustomExercises.MAX_NAME + 1), emptyList()))
        assertNull(CustomExercises.nameProblem("x".repeat(CustomExercises.MAX_NAME), emptyList()))
    }

    // ---------------------------------------------------------------- storage encoding

    @Test
    fun `needs and shares survive their stored form, order kept, nothing stored for nothing`() {
        val needs = setOf(CustomNeed.BENCH, CustomNeed.MACHINE, CustomNeed.NOTHING)
        assertEquals("MACHINE,BENCH", CustomExercises.encodeNeeds(needs))
        assertEquals(setOf(CustomNeed.MACHINE, CustomNeed.BENCH), CustomExercises.decodeNeeds("MACHINE,BENCH"))
        val shares = CustomExercises.shares(listOf(Muscle.LATS, Muscle.BICEPS), listOf(Muscle.REAR_DELTS))
        assertEquals(shares, CustomExercises.decodeShares(CustomExercises.encodeShares(shares)))
        assertEquals(shares.keys.toList(), CustomExercises.decodeShares(CustomExercises.encodeShares(shares)).keys.toList())
    }

    @Test
    fun `an unknown muscle, gear or share from a newer build drops out instead of failing`() {
        assertEquals(setOf(CustomNeed.RINGS), CustomExercises.decodeNeeds("RINGS,KETTLEBELL,"))
        assertEquals(
            mapOf(Muscle.LATS to 1.0),
            CustomExercises.decodeShares("LATS=1.0;WINGS=1.0;BICEPS=abc;TRICEPS=7;CALVES"),
        )
        assertTrue(CustomExercises.decodeShares("").isEmpty())
    }

    // ---------------------------------------------------------------- export round trip

    private fun write(vararg exercises: ExportWriter.ExerciseMeta) = ExportWriter.write(
        profile = PlayerProfile(),
        trainingMode = TrainingMode.STRENGTH,
        presets = emptyList(),
        sessions = emptyList(),
        stats = emptyList(),
        titles = emptyList(),
        skills = emptyList(),
        healthDays = emptyList(),
        exercises = exercises.toList(),
        exportedAtMs = 1L,
    )

    @Test
    fun `the export carries a custom exercise with its needs and muscles`() {
        val shares = CustomExercises.shares(listOf(Muscle.GLUTES, Muscle.HAMSTRINGS), listOf(Muscle.LOWER_BACK))
        val meta = ExportWriter.ExerciseMeta(
            name = "Reverse hyper", muscleGroup = "LEGS", isWeighted = true, metric = "REPS", category = "",
            custom = true,
            gear = CustomExercises.encodeNeeds(setOf(CustomNeed.MACHINE)),
            muscles = CustomExercises.encodeShares(shares),
        )
        val back = ExportReader.read(write(meta)).getOrThrow().exercises.single()
        assertEquals(meta, back)
        assertEquals(shares, CustomExercises.decodeShares(back.muscles))
        assertEquals(setOf(CustomNeed.MACHINE), CustomExercises.decodeNeeds(back.gear))
    }

    @Test
    fun `a catalogue exercise writes no custom keys and an older archive reads as not custom`() {
        val json = write(ExportWriter.ExerciseMeta("Pull-up", "PULL", false, "REPS", ""))
        assertFalse("\"custom\"" in json)
        val old = ExportReader.read(json).getOrThrow().exercises.single()
        assertFalse(old.custom)
        assertEquals("", old.gear)
        assertEquals("", old.muscles)
    }
}
