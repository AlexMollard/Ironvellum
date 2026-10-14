package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A rite entry's superset group through the archive: written only when set, read back, and absent in v9 and v10. */
class RiteSupersetArchiveTest {

    private fun archiveOf(vararg entries: PresetEntry): String = ExportWriter.write(
        profile = PlayerProfile(),
        trainingMode = TrainingMode.STRENGTH,
        presets = listOf(WorkoutPreset(id = 1, name = "Push day", entries = entries.toList())),
        sessions = emptyList(),
        stats = emptyList(),
        titles = emptyList(),
        skills = emptyList(),
        healthDays = emptyList(),
        exportedAtMs = 1,
    )

    @Test
    fun theArchiveIsFormatThirteen() {
        assertEquals(13, ExportWriter.FORMAT_VERSION)
    }

    @Test
    fun aRiteSupersetRoundTripsAndPlainEntriesWriteNothingNew() {
        val json = archiveOf(
            PresetEntry(exerciseId = 1, exerciseName = "Bench Press", targetSets = 3, targetReps = 8, position = 0, supersetGroup = 4),
            PresetEntry(exerciseId = 2, exerciseName = "Barbell Row", targetSets = 3, targetReps = 8, position = 1, supersetGroup = 4),
            PresetEntry(exerciseId = 3, exerciseName = "Overhead Press", targetSets = 3, targetReps = 6, position = 2),
        )
        assertEquals("only the two members write the key", 2, Regex("\"supersetGroup\"").findAll(json).count())

        val entries = ExportReader.read(json).getOrThrow().presets.single().entries
        assertEquals(listOf(4, 4, null), entries.map { it.supersetGroup })
        assertEquals(listOf("Bench Press", "Barbell Row", "Overhead Press"), entries.map { it.exerciseName })

        assertFalse(
            "a rite with no superset is byte-identical to one written before",
            archiveOf(PresetEntry(exerciseId = 1, exerciseName = "Bench Press", targetSets = 3, targetReps = 8)).contains("supersetGroup"),
        )
    }

    private fun legacy(version: Int, entryExtra: String = "") = """
        {"formatVersion":$version,"exportedAtMs":42,
         "profile":{"name":"Old Lifter","totalXp":55,"currentTitleId":null},
         "trainingMode":"STRENGTH",
         "presets":[{"id":1,"name":"Push day","note":"","scheduledDay":null,"entries":[
           {"exerciseId":1,"exerciseName":"Bench Press","targetSets":3,"targetReps":8,"targetWeightKg":60.0,"modifiers":"","position":0$entryExtra},
           {"exerciseId":2,"exerciseName":"Barbell Row","targetSets":3,"targetReps":8,"targetWeightKg":null,"modifiers":"","position":1$entryExtra}]}],
         "sessions":[],"stats":[],"titles":[],"skills":[],"healthDays":[]}
    """.trimIndent()

    @Test
    fun version9And10ArchivesStillReadAndHaveNoSupersets() {
        for (version in listOf(9, 10)) {
            val archive = ExportReader.read(legacy(version)).getOrThrow()
            assertEquals(version, archive.formatVersion)
            assertEquals(listOf(null, null), archive.presets.single().entries.map { it.supersetGroup })
        }
    }

    @Test
    fun aVersionElevenArchiveCarriesTheGroup() {
        val archive = ExportReader.read(legacy(11, entryExtra = ",\"supersetGroup\":2")).getOrThrow()
        assertEquals(listOf(2, 2), archive.presets.single().entries.map { it.supersetGroup })
    }

    @Test
    fun aNewerArchiveIsStillRefused() {
        assertTrue(ExportReader.read(legacy(14)).isFailure)
        assertNull(ExportReader.read(legacy(13)).exceptionOrNull())
    }
}
