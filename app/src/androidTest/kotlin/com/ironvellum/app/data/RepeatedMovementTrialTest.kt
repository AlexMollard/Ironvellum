package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.BlockKey
import com.ironvellum.app.domain.LoadKit
import com.ironvellum.app.domain.RoutineUpdate
import com.ironvellum.app.domain.SchemeType
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.SetScheme
import com.ironvellum.app.domain.Supersets
import com.ironvellum.app.domain.TrialDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A rite that names one movement twice: the owner's "Back + Chest Volume (dip bars)" holds two Push-up entries, one
 * "weighted, incline" and one "weighted, deficit, elevated". Each entry is a block of its own in the trial, with its own
 * sets, load and modifiers, and every write to one block leaves the other alone. Per-lift records and strength
 * still read both blocks together; that side is in the domain tests.
 */
@RunWith(AndroidJUnit4::class)
class RepeatedMovementTrialTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var dip = 0L
    private var pushUp = 0L
    private var row = 0L

    private val incline = "weighted, incline"
    private val deficit = "weighted, deficit, elevated"

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db, loadKit = { LoadKit.NONE })
        repo.ensureSeeded()
        repo.addStat(weightKg = 80.0, bodyFatPct = 14.0)
        dip = db.exerciseDao().byName("Dip")!!.id
        pushUp = db.exerciseDao().byName("Push-up")!!.id
        row = db.exerciseDao().byName("Inverted Row")!!.id
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private fun entry(id: Long, sets: Int, kg: Double?, modifiers: String = "", scheme: SetScheme = SetScheme()) =
        Repository.PresetDraftEntry(id, sets, 8, kg, modifiers = modifiers, scheme = scheme)

    /** Row, Push-up (incline), Dip, Push-up (deficit): the movement twice, a different one between the two. */
    private suspend fun routine(second: Repository.PresetDraftEntry = entry(pushUp, 3, 10.0, deficit)): Long =
        repo.savePreset(
            null, "Back + Chest Volume", "", null,
            listOf(entry(row, 3, null), entry(pushUp, 3, 10.0, incline), entry(dip, 3, null), second),
        )

    private suspend fun sets(session: Long) = repo.observeSessionSets(session).first()

    private suspend fun blockSets(session: Long, position: Int) = sets(session).filter { it.exercisePosition == position }

    @Test
    fun theRoutineStartsAsFourBlocksAndTheMovementTwiceKeepsEachEntrysOwnModifiersAndSets() = runBlocking {
        val session = repo.startSessionFromPreset(routine())
        val all = sets(session)
        assertEquals(12, all.size)
        val blocks = Supersets.blocks(all)
        assertEquals(listOf(row, pushUp, dip, pushUp), blocks.map { it.exerciseId })
        assertEquals(listOf(0, 1, 2, 3), blocks.map { it.position })
        assertEquals(listOf(incline, deficit), blocks.filter { it.exerciseId == pushUp }.map { it.sets.first().modifiers })
        blocks.forEach { block -> assertEquals(listOf(0, 1, 2), block.sets.map { it.setIndex }) }
        assertEquals("every set of a block shares its modifiers", setOf(incline), blockSets(session, 1).map { it.modifiers }.toSet())
        assertEquals(setOf(deficit), blockSets(session, 3).map { it.modifiers }.toSet())
    }

    @Test
    fun theModifiersOfOneBlockAreEditedWithoutTouchingTheOther() = runBlocking {
        val session = repo.startSessionFromPreset(routine())
        repo.setExerciseModifiers(session, BlockKey(3, pushUp), "weighted, deficit")
        assertEquals(setOf("weighted, deficit"), blockSets(session, 3).map { it.modifiers }.toSet())
        assertEquals(setOf(incline), blockSets(session, 1).map { it.modifiers }.toSet())
    }

    @Test
    fun aSetAddedToABlockLandsInThatBlockAndNumbersFromItsOwnCount() = runBlocking {
        val session = repo.startSessionFromPreset(routine())
        val added = repo.addExtraSet(session, pushUp, reps = 8, weightKg = 10.0, modifiers = deficit, atPosition = 3)
        val second = blockSets(session, 3)
        assertEquals(4, second.size)
        assertEquals(listOf(0, 1, 2, 3), second.map { it.setIndex })
        assertTrue(second.any { it.id == added })
        assertEquals("the first Push-up block is untouched", 3, blockSets(session, 1).size)
        // Without a position the movement's first block takes it, as it always did for a movement held once.
        repo.addExtraSet(session, pushUp, reps = 8, weightKg = 10.0, modifiers = "")
        assertEquals(4, blockSets(session, 1).size)
        assertEquals(4, blockSets(session, 3).size)
    }

    @Test
    fun removingASetRenumbersOnlyItsOwnBlock() = runBlocking {
        val session = repo.startSessionFromPreset(routine())
        val middle = blockSets(session, 3)[1]
        val removed = repo.removeSet(middle.id)!!
        assertEquals(listOf(0, 1), blockSets(session, 3).map { it.setIndex })
        assertEquals(listOf(0, 1, 2), blockSets(session, 1).map { it.setIndex })
        // Undo puts it back in its own block, in its place.
        assertTrue(repo.restoreSets(removed))
        assertEquals(listOf(0, 1, 2), blockSets(session, 3).map { it.setIndex })
        assertEquals(middle.id, blockSets(session, 3)[1].id)
        // Taking a whole block out leaves its namesake.
        repo.removeSessionExercise(session, BlockKey(3, pushUp))
        assertTrue(blockSets(session, 3).isEmpty())
        assertEquals(3, blockSets(session, 1).size)
    }

    @Test
    fun aLoadTypedOnOneBlockDoesNotTouchTheOtherBlocksFigures() = runBlocking {
        val session = repo.startSessionFromPreset(routine())
        val second = blockSets(session, 3)
        // A tick carries its figures to the later sets of its own block only.
        repo.updateSet(second[0].id, reps = 12, weightKg = 15.0, done = true)
        assertEquals(listOf(10.0, 10.0, 10.0), blockSets(session, 1).map { it.weightKg })
        assertEquals(listOf(8, 8, 8), blockSets(session, 1).map { it.reps })
        assertTrue(blockSets(session, 3).drop(1).all { it.weightKg == 15.0 && it.reps == 12 })
    }

    @Test
    fun takingTheLoadOffOneBlockDropsWeightedFromThatBlockAlone() = runBlocking {
        val session = repo.startSessionFromPreset(routine())
        blockSets(session, 3).forEach { repo.updateSet(it.id, it.reps, null, done = false) }
        assertEquals(setOf("deficit, elevated"), blockSets(session, 3).map { it.modifiers }.toSet())
        assertEquals(setOf(incline), blockSets(session, 1).map { it.modifiers }.toSet())
    }

    @Test
    fun movingLinkingAndUnlinkingTheSecondBlockKeepsTheTwoBlocksApart() = runBlocking {
        val session = repo.startSessionFromPreset(routine())
        repo.moveSessionExercise(session, exercisePosition = 3, up = true)
        val moved = Supersets.blocks(sets(session))
        assertEquals(listOf(row, pushUp, pushUp, dip), moved.map { it.exerciseId })
        assertEquals(listOf(incline, deficit), moved.filter { it.exerciseId == pushUp }.map { it.sets.first().modifiers })
        assertEquals(listOf(3, 3, 3, 3), moved.map { it.sets.size })
        assertTrue(repo.linkSupersetWithNext(session, moved[2].key))
        val linked = Supersets.blocks(sets(session))
        assertEquals(listOf(null, null, 1, 1), linked.map { it.group })
        assertEquals("the first Push-up block did not join the superset", setOf(incline), linked[1].sets.map { it.modifiers }.toSet())
        repo.unlinkSuperset(session, linked[2].key)
        assertTrue(sets(session).all { it.supersetGroup == null })
    }

    @Test
    fun aTopSetIsMadeInTheBlockAskedFor() = runBlocking {
        val session = repo.startSessionFromPreset(routine())
        assertTrue(repo.makeTopSet(session, BlockKey(3, pushUp)))
        assertEquals(listOf(SetKind.TOP, SetKind.BACKOFF, SetKind.BACKOFF), blockSets(session, 3).map { it.kind })
        assertEquals("the other block stays straight", setOf(SetKind.WORKING), blockSets(session, 1).map { it.kind }.toSet())
    }

    @Test
    fun theWarmupRampIsAddedToTheBlockAskedFor() = runBlocking {
        val session = repo.startSessionFromPreset(routine())
        val made = repo.addWarmupRamp(session, BlockKey(3, pushUp))
        assertTrue("a loaded movement gets a ramp", made.isNotEmpty())
        val second = blockSets(session, 3)
        assertEquals(made, second.take(made.size).map { it.id })
        assertEquals(listOf(0, 1, 2, 3, 4, 5).take(second.size), second.map { it.setIndex })
        assertEquals("the first Push-up block has no ramp", 3, blockSets(session, 1).size)
        assertTrue(blockSets(session, 1).none { it.warmup })
    }

    @Test
    fun aSchemeBelongsToItsOwnEntryNotToTheMovement() = runBlocking {
        val topped = SetScheme(type = SchemeType.TOP_BACKOFF)
        val session = repo.startSessionFromPreset(routine(second = entry(pushUp, 3, 10.0, deficit, topped)))
        assertEquals(setOf(BlockKey(3, pushUp)), repo.trialSchemes(session).keys)
        assertEquals(setOf(SetKind.WORKING), blockSets(session, 1).map { it.kind }.toSet())
        assertEquals(setOf(SetKind.TOP, SetKind.BACKOFF), blockSets(session, 3).map { it.kind }.toSet())
    }

    @Test
    fun theFinishedSessionOffersEachEntryItsOwnChangeAndSealsBothBlocks() = runBlocking {
        val presetId = routine()
        val session = repo.startSessionFromPreset(presetId)
        // The incline block goes up to 12.5 kg; the deficit block is done as written.
        blockSets(session, 1).forEach { repo.updateSet(it.id, 8, 12.5, done = true) }
        blockSets(session, 3).forEach { repo.updateSet(it.id, 8, 10.0, done = true) }
        repo.completeSession(session)
        val sealed = sets(session)
        assertEquals("both blocks were logged", 6, sealed.filter { it.exerciseId == pushUp && it.done }.size)
        assertEquals(setOf(incline, deficit), sealed.filter { it.exerciseId == pushUp }.map { it.modifiers }.toSet())
        val offer = repo.routineUpdateFor(session)!!
        val change = offer.changes.single()
        assertEquals(listOf(RoutineUpdate.Field.LOAD), change.fields)
        assertEquals(incline, change.before.modifiers)
        assertEquals(12.5, change.after.targetWeightKg!!, 0.0)
        // Saving it back moves the incline entry only.
        repo.applyRoutineUpdate(presetId, offer.changes)
        val entries = repo.observePresets().first().single { it.id == presetId }.entries.filter { it.exerciseId == pushUp }
        assertEquals(listOf(12.5, 10.0), entries.map { it.targetWeightKg })
        assertEquals(listOf(incline, deficit), entries.map { it.modifiers })
    }

    @Test
    fun amendingASealedSessionKeepsTheTwoBlocksAndTheirModifiers() = runBlocking {
        val session = repo.startSessionFromPreset(routine())
        sets(session).forEach { repo.updateSet(it.id, 8, it.weightKg, done = true) }
        repo.completeSession(session)
        val history = repo.observeHistory().first().first { it.first.id == session }.second
        val draft = TrialDraft.of(history)
        assertEquals(listOf(row, pushUp, dip, pushUp), draft.blocks.map { it.exerciseId })
        assertEquals(listOf(incline, deficit), draft.blocks.filter { it.exerciseId == pushUp }.map { it.modifiers })
        // One rep more on the second Push-up block, then Save: nothing is refused, nothing is folded.
        repo.editSealedTrial(session, draft.updateSet(3, 0) { it.copy(reps = 9) })
        val after = sets(session)
        assertEquals(listOf(0, 1, 2, 3), after.map { it.exercisePosition }.distinct())
        assertEquals(setOf(deficit), after.filter { it.exercisePosition == 3 }.map { it.modifiers }.toSet())
        assertEquals(9, after.first { it.exercisePosition == 3 && it.setIndex == 0 }.reps)
        assertEquals(setOf(incline), after.filter { it.exercisePosition == 1 }.map { it.modifiers }.toSet())
    }

    @Test
    fun anEditedRoutineKeepsEachEntrysSupersetAndSchemeWhenTheForgeRewritesIt() = runBlocking {
        val topped = SetScheme(type = SchemeType.TOP_BACKOFF)
        val presetId = routine(second = entry(pushUp, 3, 10.0, deficit, topped))
        val planned = repo.observePresets().first().single { it.id == presetId }.entries
            .map { e -> com.ironvellum.app.domain.PlannedEntry(db.exerciseDao().byId(e.exerciseId)!!.name, e.targetSets, e.targetReps, e.targetWeightKg, e.modifiers) }
        repo.savePlannedPreset(presetId, com.ironvellum.app.domain.PlannedPreset("Back + Chest Volume", "", null, planned))
        val after = repo.observePresets().first().single { it.id == presetId }.entries.filter { it.exerciseId == pushUp }
        assertEquals("the scheme stays on the entry that had it", listOf(SchemeType.STRAIGHT, SchemeType.TOP_BACKOFF), after.map { it.scheme.type })
    }

    private companion object {
        const val TEST_DB = "repeated_movement_test.db"
    }
}
