package com.ironvellum.app.domain

import org.junit.Assert.*
import org.junit.Test

class PreparationArchiveTest {
    private val step = PreparationStep(99, "A custom preparation", PreparationMeasure.SECONDS, 35, note = "a \"quote\"\nand line")
    private val routine = PreparationRoutine(77, PreparationKind.COOLDOWN, "Unused routine", listOf(step))
    private val snapshot = PreparationSnapshot(PreparationKind.COOLDOWN, routine.name, routine.steps, routine.id)
    private fun write(device: Boolean = true) = ExportWriter.write(
        profile = PlayerProfile(), trainingMode = TrainingMode.STRENGTH,
        presets = listOf(WorkoutPreset(31, "Preset", preparation = mapOf(PreparationKind.COOLDOWN to PreparationChoice(PreparationMode.SAVED, 77)))),
        sessions = listOf(WorkoutSession(41, 31, "sealed", 1, 2) to emptyList()),
        stats = emptyList(), titles = emptyList(), skills = emptyList(), healthDays = emptyList(), exportedAtMs = 100,
        includeDeviceOnly = device, preparationRoutines = listOf(routine),
        sessionPreparations = listOf(SessionPreparation(41, snapshot, PreparationProgress(status = PreparationStatus.PAUSED, remainingMs = 12345))),
    )
    @Test fun v15LocalAndCloudCarryUnusedRoutinesAttachmentsAndSealedPausedProgress() {
        listOf(true, false).forEach { device ->
            val archive = ExportReader.read(write(device)).getOrThrow()
            assertEquals(15, archive.formatVersion)
            assertEquals(listOf(routine), archive.preparationRoutines)
            assertEquals(77L, archive.presets.single().preparation[PreparationKind.COOLDOWN]?.routineId)
            assertEquals(snapshot, archive.sessionPreparations.single().snapshot)
            assertEquals(12345L, archive.sessionPreparations.single().progress.remainingMs)
        }
    }
    @Test fun v14AbsentPreparationSectionsRemainEmptyAndAuto() {
        val archive = ExportReader.read(ExportWriter.write(PlayerProfile(), TrainingMode.STRENGTH, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), exportedAtMs = 1)
            .replace("\"formatVersion\":15", "\"formatVersion\":14")).getOrThrow()
        assertTrue(archive.preparationRoutines.isEmpty())
        assertTrue(archive.sessionPreparations.isEmpty())
    }
    @Test fun malformedPreparationIsReportedWhileTrainingStillRestores() {
        val archive = ExportReader.read(write().replace("\"exerciseName\":\"A custom preparation\"", "\"exerciseName\":\"\"")).getOrThrow()
        assertEquals(1, archive.presets.size)
        assertEquals(1, archive.sessions.size)
        assertTrue(archive.preparationProblems.isNotEmpty())
        assertTrue(archive.preparationRoutines.isEmpty())
    }
    @Test fun progressCodecRoundTripsCompletedAndSkippedKeysAndRejectsNegativeCursor() {
        val p = PreparationProgress(PreparationStatus.PAUSED, 2, 1, 1, setOf("0:0:0"), setOf("1:0:0"), 12345)
        assertEquals(p, PreparationCodec.decodeProgress(PreparationCodec.encodeProgress(p)).getOrThrow())
        assertTrue(PreparationCodec.decodeProgress(PreparationCodec.encodeProgress(p.copy(stepIndex = -1))).isFailure)
    }
    @Test fun mixedMalformedAndValidDuplicateIdsNeverLeaveAnUnambiguousRoutine() {
        val json = write().replace("\"preparationRoutines\":[", "\"preparationRoutines\":[{\"id\":77,\"kind\":\"COOLDOWN\",\"name\":\"Invalid duplicate\",\"steps\":[]},")
        val archive = ExportReader.read(json).getOrThrow()
        assertTrue(archive.preparationProblems.isNotEmpty())
        assertTrue("raw duplicate IDs remain ambiguous even if one duplicate is malformed", archive.preparationRoutines.isEmpty())
    }
    @Test fun historicalRoundCountsRemainReadableWhileEditorBoundsStayStrict() {
        val historical = step.copy(rounds = 30)
        assertFalse(historical.validForEditor())
        assertEquals(listOf(historical), PreparationCodec.decodeSteps(PreparationCodec.encodeSteps(listOf(historical))).getOrThrow())
        assertTrue(PreparationCodec.decodeSteps(PreparationCodec.encodeSteps(listOf(historical.copy(rounds = 0)))).isFailure)
    }
}
