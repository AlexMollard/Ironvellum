package com.ironvellum.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.data.db.IdleStateEntity
import com.ironvellum.app.data.db.MeasurementEntity
import com.ironvellum.app.data.db.OwnedCrestFrameEntity
import com.ironvellum.app.data.db.OwnedRelicEntity
import com.ironvellum.app.data.db.PresetEntryEntity
import com.ironvellum.app.data.db.PresetEntity
import com.ironvellum.app.domain.ExportWriter
import com.ironvellum.app.domain.Sex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Export then import must return the lifter's training intact.
 *
 * This is the recovery path: it is what the docs point at when a migration
 * fails, when a device is replaced, and when someone wants their data out. The
 * writer and reader have JVM tests for their JSON shape, but the round trip
 * that matters runs through Room — `importArchive` clears the user's rows and
 * rebuilds them inside one transaction — so only an instrumented test proves
 * the thing a lifter actually relies on.
 *
 * Runs against an isolated database file: deleting the live one underneath the
 * running app is the divergence trap documented on DbSnapshot.
 */
@RunWith(AndroidJUnit4::class)
class ExportRoundTripTest {

    private lateinit var context: Context
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() = runTest {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        // A fresh install no longer imposes a week, so the training these tests
        // export is written through the same path onboarding uses.
        repo.applyStarterTemplate()
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

        // The sets are the part a lifter would actually miss: weight and reps
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

        // Anything a lifter could plausibly hand the importer: a foreign
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
            // A rejected file must cost the lifter nothing. Two independent
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

    @Test
    fun aV5ArchiveRestoresIdleGachaCosmeticsProfileAndMovementMetadata() = runTest {
        // A user-created movement with real attributes: this is the row the
        // importer used to rebuild as a guessed PULL/weighted default.
        val custom = "Nordic Curl Progression"
        assertEquals(null, db.exerciseDao().byName(custom))
        val customId = db.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(name = custom, muscleGroup = "LEGS", isWeighted = false, metric = "DURATION", category = ""),
            ),
        ).single()
        val presetId = db.presetDao().insertPreset(PresetEntity(name = "Hinges", note = "", scheduledDay = null))
        db.presetDao().insertEntries(
            listOf(
                PresetEntryEntity(presetId = presetId, exerciseId = customId, targetSets = 3, targetReps = 30, targetWeightKg = null, modifiers = "", position = 0),
            ),
        )
        val sessionId = repo.startSessionFromPreset(presetId)
        repo.updateSet(db.sessionDao().setsFor(sessionId).first().id, reps = 1, weightKg = null, done = true)
        repo.completeSession(sessionId)

        // Idle/gacha/profile state is written last so completing the session
        // (which banks rolls and shadows itself) cannot perturb the values the
        // assertions below pin.
        repo.setHeight(181.5)
        repo.setSex(Sex.FEMALE)
        repo.setInkStyle(true)
        db.idleDao().upsert(
            IdleStateEntity(essence = 777, shadows = 4, relicMultiplier = 1.31, lastCollectedAtMs = 123),
        )
        repo.grantRoll(3)
        db.gachaDao().insertFrame(OwnedCrestFrameEntity(frameId = "ember", ownedAtMs = 10))
        db.gachaDao().insertFrame(OwnedCrestFrameEntity(frameId = "verdant", ownedAtMs = 20))
        assertTrue(repo.equipFrame("ember"))
        db.gachaDao().insertRelic(OwnedRelicEntity(name = "Old King's Whetstone", multiplier = 1.31, drawnAtMs = 30))

        val export = repo.exportArchive()
        assertTrue("export reported problems: ${export.problems}", export.problems.isEmpty())

        // The writer is hand-rolled and its own reader is tolerant, so a
        // malformed archive can round-trip inside this app and still be
        // unreadable by anything else the owner points at the file. A strict
        // parser is the only thing that catches a stray comma.
        val parsed = JSONObject(export.json)
        assertEquals(ExportWriter.FORMAT_VERSION, parsed.getInt("formatVersion"))
        assertTrue("the v5 sections must be present", parsed.has("idle") && parsed.has("gacha") && parsed.has("exercises"))

        // Target: a genuinely empty install.
        context.deleteDatabase(DEST_DB)
        val freshDb = IronvellumDatabase.create(context, DEST_DB)
        val freshRepo = Repository(freshDb)
        try {
            freshRepo.ensureSeeded()
            assertTrue("the fresh install must start without the custom movement", freshDb.exerciseDao().byName(custom) == null)
            assertTrue("the fresh install must start without idle state", freshDb.idleDao().get() == null)

            val result = freshRepo.importArchive(export.json)
            assertTrue("import failed: ${result.exceptionOrNull()?.message}", result.isSuccess)

            val profile = freshDb.profileDao().get()!!
            assertEquals(181.5, profile.heightCm!!, 0.001)
            assertEquals("FEMALE", profile.sex)
            assertTrue(profile.inkStyle)

            // Named: an unrestored bank otherwise reads as a bare NPE.
            val idle = freshDb.idleDao().get()
            assertNotNull("the archive must restore the idle bank", idle)
            idle!!
            assertEquals(777L, idle.essence)
            assertEquals(4, idle.shadows)
            assertEquals(1.31, idle.relicMultiplier, 0.0001)
            assertEquals(123L, idle.lastCollectedAtMs)
            assertEquals(3, freshDb.gachaDao().get()!!.rolls)
            assertEquals("ember", freshDb.gachaDao().get()!!.equippedFrame)
            assertEquals(setOf("ember", "verdant"), freshDb.gachaDao().ownedFrameIds().toSet())
            val relic = freshDb.gachaDao().observeRelics().first().single()
            assertEquals("Old King's Whetstone", relic.name)
            assertEquals(1.31, relic.multiplier, 0.0001)

            val restored = freshDb.exerciseDao().byName(custom)!!
            assertEquals("LEGS", restored.muscleGroup)
            assertFalse(restored.isWeighted)
            assertEquals("DURATION", restored.metric)
        } finally {
            freshDb.close()
            context.deleteDatabase(DEST_DB)
        }
    }

    @Test
    fun aV4ArchiveLeavesTheLocalIdleRowsUntouched() = runTest {
        // The v4 archive carries none of the idle/gacha/cosmetic keys, so
        // absence must read as "no information" — never as an empty set that
        // wipes banked essence, rolls and relics on a legitimate old backup.
        db.idleDao().upsert(
            IdleStateEntity(essence = 5_000, shadows = 2, relicMultiplier = 1.1, lastCollectedAtMs = 999),
        )
        repo.grantRoll(2)
        db.gachaDao().insertRelic(OwnedRelicEntity(name = "Local Relic", multiplier = 1.2, drawnAtMs = 5))
        db.gachaDao().insertFrame(OwnedCrestFrameEntity(frameId = "local", ownedAtMs = 6))

        val v4 = """
            {"formatVersion":4,"exportedAtMs":42,
             "profile":{"name":"Old Lifter","totalXp":55,"currentTitleId":null},
             "trainingMode":"STRENGTH",
             "presets":[],"sessions":[],"stats":[],"titles":[],"skills":[],"healthDays":[],
             "measurements":[]}
        """.trimIndent()

        val result = repo.importArchive(v4)
        assertTrue("import failed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        // Named, because a wiped row otherwise fails as a bare NPE that reads
        // like a broken test rather than a destroyed idle bank.
        val idle = db.idleDao().get()
        assertNotNull("a v4 import must not delete the idle row", idle)
        idle!!
        assertEquals(5_000L, idle.essence)
        assertEquals(2, idle.shadows)
        assertEquals(1.1, idle.relicMultiplier, 0.0001)
        assertEquals(999L, idle.lastCollectedAtMs)
        assertEquals(2, db.gachaDao().get()!!.rolls)
        assertEquals(listOf("Local Relic"), db.gachaDao().observeRelics().first().map { it.name })
        assertEquals(listOf("local"), db.gachaDao().ownedFrameIds())
    }

    @Test
    fun anUnparseableMeasurementSiteIsReportedNotSilentlyDropped() = runTest {
        // A site this build no longer parses must show up in the export's
        // problem list — a restore that loses rows without a word is the exact
        // failure mode this archive exists to prevent.
        db.measurementDao().insert(MeasurementEntity(site = "OBSOLETE_SITE", valueCm = 33.0, takenAtMs = 7))

        val export = repo.exportArchive()

        assertTrue(export.problems.any { it.contains("OBSOLETE_SITE") })
        assertFalse("the unparseable row must not travel as data", export.json.contains("OBSOLETE_SITE"))
    }

    /**
     * The CLOUD archive, not the local one. `BACK UP NOW` uploads
     * `exportArchive(includeDeviceOnly = false)`, so that exact shape is what
     * a lifter restores from onto a new phone — and nothing else tests it.
     * The private note, body readings, measurements, height, sex and Health
     * Connect days are promised never to leave the device (PRIVACY.md), so
     * the upload must carry none of them, and a restore must keep the
     * device's own copies rather than wiping them.
     */
    @Test
    fun theCloudArchiveRestoresTrainingWithoutCarryingDeviceOnlyData() = runTest {
        val preset = db.presetDao().observePresets().first().first().preset
        val sessionId = repo.startSessionFromPreset(preset.id)
        val sets = db.sessionDao().setsFor(sessionId)
        repo.updateSet(sets.first().id, reps = 9, weightKg = 22.5, done = true)
        // A static hold: its figure lives in durationSec, and a backup that
        // restored it as reps would resurrect the bug schema 24 fixed.
        repo.updateHoldSet(sets[1].id, seconds = 45, weightKg = null, done = true)
        repo.setSessionNote(sessionId, "public: felt strong")
        repo.setSessionPrivateNote(sessionId, "SECRET-SORE-ELBOW")
        repo.completeSession(sessionId)
        repo.addStat(weightKg = 79.1, bodyFatPct = 13.5)
        db.measurementDao().insert(MeasurementEntity(site = "WAIST", valueCm = 81.7, takenAtMs = 11))

        val completedBefore = db.sessionDao().completedCount()
        val setsBefore = db.sessionDao().setsFor(sessionId).size
        val statsBefore = db.statDao().observeAll().first().size
        val xpBefore = repo.observeProfile().first()!!.totalXp
        assertTrue("the fixture must have training to lose", completedBefore > 0 && setsBefore > 0)

        val cloud = repo.exportArchive(includeDeviceOnly = false)
        listOf(
            "SECRET-SORE-ELBOW", "\"privateNote\"", "79.1", "81.7", "\"stats\"", "\"measurements\"",
            "\"healthDays\"", "\"heightCm\"", "\"sex\"",
        ).forEach { trace ->
            assertFalse("the uploaded archive must carry no trace of $trace", cloud.json.contains(trace))
        }
        assertTrue("the public note is not private and must travel", cloud.json.contains("public: felt strong"))

        // Training is lost (a new phone); the body history stays on this one.
        db.presetDao().clearAll()
        db.sessionDao().clearAll()
        assertEquals("the wipe must actually clear the record", 0, db.sessionDao().completedCount())

        val result = repo.importArchive(cloud.json)
        assertTrue("restore failed: ${result.exceptionOrNull()?.message}", result.isSuccess)

        assertEquals("completed sessions", completedBefore, db.sessionDao().completedCount())
        assertEquals("total XP", xpBefore, repo.observeProfile().first()!!.totalXp)
        // Kept, not cleared: the backup never held them, so a restore must not
        // wipe the device's own copies.
        assertEquals("body readings on this device", statsBefore, db.statDao().observeAll().first().size)
        assertEquals("measurements on this device", 1, db.measurementDao().observeAll().first().size)

        val restoredSession = db.sessionDao().observeCompletedWithSets().first().single()
        assertEquals("restored set rows", setsBefore, restoredSession.sets.size)
        assertEquals("the public note must survive the round trip", "public: felt strong", restoredSession.session.note)
        // Empty, not the original: the cloud copy never held it.
        assertEquals("a restored cloud archive must not resurrect the note", "", restoredSession.session.privateNote)

        // The preset prescribes its own unlogged hold sets, so identify the
        // one this test actually logged.
        val hold = restoredSession.sets.single { it.done && it.durationSec != null }
        assertEquals("a hold must come back as seconds", 45, hold.durationSec)
        assertEquals("a hold must not come back as repetitions", 0, hold.reps)
        val lifted = restoredSession.sets.single { it.done && it.durationSec == null }
        assertEquals(9, lifted.reps)
        assertEquals(22.5, lifted.weightKg!!, 0.001)
    }

    private companion object {
        /** Never the app's live database. */
        const val TEST_DB = "ironvellum-export-roundtrip-test.db"

        /** Second install inside the same test process. */
        const val DEST_DB = "ironvellum-export-roundtrip-dest.db"
    }
}
