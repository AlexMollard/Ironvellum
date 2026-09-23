package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.domain.CsvWorkoutReader
import com.ironvellum.app.domain.ImportAliases
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.WorkoutSession
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
        context.deleteDatabase(TEST_DB)
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

    private companion object {
        const val TEST_DB = "ironvellum-import-merge-test.db"
    }
}
