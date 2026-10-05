package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Warm-ups and supersets from a Hevy import, carried through every copy of a set. */
class WarmupSupersetTest {

    // A warm-up, then Bench and Row as superset 0, then Bench again on its own.
    private val hevy = """
        "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
        "Upper","22 Dec 2025, 08:00","22 Dec 2025, 09:00","","Bench Press (Barbell)",0,"",0,"warmup",40,10,,,
        "Upper","22 Dec 2025, 08:00","22 Dec 2025, 09:00","","Bench Press (Barbell)",0,"",1,"normal",80,5,,,
        "Upper","22 Dec 2025, 08:00","22 Dec 2025, 09:00","","Bent Over Row (Barbell)",0,"",0,"normal",60,8,,,
        "Upper","22 Dec 2025, 08:00","22 Dec 2025, 09:00","","Bench Press (Barbell)",,"",0,"normal",70,8,,,
    """.trimIndent()

    @Test
    fun hevyCarriesTheWarmupAndTheSupersetId() {
        val sets = CsvWorkoutReader.read(hevy).workouts.single().sets
        assertEquals(listOf("warmup", "normal", "normal", "normal"), sets.map { it.setType })
        assertEquals(listOf(0, 0, 0, null), sets.map { it.supersetGroup })
    }

    @Test
    fun aMovementDoneTwiceIsTwoBlocksWithSetNumbersThatNeverRepeat() {
        // Bench, Bench, Row, Bench: three blocks, Bench numbered 0, 1, 2 across two of them.
        val placements = CsvWorkoutReader.blockPlacements(listOf(1L, 1L, 2L, 1L))
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0, 2 to 2), placements)
        val keys = listOf(1L, 1L, 2L, 1L).zip(placements.map { it.second })
        assertEquals("the cloud key (movement, setIndex) must be unique", keys.size, keys.toSet().size)
    }

    @Test
    fun theDraftKeepsBothAndNeverFoldsAcrossSupersets() {
        val draft = TrialDraft.of(
            listOf(
                SessionSet(exerciseId = 1, exerciseName = "Bench Press", exercisePosition = 0, setIndex = 0, reps = 10, weightKg = 40.0, warmup = true, supersetGroup = 0),
                SessionSet(exerciseId = 1, exerciseName = "Bench Press", exercisePosition = 0, setIndex = 1, reps = 5, weightKg = 80.0, done = true, supersetGroup = 0),
                SessionSet(exerciseId = 2, exerciseName = "Row", exercisePosition = 1, setIndex = 0, reps = 8, weightKg = 60.0, done = true, supersetGroup = 0),
                SessionSet(exerciseId = 1, exerciseName = "Bench Press", exercisePosition = 2, setIndex = 2, reps = 8, weightKg = 70.0, done = true),
            ),
        )
        assertEquals(listOf(0, 0, null), draft.blocks.map { it.supersetGroup })
        assertEquals(listOf(true, false), draft.blocks[0].sets.map { it.warmup })
        assertTrue("Bench in and out of the superset are not one block", draft.hasRepeatedMovement)
        assertTrue("a metric change keeps the warm-up", TrialDraft.reshape(draft.blocks[0].sets[0], ExerciseMetric.HOLD).warmup)
        assertFalse("an added set is a working set", draft.addSet(0).blocks[0].sets.last().warmup)
    }

    @Test
    fun theArchiveRoundTripsBothAndLeavesPlainSetsUntouched() {
        val session = WorkoutSession(id = 1, label = "Upper", startedAtMs = 1_000, completedAtMs = 2_000)
        val sets = listOf(
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", setIndex = 0, reps = 10, warmup = true, supersetGroup = 3),
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", setIndex = 1, reps = 5, done = true),
        )
        val json = ExportWriter.write(
            profile = PlayerProfile(),
            trainingMode = TrainingMode.STRENGTH,
            presets = emptyList(),
            sessions = listOf(session to sets),
            stats = emptyList(),
            titles = emptyList(),
            skills = emptyList(),
            healthDays = emptyList(),
            exportedAtMs = 1,
        )
        assertEquals("only the warm-up writes the new keys", 1, Regex("\"warmup\"").findAll(json).count())

        val restored = ExportReader.read(json).getOrThrow().sessions.single().second
        assertEquals(listOf(true, false), restored.map { it.warmup })
        assertEquals(listOf(3, null), restored.map { it.supersetGroup })
    }

    @Test
    fun theCsvExportKeepsWarmupsAndReadsThemBack() {
        val zone = ZoneId.systemDefault()
        val session = WorkoutSession(id = 1, label = "Upper", startedAtMs = 1_800_000_000_000, completedAtMs = 1_800_003_600_000)
        val sets = listOf(
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", setIndex = 0, reps = 10, weightKg = 40.0, warmup = true),
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", setIndex = 1, reps = 5, weightKg = 80.0, done = true),
        )
        val parsed = CsvWorkoutReader.read(WorkoutCsvWriter.write(listOf(session to sets), zone))
        assertEquals(listOf("warmup", "normal"), parsed.workouts.single().sets.map { it.setType })
    }
}
