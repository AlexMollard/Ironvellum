package com.ironvellum.app.domain

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Effort per set: what is stored, how RIR derives from it, what an import makes of it, and where it is printed. */
class EffortTest {

    // The card writes its date and thousands in the default locale: pin English so the expected text is the same on any machine.
    private val savedLocale = java.util.Locale.getDefault()

    @org.junit.Before
    fun english() = java.util.Locale.setDefault(java.util.Locale.ENGLISH)

    @org.junit.After
    fun restoreLocale() = java.util.Locale.setDefault(savedLocale)

    // ----------------------------------------------------------- storage and derivation

    @Test
    fun `a rating is a whole number from 5 to 10 and anything lower is stored as 5`() {
        assertEquals(listOf(5, 6, 7, 8, 9, 10), Effort.CHIPS)
        assertEquals(5, Effort.clamp(3))
        assertEquals(5, Effort.clamp(5))
        assertEquals(8, Effort.clamp(8))
        assertEquals(10, Effort.clamp(12))
        assertNull(Effort.clamp(null))
    }

    @Test
    fun `reps in reserve are ten minus the rating, so changing scale loses nothing`() {
        assertEquals(listOf(5, 4, 3, 2, 1, 0), Effort.CHIPS.map(Effort::rir))
        assertEquals(listOf(5, 6, 7, 8, 9, 10), listOf(5, 4, 3, 2, 1, 0).map(Effort::rpeOfRir))
        // Five or more in reserve all read as the lowest chip.
        assertEquals(5, Effort.rpeOfRir(8))
    }

    @Test
    fun `the chips and the logged value read in the chosen scale`() {
        assertEquals(listOf("≤5", "6", "7", "8", "9", "10"), Effort.CHIPS.map { Effort.chip(it, EffortScale.RPE) })
        assertEquals(listOf("5+", "4", "3", "2", "1", "0"), Effort.CHIPS.map { Effort.chip(it, EffortScale.RIR) })
        assertEquals("RPE 7", Effort.value(7, EffortScale.RPE))
        assertEquals("2 RIR", Effort.value(8, EffortScale.RIR))
        assertEquals("RPE ≤5", Effort.value(5, EffortScale.RPE))
        assertEquals("5+ RIR", Effort.value(5, EffortScale.RIR))
    }

    @Test
    fun `the caption says what the chosen chip means`() {
        assertEquals("RPE 8: about two reps left", Effort.caption(8, EffortScale.RPE))
        assertEquals("RPE 10: nothing left", Effort.caption(10, EffortScale.RPE))
        assertEquals("RPE ≤5: five or more reps left", Effort.caption(5, EffortScale.RPE))
        assertEquals("2 RIR: two reps left", Effort.caption(8, EffortScale.RIR))
        assertEquals("0 RIR: nothing left", Effort.caption(10, EffortScale.RIR))
    }

    @Test
    fun `an unknown stored scale reads as RPE`() {
        assertEquals(EffortScale.RIR, EffortScale.fromWire("RIR"))
        assertEquals(EffortScale.RPE, EffortScale.fromWire("RPE"))
        assertEquals(EffortScale.RPE, EffortScale.fromWire(null))
        assertEquals(EffortScale.RPE, EffortScale.fromWire("borg"))
    }

    // ----------------------------------------------------------- import

    @Test
    fun `an imported rating becomes a whole number, halves round up and the floor is 5`() {
        assertEquals(8, Effort.fromImported(8.0))
        assertEquals(9, Effort.fromImported(8.5))
        assertEquals(8, Effort.fromImported(7.9))
        assertEquals(5, Effort.fromImported(3.0))
        assertEquals(5, Effort.fromImported(1.0))
        assertEquals(10, Effort.fromImported(10.0))
        assertEquals(10, Effort.fromImported(10.4))
    }

    @Test
    fun `a blank, zero, negative or impossible cell is no rating`() {
        assertNull(Effort.fromImported(null))
        assertNull(Effort.fromImported(0.0))
        assertNull(Effort.fromImported(-2.0))
        assertNull(Effort.fromImported(11.0))
        assertNull(Effort.fromImported(Double.NaN))
    }

    @Test
    fun `the Strong and Hevy readers still hand the figure over for the import to store`() {
        val csv = """
            "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
            "Legs","22 Dec 2025, 08:00","22 Dec 2025, 09:00","","Squat (Barbell)",,"",0,"normal",100,5,,,8.5
            "Legs","22 Dec 2025, 08:00","22 Dec 2025, 09:00","","Squat (Barbell)",,"",1,"normal",100,5,,,
        """.trimIndent()
        val sets = CsvWorkoutReader.read(csv).workouts.single().sets
        assertEquals(listOf(9, null), sets.map { Effort.fromImported(it.rpe) })
    }

    // ----------------------------------------------------------- the draft keeps it

    @Test
    fun `an amendment keeps the rating through a metric change and clears it on a new set`() {
        val set = TrialDraft.DraftSet(reps = 5, weightKg = 100.0, rpe = 8)
        assertEquals(8, TrialDraft.reshape(set, ExerciseMetric.HOLD).rpe)
        val draft = TrialDraft.of(
            listOf(SessionSet(exerciseId = 1, exerciseName = "Squat", setIndex = 0, reps = 5, weightKg = 100.0, done = true, rpe = 8)),
        )
        assertEquals(8, draft.blocks.single().sets.single().rpe)
        assertNull("an added set is a fresh one", draft.addSet(0).blocks.single().sets.last().rpe)
    }

    // ----------------------------------------------------------- the archive

    @Test
    fun `a rating round trips the archive, and an unrated set writes nothing new`() {
        val session = WorkoutSession(id = 1, label = "Legs", startedAtMs = 1_000, completedAtMs = 2_000)
        val sets = listOf(
            SessionSet(exerciseId = 1, exerciseName = "Squat", setIndex = 0, reps = 5, weightKg = 100.0, done = true, rpe = 8),
            SessionSet(exerciseId = 1, exerciseName = "Squat", setIndex = 1, reps = 5, weightKg = 100.0, done = true),
        )
        fun write(list: List<SessionSet>) = ExportWriter.write(
            profile = PlayerProfile(), trainingMode = TrainingMode.STRENGTH, presets = emptyList(),
            sessions = listOf(session to list), stats = emptyList(), titles = emptyList(), skills = emptyList(),
            healthDays = emptyList(), exportedAtMs = 1,
        )
        val json = write(sets)
        assertEquals("only the rated set writes the key", 1, Regex("\"rpe\"").findAll(json).count())
        assertEquals(listOf(8, null), ExportReader.read(json).getOrThrow().sessions.single().second.map { it.rpe })
        assertFalse(write(sets.map { it.copy(rpe = null) }).contains("rpe"))
    }

    @Test
    fun `version 9 and 10 archives carry no effort and still read`() {
        for (version in listOf(9, 10)) {
            val json = """
                {"formatVersion":$version,"exportedAtMs":42,
                 "profile":{"name":"Old","totalXp":1,"currentTitleId":null},"trainingMode":"STRENGTH",
                 "presets":[],"sessions":[{"id":1,"presetId":null,"label":"Legs","startedAtMs":1,"completedAtMs":2,"xpAwarded":0,"strengthScore":0,
                   "sets":[{"id":1,"exerciseId":1,"exerciseName":"Squat","exercisePosition":0,"setIndex":0,"reps":5,"weightKg":100.0,"modifiers":"","done":true}]}],
                 "stats":[],"titles":[],"skills":[],"healthDays":[]}
            """.trimIndent()
            assertNull(ExportReader.read(json).getOrThrow().sessions.single().second.single().rpe)
        }
    }

    // ----------------------------------------------------------- the CSV

    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun `the CSV has an RPE column after the set type and reads back what it wrote`() {
        val session = WorkoutSession(id = 1, label = "Legs", startedAtMs = 1_800_000_000_000, completedAtMs = 1_800_003_600_000)
        val sets = listOf(
            SessionSet(exerciseId = 1, exerciseName = "Back Squat", setIndex = 0, reps = 5, weightKg = 100.0, done = true, rpe = 8),
            SessionSet(exerciseId = 1, exerciseName = "Back Squat", setIndex = 1, reps = 5, weightKg = 100.0, done = true),
        )
        val csv = WorkoutCsvWriter.write(listOf(session to sets), zone)
        assertTrue(csv.contains("Set Type,RPE"))
        val back = CsvWorkoutReader.read(csv).workouts.single().sets
        assertEquals(listOf(8.0, null), back.map { it.rpe })
    }

    // ----------------------------------------------------------- the share card

    private val squat = Exercise(id = 1, name = "Back Squat", muscleGroup = MuscleGroup.LEGS, isWeighted = true)
    private val trial = WorkoutSession(id = 1, label = "Legs", startedAtMs = 1_758_000_000_000, completedAtMs = 1_758_000_000_000 + 47 * 60_000L)

    private fun squats(vararg rated: Pair<Double, Int?>) = rated.mapIndexed { i, (kg, rpe) ->
        SessionSet(id = i + 1L, exerciseId = 1, exerciseName = "Back Squat", setIndex = i, reps = 5, weightKg = kg, done = true, rpe = rpe)
    }

    private fun share(sets: List<SessionSet>, effort: Boolean, scale: EffortScale = EffortScale.RPE, unit: LoadUnit = LoadUnit.KG) =
        WorkoutShare.format(trial, sets, mapOf(1L to squat), zone, includeEffort = effort, effortScale = scale, unit = unit)

    @Test
    fun `the card prints only the top set's effort and never an at sign figure`() {
        val sets = squats(100.0 to 7, 102.5 to 9, 100.0 to 8)
        val text = share(sets, effort = true)
        assertTrue(text, text.contains("Back Squat · top 102.5kg\n${WorkoutShare.HIT}${WorkoutShare.HIT}${WorkoutShare.HIT} 3 × 5 · RPE 9"))
        assertFalse("one set's effort, not each", text.contains("RPE 7") || text.contains("RPE 8"))
        assertFalse(text.contains("@9") || text.contains("@ 9") || text.contains("@8"))
    }

    @Test
    fun `the card reads RIR on the RIR scale and in the lifter's unit`() {
        val text = share(squats(100.0 to 8, 100.0 to 8, 100.0 to 8), effort = true, scale = EffortScale.RIR, unit = LoadUnit.LB)
        assertTrue(text, text.contains("Back Squat · 220.5lb\n${WorkoutShare.HIT}${WorkoutShare.HIT}${WorkoutShare.HIT} 3 × 5 · 2 RIR"))
    }

    @Test
    fun `the card carries no effort unless asked, or when nothing was rated`() {
        val rated = squats(100.0 to 8)
        assertFalse(share(rated, effort = false).contains("RPE"))
        assertEquals(share(squats(100.0 to null), effort = false), share(squats(100.0 to null), effort = true))
        // A top set that was not rated says nothing, even when another was.
        assertFalse(share(squats(100.0 to 8, 110.0 to null), effort = true).contains("RPE"))
    }
}
