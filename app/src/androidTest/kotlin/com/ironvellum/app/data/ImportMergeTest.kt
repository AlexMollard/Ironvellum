package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.domain.CsvWorkoutReader
import com.ironvellum.app.domain.ImportAliases
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.ImportedWorkouts
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The CSV merge (Strong/Hevy import) on a real database. Write-only evidence
 * in CI: these prove the append path adds without destroying, re-imports add
 * nothing, imported history earns strength scores, and imported sessions
 * never enter the feed-push selection. NEVER run on the owner's phone.
 *
 * The CSV below is SYNTHETIC, hand-built from the documented Hevy export
 * format — it is not anyone's real training data.
 */
@RunWith(AndroidJUnit4::class)
class ImportMergeTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository

    /** Synthetic Hevy export: two exercises, a warmup, and a distance row. */
    private val hevyCsv = listOf(
        "\"title\",\"start_time\",\"end_time\",\"description\",\"exercise_title\",\"superset_id\",\"exercise_notes\",\"set_index\",\"set_type\",\"weight_kg\",\"reps\",\"distance_km\",\"duration_seconds\",\"rpe\"",
        "\"Morning workout\",\"22 Dec 2025, 08:00\",\"22 Dec 2025, 08:37\",\"easy session\",\"Pull Up (Assisted)\",,\"\",0,\"normal\",0,10,0,0,8.5",
        "\"Morning workout\",\"22 Dec 2025, 08:00\",\"22 Dec 2025, 08:37\",\"easy session\",\"Leg Press (Machine)\",,\"\",1,\"warmup\",90,12,0,0,",
        "\"Morning workout\",\"22 Dec 2025, 08:00\",\"22 Dec 2025, 08:37\",\"easy session\",\"Seated Shoulder Press (Machine)\",,\"\",2,\"normal\",25,8,0,0,9",
        "\"Morning workout\",\"22 Dec 2025, 08:00\",\"22 Dec 2025, 08:37\",\"easy session\",\"Running\",,\"\",0,\"normal\",0,0,5,1800,",
    ).joinToString("\n")

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        // A fresh install imposes no week; write the one the pre-existing
        // session trains from through the path onboarding uses.
        repo.applyStarterTemplate()
        repo.addStat(weightKg = 80.0, bodyFatPct = 14.0)
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun parsed() = CsvWorkoutReader.read(hevyCsv)

    /**
     * The review screen's mapping: the unmatched machine press the lifter
     * mapped to the catalogue's Overhead Press; everything else resolves
     * through the alias table.
     */
    private suspend fun mapping(): Map<String, Long> {
        val catalogue = repo.catalogueNamesOnce()
        val result = HashMap<String, Long>()
        parsed().workouts.forEach { w ->
            w.sets.forEach { s ->
                val key = s.exerciseName.trim().lowercase()
                val resolved = ImportAliases.resolve(s.exerciseName, catalogue)
                result[key] = if (resolved != null) {
                    repo.exerciseIdByName(resolved)!!
                } else {
                    repo.exerciseIdByName("Overhead Press")!!
                }
            }
        }
        return result
    }

    @Test
    fun machineAssistanceImportKeepsItsExerciseIdentityAndRecordedKilograms() = runBlocking {
        val csv = hevyCsv.replace(",0,10,0,0,8.5", ",21,10,0,0,8.5")
        repo.mergeImported(CsvWorkoutReader.read(csv), mapping())
        val imported = db.sessionDao().observeCompletedWithSets().first().single { it.session.imported }
        val assistedId = repo.exerciseIdByName("Assisted Pull-up")!!
        val plainId = repo.exerciseIdByName("Pull-up")!!
        val row = imported.sets.single { it.exerciseId == assistedId }
        assertEquals(21.0, row.weightKg!!, 0.0)
        assertEquals(10, row.reps)
        assertTrue(row.done)
        assertTrue(imported.sets.none { it.exerciseId == plainId })
    }

    @Test
    fun mergeAddsSessionsWithoutTouchingExistingOnes() = runBlocking {
        // Pre-existing history, logged the normal way.
        val preset = db.presetDao().observePresets().first().first().preset
        val sessionId = repo.startSessionFromPreset(preset.id)
        val set = db.sessionDao().setsFor(sessionId).first()
        repo.updateSet(set.id, reps = 8, weightKg = 20.0, done = true)
        repo.completeSession(sessionId)
        val before = db.sessionDao().observeCompletedWithSets().first().single { !it.session.imported }

        val result = repo.mergeImported(parsed(), mapping())
        assertEquals(1, result.sessions)
        assertEquals(4, result.sets)

        val after = db.sessionDao().observeCompletedWithSets().first()
        // The lifter's own row is byte-identical: label, XP and score intact.
        val kept = after.single { !it.session.imported }
        assertEquals(before.session, kept.session)
        assertEquals(before.sets, kept.sets)
        assertEquals(2, after.size)
    }

    @Test
    fun aSecondImportOfTheSameDataAddsNothing() = runBlocking {
        repo.mergeImported(parsed(), mapping())
        val countAfterFirst = db.sessionDao().completedCount()
        assertEquals(1, countAfterFirst)

        val second = repo.mergeImported(parsed(), mapping())
        assertEquals(0, second.sessions)
        assertEquals(0, second.sets)
        assertEquals(1, second.skipped)
        assertEquals(countAfterFirst, db.sessionDao().completedCount())
    }

    @Test
    fun importedSessionsWithScoredSetsEarnStrengthScores() = runBlocking {
        repo.mergeImported(parsed(), mapping())
        val imported = db.sessionDao().observeCompletedWithSets().first().single { it.session.imported }
        assertTrue(imported.session.imported)
        // rescoreStrengthScores(onlyUnscored) runs after the merge; the done
        // weighted set (Overhead Press 25 kg x 8) must have scored above zero.
        assertTrue(
            "imported session must carry a strength score, got ${imported.session.strengthScore}",
            imported.session.strengthScore > 0,
        )
        // XP was awarded once for the history, with no gacha/quest path: the
        // profile total grew, the session records its share.
        assertTrue(db.profileDao().get()!!.totalXp > 0)
        assertTrue(imported.session.xpAwarded > 0)
        // Warm-ups never score: the leg-press warmup row landed done = 0.
        assertTrue("sets: ${imported.sets}", imported.sets.any { !it.done })
        // Workout notes go to the PRIVATE note, never the public one.
        assertEquals("easy session", imported.session.privateNote)
        assertEquals("", imported.session.note)
    }

    @Test
    fun importedSessionsAreExcludedFromTheFeedPushSelection() {
        val imported = WorkoutSession(
            id = 1,
            label = "Morning workout",
            startedAtMs = 1_000,
            completedAtMs = 2_000,
            imported = true,
        )
        val logged = WorkoutSession(
            id = 2,
            label = "Logged today",
            startedAtMs = 3_000,
            completedAtMs = 4_000,
        )
        val history = listOf(
            imported to emptyList(),
            logged to listOf(SessionSet(id = 1, exerciseId = 1, setIndex = 1, reps = 5)),
        )
        val pending = CloudSync.pendingForPush(history, emptyMap())
        // The imported session is filtered before the watermark check, so it
        // can never enter the retry path either.
        assertEquals(listOf(2L), pending.map { it.first.id })
        // Once the logged session's current content is watermarked, nothing is pending.
        val (loggedSession, loggedSets) = history[1]
        val pushed = mapOf(2L to CloudSync.pushFingerprint(loggedSession, loggedSets))
        assertTrue(CloudSync.pendingForPush(history, pushed).isEmpty())
    }

    /** Hevy's dropset and failure rows become real kinds, count as working sets and no longer leave modifier text. */
    @Test
    fun importedSetTypesBecomeKinds() = runBlocking {
        val csv = listOf(
            "\"title\",\"start_time\",\"end_time\",\"description\",\"exercise_title\",\"superset_id\",\"exercise_notes\",\"set_index\",\"set_type\",\"weight_kg\",\"reps\",\"distance_km\",\"duration_seconds\",\"rpe\"",
            "\"Chest day\",\"23 Dec 2025, 08:00\",\"23 Dec 2025, 08:37\",\"\",\"Bench Press (Barbell)\",,\"\",0,\"warmup\",40,10,0,0,",
            "\"Chest day\",\"23 Dec 2025, 08:00\",\"23 Dec 2025, 08:37\",\"\",\"Bench Press (Barbell)\",,\"\",1,\"normal\",80,5,0,0,",
            "\"Chest day\",\"23 Dec 2025, 08:00\",\"23 Dec 2025, 08:37\",\"\",\"Bench Press (Barbell)\",,\"\",2,\"dropset\",60,8,0,0,",
            "\"Chest day\",\"23 Dec 2025, 08:00\",\"23 Dec 2025, 08:37\",\"\",\"Bench Press (Barbell)\",,\"\",3,\"failure\",60,6,0,0,",
        ).joinToString("\n")
        val parsed = CsvWorkoutReader.read(csv)
        val bench = repo.exerciseIdByName("Bench Press")!!
        repo.mergeImported(parsed, mapOf("bench press (barbell)" to bench))
        val sets = db.sessionDao().observeCompletedWithSets().first().single { it.session.label == "Chest day" }.sets.sortedBy { it.setIndex }
        assertEquals(listOf("WARMUP", "WORKING", "DROP", "FAILURE"), sets.map { it.kind })
        assertEquals("only the warm-up is unticked", listOf(false, true, true, true), sets.map { it.done })
        assertTrue("the kind replaced the modifier text", sets.all { it.modifiers.isBlank() })
    }

    @Test
    fun signedExternalImportKeepsTheRawValueForReview() = runBlocking {
        val csv = hevyCsv.replace("Pull Up (Assisted)", "Bench Press")
            .replace(",0,10,0,0,8.5", ",-20,5,0,0,8.5")
        val benchId = repo.exerciseIdByName("Bench Press")!!
        repo.mergeImported(CsvWorkoutReader.read(csv), mapping() + ("bench press" to benchId))
        val imported = db.sessionDao().observeCompletedWithSets().first().single { it.session.imported }
        val row = imported.sets.single { it.exerciseId == benchId }
        assertEquals(-20.0, row.weightKg!!, 0.0)
        assertEquals(5, row.reps)
        assertTrue(row.done)
    }

    @Test
    fun priorHistoryThenSelectedTemplateSurvivesRenameAndRepeat() = runBlocking {
        val parsed = parsed()
        val mapping = mapping()
        val key = ImportedWorkouts.candidates(parsed).single().key
        repo.savePreset(null, "Morning workout", "personal", null, emptyList())
        repo.mergeImported(parsed, mapping)
        val xp = db.profileDao().get()!!.totalXp
        val added = repo.mergeImported(parsed, mapping, setOf(key))
        assertEquals(0, added.sessions)
        assertEquals(1, added.routines)
        val imported = repo.observePresets().first().single { it.importKey == key }
        repo.savePreset(imported.id, "Renamed", imported.note, null, imported.entries.map {
            Repository.PresetDraftEntry(it.exerciseId, it.targetSets, it.targetReps, it.targetWeightKg, it.modifiers, it.supersetGroup, it.scheme, it.warmup)
        })
        val repeated = repo.mergeImported(parsed, mapping, setOf(key))
        assertEquals(0, repeated.sessions)
        assertEquals(0, repeated.routines)
        assertEquals(1, repeated.routinesSkipped)
        assertEquals(xp, db.profileDao().get()!!.totalXp)
        val presets = repo.observePresets().first()
        assertEquals("personal", presets.single { it.name == "Morning workout" }.note)
        assertEquals("Renamed", presets.single { it.importKey == key }.name)
    }

    @Test
    fun selectedCsvTemplateStartsWithRecordedAttemptsAndTopBackoff() = runBlocking {
        assertCopiedWorkoutStartsWithRecordedFigures(selectedCsv = true)
    }

    @Test
    fun completedHistoryCopyStartsWithRecordedAttemptsAndTopBackoff() = runBlocking {
        assertCopiedWorkoutStartsWithRecordedFigures(selectedCsv = false)
    }

    private suspend fun assertCopiedWorkoutStartsWithRecordedFigures(selectedCsv: Boolean) {
        val csv = listOf(
            "\"title\",\"start_time\",\"end_time\",\"description\",\"exercise_title\",\"superset_id\",\"exercise_notes\",\"set_index\",\"set_type\",\"weight_kg\",\"reps\",\"distance_km\",\"duration_seconds\",\"rpe\"",
            "\"Copied day\",\"23 Dec 2025, 08:00\",\"23 Dec 2025, 08:37\",\"\",\"Bouldering\",,\"\",0,\"normal\",0,5,0,0,",
            "\"Copied day\",\"23 Dec 2025, 08:00\",\"23 Dec 2025, 08:37\",\"\",\"Bench Press\",,\"\",0,\"top\",100,5,0,0,",
            "\"Copied day\",\"23 Dec 2025, 08:00\",\"23 Dec 2025, 08:37\",\"\",\"Bench Press\",,\"\",1,\"backoff\",50,8,0,0,",
        ).joinToString("\n")
        val parsed = CsvWorkoutReader.read(csv)
        val climbing = repo.exerciseIdByName("Bouldering")!!
        val bench = repo.exerciseIdByName("Bench Press")!!
        val mapping = mapOf("bouldering" to climbing, "bench press" to bench)
        val key = ImportedWorkouts.candidates(parsed).single().key
        repo.mergeImported(parsed, mapping)
        val before = db.sessionDao().observeCompletedWithSets().first().single { it.session.label == "Copied day" }
        val xp = db.profileDao().get()!!.totalXp
        val presetId = if (selectedCsv) {
            repo.mergeImported(parsed, mapping, setOf(key))
            repo.observePresets().first().single { it.importKey == key }.id
        } else {
            val draft = repo.workoutDraft(before.session.id)!!
            repo.savePreset(null, draft.name, draft.note, null, draft.entries.map {
                Repository.PresetDraftEntry(it.exerciseId, it.targetSets, it.targetReps, it.targetWeightKg, it.modifiers, it.supersetGroup, it.scheme, it.warmup)
            })
        }
        val saved = repo.observePresets().first().single { it.id == presetId }
        assertEquals(5, saved.entries.single { it.exerciseId == climbing }.targetReps)
        val topBackoff = saved.entries.single { it.exerciseId == bench }
        assertEquals(5..5, topBackoff.scheme.topReps)
        assertEquals(8..8, topBackoff.scheme.backoffReps)
        assertEquals(50.0, topBackoff.scheme.backoffFixedKg!!, 0.0)
        val started = db.sessionDao().setsFor(repo.startSessionFromPreset(presetId))
        assertEquals(5, started.single { it.exerciseId == climbing }.reps)
        val benchSets = started.filter { it.exerciseId == bench }.sortedBy { it.setIndex }
        assertEquals(listOf("TOP", "BACKOFF"), benchSets.map { it.kind })
        assertEquals(listOf(5, 8), benchSets.map { it.reps })
        assertEquals(listOf(100.0, 50.0), benchSets.map { it.weightKg })
        val after = db.sessionDao().observeCompletedWithSets().first().single { it.session.id == before.session.id }
        assertEquals(before.session, after.session)
        assertEquals(before.sets, after.sets)
        assertEquals(xp, db.profileDao().get()!!.totalXp)
    }

    @Test
    fun selectedCsvPreservesBodyweightBackoffsThroughRefill() = runBlocking {
        assertCopiedBodyweightBackoffs(selectedCsv = true)
    }

    @Test
    fun historyCopyPreservesBodyweightBackoffsThroughRefill() = runBlocking {
        assertCopiedBodyweightBackoffs(selectedCsv = false)
    }

    private suspend fun assertCopiedBodyweightBackoffs(selectedCsv: Boolean) {
        val header = "\"title\",\"start_time\",\"end_time\",\"description\",\"exercise_title\",\"superset_id\",\"exercise_notes\",\"set_index\",\"set_type\",\"weight_kg\",\"reps\",\"distance_km\",\"duration_seconds\",\"rpe\""
        fun row(name: String, index: Int, kind: String, weight: String, reps: Int) =
            "\"Bodyweight copy\",\"24 Dec 2025, 08:00\",\"24 Dec 2025, 08:37\",\"\",\"$name\",,\"\",$index,\"$kind\",$weight,$reps,0,0,"
        val csv = listOf(
            header,
            row("Pull-up", 0, "top", "", 5), row("Pull-up", 1, "backoff", "", 8),
            row("Bench Press", 0, "top", "100", 5), row("Bench Press", 1, "backoff", "50", 8),
            row("Pull-up", 0, "top", "20", 5), row("Pull-up", 1, "backoff", "", 8),
            row("Assisted Pull-up", 0, "top", "", 5), row("Assisted Pull-up", 1, "backoff", "", 8),
            row("Pull-up", 0, "top", "0", 5), row("Pull-up", 1, "backoff", "", 8),
            row("Pull-up", 2, "backoff", "0", 8), row("Pull-up", 3, "backoff", "10", 8),
        ).joinToString("\n")
        val parsed = CsvWorkoutReader.read(csv)
        val mapping = listOf("Pull-up", "Bench Press", "Assisted Pull-up").associate {
            it.lowercase() to repo.exerciseIdByName(it)!!
        }
        val key = ImportedWorkouts.candidates(parsed).single().key
        repo.mergeImported(parsed, mapping, if (selectedCsv) setOf(key) else emptySet())
        val presetId = if (selectedCsv) {
            repo.observePresets().first().single { it.importKey == key }.id
        } else {
            val history = db.sessionDao().observeCompletedWithSets().first().single { it.session.label == "Bodyweight copy" }
            val draft = repo.workoutDraft(history.session.id)!!
            repo.savePreset(null, draft.name, draft.note, null, draft.entries.map {
                Repository.PresetDraftEntry(it.exerciseId, it.targetSets, it.targetReps, it.targetWeightKg, it.modifiers, it.supersetGroup, it.scheme, it.warmup)
            })
        }
        val entries = db.presetDao().presetWithEntries(presetId)!!.entries.sortedBy { it.position }
        val schemes = entries.map { com.ironvellum.app.domain.SetScheme.decode(it.setScheme) }
        assertEquals(listOf(0.0, 50.0, 0.0, null, 0.0), schemes.map { it.backoffFixedKg })
        assertTrue(schemes.all { it.topReps == 5..5 && it.backoffReps == 8..8 })

        val sessionId = repo.startSessionFromPreset(presetId)
        val blocks = db.sessionDao().setsFor(sessionId).groupBy { it.exercisePosition }.toSortedMap().values.toList()
        assertEquals(5, blocks.size)
        val expectedTop = listOf(null, 100.0, 20.0, null, 0.0)
        val expectedBackoff = listOf(0.0, 50.0, 0.0, null, 0.0)
        val expectedBackoffCounts = listOf(1, 1, 1, 1, 3)
        blocks.forEachIndexed { index, rows ->
            val ordered = rows.sortedBy { it.setIndex }
            val top = ordered.first { it.kind == "TOP" }
            val backs = ordered.filter { it.kind == "BACKOFF" }
            assertEquals(5, top.reps)
            assertEquals(expectedTop[index], top.weightKg)
            assertEquals(expectedBackoffCounts[index], backs.size)
            assertTrue(backs.all { it.reps == 8 && it.weightKg == expectedBackoff[index] })
            repo.updateSet(top.id, top.reps, top.weightKg, true)
            val refilled = db.sessionDao().setsFor(sessionId).filter { it.exercisePosition == top.exercisePosition && it.kind == "BACKOFF" }
            assertEquals(expectedBackoffCounts[index], refilled.size)
            assertTrue(refilled.all { it.reps == 8 && it.weightKg == expectedBackoff[index] })
        }
    }

    @Test
    fun concurrentImportsCreateOneSessionAndOneTemplate() = runBlocking {
        val parsed = parsed()
        val mapping = mapping()
        val key = ImportedWorkouts.candidates(parsed).single().key
        val initialXp = db.profileDao().get()!!.totalXp
        val results = coroutineScope {
            List(2) { async { repo.mergeImported(parsed, mapping, setOf(key)) } }.awaitAll()
        }
        assertEquals(1, results.sumOf { it.sessions })
        assertEquals(1, results.sumOf { it.routines })
        assertEquals(1, repo.observePresets().first().count { it.importKey == key })
        assertEquals(initialXp + results.sumOf { it.xpAwarded }.toLong(), db.profileDao().get()!!.totalXp)
        val xp = db.profileDao().get()!!.totalXp
        assertEquals(0, repo.mergeImported(parsed, mapping, setOf(key)).xpAwarded)
        assertEquals(xp, db.profileDao().get()!!.totalXp)
    }

    @Test
    fun archiveRestoreRetainsImportProvenanceAndHistory() = runBlocking {
        val parsed = parsed()
        val initialMapping = mapping()
        val key = ImportedWorkouts.candidates(parsed).single().key
        repo.mergeImported(parsed, initialMapping, setOf(key))
        val archive = repo.exportJson()
        repo.importArchive(archive).getOrThrow()
        val restoredMapping = mapping()
        val xp = db.profileDao().get()!!.totalXp
        val repeated = repo.mergeImported(parsed, restoredMapping, setOf(key))
        assertEquals(0, repeated.sessions)
        assertEquals(0, repeated.routines)
        assertEquals(1, repeated.routinesSkipped)
        assertEquals(xp, db.profileDao().get()!!.totalXp)
        assertEquals(1, repo.observePresets().first().count { it.importKey == key })
    }

    private companion object {
        const val TEST_DB = "ironvellum-import-merge-test.db"
    }
}
