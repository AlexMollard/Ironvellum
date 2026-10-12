package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ranked suggestions: every surface that proposes a movement gets up to three
 * options, best first, whose first option is exactly what the generator
 * itself would have picked.
 */
class ProgramSuggestionsTest {

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    private val strength = ProgramRules.strengthProfile(
        listOf(
            LoggedLift("Bench Press", 75.0, 10),
            LoggedLift("Back Squat", 80.0, 10),
            LoggedLift("Barbell Row", 60.0, 10),
        ),
    )

    private val kits = listOf(
        Equipment.FULL_GYM,
        Equipment.NOTHING,
        Equipment(fullGym = false, gear = setOf(Gear.DUMBBELLS, Gear.BENCH), dumbbellMaxKg = 20.0),
    )

    private fun request(kit: Equipment, focus: TrainingFocus = TrainingFocus.MUSCLE) =
        ProgramRequest(focus, VolumeLevel.STANDARD, kit, 4)

    private fun suggest(request: ProgramRequest, slot: Slot, week: List<PlannedPreset> = emptyList()) =
        ProgramGenerator.suggest(request, slot, week, catalogue, strength)

    @Test
    fun `a pick offers at most three options best first`() {
        for (kit in kits) for (focus in TrainingFocus.entries) for (pattern in MovementPattern.entries) {
            val ranked = suggest(request(kit, focus), Slot.Pick(pattern = pattern))
            assertTrue("$kit $focus $pattern: ${ranked.options.size}", ranked.options.size <= 3)
            val scores = ranked.options.map { it.score }
            assertEquals("$kit $focus $pattern not best first", scores.sortedDescending(), scores)
            assertEquals(ranked.options.map { it.exercise.name }.distinct().size, ranked.options.size)
        }
    }

    @Test
    fun `option zero is the pick the generator makes`() {
        var compared = 0
        for (kit in kits) for (focus in TrainingFocus.entries) {
            val request = request(kit, focus)
            for (pattern in MovementPattern.entries) {
                val legacy = ProgramGenerator.rankPool(request, catalogue, strength, pattern = pattern)
                val ranked = suggest(request, Slot.Pick(pattern = pattern))
                assertEquals("$kit $focus $pattern", legacy.take(3).map { it.name }, ranked.options.map { it.exercise.name })
                compared += ranked.options.size
            }
            for (muscle in ProgramRules.TRACKED) {
                val legacy = ProgramGenerator.rankPool(request, catalogue, strength, muscle = muscle)
                val ranked = suggest(request, Slot.Pick(muscle = muscle))
                assertEquals("$kit $focus $muscle", legacy.take(3).map { it.name }, ranked.options.map { it.exercise.name })
            }
        }
        assertTrue("nothing was compared", compared > 0)
    }

    @Test
    fun `a generated week keeps picking what the first option names`() {
        // The week's lead slot is the first option of its own pick.
        val request = request(Equipment.FULL_GYM, TrainingFocus.STRENGTH).copy(split = TrainingSplit.forDays(4))
        val plan = ProgramGenerator.week(request, catalogue, strength)
        val lead = plan.presets.first().entries.first().exerciseName
        val profile = MuscleMap.profile(lead)!!
        val ranked = suggest(request, Slot.Pick(pattern = profile.pattern))
        assertEquals(lead, ranked.options.first().exercise.name)
    }

    @Test
    fun `reasons are at most two enum tags in a fixed order`() {
        for (kit in kits) for (muscle in ProgramRules.TRACKED) {
            for (candidate in suggest(request(kit), Slot.ShortMuscle(muscle)).options) {
                assertTrue("${candidate.exercise.name}: ${candidate.tags}", candidate.tags.size <= 2)
                assertEquals(candidate.tags.map { it.reason.ordinal }.sorted(), candidate.tags.map { it.reason.ordinal })
                assertEquals(candidate.tags.map { it.reason }.distinct(), candidate.tags.map { it.reason })
            }
        }
    }

    @Test
    fun `a movement that fills a short muscle says so`() {
        val ranked = suggest(request(Equipment.FULL_GYM), Slot.ShortMuscle(Muscle.CALVES))
        assertTrue(ranked.options.isNotEmpty())
        val best = ranked.options.first()
        assertTrue(best.tags.any { it.reason == Reason.FILLS_SHORT_MUSCLE && it.muscle == Muscle.CALVES })
        assertTrue((best.coverageDelta[Muscle.CALVES] ?: 0.0) > 0.0)
    }

    @Test
    fun `ties are broken by name whatever order the catalogue arrives in`() {
        for (kit in kits) {
            val request = request(kit)
            for (muscle in ProgramRules.TRACKED) {
                val forward = ProgramGenerator.suggest(request, Slot.ShortMuscle(muscle), emptyList(), catalogue, strength)
                val backward = ProgramGenerator.suggest(request, Slot.ShortMuscle(muscle), emptyList(), catalogue.reversed(), strength)
                assertEquals("$kit $muscle", forward, backward)
            }
        }
    }

    @Test
    fun `a compound only stand-in offers three by similarity`() {
        val request = request(Equipment.FULL_GYM).copy(compoundOnly = true)
        var offered = 0
        for (name in listOf("Lateral Raise", "Triceps Pushdown", "Barbell Curl", "Leg Extension", "Lying Leg Curl", "Cable Fly")) {
            val entry = PlannedEntry(name, 3, 12, null)
            val ranked = suggest(request, Slot.StandIn(entry), listOf(PlannedPreset("Day", "", 1, listOf(entry))))
            assertTrue("$name stand-ins: ${ranked.options.map { it.exercise.name }}", ranked.options.size <= 3)
            if (ranked.options.isEmpty()) continue
            offered++
            val profile = MuscleMap.profile(entry)!!
            val similarities = ranked.options.map { ProgramGenerator.similarity(profile, MuscleMap.profile(it.exercise.name)!!) }
            assertEquals(name, similarities.sortedDescending(), similarities)
            assertTrue(similarities.all { it >= ProgramGenerator.COMPOUND_ONLY_MIN_SIMILARITY })
            assertFalse(ranked.options.any { it.exercise.name == entry.exerciseName })
            ranked.options.forEach { option ->
                assertTrue(option.tags.size <= 2)
                assertTrue(option.tags.any { it.reason == Reason.STAND_IN_FOR && it.other == name })
            }
        }
        assertTrue("no isolation entry had a stand-in", offered > 0)
    }

    @Test
    fun `a replacement for a removed movement never offers it back`() {
        val request = request(Equipment.FULL_GYM)
        val removed = PlannedEntry("Lying Leg Curl", 3, 12, null)
        val week = listOf(PlannedPreset("Legs", "", 2, listOf(PlannedEntry("Back Squat", 3, 8, null), removed)))
        val ranked = suggest(request, Slot.ReplaceRemoved(removed), week)
        assertTrue(ranked.options.isNotEmpty())
        assertFalse(ranked.options.any { it.exercise.name == removed.exerciseName })
        assertFalse("offers a movement the session holds", ranked.options.any { it.exercise.name == "Back Squat" })
    }

    @Test
    fun `a swap offers stretch-biased twins of the movement`() {
        val request = request(Equipment.FULL_GYM)
        val entry = PlannedEntry("Lying Leg Curl", 3, 12, null)
        val ranked = suggest(request, Slot.Swap(entry), listOf(PlannedPreset("Legs", "", 2, listOf(entry))))
        assertTrue(ranked.options.isNotEmpty())
        ranked.options.forEach {
            assertTrue(MuscleMap.profile(it.exercise.name)!!.stretchBias)
            assertEquals(MuscleMap.profile(entry)!!.pattern, MuscleMap.profile(it.exercise.name)!!.pattern)
        }
    }

    @Test
    fun `fewer than three candidates returns them all`() {
        val request = request(Equipment.FULL_GYM)
        val pool = ProgramGenerator.prescribable(catalogue, request).map { it.name }
        val top = suggest(request, Slot.Pick(muscle = Muscle.BICEPS)).options.map { it.exercise.name }
        assertEquals(3, top.size)
        val keep = top.take(2)
        val two = suggest(request, Slot.Pick(muscle = Muscle.BICEPS, exclude = (pool - keep.toSet()).toSet()))
        assertEquals(keep, two.options.map { it.exercise.name })
        val none = suggest(request, Slot.Pick(muscle = Muscle.BICEPS, exclude = pool.toSet()))
        assertTrue(none.options.isEmpty())
    }
}
