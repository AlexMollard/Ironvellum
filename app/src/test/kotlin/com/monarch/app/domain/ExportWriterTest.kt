package com.monarch.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportWriterTest {

    @Test
    fun `writes profile presets sessions stats and titles`() {
        val json = ExportWriter.write(
            profile = PlayerProfile(name = "Pog Champ", totalXp = 1234, currentTitleId = "awakened"),
            presets = listOf(
                WorkoutPreset(
                    id = 7,
                    name = "Heavy Pull",
                    note = "Low reps, high weight",
                    entries = listOf(
                        PresetEntry(exerciseId = 1, exerciseName = "Weighted Pull-up", targetSets = 5, targetReps = 5, targetWeightKg = 20.0, position = 0),
                        PresetEntry(exerciseId = 2, exerciseName = "Barbell Row", targetSets = 4, targetReps = 8, targetWeightKg = null, position = 1),
                    ),
                ),
            ),
            sessions = listOf(
                WorkoutSession(id = 3, presetId = 7, label = "Heavy Pull", startedAtMs = 100, completedAtMs = 200, xpAwarded = 85) to
                    listOf(SessionSet(id = 9, exerciseId = 1, exerciseName = "Weighted Pull-up", setIndex = 0, reps = 5, weightKg = 20.0)),
                WorkoutSession(id = 4, presetId = null, label = "Freeform", startedAtMs = 300, completedAtMs = null) to emptyList(),
            ),
            stats = listOf(StatEntry(id = 1, takenAtMs = 50, weightKg = 70.0, heightCm = 175.0, bodyFatPct = 15.0)),
            titles = listOf(UnlockedTitle("awakened", 210)),
            exportedAtMs = 999,
        )

        assertTrue(json.startsWith("{\"formatVersion\":2,"))
        assertTrue(json.contains("\"modifiers\":\"\""))
        assertTrue(json.contains("\"name\":\"Pog Champ\""))
        assertTrue(json.contains("\"totalXp\":1234"))
        assertTrue(json.contains("\"name\":\"Heavy Pull\""))
        assertTrue(json.contains("\"targetWeightKg\":20.0"))
        assertTrue(json.contains("\"targetWeightKg\":null"))
        assertTrue(json.contains("\"completedAtMs\":null"))
        assertTrue(json.contains("\"bodyFatPct\":15.0"))
        assertTrue(json.contains("\"titleId\":\"awakened\""))
        assertTrue(json.endsWith("}"))
    }

    @Test
    fun `escapes quotes and newlines in strings`() {
        val json = ExportWriter.write(
            profile = PlayerProfile(name = "Mo\"narch\nThe First", totalXp = 0),
            presets = emptyList(),
            sessions = emptyList(),
            stats = emptyList(),
            titles = emptyList(),
            exportedAtMs = 0,
        )
        assertTrue(json.contains("Mo\\\"narch\\nThe First"))
        assertFalse(json.contains("Mo\"narch"))
    }

    @Test
    fun `null title renders as null`() {
        val json = ExportWriter.write(
            profile = PlayerProfile(name = "x", totalXp = 0, currentTitleId = null),
            presets = emptyList(),
            sessions = emptyList(),
            stats = emptyList(),
            titles = emptyList(),
            exportedAtMs = 0,
        )
        assertTrue(json.contains("\"currentTitleId\":null"))
    }

    @Test
    fun `empty collections render as empty arrays`() {
        val json = ExportWriter.write(PlayerProfile("x", 0), emptyList(), emptyList(), emptyList(), emptyList(), 0)
        assertEquals(
            "{\"formatVersion\":2,\"exportedAtMs\":0," +
                "\"profile\":{\"name\":\"x\",\"totalXp\":0,\"currentTitleId\":null}," +
                "\"presets\":[],\"sessions\":[],\"stats\":[],\"titles\":[]}",
            json,
        )
    }
}
