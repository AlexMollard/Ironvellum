package com.monarch.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportReaderTest {

    private fun fullArchiveJson(): String = ExportWriter.write(
        profile = PlayerProfile(
            name = "Mo\"narch \\ The Türkçe Æon",
            totalXp = 123_456_789_012,
            currentTitleId = "awakened",
        ),
        trainingMode = TrainingMode.HYPERTROPHY,
        presets = listOf(
            WorkoutPreset(
                id = 7,
                name = "Heavy Pull",
                note = "note with \"quote\" and \\ slash",
                scheduledDay = 3,
                entries = listOf(
                    PresetEntry(exerciseId = 1, exerciseName = "Weighted Pull-up", targetSets = 5, targetReps = 5, targetWeightKg = 20.0, modifiers = "deficit", position = 0),
                    PresetEntry(exerciseId = 2, exerciseName = "Barbell Row", targetSets = 4, targetReps = 8, targetWeightKg = null, position = 1),
                ),
            ),
        ),
        sessions = listOf(
            WorkoutSession(id = 3, presetId = 7, label = "Heavy Pull", startedAtMs = 100, completedAtMs = 200, xpAwarded = 85, strengthScore = 42) to
                listOf(
                    SessionSet(id = 9, exerciseId = 1, exerciseName = "Weighted Pull-up", setIndex = 0, reps = 5, weightKg = 20.5, modifiers = "deficit", done = true),
                    SessionSet(id = 10, exerciseId = 1, exerciseName = "Weighted Pull-up", setIndex = 1, reps = 4, weightKg = null, done = false),
                ),
            WorkoutSession(id = 4, presetId = null, label = "Freeform", startedAtMs = 300, completedAtMs = null) to emptyList(),
        ),
        stats = listOf(
            StatEntry(takenAtMs = 50, weightKg = 70.0, heightCm = 175.0, bodyFatPct = 15.0),
            StatEntry(takenAtMs = 60, weightKg = 70.5, heightCm = 175.0, bodyFatPct = null),
        ),
        titles = listOf(UnlockedTitle("awakened", 210), UnlockedTitle("monarch", 1000)),
        skills = listOf(
            SkillPractice(skillName = "handstand", practicedAtMs = 400, claimed = true, value = 30, weightKg = null),
            SkillPractice(skillName = "pistol squat", practicedAtMs = 500, claimed = false, value = 5, weightKg = 8.0),
        ),
        healthDays = listOf(
            HealthDay(date = LocalDate.of(2026, 9, 10), steps = 9000, distanceKm = 6.5, activeKcal = 320, sleepMinutes = 460, restingHr = 58),
            HealthDay(date = LocalDate.of(2026, 9, 11), steps = 1200, distanceKm = 0.9, activeKcal = 40, sleepMinutes = 0, restingHr = null),
        ),
        measurements = listOf(
            MeasurementEntry(site = MeasurementSite.WAIST, valueCm = 82.5, takenAtMs = 100),
            MeasurementEntry(site = MeasurementSite.UPPER_ARM, valueCm = 36.0, takenAtMs = 200),
        ),
        measurementGoals = listOf(
            MeasurementGoal(site = MeasurementSite.WAIST, targetCm = 80.0, setAtMs = 90, startCm = 85.0, achievedAtMs = null),
        ),
        exportedAtMs = 999,
    )

    @Test
    fun `round trip preserves every field of every section`() {
        val archive = ExportReader.read(fullArchiveJson()).getOrThrow()

        assertEquals(4, archive.formatVersion)
        assertEquals(999, archive.exportedAtMs)
        assertEquals("Mo\"narch \\ The Türkçe Æon", archive.profile.name)
        assertEquals(123_456_789_012L, archive.profile.totalXp)
        assertEquals("awakened", archive.profile.currentTitleId)
        assertEquals(TrainingMode.HYPERTROPHY, archive.trainingMode)

        val preset = archive.presets.single()
        assertEquals(7L, preset.id)
        assertEquals("Heavy Pull", preset.name)
        assertEquals("note with \"quote\" and \\ slash", preset.note)
        assertEquals(3, preset.scheduledDay)
        assertEquals(
            listOf(
                PresetEntry(exerciseId = 1, exerciseName = "Weighted Pull-up", targetSets = 5, targetReps = 5, targetWeightKg = 20.0, modifiers = "deficit", position = 0),
                PresetEntry(exerciseId = 2, exerciseName = "Barbell Row", targetSets = 4, targetReps = 8, targetWeightKg = null, position = 1),
            ),
            preset.entries,
        )

        assertEquals(2, archive.sessions.size)
        val (session, sets) = archive.sessions[0]
        assertEquals(WorkoutSession(id = 3, presetId = 7, label = "Heavy Pull", startedAtMs = 100, completedAtMs = 200, xpAwarded = 85, strengthScore = 42), session)
        assertEquals(
            listOf(
                SessionSet(id = 9, exerciseId = 1, exerciseName = "Weighted Pull-up", setIndex = 0, reps = 5, weightKg = 20.5, modifiers = "deficit", done = true),
                SessionSet(id = 10, exerciseId = 1, exerciseName = "Weighted Pull-up", setIndex = 1, reps = 4, weightKg = null, done = false),
            ),
            sets,
        )
        assertEquals(
            WorkoutSession(id = 4, presetId = null, label = "Freeform", startedAtMs = 300, completedAtMs = null),
            archive.sessions[1].first,
        )
        assertTrue(archive.sessions[1].second.isEmpty())

        assertEquals(
            listOf(
                StatEntry(takenAtMs = 50, weightKg = 70.0, heightCm = 175.0, bodyFatPct = 15.0),
                StatEntry(takenAtMs = 60, weightKg = 70.5, heightCm = 175.0, bodyFatPct = null),
            ),
            archive.stats,
        )
        assertEquals(listOf(UnlockedTitle("awakened", 210), UnlockedTitle("monarch", 1000)), archive.titles)

        assertEquals(
            listOf(
                SkillPractice(skillName = "handstand", practicedAtMs = 400, claimed = true, value = 30, weightKg = null),
                SkillPractice(skillName = "pistol squat", practicedAtMs = 500, claimed = false, value = 5, weightKg = 8.0),
            ),
            archive.skills,
        )

        assertEquals(
            listOf(
                HealthDay(date = LocalDate.of(2026, 9, 10), steps = 9000, distanceKm = 6.5, activeKcal = 320, sleepMinutes = 460, restingHr = 58),
                HealthDay(date = LocalDate.of(2026, 9, 11), steps = 1200, distanceKm = 0.9, activeKcal = 40, sleepMinutes = 0, restingHr = null),
            ),
            archive.healthDays,
        )

        assertEquals(
            listOf(
                MeasurementEntry(id = 0, site = MeasurementSite.WAIST, valueCm = 82.5, takenAtMs = 100),
                MeasurementEntry(id = 0, site = MeasurementSite.UPPER_ARM, valueCm = 36.0, takenAtMs = 200),
            ),
            archive.measurements,
        )
        assertEquals(
            listOf(MeasurementGoal(site = MeasurementSite.WAIST, targetCm = 80.0, setAtMs = 90, startCm = 85.0, achievedAtMs = null)),
            archive.measurementGoals,
        )
    }

    @Test
    fun `v3 archive without measurement sections restores with empty defaults`() {
        val v3 = """
            {"formatVersion":3,"exportedAtMs":42,
             "profile":{"name":"Old Hunter","totalXp":55,"currentTitleId":null},
             "trainingMode":"STRENGTH",
             "presets":[],"sessions":[],"stats":[],"titles":[],"skills":[],"healthDays":[]}
        """.trimIndent()

        val archive = ExportReader.read(v3).getOrThrow()

        assertTrue(archive.measurements.isEmpty())
        assertTrue(archive.measurementGoals.isEmpty())
    }

    @Test
    fun `session title and both notes survive a real write then read`() {
        // Regression: the writer omitted these three keys entirely, so every
        // restore silently blanked the titles and public notes and DESTROYED
        // the private notes — the one thing in the archive with no other copy.
        val session = WorkoutSession(
            id = 4,
            presetId = null,
            label = "Push",
            startedAtMs = 1_000,
            completedAtMs = 2_000,
            xpAwarded = 120,
            strengthScore = 88,
            title = "Rainy morning \"grind\"",
            note = "Felt strong on dips.",
            privateNote = "Shoulder twinge \\ watch it",
        )
        val json = ExportWriter.write(
            profile = PlayerProfile(),
            trainingMode = TrainingMode.STRENGTH,
            presets = emptyList(),
            sessions = listOf(session to emptyList()),
            stats = emptyList(),
            titles = emptyList(),
            skills = emptyList(),
            healthDays = emptyList(),
            exportedAtMs = 1,
        )

        val restored = ExportReader.read(json).getOrThrow().sessions.single().first

        assertEquals("Rainy morning \"grind\"", restored.title)
        assertEquals("Felt strong on dips.", restored.note)
        assertEquals("Shoulder twinge \\ watch it", restored.privateNote)
    }

    @Test
    fun `v1 archive without new sections restores with defaults`() {
        // Hand-written v1-shaped archive: no trainingMode, skills or healthDays.
        val v1 = """
            {"formatVersion":1,"exportedAtMs":42,
             "profile":{"name":"Old Hunter","totalXp":55,"currentTitleId":null},
             "presets":[],"sessions":[],"stats":[],"titles":[]}
        """.trimIndent()

        val archive = ExportReader.read(v1).getOrThrow()

        assertEquals(1, archive.formatVersion)
        assertEquals("Old Hunter", archive.profile.name)
        assertEquals(TrainingMode.STRENGTH, archive.trainingMode)
        assertTrue(archive.skills.isEmpty())
        assertTrue(archive.healthDays.isEmpty())
    }

    @Test
    fun `malformed json fails without throwing`() {
        listOf(
            "",
            "{not json",
            "{\"formatVersion\":2,",
            "{\"formatVersion\":2,\"exportedAtMs\":0}", // missing profile section
            "{\"formatVersion\":2,\"exportedAtMs\":0,\"profile\":{\"name\":\"x\"}}", // missing presets/sessions/stats/titles
            "[]", // wrong top-level type
            "{\"formatVersion\":true}",
        ).forEach { bad ->
            val result = ExportReader.read(bad)
            assertTrue("expected failure for <$bad>", result.isFailure)
            assertNotNull(result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `future formatVersion fails`() {
        // Derived from the constant so the guard survives the next format bump.
        val future = fullArchiveJson().replace(
            "\"formatVersion\":${ExportWriter.FORMAT_VERSION}",
            "\"formatVersion\":${ExportWriter.FORMAT_VERSION + 1}",
        )
        val result = ExportReader.read(future)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("${ExportWriter.FORMAT_VERSION + 1}"))
    }
}
