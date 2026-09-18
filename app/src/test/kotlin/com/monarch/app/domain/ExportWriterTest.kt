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

        assertTrue(json.startsWith("{\"formatVersion\":${ExportWriter.FORMAT_VERSION},"))
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
        assertTrue(
            json.contains(
                "\"profile\":{\"name\":\"x\",\"totalXp\":0,\"currentTitleId\":null," +
                    "\"heightCm\":null,\"sex\":null,\"inkStyle\":false},\"trainingMode\":\"STRENGTH\"",
            ),
        )
    }

    @Test
    fun `v5 sections render with keys omitted when not supplied`() {
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
        // A caller that does not supply the v5 sections (older signature shape)
        // must not emit half-present keys.
        assertFalse(json.contains("\"idle\""))
        assertFalse(json.contains("\"gacha\""))
        assertFalse(json.contains("\"crestFrames\""))
        assertFalse(json.contains("\"relics\""))
        assertTrue(json.contains("\"exercises\":[]"))
    }

    @Test
    fun `v5 sections render completely when supplied`() {
        val json = ExportWriter.write(
            profile = PlayerProfile(name = "x", totalXp = 5, inkStyle = true),
            trainingMode = TrainingMode.STRENGTH,
            presets = emptyList(),
            sessions = emptyList(),
            stats = emptyList(),
            titles = emptyList(),
            skills = emptyList(),
            healthDays = emptyList(),
            exercises = listOf(
                ExportWriter.ExerciseMeta(name = "Planche Press", muscleGroup = "PUSH", isWeighted = false, metric = "ATTEMPTS_GRADE", category = ""),
            ),
            heightCm = 181.0,
            sex = "MALE",
            idle = ExportWriter.IdleSnapshot(essence = 42, shadows = 2, relicMultiplier = 1.1, lastCollectedAtMs = 7),
            gacha = ExportWriter.GachaSnapshot(rolls = 1, equippedFrame = null),
            crestFrames = listOf(ExportWriter.CrestFrameSnapshot("ember", 10)),
            relics = listOf(ExportWriter.RelicSnapshot("Whetstone", 1.31, 20)),
            exportedAtMs = 0,
        )

        assertTrue(json.contains("\"heightCm\":181.0"))
        assertTrue(json.contains("\"sex\":\"MALE\""))
        assertTrue(json.contains("\"inkStyle\":true"))
        assertTrue(json.contains("\"exercises\":[{\"name\":\"Planche Press\",\"muscleGroup\":\"PUSH\",\"isWeighted\":false,\"metric\":\"ATTEMPTS_GRADE\",\"category\":\"\"}]"))
        assertTrue(json.contains("\"idle\":{\"essence\":42,\"shadows\":2,\"relicMultiplier\":1.1,\"lastCollectedAtMs\":7}"))
        assertTrue(json.contains("\"gacha\":{\"rolls\":1,\"equippedFrame\":null}"))
        assertTrue(json.contains("\"crestFrames\":[{\"frameId\":\"ember\",\"ownedAtMs\":10}]"))
        assertTrue(json.contains("\"relics\":[{\"name\":\"Whetstone\",\"multiplier\":1.31,\"drawnAtMs\":20}]"))
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
        val json = ExportWriter.write(
            profile = PlayerProfile("x", 0),
            trainingMode = TrainingMode.STRENGTH,
            presets = emptyList(),
            sessions = emptyList(),
            stats = emptyList(),
            titles = emptyList(),
            skills = emptyList(),
            healthDays = emptyList(),
            exportedAtMs = 0,
        )
        assertEquals(
            "{\"formatVersion\":${ExportWriter.FORMAT_VERSION},\"exportedAtMs\":0," +
                "\"profile\":{\"name\":\"x\",\"totalXp\":0,\"currentTitleId\":null," +
                "\"heightCm\":null,\"sex\":null,\"inkStyle\":false}," +
                "\"trainingMode\":\"STRENGTH\"," +
                "\"presets\":[],\"sessions\":[],\"stats\":[],\"titles\":[],\"skills\":[],\"healthDays\":[]," +
                "\"measurements\":[],\"exercises\":[]}",
            json,
        )
    }

    @Test
    fun `measurements survive a real write then read`() {
        // Regression guard: the writer once silently omitted a section and the
        // restore destroyed the data. This key must round-trip.
        // The archive carries no ids — restored rows are new (same as stats).
        val entries = listOf(
            MeasurementEntry(site = MeasurementSite.WAIST, valueCm = 82.5, takenAtMs = 100),
            MeasurementEntry(site = MeasurementSite.UPPER_ARM, valueCm = 36.0, takenAtMs = 200),
        )
        val json = ExportWriter.write(
            profile = PlayerProfile(),
            trainingMode = TrainingMode.STRENGTH,
            presets = emptyList(),
            sessions = emptyList(),
            stats = emptyList(),
            titles = emptyList(),
            skills = emptyList(),
            healthDays = emptyList(),
            measurements = entries,
            exportedAtMs = 1,
        )
        val restored = ExportReader.read(json).getOrThrow()

        assertEquals(entries, restored.measurements)
    }

    @Test
    fun `timed distance and grade set fields survive a real write then read`() {
        // Regression guard: the writer once omitted durationSec/distanceM/grade,
        // so a restore wiped the measurement of every non-lifting set.
        val sets = listOf(
            SessionSet(
                id = 9, exerciseId = 3, exerciseName = "Board Problem", setIndex = 0, reps = 1,
                weightKg = null, modifiers = "", done = true,
                durationSec = 95, distanceM = 1250.5, grade = "V7",
            ),
        )
        val json = ExportWriter.write(
            profile = PlayerProfile(),
            trainingMode = TrainingMode.STRENGTH,
            presets = emptyList(),
            sessions = listOf(
                WorkoutSession(id = 3, label = "S", startedAtMs = 1) to sets,
            ),
            stats = emptyList(),
            titles = emptyList(),
            skills = emptyList(),
            healthDays = emptyList(),
            exportedAtMs = 1,
        )
        val restored = ExportReader.read(json).getOrThrow().sessions.single().second.single()

        assertEquals(95, restored.durationSec)
        assertEquals(1250.5, restored.distanceM!!, 0.0)
        assertEquals("V7", restored.grade)
    }
}
