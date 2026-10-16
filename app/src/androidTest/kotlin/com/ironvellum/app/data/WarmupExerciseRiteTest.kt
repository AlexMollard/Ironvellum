package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A rite entry marked as a warm-up exercise is a whole movement done before the first working set. It is never a set
 * row, so it earns no XP, volume, coverage, peak, progression change or deed, and no offer to update the rite ever
 * names it; the rewrites of the rite (the editor, the Forge, the archive) keep the flag and keep it in front.
 */
@RunWith(AndroidJUnit4::class)
class WarmupExerciseRiteTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private lateinit var lifts: List<Long>
    private lateinit var liftNames: List<String>

    @Before
    fun setUp() = runBlocking {
        open()
    }

    private suspend fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        repo.addStat(weightKg = 80.0, bodyFatPct = 14.0)
        val reps = db.exerciseDao().observeAll().first().filter { it.metric == "REPS" && it.category.isEmpty() }.take(3)
        lifts = reps.map { it.id }
        liftNames = reps.map { it.name }
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private fun draft(exerciseId: Long, sets: Int, reps: Int, warmup: Boolean = false) =
        Repository.PresetDraftEntry(exerciseId = exerciseId, targetSets = sets, targetReps = reps, targetWeightKg = null, warmup = warmup)

    /** Saves a rite of two worked lifts and, when [withDrill], a warm-up exercise listed last on purpose. */
    private suspend fun riteWith(withDrill: Boolean): Long = repo.savePreset(
        presetId = null,
        name = "Legs",
        note = "",
        scheduledDay = null,
        entries = listOfNotNull(draft(lifts[0], 3, 8), draft(lifts[1], 3, 10), if (withDrill) draft(lifts[2], 2, 10, warmup = true) else null),
    )

    private suspend fun doEverySet(sessionId: Long) {
        db.sessionDao().setsFor(sessionId).forEach { repo.updateSet(it.id, it.reps, it.weightKg, done = true) }
    }

    @Test
    fun aWarmupLeadsTheRiteAndIsNeverASetRow() = runBlocking {
        val presetId = riteWith(withDrill = true)
        val stored = db.presetDao().presetWithEntries(presetId)!!.entries.sortedBy { it.position }
        assertEquals("the warm-up is saved at the head whatever order it came in", listOf(lifts[2], lifts[0], lifts[1]), stored.map { it.exerciseId })
        assertEquals(listOf(true, false, false), stored.map { it.warmup })
        assertEquals(listOf(true, false, false), repo.observePresets().first().single().entries.map { it.warmup })

        val sessionId = repo.startSessionFromPreset(presetId)
        val sets = db.sessionDao().setsFor(sessionId)
        assertTrue("the drill is not a set row, of any kind", sets.none { it.exerciseId == lifts[2] })
        assertEquals("the worked lifts keep their sets", 6, sets.size)
        assertEquals("and are numbered without the warm-up", listOf(0, 1), sets.map { it.exercisePosition }.distinct().sorted())
        assertEquals(listOf(lifts[2]), repo.trialWarmups(sessionId).map { it.exerciseId })
    }

    @Test
    fun aWarmupAddsNothingToWhatTheTrialPaysOrScores() = runBlocking {
        val plain = riteWith(withDrill = false).let { repo.startSessionFromPreset(it) }.also { doEverySet(it) }.let { repo.completeSession(it) }
        db.close()
        open()
        val warmed = riteWith(withDrill = true).let { repo.startSessionFromPreset(it) }.also { doEverySet(it) }.let { repo.completeSession(it) }
        assertEquals("same pay, bonuses included: the rite is as planned either way", plain.xpAwarded, warmed.xpAwarded)
        assertEquals(plain.strengthScore, warmed.strengthScore)
    }

    @Test
    fun noOfferToUpdateTheRiteIsMadeForAWarmup() = runBlocking {
        val presetId = riteWith(withDrill = true)
        val sessionId = repo.startSessionFromPreset(presetId)
        doEverySet(sessionId)
        repo.completeSession(sessionId)
        assertNull("done as planned, and the drill was not part of what was done", repo.routineUpdateFor(sessionId))
        assertTrue(db.presetDao().presetWithEntries(presetId)!!.entries.any { it.warmup })
    }

    @Test
    fun theForgeRewritesTheWorkedExercisesAndKeepsTheWarmups() = runBlocking {
        val presetId = riteWith(withDrill = true)
        repo.savePlannedPreset(
            presetId,
            PlannedPreset(
                name = "Legs",
                note = "",
                scheduledDay = null,
                entries = listOf(PlannedEntry(liftNames[0], sets = 5, reps = 5, targetWeightKg = null, modifiers = "")),
            ),
        )
        val stored = db.presetDao().presetWithEntries(presetId)!!.entries.sortedBy { it.position }
        assertEquals("the second worked lift was dropped by the Forge, the warm-up stays", listOf(lifts[2], lifts[0]), stored.map { it.exerciseId })
        assertEquals(listOf(true, false), stored.map { it.warmup })
        assertEquals(listOf(2, 5), stored.map { it.targetSets })
    }

    @Test
    fun theFlagSurvivesAnArchiveRoundTrip() = runBlocking {
        riteWith(withDrill = true)
        val archive = repo.exportJson()
        assertTrue(repo.importArchive(archive).isSuccess)
        assertEquals(listOf(true, false, false), repo.observePresets().first().single().entries.map { it.warmup })
    }

    @Test
    fun theSharedCodeCarriesTheWorkedPlanOnly() = runBlocking {
        riteWith(withDrill = true)
        val shared = repo.sharedRoutine().single()
        assertEquals(liftNames.take(2).toSet(), shared.entries.map { it.exerciseName }.toSet())
    }

    private companion object {
        const val TEST_DB = "warmup_exercise_rite_test.db"
    }
}
