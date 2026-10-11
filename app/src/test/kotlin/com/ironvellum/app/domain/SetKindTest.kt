package com.ironvellum.app.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Set kinds: how words map to them, what counts, and that they survive the archive and the CSV. */
class SetKindTest {

    @Test
    fun `an import word maps to a real kind`() {
        assertEquals(SetKind.WARMUP, SetKind.fromImport("warmup"))
        assertEquals(SetKind.DROP, SetKind.fromImport("dropset"))
        assertEquals(SetKind.FAILURE, SetKind.fromImport("failure"))
        assertEquals(SetKind.WORKING, SetKind.fromImport("normal"))
        assertEquals(SetKind.WORKING, SetKind.fromImport(""))
        assertEquals(SetKind.WORKING, SetKind.fromImport(null))
        assertEquals("unknown words are plain sets", SetKind.WORKING, SetKind.fromImport("amrap"))
        assertEquals("case and padding do not matter", SetKind.DROP, SetKind.fromImport("  DropSet "))
    }

    @Test
    fun `only a warm-up is left out of the work`() {
        assertEquals(listOf(SetKind.WARMUP), SetKind.entries.filter { !it.countsAsWork })
    }

    @Test
    fun `the stored name, the wire word and the csv word read back, and unknown ones read as working`() {
        SetKind.entries.forEach {
            assertEquals(it, SetKind.fromName(it.name))
            assertEquals(it, SetKind.fromWire(it.wire))
            assertEquals(it, SetKind.fromImport(SetKind.csvType(it)))
        }
        assertEquals(SetKind.WORKING, SetKind.fromName("SUPERSET"))
        assertEquals(SetKind.WORKING, SetKind.fromName(null))
        assertEquals(SetKind.WORKING, SetKind.fromWire("pyramid"))
    }

    @Test
    fun `an archive carries a kind only when it is not plain and still reads the older warmup key`() {
        val session = WorkoutSession(id = 1, label = "Upper", startedAtMs = 1_000, completedAtMs = 2_000)
        val sets = SetKind.entries.mapIndexed { i, kind ->
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", setIndex = i, reps = 5, done = kind.countsAsWork, kind = kind)
        }
        val json = write(listOf(session to sets))
        assertEquals("a plain or warm-up set writes no kind", 3, Regex("\"kind\"").findAll(json).count())
        assertEquals("a warm-up keeps the key older readers know", 1, Regex("\"warmup\"").findAll(json).count())
        assertEquals(SetKind.entries.toList(), ExportReader.read(json).getOrThrow().sessions.single().second.map { it.kind })

        // A kind word this build does not know is a working set, not a failed restore.
        val unknown = json.replace("\"kind\":\"drop\"", "\"kind\":\"pyramid\"")
        assertEquals(
            listOf(SetKind.WORKING, SetKind.WARMUP, SetKind.WORKING, SetKind.FAILURE, SetKind.REST_PAUSE),
            ExportReader.read(unknown).getOrThrow().sessions.single().second.map { it.kind },
        )
    }

    @Test
    fun `the csv export writes the kind and the import reads it back`() {
        val session = WorkoutSession(id = 1, label = "Upper", startedAtMs = 1_800_000_000_000, completedAtMs = 1_800_003_600_000)
        val sets = SetKind.entries.mapIndexed { i, kind ->
            SessionSet(exerciseId = 1, exerciseName = "Bench Press", setIndex = i, reps = 5, weightKg = 80.0, done = kind.countsAsWork, kind = kind)
        }
        val parsed = CsvWorkoutReader.read(WorkoutCsvWriter.write(listOf(session to sets), ZoneId.systemDefault()))
        assertEquals(SetKind.entries.toList(), parsed.workouts.single().sets.map { SetKind.fromImport(it.setType) })
    }

    @Test
    fun `a set is judged at its place among the counting sets, warm-ups ahead of it do not move it`() {
        fun s(i: Int, kind: SetKind = SetKind.WORKING, exerciseId: Long = 1) =
            SessionSet(id = i.toLong(), exerciseId = exerciseId, exerciseName = "Squat", setIndex = i, reps = 5, kind = kind, done = kind.countsAsWork)
        val sets = listOf(s(0, SetKind.WARMUP), s(1, SetKind.WARMUP), s(2), s(3, SetKind.DROP), s(4))
        assertEquals(listOf(0, 0, 0, 1, 2), sets.map { SetRecords.position(it, sets) })
        // Another movement's warm-ups never shift it.
        val other = sets + s(5, exerciseId = 2)
        assertEquals(5, SetRecords.position(other.last(), other))
    }

    @Test
    fun `a ramp of warm-ups does not make working set one compare against a later record`() {
        val history = WorkoutSession(id = 1, label = "Old", startedAtMs = 1_000, completedAtMs = 2_000)
        fun s(i: Int, reps: Int, kind: SetKind = SetKind.WORKING) =
            SessionSet(id = i.toLong(), exerciseId = 1, exerciseName = "Squat", setIndex = i, reps = reps, weightKg = 100.0, done = kind.countsAsWork, kind = kind)
        val records = SetRecords.records(
            history = listOf(history to listOf(s(0, 5, SetKind.WARMUP), s(1, 5, SetKind.WARMUP), s(2, 8), s(3, 6))),
            bodyweightAt = { 80.0 },
        )
        assertEquals(setOf("squat" to 0, "squat" to 1), records.keys)
    }

    @Test
    fun `an amend draft keeps the kind and a new set is a working one`() {
        val draft = TrialDraft.of(
            listOf(SessionSet(exerciseId = 1, exerciseName = "Squat", setIndex = 0, reps = 5, kind = SetKind.DROP, done = true)),
        )
        assertEquals(SetKind.DROP, draft.blocks.single().sets.single().kind)
        assertEquals(SetKind.WORKING, draft.addSet(0).blocks.single().sets.last().kind)
        assertEquals(SetKind.DROP, TrialDraft.reshape(draft.blocks.single().sets.single(), ExerciseMetric.HOLD).kind)
    }

    @Test
    fun `a warm-up row never changes what the rite is offered`() {
        val entry = PresetEntry(id = 1, exerciseId = 1, exerciseName = "Squat", targetSets = 3, targetReps = 5, targetWeightKg = 100.0, modifiers = "", position = 0)
        fun s(i: Int, kind: SetKind = SetKind.WORKING) =
            SessionSet(exerciseId = 1, exercisePosition = 0, setIndex = i, reps = 5, weightKg = 100.0, done = kind.countsAsWork, kind = kind)
        val ramp = listOf(s(0, SetKind.WARMUP), s(1, SetKind.WARMUP), s(2), s(3), s(4))
        assertTrue("the three working rows are the prescription", RoutineUpdate.propose(listOf(entry), ramp) { ExerciseMetric.REPS }.isEmpty())
    }

    private fun write(sessions: List<Pair<WorkoutSession, List<SessionSet>>>) = ExportWriter.write(
        profile = PlayerProfile(),
        trainingMode = TrainingMode.STRENGTH,
        presets = emptyList(),
        sessions = sessions,
        stats = emptyList(),
        titles = emptyList(),
        skills = emptyList(),
        healthDays = emptyList(),
        exportedAtMs = 1,
    )
}
