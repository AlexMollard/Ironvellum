package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** A rite entry's set scheme through the archive: written only when it is not Straight, read back whole, absent in v11. */
class RiteSchemeArchiveTest {

    private fun archiveOf(vararg entries: PresetEntry): String = ExportWriter.write(
        profile = PlayerProfile(),
        trainingMode = TrainingMode.STRENGTH,
        presets = listOf(WorkoutPreset(id = 1, name = "Pull day", entries = entries.toList())),
        sessions = emptyList(),
        stats = emptyList(),
        titles = emptyList(),
        skills = emptyList(),
        healthDays = emptyList(),
        exportedAtMs = 1,
    )

    /** The owner's own plan: a weighted pull-up, top set +22 kg x 2-4, then three back-offs at a fixed +15 kg x 4-5. */
    private val owners = SetScheme(
        type = SchemeType.TOP_BACKOFF, topReps = 2..4, backoffSets = 3, backoffFixedKg = 15.0, backoffReps = 4..5,
    )

    @Test
    fun theOwnersPlanRoundTripsAndStraightEntriesWriteNothingNew() {
        val json = archiveOf(
            PresetEntry(exerciseId = 1, exerciseName = "Pull-up", targetSets = 4, targetReps = 2, targetWeightKg = 22.0, modifiers = "weighted", scheme = owners),
            PresetEntry(exerciseId = 2, exerciseName = "Chin-up", targetSets = 3, targetReps = 8, position = 1),
        )
        assertEquals("only the lift with a scheme writes the key", 1, Regex("\"setScheme\"").findAll(json).count())
        val entries = ExportReader.read(json).getOrThrow().presets.single().entries
        assertEquals(owners, entries[0].scheme)
        assertEquals(SetScheme(), entries[1].scheme)
        assertEquals(22.0, entries[0].targetWeightKg!!, 0.0)
        assertFalse(
            "a rite with no scheme is byte-identical to one written before",
            archiveOf(PresetEntry(exerciseId = 1, exerciseName = "Chin-up", targetSets = 3, targetReps = 8)).contains("setScheme"),
        )
    }

    @Test
    fun everySchemeSurvivesTheArchive() {
        val schemes = listOf(
            SetScheme(type = SchemeType.TOP_BACKOFF, backoffPercent = 15, warmupRamp = true),
            SetScheme(type = SchemeType.DROPS, dropSets = 2, dropCount = 2, dropPercent = 25),
            SetScheme(type = SchemeType.CUSTOM, custom = listOf(SetKind.WORKING, SetKind.TOP, SetKind.BACKOFF, SetKind.FAILURE)),
            SetScheme(warmupRamp = true),
        )
        val json = archiveOf(*schemes.mapIndexed { i, s ->
            PresetEntry(exerciseId = i + 1L, exerciseName = "Lift $i", targetSets = 4, targetReps = 5, position = i, scheme = s)
        }.toTypedArray())
        assertEquals(schemes, ExportReader.read(json).getOrThrow().presets.single().entries.map { it.scheme })
    }

    @Test
    fun aSetOfKindTopOrBackoffRoundTripsAsItself() {
        val sets = listOf(SetKind.TOP, SetKind.BACKOFF).mapIndexed { i, kind ->
            SessionSet(exerciseId = 1, exerciseName = "Pull-up", setIndex = i, reps = 4, weightKg = 20.0, done = true, kind = kind)
        }
        val json = ExportWriter.write(
            profile = PlayerProfile(),
            trainingMode = TrainingMode.STRENGTH,
            presets = emptyList(),
            sessions = listOf(WorkoutSession(id = 1, label = "Pull", startedAtMs = 1, completedAtMs = 2) to sets),
            stats = emptyList(),
            titles = emptyList(),
            skills = emptyList(),
            healthDays = emptyList(),
            exportedAtMs = 1,
        )
        val back = ExportReader.read(json).getOrThrow().sessions.single().second
        assertEquals(listOf(SetKind.TOP, SetKind.BACKOFF), back.map { it.kind })
    }
}
