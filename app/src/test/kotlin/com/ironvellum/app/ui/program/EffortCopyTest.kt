package com.ironvellum.app.ui.program

import com.ironvellum.app.data.Seed
import com.ironvellum.app.domain.EffortCue
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.StrengthProfile
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.TrainingSplit
import com.ironvellum.app.domain.VolumeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Effort-first words: a set is a hard set taken near failure, and reps only guide the load. Every screen that
 * prescribes work says it through these, and the Forge's pickers keep the split and the day count in step.
 */
class EffortCopyTest {

    // Weekday and month names follow the default locale: pin English so the expected text is the same on any machine.
    private val savedLocale = java.util.Locale.getDefault()

    @org.junit.Before
    fun english() = java.util.Locale.setDefault(java.util.Locale.ENGLISH)

    @org.junit.After
    fun restoreLocale() = java.util.Locale.setDefault(savedLocale)

    private val compound = PlannedEntry(
        "Bench Press", sets = 3, reps = 8, targetWeightKg = 60.0,
        setsMin = 2, setsMax = 3, effort = EffortCue.RIR_0_2, repLow = 6, repHigh = 10,
    )
    private val isolation = PlannedEntry(
        "Lateral Raise", sets = 2, reps = 12, targetWeightKg = 8.0,
        setsMin = 2, setsMax = 2, effort = EffortCue.TO_FAILURE, repLow = 10, repHigh = 15,
    )

    @Test
    fun `a movement line says the hard sets and how near failure they are taken`() {
        assertEquals("3 hard sets · stop 0–2 reps short of failure", movementLine(compound))
        assertEquals("2 hard sets · to failure", movementLine(isolation))
        assertEquals("1 hard set · to failure", movementLine(isolation.copy(sets = 1)))
    }

    @Test
    fun `the right-hand figure is the rep guide and the load, or the reps alone`() {
        assertEquals("~6–10 reps · 60 kg", movementDetail(compound, "60 kg"))
        assertEquals("~6–10 reps", movementDetail(compound, null))
        assertEquals("~8 reps", repGuide(compound.copy(repLow = 8, repHigh = 8)))
    }

    @Test
    fun `the editor explains the effort cue and that reps only steer the load`() {
        assertEquals("Stop 0–2 reps short of failure. Reps are a guide to load.", effortSentence(EffortCue.RIR_0_2))
        assertEquals("Take the last set to failure. Reps are a guide to load.", effortSentence(EffortCue.TO_FAILURE))
        assertEquals("Suggested 2–3. Set it to whatever you will do.", suggestedSets(compound))
        assertEquals("Suggested 2. Set it to whatever you will do.", suggestedSets(isolation))
    }

    @Test
    fun `a day reads its movements and its hard sets`() {
        val day = PlannedPreset("Upper A", "", 1, listOf(compound, isolation))
        assertEquals(5, dayHardSets(day))
        assertEquals("Upper A · 2 movements · 5 hard sets", dayHeadline(day))
        assertEquals("1 movement", movementsText(1))
    }

    @Test
    fun `a dumbbell at the lifter's cap says what to do instead`() {
        val dumbbells = Equipment(fullGym = false, gear = setOf(Gear.DUMBBELLS), dumbbellMaxKg = 20.0, dumbbellPair = false)
        val press = PlannedEntry("Dumbbell Shoulder Press", 3, 10, 20.0, repLow = 8, repHigh = 12)
        assertEquals(
            "20 kg is the heaviest dumbbell in your armoury. When 12 reps get easy, add reps or a set instead.",
            dumbbellNote(press, dumbbells, "20 kg"),
        )
        // Below the cap, a barbell lift or a full gym: nothing to say.
        assertNull(dumbbellNote(press.copy(targetWeightKg = 17.5), dumbbells, "20 kg"))
        assertNull(dumbbellNote(compound, dumbbells, "20 kg"))
        assertNull(dumbbellNote(press, Equipment.FULL_GYM, "20 kg"))
        assertNull(dumbbellNote(press, null, "20 kg"))
    }

    @Test
    fun `the starting sets caption names the windows the level really uses`() {
        assertTrue(startingSetsCaption(VolumeLevel.LOW).startsWith("Big lifts start at 2–3 hard sets, single-joint work at 2."))
        assertTrue(startingSetsCaption(VolumeLevel.STANDARD).contains("2–4 hard sets, single-joint work at 2–3"))
        assertTrue(startingSetsCaption(VolumeLevel.HIGH).contains("3–5 hard sets, single-joint work at 3–4"))
    }

    // ---------------------------------------------------------------- the Forge's pickers

    @Test
    fun `picking a split keeps the days when it fits and takes its first otherwise`() {
        assertEquals(3, daysAfterSplit(TrainingSplit.FULL_BODY, 3))
        assertEquals(4, daysAfterSplit(TrainingSplit.UPPER_LOWER, 3))
        assertEquals(6, daysAfterSplit(TrainingSplit.PUSH_PULL_LEGS, 6))
        assertEquals(5, daysAfterSplit(TrainingSplit.UPPER_LOWER_PPL, 2))
    }

    @Test
    fun `picking a day count keeps the split when it fits and takes the first that does`() {
        assertEquals(TrainingSplit.FULL_BODY, splitAfterDays(TrainingSplit.FULL_BODY, 3))
        assertEquals(TrainingSplit.UPPER_LOWER, splitAfterDays(TrainingSplit.FULL_BODY, 4))
        assertEquals(TrainingSplit.UPPER_LOWER_PPL, splitAfterDays(TrainingSplit.UPPER_LOWER, 5))
        assertEquals(TrainingSplit.PUSH_PULL_LEGS, splitAfterDays(TrainingSplit.FULL_BODY, 6))
        // Every pair the pickers can reach is one the generator offers.
        for (split in FORGE_SPLITS) for (days in FORGE_DAYS) {
            val s = splitAfterDays(split, days)
            assertTrue("$split/$days -> $s", days in s.dayOptions)
        }
    }

    @Test
    fun `the Forge offers every split and the days they run`() {
        assertEquals(TrainingSplit.entries.toSet(), FORGE_SPLITS.toSet())
        assertEquals(listOf(2, 3, 4, 5, 6), FORGE_DAYS)
        assertEquals("4 days a week · Upper / lower", cycleSubtitle(4, TrainingSplit.UPPER_LOWER))
    }

    @Test
    fun `days read short and join into a sentence`() {
        assertEquals("Mon", dayShort(1))
        assertEquals("—", dayShort(null))
        assertEquals("Mon, Wed and Fri", joinDays(listOf(5, 1, 3)))
        assertEquals("Tue", joinDays(listOf(2)))
    }

    // ---------------------------------------------------------------- a picked movement

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    @Test
    fun `a picked movement is prescribed the way the generator would`() {
        val bench = catalogue.first { it.name == "Bench Press" }
        val entry = newPlannedEntry(bench, sets = null, VolumeLevel.STANDARD, TrainingFocus.MUSCLE, Equipment.FULL_GYM, StrengthProfile())
        // The starting sets for the level, the goal's rep window and the effort cue are filled in.
        assertTrue(entry.sets in 2..4)
        assertEquals(EffortCue.RIR_0_2, entry.effort)
        assertTrue(entry.repLow <= entry.reps && entry.reps <= entry.repHigh)
        assertEquals(entry.sets.coerceIn(entry.setsMin, entry.setsMax), entry.sets)

        // A swap keeps the sets the lifter chose, inside the sanity range.
        assertEquals(5, newPlannedEntry(bench, 5, VolumeLevel.LOW, TrainingFocus.MUSCLE, Equipment.FULL_GYM, StrengthProfile()).sets)
        assertEquals(10, newPlannedEntry(bench, 40, VolumeLevel.LOW, TrainingFocus.MUSCLE, Equipment.FULL_GYM, StrengthProfile()).sets)
        assertNotNull(entry.exerciseName)
    }
}
