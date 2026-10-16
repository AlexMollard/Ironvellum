package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.CsvWorkoutReader
import com.ironvellum.app.domain.ImportAliases
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.TrialDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Effort per set through the database: rated only while logged, kept by the archive, filled by an import, saved by an amendment. */
@RunWith(AndroidJUnit4::class)
class EffortRepositoryTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        repo.addStat(weightKg = 80.0, bodyFatPct = 14.0)
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun bench() = db.exerciseDao().observeAll().first().first { it.name == "Bench Press" }.id

    private suspend fun liveTrial(sets: Int = 2): Pair<Long, List<Long>> {
        val session = repo.startFreeformSession("Bench")
        repeat(sets) { repo.addExtraSet(session, bench(), 8, 60.0, "") }
        return session to db.sessionDao().setsFor(session).sortedBy { it.setIndex }.map { it.id }
    }

    private suspend fun rpe(setId: Long) = db.sessionDao().setById(setId)!!.rpe

    @Test
    fun aLoggedSetTakesARatingAndKeepsItThroughEdits() = runBlocking {
        val (_, sets) = liveTrial()
        repo.updateSet(sets[0], 8, 60.0, done = true)
        repo.setRpe(sets[0], 8)
        assertEquals(8, rpe(sets[0]))
        // The steppers rewrite the set and must not drop its rating.
        repo.updateSet(sets[0], 9, 62.5, done = true)
        assertEquals(8, rpe(sets[0]))
        // Below 5 reads 5; null clears.
        repo.setRpe(sets[0], 3)
        assertEquals(5, rpe(sets[0]))
        repo.setRpe(sets[0], null)
        assertNull(rpe(sets[0]))
    }

    @Test
    fun aSetNotLoggedOrAWarmupIsNeverRatedAndUnloggingTakesTheRatingOff() = runBlocking {
        val (_, sets) = liveTrial()
        repo.setRpe(sets[0], 8)
        assertNull("not logged yet", rpe(sets[0]))
        repo.updateSet(sets[0], 8, 60.0, done = true)
        repo.setRpe(sets[0], 8)
        repo.updateSet(sets[0], 8, 60.0, done = false)
        assertNull("taking the tick off takes the rating off", rpe(sets[0]))
        repo.updateSet(sets[1], 8, 60.0, done = true)
        repo.setRpe(sets[1], 7)
        repo.setKind(sets[1], SetKind.WARMUP)
        assertNull("a warm-up is never rated", rpe(sets[1]))
    }

    @Test
    fun aSealedTrialCannotBeRatedFromTheLiveScreen() = runBlocking {
        val (session, sets) = liveTrial()
        repo.updateSet(sets[0], 8, 60.0, done = true)
        repo.setRpe(sets[0], 8)
        repo.completeSession(session)
        repo.setRpe(sets[0], 10)
        assertEquals(8, rpe(sets[0]))
    }

    @Test
    fun anAmendmentSavesTheRatingAndDropsItFromAnUnloggedSet() = runBlocking {
        val (session, sets) = liveTrial(sets = 3)
        sets.forEach { repo.updateSet(it, 8, 60.0, done = true) }
        repo.setRpe(sets[0], 7)
        repo.completeSession(session)

        val draft = TrialDraft.of(repo.observeSessionSets(session).first())
        val rated = draft.updateSet(0, 1) { it.copy(rpe = 9) }.updateSet(0, 2) { it.copy(done = false, rpe = 6) }
        repo.editSealedTrial(session, rated)

        val after = db.sessionDao().setsFor(session).sortedBy { it.setIndex }
        assertEquals(listOf(7, 9, null), after.map { it.rpe })
    }

    @Test
    fun theArchiveKeepsEveryRating() = runBlocking {
        val (session, sets) = liveTrial()
        sets.forEach { repo.updateSet(it, 8, 60.0, done = true) }
        repo.setRpe(sets[0], 8)
        repo.setRpe(sets[1], 10)
        repo.completeSession(session)

        assertTrue(repo.importArchive(repo.exportJson()).isSuccess)
        val restored = db.sessionDao().observeCompletedWithSets().first().single().sets.sortedBy { it.setIndex }
        assertEquals(listOf(8, 10), restored.map { it.rpe })
    }

    @Test
    fun anImportFillsTheEffortFieldInsteadOfWritingRpeIntoTheModifiers() = runBlocking {
        val csv = listOf(
            "\"title\",\"start_time\",\"end_time\",\"description\",\"exercise_title\",\"superset_id\",\"exercise_notes\",\"set_index\",\"set_type\",\"weight_kg\",\"reps\",\"distance_km\",\"duration_seconds\",\"rpe\"",
            "\"Push\",\"22 Dec 2025, 08:00\",\"22 Dec 2025, 08:37\",\"\",\"Bench Press (Barbell)\",,\"felt heavy\",0,\"normal\",80,5,0,0,8.5",
            "\"Push\",\"22 Dec 2025, 08:00\",\"22 Dec 2025, 08:37\",\"\",\"Bench Press (Barbell)\",,\"\",1,\"normal\",80,5,0,0,3",
            "\"Push\",\"22 Dec 2025, 08:00\",\"22 Dec 2025, 08:37\",\"\",\"Bench Press (Barbell)\",,\"\",2,\"warmup\",40,10,0,0,7",
            "\"Push\",\"22 Dec 2025, 08:00\",\"22 Dec 2025, 08:37\",\"\",\"Bench Press (Barbell)\",,\"\",3,\"normal\",80,5,0,0,",
        ).joinToString("\n")
        val parsed = CsvWorkoutReader.read(csv)
        val catalogue = repo.catalogueNamesOnce()
        val mapping = parsed.workouts.flatMap { it.sets }.associate { s ->
            s.exerciseName.trim().lowercase() to repo.exerciseIdByName(ImportAliases.resolve(s.exerciseName, catalogue)!!)!!
        }
        repo.mergeImported(parsed, mapping)

        val sets = db.sessionDao().observeCompletedWithSets().first().single().sets.sortedBy { it.setIndex }
        assertEquals("halves round up, 5 or less reads 5, a warm-up and a blank are unrated", listOf(9, 5, null, null), sets.map { it.rpe })
        assertEquals("the set's own note stays", "felt heavy", sets[0].modifiers)
        assertFalse("RPE is a field now, not modifier text", sets.any { it.modifiers.contains("RPE") })
    }

    private companion object {
        const val TEST_DB = "effort_repository_test.db"
    }
}
