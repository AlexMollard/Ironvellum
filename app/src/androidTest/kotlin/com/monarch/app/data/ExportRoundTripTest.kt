package com.monarch.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Export then import must return the hunter's training intact.
 *
 * This is the recovery path: it is what the docs point at when a migration
 * fails, when a device is replaced, and when someone wants their data out. The
 * writer and reader have JVM tests for their JSON shape, but the round trip
 * that matters runs through Room — `importArchive` clears the user's rows and
 * rebuilds them inside one transaction — so only an instrumented test proves
 * the thing a hunter actually relies on.
 *
 * Runs against an isolated database file: deleting the live one underneath the
 * running app is the divergence trap documented on DbSnapshot.
 */
@RunWith(AndroidJUnit4::class)
class ExportRoundTripTest {

    private lateinit var context: Context
    private lateinit var db: MonarchDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runTest {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = MonarchDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun anExportedArchiveRestoresTheTrainingItDescribes() = runTest {
        // A completed session with real set data, plus a body stat, because the
        // two travel through different parts of the archive.
        val preset = db.presetDao().observePresets().first().first().preset
        val sessionId = repo.startSessionFromPreset(preset.id)
        val sets = db.sessionDao().setsFor(sessionId)
        assertTrue("the preset must auto-fill sets to log", sets.isNotEmpty())
        repo.updateSet(sets.first().id, reps = 8, weightKg = 20.0, done = true)
        repo.completeSession(sessionId)
        repo.addStat(weightKg = 82.5, bodyFatPct = 14.0)

        val completedBefore = db.sessionDao().completedCount()
        val setsBefore = db.sessionDao().setsFor(sessionId).size
        val statsBefore = db.statDao().observeAll().first().size
        val xpBefore = repo.observeProfile().first()!!.totalXp
        assertTrue("the fixture must have produced XP to restore", xpBefore > 0)

        val archive = repo.exportJson()

        // Wipe the way a fresh install would, then restore from the archive.
        db.presetDao().clearAll()
        db.sessionDao().clearAll()
        db.statDao().clearAll()
        assertEquals("the wipe must actually clear the record", 0, db.sessionDao().completedCount())

        val result = repo.importArchive(archive)
        assertTrue("import failed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        assertEquals("completed sessions", completedBefore, db.sessionDao().completedCount())
        assertEquals("body stats", statsBefore, db.statDao().observeAll().first().size)
        assertEquals("total XP", xpBefore, repo.observeProfile().first()!!.totalXp)

        // The sets are the part a hunter would actually miss: weight and reps
        // per set are the training record, not a derived number.
        val restored = db.sessionDao().observeCompletedWithSets().first().flatMap { it.sets }
        assertEquals("restored set rows", setsBefore, restored.size)
        // Which sets were LOGGED is the training record; an archive that
        // restores the rows but forgets the flag looks intact and is not.
        val loggedSets = restored.filter { it.done }
        assertEquals("the archive must remember which sets were logged", 1, loggedSets.size)
        val logged = loggedSets.single()
        assertEquals(8, logged.reps)
        assertEquals(20.0, logged.weightKg!!, 0.001)
    }

    @Test
    fun aRejectedArchiveLeavesTheExistingTrainingAlone() = runTest {
        // Same fixture, smaller: one completed session is enough to notice loss.
        val preset = db.presetDao().observePresets().first().first().preset
        val sessionId = repo.startSessionFromPreset(preset.id)
        repo.updateSet(db.sessionDao().setsFor(sessionId).first().id, reps = 5, weightKg = 40.0, done = true)
        repo.completeSession(sessionId)
        val completedBefore = db.sessionDao().completedCount()
        val setsBefore = db.sessionDao().setsFor(sessionId).size
        assertTrue("the fixture must have training to lose", completedBefore > 0 && setsBefore > 0)

        // Anything a hunter could plausibly hand the importer: a foreign
        // document, a truncated archive, and one whose training mode this
        // build has never heard of.
        val hostile = listOf(
            "not json at all",
            "{\"formatVersion\":1,\"exportedAtMs\":1,\"profile\":{\"name\":\"x\"",
            "{\"formatVersion\":1,\"exportedAtMs\":1,\"profile\":{\"name\":\"x\",\"totalXp\":1}," +
                "\"trainingMode\":\"POWERBUILDING\",\"presets\":[],\"sessions\":[],\"stats\":[],\"titles\":[]}",
        )
        for (json in hostile) {
            val result = repo.importArchive(json)
            assertTrue("this must be refused, not applied: $json", result.isFailure)
            // A rejected file must cost the hunter nothing. Two independent
            // things guarantee that — validation runs before the clears, and
            // the clears run inside a transaction — so mutating either one
            // alone still passes here (measured). This fails when BOTH are
            // gone, which is the only state where training is actually lost.
            assertEquals(
                "a refused import must not touch the completed sessions",
                completedBefore,
                db.sessionDao().completedCount(),
            )
            assertEquals(
                "a refused import must not touch the logged sets",
                setsBefore,
                db.sessionDao().setsFor(sessionId).size,
            )
        }
    }

    @Test
    fun anArchiveFromAnotherInstallBringsItsMovementsWithoutDuplicatingOurs() = runTest {
        val preset = db.presetDao().observePresets().first().first().preset
        val sessionId = repo.startSessionFromPreset(preset.id)
        val firstSet = db.sessionDao().setsFor(sessionId).first()
        val movement = db.exerciseDao().byId(firstSet.exerciseId)!!.name
        repo.updateSet(firstSet.id, reps = 6, weightKg = 30.0, done = true)
        repo.completeSession(sessionId)
        val archive = repo.exportJson()
        val catalogueBefore = db.exerciseDao().count()

        // Exercise ids differ between installs, so the importer matches on NAME
        // and creates what it cannot find. The premise is asserted against the
        // database, not assumed: the catalogue is seeded from two places
        // (Seed.kt and the skill tree), so "surely absent" guesses are wrong.
        val foreign = "Sandbag Zercher Carry"
        assertEquals("the fixture name must be unknown here", null, db.exerciseDao().byName(foreign))
        val fromElsewhere = archive.replace(movement, foreign)
        assertTrue("the rename must have taken", fromElsewhere.contains(foreign))

        val imported = repo.importArchive(fromElsewhere)
        assertTrue("import failed: ${imported.exceptionOrNull()?.message}", imported.isSuccess)
        assertEquals(
            "the foreign movement must be created, exactly once",
            catalogueBefore + 1,
            db.exerciseDao().count(),
        )
        val restoredNames = db.sessionDao().observeCompletedWithSets().first()
            .flatMap { it.sets }.map { it.exerciseId }.distinct()
            .mapNotNull { db.exerciseDao().byId(it)?.name }
        assertTrue("the restored sets must reference it: $restoredNames", restoredNames.contains(foreign))

        // Now the trap: the same archive SHOUTED is still our own movement.
        // Matching case-sensitively would mint a duplicate catalogue row on
        // every restore — which is how a catalogue silently fills with
        // near-identical movements.
        val shouted = archive.replace(movement, movement.uppercase())
        assertTrue(repo.importArchive(shouted).isSuccess)
        assertEquals(
            "a case variant of a known movement must not create a second row",
            catalogueBefore + 1,
            db.exerciseDao().count(),
        )
    }

    private companion object {
        /** Never the app's live database. */
        const val TEST_DB = "monarch-export-roundtrip-test.db"
    }
}
