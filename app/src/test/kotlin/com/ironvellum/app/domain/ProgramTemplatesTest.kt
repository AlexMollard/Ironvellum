package com.ironvellum.app.domain

import com.ironvellum.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hand-authored templates against the REAL seed catalogue: entries must
 * exist, MUSCLE weekly volume must sit inside the authored level's range for
 * every tracked muscle, every volume level must stay finishable and name
 * what it leaves short, STRENGTH templates must practise the big lifts often
 * enough, and adaptation must respect the equipment.
 */
class ProgramTemplatesTest {

    private val catalogue: List<Exercise> = Seed.exercises.map {
        Exercise(
            name = it.name,
            muscleGroup = MuscleGroup.valueOf(it.muscleGroup),
            isWeighted = it.isWeighted,
            metric = ExerciseMetric.valueOf(it.metric),
            category = it.category,
        )
    }

    private val emptyStrength = StrengthProfile(emptyMap())

    private fun byName(name: String): Exercise = catalogue.first { it.name == name }

    private fun isMachine(name: String): Boolean =
        name.trim().lowercase().startsWith("assisted") ||
            MovementDifficulty.loadFactor(name) < MovementDifficulty.FREE_WEIGHT_LOAD

    private fun volumeOf(plan: RoutinePlan): Map<Muscle, Double> =
        ProgramRules.weeklyVolume(plan.presets)

    @Test
    fun `every template at every volume stays finishable and names any muscle it leaves short`() {
        for (template in ProgramTemplates.ALL) for (volume in VolumeLevel.entries) {
            val plan = ProgramTemplates.build(template, volume, Equipment.FULL_GYM, catalogue, emptyStrength)
            plan.presets.forEach { built ->
                val seconds = ProgramRules.sessionSeconds(built.entries, template.focus)
                assertTrue(
                    "${template.id} at $volume: ${built.name} runs ${seconds / 60} min",
                    seconds <= ProgramRules.SESSION_BUDGET_SECONDS,
                )
            }
            if (template.focus != TrainingFocus.MUSCLE) continue
            val floor = ProgramRules.weeklySetTarget(volume, TrainingFocus.MUSCLE).start
            val volumeMap = volumeOf(plan)
            val note = plan.presets.first().note
            ProgramRules.TRACKED.filter { (volumeMap[it] ?: 0.0) < floor }.forEach { muscle ->
                assertTrue(
                    "${template.id} at $volume: ${muscle.label} at ${volumeMap[muscle]} not in note: $note",
                    muscle.label.lowercase() in note,
                )
            }
        }
    }

    @Test
    fun `the chosen volume moves a muscle template's weekly dose`() {
        for (template in ProgramTemplates.ALL.filter { it.focus == TrainingFocus.MUSCLE }) {
            val total = { volume: VolumeLevel ->
                ProgramTemplates.build(template, volume, Equipment.FULL_GYM, catalogue, emptyStrength)
                    .presets.sumOf { day -> day.entries.sumOf { it.sets } }
            }
            val low = total(VolumeLevel.LOW)
            val high = total(VolumeLevel.HIGH)
            assertTrue("${template.id}: low $low sets not below high $high", low < high)
        }
    }

    @Test
    fun `every template entry exists in the catalogue`() {
        for (template in ProgramTemplates.ALL) for (day in template.days) for (entry in day.entries) {
            val match = catalogue.firstOrNull { it.name.equals(entry.exerciseName, ignoreCase = true) }
            assertNotNull(
                "${template.id} prescribes unknown movement ${entry.exerciseName}",
                match,
            )
        }
    }

    @Test
    fun `scheduled days never collide inside a template`() {
        for (template in ProgramTemplates.ALL) {
            val days = template.days.mapNotNull { it.scheduledDay }
            assertEquals("duplicate day in ${template.id}", days.size, days.distinct().size)
            days.forEach { assertTrue("day $it outside ISO week", it in 1..7) }
        }
    }

    @Test
    fun `muscle templates put every tracked muscle inside its tier range`() {
        for (template in ProgramTemplates.ALL.filter { it.focus == TrainingFocus.MUSCLE }) {
            val plan = ProgramTemplates.build(template, template.authoredVolume, Equipment.FULL_GYM, catalogue, emptyStrength)
            val range = ProgramRules.weeklySetTarget(template.authoredVolume, TrainingFocus.MUSCLE)
            val volume = volumeOf(plan)
            for (muscle in ProgramRules.TRACKED) {
                val sets = volume[muscle] ?: 0.0
                assertTrue(
                    "${template.id}: $muscle at $sets fractional sets/week, outside ${range}",
                    sets >= range.start && sets <= range.endInclusive,
                )
            }
        }
    }

    @Test
    fun `strength templates practise squat and bench twice and deadlift once`() {
        for (template in ProgramTemplates.ALL.filter { it.focus == TrainingFocus.STRENGTH }) {
            val daysWith = { name: String ->
                template.days.count { day -> day.entries.any { it.exerciseName == name } }
            }
            assertTrue(
                "${template.id}: squat practised ${daysWith("Back Squat")}x/week, need >=2",
                daysWith("Back Squat") >= 2,
            )
            assertTrue(
                "${template.id}: bench practised ${daysWith("Bench Press")}x/week, need >=2",
                daysWith("Bench Press") >= 2,
            )
            assertTrue(
                "${template.id}: deadlift practised ${daysWith("Deadlift")}x/week, need >=1",
                daysWith("Deadlift") >= 1,
            )
        }
    }

    @Test
    fun `home weights adaptation never prescribes a machine`() {
        for (template in ProgramTemplates.ALL) {
            val plan = ProgramTemplates.build(template, template.authoredVolume, Equipment(fullGym = false, gear = Gear.entries.toSet()), catalogue, emptyStrength)
            assertTrue("plan empty for ${template.id}", plan.presets.isNotEmpty())
            plan.presets.flatMap { it.entries }.forEach { entry ->
                assertFalse(
                    "${template.id}: machine ${entry.exerciseName} in a HOME_WEIGHTS build",
                    isMachine(entry.exerciseName),
                )
            }
        }
    }

    @Test
    fun `every adapted reason cites only registered papers`() {
        // A light single dumbbell forces the cap swap, no gear the substitution.
        val kits = listOf(
            Equipment.NOTHING,
            Equipment(fullGym = false, gear = setOf(Gear.PULL_UP_BAR, Gear.DUMBBELLS), dumbbellMaxKg = 8.0, dumbbellPair = false),
            Equipment.FULL_GYM,
        )
        assertCitationsResolve(
            ProgramTemplates.ALL.flatMap { template ->
                kits.map { ProgramTemplates.build(template, template.authoredVolume, it, catalogue, emptyStrength) }
            },
        )
    }

    @Test
    fun `bodyweight adaptation never prescribes a loaded movement`() {
        for (template in ProgramTemplates.ALL) {
            val plan = ProgramTemplates.build(template, template.authoredVolume, Equipment.NOTHING, catalogue, emptyStrength)
            assertTrue("plan empty for ${template.id}", plan.presets.isNotEmpty())
            plan.presets.flatMap { it.entries }.forEach { entry ->
                assertFalse(
                    "${template.id}: loaded ${entry.exerciseName} in a BODYWEIGHT build",
                    byName(entry.exerciseName).isWeighted,
                )
            }
        }
    }

    @Test
    fun `adaptation substitutes within the same movement pattern`() {
        val template = ProgramTemplates.ALL.first { it.id == "upper_lower_muscle" }
        val originalPatterns = template.days.flatMap { day ->
            day.entries.mapNotNull { MuscleMap.profile(it.exerciseName)?.pattern }
        }.toSet()
        val plan = ProgramTemplates.build(template, template.authoredVolume, Equipment.NOTHING, catalogue, emptyStrength)
        plan.presets.flatMap { it.entries }.forEach { entry ->
            val pattern = MuscleMap.profile(entry.exerciseName)?.pattern
            assertTrue(
                "adapted ${entry.exerciseName} left the original pattern set",
                pattern == null || pattern in originalPatterns,
            )
            if (MuscleMap.profile(entry.exerciseName) != null) {
                assertTrue("adapted entry ${entry.exerciseName} has no why", entry.why.isNotBlank())
            }
        }
    }

    @Test
    fun `build fills loads from the strength profile and labels them`() {
        // Bench Press 75 kg x 10 -> e1RM 100 kg (Epley, rep term capped at 12).
        val strength = ProgramRules.strengthProfile(listOf(LoggedLift("Bench Press", 75.0, 10)))
        val template = ProgramTemplates.ALL.first { it.id == "upper_lower_muscle" }
        val plan = ProgramTemplates.build(template, template.authoredVolume, Equipment.FULL_GYM, catalogue, strength)
        val bench = plan.presets.flatMap { it.entries }.first { it.exerciseName == "Bench Press" }
        assertNotNull("bench press left unloaded with an e1RM on file", bench.targetWeightKg)
        assertTrue(bench.loadNote!!.contains("e1RM"))
    }

    @Test
    fun `strength template mains argue specificity and days carry rest guidance`() {
        val template = ProgramTemplates.ALL.first { it.id == "full_body_strength" }
        val plan = ProgramTemplates.build(template, template.authoredVolume, Equipment.FULL_GYM, catalogue, emptyStrength)
        plan.presets.flatMap { it.entries }.forEach { entry ->
            val isMain = entry.exerciseName in setOf("Back Squat", "Bench Press", "Deadlift", "Overhead Press")
            if (isMain) {
                assertTrue(
                    "${entry.exerciseName} why misses specificity reasoning: ${entry.why}",
                    entry.why.contains("Buckner 2017"),
                )
                assertTrue(
                    "hypertrophy reasoning in a strength main lift: ${entry.why}",
                    !entry.why.contains("grow best"),
                )
            }
            assertFalse(
                "day note repeats the summary instead of rest guidance",
                entry.why.contains("linear progression"),
            )
        }
        // Goal- and tier-appropriate note, not the summary, on every day.
        plan.presets.forEach { day ->
            assertTrue(day.note.contains("Rest 3-5 min"))
            assertTrue(day.note.contains("reps in reserve"))
        }
    }
}
