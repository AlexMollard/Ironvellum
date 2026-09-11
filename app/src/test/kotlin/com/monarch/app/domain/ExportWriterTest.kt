package com.monarch.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportWriterTest {

    @Test
    fun `writes profile trainingMode presets sessions stats titles skills and healthDays`() {
        val json = ExportWriter.write(
            profile = PlayerProfile(name = "Pog Champ", totalXp = 1234, currentTitleId = "awakened"),
            trainingMode = TrainingMode.HYPERTROPHY,
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
            skills = listOf(SkillPractice(skillName = "handstand", practicedAtMs = 400, claimed = true, value = 30, weightKg = null)),
            healthDays = listOf(HealthDay(date = LocalDate.of(2026, 9, 10), steps = 9000, distanceKm = 6.5, activeKcal = 320, sleepMinutes = 460, restingHr = 58)),
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
        assertTrue(json.contains("\"trainingMode\":\"HYPERTROPHY\""))
        assertTrue(json.contains("\"skillName\":\"handstand\""))
        assertTrue(json.contains("\"claimed\":true"))
        assertTrue(json.contains("\"weightKg\":null"))
        assertTrue(json.contains("\"date\":\"2026-09-10\""))
        assertTrue(json.contains("\"restingHr\":58"))
        assertTrue(json.endsWith("}"))
    }

    @Test
    fun `trainingMode follows profile section`() {
        val json = ExportWriter.write(
            profile = PlayerProfile(name = "x", totalXp = 0),
            trainingMode = TrainingMode.STRENGTH,
            presets = emptyList(),
            sessions = emptyList(),
            stats = emptyList(),
            titles = emptyList(),
            skills = emptyList(),
            healthDays = emptyList(),
            exportedAtMs = 0,
        )
        assertTrue(json.contains("\"profile\":{\"name\":\"x\",\"totalXp\":0,\"currentTitleId\":null},\"trainingMode\":\"STRENGTH\""))
    }

    @Test
    fun `escapes quotes and newlines in strings`() {
        val json = ExportWriter.write(
            profile = PlayerProfile(name = "Mo\"narch\nThe First", totalXp = 0),
            trainingMode = TrainingMode.STRENGTH,
            presets = emptyList(),
            sessions = emptyList(),
            stats = emptyList(),
            titles = emptyList(),
            skills = emptyList(),
            healthDays = emptyList(),
            exportedAtMs = 0,
        )
        assertTrue(json.contains("Mo\\\"narch\\nThe First"))
        assertFalse(json.contains("Mo\"narch"))
    }

    @Test
    fun `null title renders as null`() {
        val json = ExportWriter.write(
            profile = PlayerProfile(name = "x", totalXp = 0, currentTitleId = null),
            trainingMode = TrainingMode.STRENGTH,
            presets = emptyList(),
            sessions = emptyList(),
            stats = emptyList(),
            titles = emptyList(),
            skills = emptyList(),
            healthDays = emptyList(),
            exportedAtMs = 0,
        )
        assertTrue(json.contains("\"currentTitleId\":null"))
    }

    @Test
    fun `empty collections render as empty arrays`() {
        val json = ExportWriter.write(PlayerProfile("x", 0), TrainingMode.STRENGTH, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0)
        assertEquals(
            "{\"formatVersion\":2,\"exportedAtMs\":0," +
                "\"profile\":{\"name\":\"x\",\"totalXp\":0,\"currentTitleId\":null}," +
                "\"trainingMode\":\"STRENGTH\"," +
                "\"presets\":[],\"sessions\":[],\"stats\":[],\"titles\":[],\"skills\":[],\"healthDays\":[]}",
            json,
        )
    }
}
