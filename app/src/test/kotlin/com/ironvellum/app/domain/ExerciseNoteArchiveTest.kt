package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseNoteArchiveTest {

    private fun archive(session: WorkoutSession, includeDeviceOnly: Boolean = true): String = ExportWriter.write(
        profile = PlayerProfile(),
        trainingMode = TrainingMode.STRENGTH,
        presets = emptyList(),
        sessions = listOf(session to emptyList()),
        stats = emptyList(),
        titles = emptyList(),
        skills = emptyList(),
        healthDays = emptyList(),
        exportedAtMs = 1,
        includeDeviceOnly = includeDeviceOnly,
    )

    private val noted = WorkoutSession(
        id = 1,
        label = "Legs",
        startedAtMs = 10,
        completedAtMs = 20,
        exerciseNotes = listOf(ExerciseNote("Back squat", "Bar \"felt\" fast\nadd 2.5")),
    )

    @Test
    fun `exercise notes survive a round trip`() {
        val restored = ExportReader.read(archive(noted)).getOrThrow().sessions.single().first
        assertEquals(noted.exerciseNotes, restored.exerciseNotes)
    }

    @Test
    fun `the cloud copy leaves exercise notes out`() {
        val json = archive(noted, includeDeviceOnly = false)
        assertFalse(json.contains("exerciseNotes"))
        assertTrue(ExportReader.read(json).getOrThrow().sessions.single().first.exerciseNotes.isEmpty())
    }

    @Test
    fun `a trial without notes writes no key and an older archive reads as none`() {
        assertFalse(archive(noted.copy(exerciseNotes = emptyList())).contains("exerciseNotes"))
    }

    @Test
    fun `an oversized or blank note is bounded on read`() {
        val long = "x".repeat(EXERCISE_NOTE_MAX + 50)
        val json = archive(noted.copy(exerciseNotes = listOf(ExerciseNote("A", long), ExerciseNote("B", "  "))))
        val notes = ExportReader.read(json).getOrThrow().sessions.single().first.exerciseNotes
        assertEquals(listOf(ExerciseNote("A", "x".repeat(EXERCISE_NOTE_MAX))), notes)
    }
}
