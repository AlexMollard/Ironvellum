package com.ironvellum.app.domain

import com.ironvellum.app.ui.dashboard.plannedRows
import com.ironvellum.app.ui.program.toPlanned
import com.ironvellum.app.ui.train.EditorEntry
import com.ironvellum.app.ui.train.EditorUi
import com.ironvellum.app.ui.train.canSave
import com.ironvellum.app.ui.train.editorChanged
import com.ironvellum.app.ui.train.warmupsFirst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A warm-up exercise is a whole movement of a rite, done before the first working set. Whatever counts the rite's
 * work (the plan the Forge and the coverage maps read, the exercise and set counts) leaves it out; the places it
 * shows (Today's rows, the archive) say so.
 */
class WarmupExerciseTest {

    private val drill = PresetEntry(exerciseId = 9, exerciseName = "Knee-to-Wall Dorsiflexion", targetSets = 2, targetReps = 10, position = 0, warmup = true)
    private val squat = PresetEntry(exerciseId = 1, exerciseName = "Back Squat", targetSets = 4, targetReps = 5, position = 1)
    private val bench = PresetEntry(exerciseId = 2, exerciseName = "Bench Press", targetSets = 3, targetReps = 8, position = 2)
    private val rite = WorkoutPreset(id = 1, name = "Legs", entries = listOf(drill, squat, bench))

    @Test
    fun theWorkingEntriesLeaveTheWarmupsOut() {
        assertEquals(listOf(squat, bench), rite.working)
        assertEquals(listOf(drill), rite.warmups)
    }

    @Test
    fun thePlanTheForgeAndTheCoverageMapsReadIsTheWorkedExercisesOnly() {
        val planned = rite.toPlanned()
        assertEquals(listOf("Back Squat", "Bench Press"), planned.entries.map { it.exerciseName })
        // The drill adds no set to any muscle, however many it has.
        val withDrill = ProgramRules.weeklyVolume(listOf(planned))
        val without = ProgramRules.weeklyVolume(listOf(WorkoutPreset(id = 2, name = "Legs", entries = listOf(squat, bench)).toPlanned()))
        assertEquals(without, withDrill)
        assertEquals("the plan's set total is the worked sets", 7, planned.entries.sumOf { it.sets })
    }

    @Test
    fun todaysRowsListTheWarmupsFirstAndMarkedAsSuch() {
        val rows = plannedRows(listOf(squat, drill, bench))
        assertEquals(listOf("Knee-to-Wall Dorsiflexion", "Back Squat", "Bench Press"), rows.map { it.name })
        assertEquals(listOf(true, false, false), rows.map { it.warmup })
        assertEquals("Warm-up", rows.first().value)
    }

    @Test
    fun aWarmupLineSaysTheTargetsTheMovementUses() {
        fun item(metric: ExerciseMetric, sets: Int, reps: Int, kg: Double? = null) =
            WarmupStep.Item(1, "x", metric, sets, reps, kg)
        val kg = { v: Double -> "${v.toInt()} kg" }
        assertEquals("2 × 10", item(ExerciseMetric.REPS, 2, 10).line(kg))
        assertEquals("3 × 8 · 20 kg", item(ExerciseMetric.REPS, 3, 8, 20.0).line(kg))
        assertEquals("2 × 30 s", item(ExerciseMetric.HOLD, 2, 30).line(kg))
        assertEquals("1 × 5 min", item(ExerciseMetric.DURATION, 1, 5).line(kg))
        assertEquals("a zero load is no load", "2 × 10", item(ExerciseMetric.REPS, 2, 10, 0.0).line(kg))
    }

    @Test
    fun theArchiveWritesTheFlagOnlyForAWarmupAndReadsItBack() {
        fun archive(vararg entries: PresetEntry) = ExportWriter.write(
            profile = PlayerProfile(),
            trainingMode = TrainingMode.STRENGTH,
            presets = listOf(WorkoutPreset(id = 1, name = "Legs", entries = entries.toList())),
            sessions = emptyList(),
            stats = emptyList(),
            titles = emptyList(),
            skills = emptyList(),
            healthDays = emptyList(),
            exportedAtMs = 1,
        )
        val json = archive(drill, squat)
        assertEquals("only the warm-up writes the key", 1, Regex("\"warmup\"").findAll(json).count())
        assertEquals(listOf(true, false), ExportReader.read(json).getOrThrow().presets.single().entries.map { it.warmup })
        assertFalse("a rite with no warm-up is byte-identical to before", archive(squat, bench).contains("warmup"))
        // An archive from before the flag (no key) reads every entry as worked.
        val old = json.replace(",\"warmup\":true", "").replace(Regex("\"formatVersion\":\\d+"), "\"formatVersion\":12")
        assertTrue(ExportReader.read(old).getOrThrow().presets.single().entries.none { it.warmup })
    }

    @Test
    fun theEditorKeepsWarmupsAheadAndNeedsOneWorkedExercise() {
        fun entry(name: String, warmup: Boolean) = EditorEntry(1, name, sets = "2", reps = "10", weight = "", modifiers = "", warmup = warmup)
        assertTrue(listOf(entry("a", true), entry("b", true), entry("c", false)).warmupsFirst())
        assertTrue(emptyList<EditorEntry>().warmupsFirst())
        assertFalse(listOf(entry("c", false), entry("a", true)).warmupsFirst())

        val onlyWarmups = EditorUi(name = "Legs", entries = listOf(entry("a", true)))
        assertFalse("warm-ups alone leave a trial nothing to log", onlyWarmups.canSave())
        assertTrue(onlyWarmups.copy(entries = onlyWarmups.entries + entry("b", false)).canSave())
        // Marking an entry is an edit the discard prompt notices.
        assertTrue(editorChanged(onlyWarmups, onlyWarmups.copy(entries = listOf(entry("a", false)))))
    }
}
