package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.PresetEntity
import com.ironvellum.app.data.db.PresetEntryEntity
import com.ironvellum.app.domain.EntryAdjust
import com.ironvellum.app.domain.SessionOverride
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Today's fatigue advice begins a workout with changes (a swapped lift, a lightened one) without editing the routine,
 * and the end-of-workout offer to update the routine stays quiet about them.
 */
@RunWith(AndroidJUnit4::class)
class FatigueOverrideTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private val remembered = mutableMapOf<Long, SessionOverride>()

    private val adjustments = object : SessionAdjustments {
        override fun get(sessionId: Long) = remembered[sessionId] ?: SessionOverride.NONE
        override fun put(sessionId: Long, override: SessionOverride) {
            remembered[sessionId] = override
        }
    }

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db, adjustments = adjustments)
        repo.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private class Rite(val presetId: Long, val a: Long, val b: Long, val c: Long, val entryA: Long, val entryB: Long)

    private suspend fun rite(): Rite {
        val (a, b, c) = db.exerciseDao().observeAll().first()
            .filter { it.metric == "REPS" && it.category.isEmpty() }
            .take(3)
            .map { it.id }
        val presetId = db.presetDao().insertPreset(PresetEntity(name = "Push Strength", note = "", scheduledDay = null))
        db.presetDao().insertEntries(
            listOf(
                PresetEntryEntity(presetId = presetId, exerciseId = a, targetSets = 3, targetReps = 10, targetWeightKg = 40.0, modifiers = "", position = 0),
                PresetEntryEntity(presetId = presetId, exerciseId = b, targetSets = 3, targetReps = 8, targetWeightKg = null, modifiers = "", position = 1),
            ),
        )
        val entries = db.presetDao().presetWithEntries(presetId)!!.entries.sortedBy { it.position }
        return Rite(presetId, a, b, c, entries[0].id, entries[1].id)
    }

    private val swapTo = { c: Long -> EntryAdjust.Swap(exerciseId = c, exerciseName = "Stand-in", modifiers = "", sets = 3, reps = 12, weightKg = null) }

    @Test
    fun aSwapAndALightenedLiftStartTheWorkoutAndLeaveTheRoutineAlone() = runBlocking {
        val r = rite()
        val before = db.presetDao().presetWithEntries(r.presetId)!!.entries.sortedBy { it.position }

        val override = SessionOverride(mapOf(r.entryA to EntryAdjust(swap = swapTo(r.c)), r.entryB to EntryAdjust(lighten = true)))
        val sessionId = repo.startSessionFromPreset(r.presetId, override)

        val sets = db.sessionDao().setsFor(sessionId)
        val first = sets.filter { it.exercisePosition == 0 }
        val second = sets.filter { it.exercisePosition == 1 }
        assertEquals("the stand-in, with its own sets and reps and none of the old lift's load", List(3) { r.c }, first.map { it.exerciseId })
        assertEquals(List(3) { 12 }, first.map { it.reps })
        assertEquals(List(3) { null }, first.map { it.weightKg })
        assertEquals("one set fewer", 2, second.size)
        assertEquals("one rep less, so one more in reserve", List(2) { 7 }, second.map { it.reps })

        val after = db.presetDao().presetWithEntries(r.presetId)!!.entries.sortedBy { it.position }
        assertEquals("the routine is never edited", before, after)
        assertEquals(override.entries, adjustments.get(sessionId).entries)
    }

    @Test
    fun theEndOfWorkoutOfferStaysQuietAboutWhatTheLifterChoseOnToday() = runBlocking {
        val r = rite()
        val override = SessionOverride(mapOf(r.entryA to EntryAdjust(swap = swapTo(r.c)), r.entryB to EntryAdjust(lighten = true)))
        val sessionId = repo.startSessionFromPreset(r.presetId, override)
        db.sessionDao().setsFor(sessionId).forEach { repo.updateSet(it.id, it.reps, it.weightKg, done = true) }
        repo.completeSession(sessionId)

        assertNull("no swap, no set trim, no addition is offered back to the routine", repo.routineUpdateFor(sessionId))
    }

    @Test
    fun withoutTheOverrideRememberedTheSameWorkoutWouldHaveBeenOffered() = runBlocking {
        val r = rite()
        val override = SessionOverride(mapOf(r.entryA to EntryAdjust(swap = swapTo(r.c)), r.entryB to EntryAdjust(lighten = true)))
        val sessionId = repo.startSessionFromPreset(r.presetId, override)
        db.sessionDao().setsFor(sessionId).forEach { repo.updateSet(it.id, it.reps, it.weightKg, done = true) }
        repo.completeSession(sessionId)
        remembered.clear()

        val offer = repo.routineUpdateFor(sessionId)
        assertNotNull(offer)
        assertEquals("the swap is what the override was silencing", r.c, offer!!.swaps.single().toId)
    }

    @Test
    fun noOverrideStartsTheWorkoutExactlyAsTheRoutineWritesIt() = runBlocking {
        val r = rite()
        val sessionId = repo.startSessionFromPreset(r.presetId)
        val sets = db.sessionDao().setsFor(sessionId)
        assertEquals(listOf(r.a, r.a, r.a, r.b, r.b, r.b), sets.sortedWith(compareBy({ it.exercisePosition }, { it.setIndex })).map { it.exerciseId })
        assertEquals(List(3) { 10 } + List(3) { 8 }, sets.sortedWith(compareBy({ it.exercisePosition }, { it.setIndex })).map { it.reps })
        assertEquals(SessionOverride.NONE.entries, adjustments.get(sessionId).entries)
    }

    private companion object {
        const val TEST_DB = "fatigue_override_test.db"
    }
}
