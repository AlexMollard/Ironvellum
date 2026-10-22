package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.BlockKey
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.LoadKit
import com.ironvellum.app.domain.Progression
import com.ironvellum.app.domain.ProgressionSuggestion
import com.ironvellum.app.domain.SchemeType
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.SetScheme
import com.ironvellum.app.domain.blockKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A workout starts on exactly what its routine writes; what the progression engine would have changed is a suggestion
 * on the block, applied only when accepted, and only to the sets still to log.
 */
@RunWith(AndroidJUnit4::class)
class ProgressionSuggestionTrialTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var row = 0L
    private var pullUp = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db, loadKit = { LoadKit.NONE })
        repo.ensureSeeded()
        row = db.exerciseDao().byName("Barbell Row")!!.id
        pullUp = db.exerciseDao().byName("Pull-up")!!.id
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private fun entry(id: Long, sets: Int = 3, reps: Int = 8, kg: Double? = 60.0, scheme: SetScheme = SetScheme(), modifiers: String = "") =
        Repository.PresetDraftEntry(id, sets, reps, kg, modifiers = modifiers, scheme = scheme)

    private suspend fun rite(vararg entries: Repository.PresetDraftEntry): Long =
        repo.savePreset(null, "Pull day", "", null, entries.toList())

    private suspend fun sets(session: Long) = repo.observeSessionSets(session).first()

    private suspend fun block(session: Long, position: Int, exerciseId: Long) =
        sets(session).filter { it.exercisePosition == position && it.exerciseId == exerciseId }.sortedBy { it.setIndex }

    /** Logs every set of the session at [reps] (seconds, for a hold) and its own load, and seals it. */
    private suspend fun logAll(session: Long, reps: Int) {
        sets(session).forEach { set ->
            val exercise = db.exerciseDao().byId(set.exerciseId)!!
            if (exercise.metric == "HOLD") repo.updateHoldSet(set.id, reps, set.weightKg, done = true)
            else repo.updateSet(set.id, reps, set.weightKg, done = true)
        }
        repo.completeSession(session)
    }

    @Test
    fun everyLeverAndSchemeBeginsExactlyAsWrittenWhateverTheEngineWouldHaveChanged() = runBlocking {
        val catalogue = db.exerciseDao().observeAll().first()
        val byLever = catalogue.groupBy { e ->
            val metric = runCatching { ExerciseMetric.valueOf(e.metric) }.getOrDefault(ExerciseMetric.REPS)
            Progression.leverFor(e.name, e.isWeighted, metric)
        }
        val chosen = Progression.Lever.entries.mapNotNull { lever -> byLever[lever]?.firstOrNull { it.id != pullUp && it.id != row } }
        assertTrue("the catalogue has the main levers", chosen.size >= 3)
        val straight = chosen.map { e ->
            val hold = e.metric == "HOLD"
            entry(e.id, sets = 3, reps = if (hold) 30 else 8, kg = if (hold) null else 20.0)
        }
        val owners = SetScheme(type = SchemeType.TOP_BACKOFF, topReps = 2..4, backoffSets = 2, backoffFixedKg = 15.0, backoffReps = 4..5)
        val drops = SetScheme(type = SchemeType.DROPS, dropSets = 1, dropCount = 1)
        val rite = rite(*straight.toTypedArray(), entry(pullUp, 3, 2, 22.0, owners), entry(row, 3, 8, 60.0, drops))
        // Two cleared workouts at the top of every range: the engine would have moved all of it.
        repeat(2) { logAll(repo.startSessionFromPreset(rite), reps = 40) }

        val session = repo.startSessionFromPreset(rite)
        straight.forEachIndexed { position, written ->
            val hold = db.exerciseDao().byId(written.exerciseId)!!.metric == "HOLD"
            block(session, position, written.exerciseId).forEach { set ->
                assertEquals("lever ${chosen[position].name}", written.targetWeightKg, set.weightKg)
                if (hold) {
                    assertEquals(0, set.reps)
                    assertEquals(written.targetReps, set.durationSec)
                } else {
                    assertEquals(written.targetReps, set.reps)
                }
            }
        }
        val top = block(session, straight.size, pullUp)
        assertEquals(listOf(SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF), top.map { it.kind })
        assertEquals(listOf(22.0, 15.0, 15.0), top.map { it.weightKg })
        assertEquals(2, top.first().reps)
        val dropped = block(session, straight.size + 1, row)
        assertEquals(listOf(SetKind.WORKING, SetKind.WORKING, SetKind.WORKING, SetKind.DROP), dropped.map { it.kind })
        assertEquals(listOf(60.0, 60.0, 60.0), dropped.take(3).map { it.weightKg })
        assertEquals(listOf(8, 8, 8, 8), dropped.map { it.reps })
        // Not vacuous: the engine does have something to say about this routine.
        assertTrue(repo.trialSuggestions(session).isNotEmpty())
    }

    @Test
    fun acceptingChangesOnlyTheUnloggedSetsOfThatBlock() = runBlocking {
        val rite = rite(entry(row), entry(pullUp, kg = null, reps = 6))
        logAll(repo.startSessionFromPreset(rite), reps = 8)
        val session = repo.startSessionFromPreset(rite)
        val key = repo.blockOf(session, row)
        val suggestion = repo.trialSuggestions(session).getValue(key)
        assertTrue(suggestion.movesFigures)
        val others = block(session, 1, pullUp).map { it.reps to it.weightKg }
        // One set is logged before the lifter accepts.
        val first = block(session, 0, row).first()
        repo.updateSet(first.id, 8, 60.0, done = true)

        val accepted = repo.acceptSuggestion(session, key)
        assertNotNull(accepted)
        val after = block(session, 0, row)
        assertTrue(after.first().done)
        assertEquals(60.0, after.first().weightKg!!, 0.0)
        assertEquals(8, after.first().reps)
        after.drop(1).forEach {
            assertEquals(suggestion.weightKg!!, it.weightKg!!, 0.0)
            assertEquals(suggestion.reps, it.reps)
            assertFalse(it.done)
        }
        assertEquals("another block is left alone", others, block(session, 1, pullUp).map { it.reps to it.weightKg })
        // What was accepted is no longer on offer for the block's pending sets.
        assertFalse(suggestion.stillOffered(sets(session).filter { it.blockKey == key }))
    }

    @Test
    fun aDropChainFollowsTheAcceptedLoad() = runBlocking {
        val drops = SetScheme(type = SchemeType.DROPS, dropSets = 1, dropCount = 1)
        val rite = rite(entry(row, scheme = drops))
        logAll(repo.startSessionFromPreset(rite), reps = 8)
        val session = repo.startSessionFromPreset(rite)
        val key = repo.blockOf(session, row)
        val suggestion = repo.trialSuggestions(session).getValue(key)
        repo.acceptSuggestion(session, key)
        val after = block(session, 0, row)
        assertEquals(listOf(SetKind.WORKING, SetKind.WORKING, SetKind.WORKING, SetKind.DROP), after.map { it.kind })
        after.take(3).forEach { assertEquals(suggestion.weightKg!!, it.weightKg!!, 0.0) }
        assertTrue("the drop is lighter than the accepted load", after.last().weightKg!! < suggestion.weightKg!!)
    }

    @Test
    fun twoBlocksOfTheSameMovementGetTheirOwnSuggestions() = runBlocking {
        val rite = rite(entry(row, kg = 60.0), entry(row, kg = 30.0))
        val first = repo.startSessionFromPreset(rite)
        // Block 0 cleared its 8s at 60; block 1 cleared 14s at 30.
        block(first, 0, row).forEach { repo.updateSet(it.id, 8, 60.0, done = true) }
        block(first, 1, row).forEach { repo.updateSet(it.id, 14, 30.0, done = true) }
        repo.completeSession(first)

        val session = repo.startSessionFromPreset(rite)
        // Both begin as written.
        assertEquals(listOf(60.0, 60.0, 60.0), block(session, 0, row).map { it.weightKg })
        assertEquals(listOf(30.0, 30.0, 30.0), block(session, 1, row).map { it.weightKg })
        val suggestions = repo.trialSuggestions(session)
        val a = suggestions.getValue(BlockKey(0, row))
        val b = suggestions.getValue(BlockKey(1, row))
        assertEquals(62.5, a.weightKg!!, 0.0)
        assertEquals(32.5, b.weightKg!!, 0.0)
        // Taking one leaves the other alone.
        repo.acceptSuggestion(session, BlockKey(1, row))
        assertEquals(listOf(60.0, 60.0, 60.0), block(session, 0, row).map { it.weightKg })
        assertEquals(listOf(32.5, 32.5, 32.5), block(session, 1, row).map { it.weightKg })
    }

    @Test
    fun aBlockWithNoHistoryOfItsOwnHasNoSuggestion() = runBlocking {
        // The history has one block of the movement; a second block added to the routine has none to read.
        val single = rite(entry(row))
        logAll(repo.startSessionFromPreset(single), reps = 8)
        val twice = repo.savePreset(single, "Pull day", "", null, listOf(entry(row), entry(row, kg = 30.0)))
        val session = repo.startSessionFromPreset(twice)
        val suggestions = repo.trialSuggestions(session)
        assertNotNull(suggestions[BlockKey(0, row)])
        assertNull(suggestions[BlockKey(1, row)])
    }

    @Test
    fun theEndOfWorkoutOfferSeesTheAcceptedFiguresAndKeepsThemInTheRoutine() = runBlocking {
        val rite = rite(entry(row))
        logAll(repo.startSessionFromPreset(rite), reps = 8)
        val session = repo.startSessionFromPreset(rite)
        val key = repo.blockOf(session, row)
        val suggestion = repo.trialSuggestions(session).getValue(key)
        assertNotNull(repo.acceptSuggestion(session, key))
        logAll(session, reps = suggestion.reps)
        val offer = repo.routineUpdateFor(session)!!
        repo.applyRoutineUpdate(offer.presetId, offer.changes)
        val written = repo.observePresets().first().single().entries.single()
        assertEquals(suggestion.weightKg!!, written.targetWeightKg!!, 0.0)
        assertEquals(suggestion.reps, written.targetReps)
        assertEquals(3, written.targetSets)
    }

    private fun figures(rows: List<com.ironvellum.app.data.db.SetLogEntity>) =
        rows.map { listOf(it.id, it.reps, it.weightKg, it.durationSec, it.done) }

    @Test
    fun undoPutsEveryUnloggedSetBackAsItWasHandEditsIncluded() = runBlocking {
        val rite = rite(entry(row))
        logAll(repo.startSessionFromPreset(rite), reps = 8)
        val session = repo.startSessionFromPreset(rite)
        val key = repo.blockOf(session, row)
        // The lifter has already changed the last set by hand before accepting.
        val edited = block(session, 0, row).last()
        repo.updateSet(edited.id, 5, 47.5, done = false)
        val before = db.sessionDao().setsFor(session)

        val snapshot = repo.acceptSuggestion(session, key)!!
        assertTrue("accepting changed something", figures(db.sessionDao().setsFor(session)) != figures(before))
        repo.undoSuggestion(session, snapshot)

        assertEquals(figures(before), figures(db.sessionDao().setsFor(session)))
        // The block is on offer again, with the original figures.
        assertTrue(repo.trialSuggestions(session).getValue(key).stillOffered(sets(session).filter { it.blockKey == key }))
    }

    @Test
    fun undoPutsATopSetAndItsBackoffsBackAsTheyWere() = runBlocking {
        val owners = SetScheme(type = SchemeType.TOP_BACKOFF, topReps = 2..4, backoffSets = 2, backoffFixedKg = 15.0, backoffReps = 4..5)
        val rite = rite(entry(pullUp, 3, 2, 22.0, owners))
        repeat(2) { logAll(repo.startSessionFromPreset(rite), reps = 40) }
        val session = repo.startSessionFromPreset(rite)
        val key = repo.blockOf(session, pullUp)
        val backoff = block(session, 0, pullUp)[1]
        repo.updateSet(backoff.id, 6, 17.5, done = false)
        val before = db.sessionDao().setsFor(session)

        val snapshot = repo.acceptSuggestion(session, key)!!
        assertTrue(figures(db.sessionDao().setsFor(session)) != figures(before))
        repo.undoSuggestion(session, snapshot)

        assertEquals(figures(before), figures(db.sessionDao().setsFor(session)))
        assertEquals(listOf(SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF), block(session, 0, pullUp).map { it.kind })
    }

    @Test
    fun undoLeavesASetLoggedAfterTheAcceptAlone() = runBlocking {
        val rite = rite(entry(row))
        logAll(repo.startSessionFromPreset(rite), reps = 8)
        val session = repo.startSessionFromPreset(rite)
        val key = repo.blockOf(session, row)
        val suggestion = repo.trialSuggestions(session).getValue(key)
        val snapshot = repo.acceptSuggestion(session, key)!!
        val first = block(session, 0, row).first()
        repo.updateSet(first.id, suggestion.reps, suggestion.weightKg, done = true)

        repo.undoSuggestion(session, snapshot)

        val after = block(session, 0, row)
        assertTrue(after.first().done)
        assertEquals(suggestion.weightKg!!, after.first().weightKg!!, 0.0)
        assertEquals(suggestion.reps, after.first().reps)
        after.drop(1).forEach {
            assertEquals(60.0, it.weightKg!!, 0.0)
            assertEquals(8, it.reps)
        }
    }

    @Test
    fun theUndoSnapshotSurvivesAndADismissalKeepsTheAcceptedFigures() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("suggestion_undos_test", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val rite = rite(entry(row))
            logAll(repo.startSessionFromPreset(rite), reps = 8)
            val session = repo.startSessionFromPreset(rite)
            val key = repo.blockOf(session, row)
            val suggestion = repo.trialSuggestions(session).getValue(key)
            val snapshot = repo.acceptSuggestion(session, key)!!
            SuggestionUndos(prefs).save(session, key, snapshot)
            SuggestionUndos(prefs).save(session, BlockKey(3, 9), listOf(SetFigures(1, 2, null, 30)))
            // A new store (a new process) reads the same snapshot back, nulls included.
            assertEquals(snapshot, SuggestionUndos(prefs).snapshots(session).getValue(key))
            assertEquals(listOf(SetFigures(1, 2, null, 30)), SuggestionUndos(prefs).snapshots(session).getValue(BlockKey(3, 9)))
            assertTrue("another trial is untouched", SuggestionUndos(prefs).snapshots(session + 1).isEmpty())

            // Dismiss: the confirmation goes, the accepted figures stay.
            SuggestionUndos(prefs).clear(session, key)
            assertFalse(key in SuggestionUndos(prefs).snapshots(session))
            assertTrue(BlockKey(3, 9) in SuggestionUndos(prefs).snapshots(session))
            block(session, 0, row).forEach { assertEquals(suggestion.weightKg!!, it.weightKg!!, 0.0) }
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun aDismissalIsKeptPerTrialAndBlock() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("suggestion_dismissals_test", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = SuggestionDismissals(prefs)
            assertTrue(store.dismissed(7).isEmpty())
            store.dismiss(7, BlockKey(1, 40))
            assertEquals(setOf(BlockKey(1, 40)), SuggestionDismissals(prefs).dismissed(7))
            assertTrue("another trial is untouched", store.dismissed(8).isEmpty())
            assertFalse(BlockKey(0, 40) in store.dismissed(7))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    private companion object {
        const val TEST_DB = "progression_suggestion_test.db"
    }
}
