package com.ironvellum.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.Xp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Forward migration validation. Creates the schema at the CURRENT exported
 * version and re-opens it through the full registered chain via
 * runMigrationsAndValidate, which checks the resulting schema against Room's
 * expectations. When the version is bumped and a new migration is added to
 * MIGRATIONS, this test picks it up automatically: the new version's schema
 * JSON is created and validated, and the new migration must carry the old
 * schema to it or the validate step throws.
 *
 * LIMITATION: schema export only just turned on, so no historical JSONs exist
 * for versions 11..20. A true 11 -> current path cannot be tested until those
 * schemas are reconstructed; this test therefore proves the current schema is
 * valid and future migrations stay covered.
 */
@RunWith(AndroidJUnit4::class)
class MigrationForwardTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IronvellumDatabase::class.java,
    )

    private val dbName = "ironvellum-migration-forward-test.db"

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .deleteDatabase(dbName)
    }

    @Test
    fun currentSchemaIsValidAndChainReopensIt() = runTest {
        // Create exactly the schema exported for the current version...
        helper.createDatabase(dbName, IronvellumDatabase.VERSION).close()

        // ...then re-open through the registered chain and validate. At the
        // current version this is a no-op walk; once the version bumps it
        // exercises every newly added migration and validates its output.
        helper.runMigrationsAndValidate(
            dbName,
            IronvellumDatabase.VERSION,
            true,
            *IronvellumDatabase.MIGRATIONS,
        ).close()
    }

    /**
     * A REAL upgrade walk, not a no-op one.
     *
     * Starts at the exported schema 21, writes a profile row, then migrates
     * through the full registered chain to IronvellumDatabase.VERSION and checks
     * the row survived with the new column readable. This exercises 21->22
     * (inkStyle added), 22->23 (reset to CLEAN) and 25->26 (restored to INK).
     *
     * This is the guard for a whole bug class: a migration was first written
     * against `profiles` when the table is `profile`. That compiles, passes
     * every JVM test, and only fails when a real install upgrades.
     */
    @Test
    fun upgradeFrom21PreservesTheProfileAndAddsInkStyle() = runTest {
        helper.createDatabase(dbName, 21).use { old ->
            old.execSQL(
                "INSERT OR REPLACE INTO profile " +
                    "(id, name, totalXp, currentTitleId, lifetimeStrength, trainingMode, heightCm, sex) " +
                    "VALUES (1, 'Kaida', 4200, 'shadow_ascendant', 777, 'HYPERTROPHY', 178.0, 'FEMALE')",
            )
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            IronvellumDatabase.VERSION,
            true,
            *IronvellumDatabase.MIGRATIONS,
        )

        db.query("SELECT name, totalXp, trainingMode, inkStyle FROM profile WHERE id = 1").use { c ->
            assertTrue("the profile row must survive the upgrade", c.moveToFirst())
            assertEquals("Kaida", c.getString(0))
            assertEquals(4200L, c.getLong(1))
            assertEquals("HYPERTROPHY", c.getString(2))
            // INK is the app's own look and the 25_26 migration restores it on
            // every existing row, reversing 22_23. Both overwrites are safe for
            // the same reason: the toggle is unreleased, so no stored
            // preference was ever expressed.
            assertEquals(1, c.getInt(3))
        }
        db.close()
    }

    /**
     * The upgrade must carry the TRAINING, not just the profile row.
     *
     * The test above writes one profile and checks it survives, which a
     * migration that quietly dropped `sessions`, `set_logs`, `title_unlocks` or
     * `measurements` would still pass — and losing a lifter's logged training
     * on an app update is the worst bug this project can ship. So seed a real
     * session with its sets, an earned title and a body measurement at the
     * shipped baseline, walk the whole chain, and read every one of them back.
     */
    @Test
    fun upgradeFrom21CarriesTheTrainingItself() = runTest {
        helper.createDatabase(dbName, 21).use { old ->
            old.execSQL(
                "INSERT INTO sessions (id, presetId, label, startedAtMs, completedAtMs, " +
                    "xpAwarded, strengthScore, title, note, privateNote) " +
                    "VALUES (91, NULL, 'Heavy Pull', 1700000000000, 1700003600000, 240, 512, '', '', 'sore left elbow')",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (5001, 91, 7, 0, 1, 6, 42.5, '', 1, NULL, NULL, NULL)",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (5002, 91, 7, 0, 2, 5, 45.0, '', 1, NULL, NULL, NULL)",
            )
            old.execSQL("INSERT INTO title_unlocks (titleId, unlockedAtMs) VALUES ('first_blood', 1700000000001)")
            old.execSQL(
                "INSERT INTO measurements (id, site, valueCm, takenAtMs) " +
                    "VALUES (301, 'Waist', 81.5, 1700000000002)",
            )
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            IronvellumDatabase.VERSION,
            true,
            *IronvellumDatabase.MIGRATIONS,
        )

        db.query("SELECT label, xpAwarded, strengthScore, privateNote FROM sessions WHERE id = 91").use { c ->
            assertTrue("the session must survive the upgrade", c.moveToFirst())
            assertEquals("Heavy Pull", c.getString(0))
            assertEquals(240L, c.getLong(1))
            assertEquals(512L, c.getLong(2))
            // The private note is the one field that never leaves the device;
            // losing it silently would be invisible until the lifter looked.
            assertEquals("sore left elbow", c.getString(3))
        }
        db.query("SELECT COUNT(*), SUM(reps), MAX(weightKg) FROM set_logs WHERE sessionId = 91").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("both logged sets must survive", 2, c.getInt(0))
            assertEquals(11, c.getInt(1))
            assertEquals(45.0, c.getDouble(2), 0.001)
        }
        db.query("SELECT unlockedAtMs FROM title_unlocks WHERE titleId = 'first_blood'").use { c ->
            assertTrue("an earned title must survive the upgrade", c.moveToFirst())
            assertEquals(1700000000001L, c.getLong(0))
        }
        db.query("SELECT site, valueCm FROM measurements WHERE id = 301").use { c ->
            assertTrue("a body measurement must survive the upgrade", c.moveToFirst())
            assertEquals("Waist", c.getString(0))
            assertEquals(81.5, c.getDouble(1), 0.001)
        }
        db.close()
    }

    /**
     * Static holds become HOLD at schema 24, and their figure moves from
     * `reps` to `durationSec`.
     *
     * Modelled on the owner's real `Push` session: a hollow hold logged as
     * 45 and 30 "reps" — seconds typed into the reps box — alongside real
     * repetitions of a counted movement. Both must come out with the right
     * figure in the right column, and the counted movement must be untouched.
     */
    @Test
    fun upgradeTo24MovesHoldSecondsOutOfTheRepsColumn() = runTest {
        helper.createDatabase(dbName, 23).use { old ->
            old.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, isWeighted, metric, category) " +
                    "VALUES (91, 'Hollow Hold', 'CORE', 0, 'REPS', '')",
            )
            old.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, isWeighted, metric, category) " +
                    "VALUES (14, 'Handstand Push-up', 'PUSH', 0, 'REPS', '')",
            )
            old.execSQL(
                "INSERT INTO sessions (id, presetId, label, startedAtMs, completedAtMs, " +
                    "xpAwarded, strengthScore, title, note, privateNote) " +
                    "VALUES (2, NULL, 'Push', 1789782608320, 1789785525853, 330, 610, '', '', '')",
            )
            // Seconds in the reps column — the shape every install has today.
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (29, 2, 91, 3, 0, 45, NULL, 'hold seconds', 1, NULL, NULL, NULL)",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (30, 2, 91, 3, 1, 30, NULL, '', 1, NULL, NULL, NULL)",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (19, 2, 14, 0, 0, 6, NULL, '', 1, NULL, NULL, NULL)",
            )
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            IronvellumDatabase.VERSION,
            true,
            *IronvellumDatabase.MIGRATIONS,
        )

        db.query("SELECT metric FROM exercises WHERE id = 91").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("the hold must be re-metricked", "HOLD", c.getString(0))
        }
        db.query("SELECT metric FROM exercises WHERE id = 14").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("a counted movement must be left alone", "REPS", c.getString(0))
        }
        db.query("SELECT reps, durationSec, modifiers FROM set_logs WHERE id = 29").use { c ->
            assertTrue("the hold set must survive", c.moveToFirst())
            assertEquals("its seconds must leave the reps column", 0, c.getInt(0))
            assertEquals("its seconds must land in durationSec", 45, c.getInt(1))
            assertEquals("the legacy modifier is now redundant", "", c.getString(2))
        }
        db.query("SELECT reps, durationSec FROM set_logs WHERE id = 30").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(30, c.getInt(1))
        }
        // The counted set is the control: a migration that rewrote every row
        // would pass every assertion above and still destroy the session.
        db.query("SELECT reps, durationSec FROM set_logs WHERE id = 19").use { c ->
            assertTrue("the counted set must survive", c.moveToFirst())
            assertEquals("its reps must not move", 6, c.getInt(0))
            assertTrue("it must gain no duration", c.isNull(1))
        }
        db.close()
    }

    /**
     * Schema 24 -> 25 adds the scoringVersion marker to the profile.
     *
     * The migration must add the column ONLY: it writes 0 (never restated)
     * and leaves the restating itself to ensureSeeded, which is Kotlin work.
     * A migration that tried to compute scores in SQL — or that reset the
     * profile row to make room — would fail here.
     */
    @Test
    fun upgradeTo25AddsTheScoringVersionMarkerAtZero() = runTest {
        helper.createDatabase(dbName, 24).use { old ->
            old.execSQL(
                "INSERT OR REPLACE INTO profile " +
                    "(id, name, totalXp, currentTitleId, lifetimeStrength, trainingMode, heightCm, sex, inkStyle) " +
                    "VALUES (1, 'Kaida', 4200, 'shadow_ascendant', 777, 'HYPERTROPHY', 178.0, 'FEMALE', 0)",
            )
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            IronvellumDatabase.VERSION,
            true,
            *IronvellumDatabase.MIGRATIONS,
        )

        db.query(
            "SELECT name, totalXp, lifetimeStrength, scoringVersion FROM profile WHERE id = 1",
        ).use { c ->
            assertTrue("the profile row must survive the upgrade", c.moveToFirst())
            assertEquals("Kaida", c.getString(0))
            assertEquals(4200L, c.getLong(1))
            assertEquals(777L, c.getLong(2))
            // 0 = the stored scores have never been restated under the
            // current formula; ensureSeeded consumes exactly this marker.
            assertEquals(0, c.getInt(3))
        }
        db.close()
    }

    /**
     * Schema 26 -> 27 renames the top crest frame's catalogue id from
     * "monarch" to "masterwork".
     *
     * The id is stored in two independent places — one row per owned frame,
     * and again on the profile as the equipped frame — so a migration that
     * moved only one of them would leave a lifter wearing a frame the
     * catalogue no longer answers to, and the crest would silently draw
     * nothing. Both are asserted, and so is the absence of the old id: a
     * migration that copied instead of renaming would pass the first two
     * checks.
     */
    @Test
    fun upgradeTo27RenamesTheTopCrestFrameEverywhereItIsStored() = runTest {
        helper.createDatabase(dbName, 26).use { old ->
            old.execSQL(
                "INSERT OR REPLACE INTO gacha_state (id, rolls, equippedFrame) VALUES (1, 9, 'monarch')",
            )
            old.execSQL("INSERT OR REPLACE INTO owned_crest_frames (frameId, ownedAtMs) VALUES ('monarch', 555)")
            old.execSQL("INSERT OR REPLACE INTO owned_crest_frames (frameId, ownedAtMs) VALUES ('gold', 111)")
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            IronvellumDatabase.VERSION,
            true,
            *IronvellumDatabase.MIGRATIONS,
        )

        db.query("SELECT rolls, equippedFrame FROM gacha_state WHERE id = 1").use { c ->
            assertTrue("the gacha row must survive the upgrade", c.moveToFirst())
            assertEquals(9, c.getInt(0))
            assertEquals("masterwork", c.getString(1))
        }
        db.query("SELECT frameId, ownedAtMs FROM owned_crest_frames ORDER BY ownedAtMs").use { c ->
            val owned = mutableListOf<Pair<String, Long>>()
            while (c.moveToNext()) owned.add(c.getString(0) to c.getLong(1))
            // The unrelated frame is untouched, the renamed one keeps the date
            // it was won, and nothing answers to "monarch" any more.
            assertEquals(listOf("gold" to 111L, "masterwork" to 555L), owned)
        }
        db.close()
    }

    /**
     * Schema 27 -> 28 adds the pity counter to the gacha row.
     *
     * The banked rolls and the worn crest are what a lifter would actually
     * miss, so the assertion is that they came through beside the new column —
     * a migration that recreated the table and lost them would still validate
     * against the exported schema.
     */
    @Test
    fun upgradeTo28AddsThePityCounterWithoutLosingTheBank() = runTest {
        helper.createDatabase(dbName, 27).use { old ->
            old.execSQL(
                "INSERT OR REPLACE INTO gacha_state (id, rolls, equippedFrame) VALUES (1, 4, 'masterwork')",
            )
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            IronvellumDatabase.VERSION,
            true,
            *IronvellumDatabase.MIGRATIONS,
        )

        db.query("SELECT rolls, equippedFrame, figureStreak FROM gacha_state WHERE id = 1").use { c ->
            assertTrue("the gacha row must survive the upgrade", c.moveToFirst())
            assertEquals(4, c.getInt(0))
            assertEquals("masterwork", c.getString(1))
            // An existing lifter starts owing nothing: pity counts forward from
            // the upgrade rather than carrying an invisible debt.
            assertEquals(0, c.getInt(2))
        }
        db.close()
    }

    /**
     * Schema 28 -> 29 marks CSV-imported sessions on `sessions`.
     *
     * DATA-survival, not schema-shape: a real training row is written at 28
     * and must come through the upgrade with every field it had, gaining only
     * `imported = 0` — every row that exists today was logged in the app, so
     * all of them stay pushable to the feed. A migration that recreated the
     * sessions table and dropped the training would still validate against
     * the exported schema, which is exactly why the row itself is asserted.
     */
    @Test
    fun upgradeTo29MarksImportedWithoutLosingTheTraining() = runTest {
        helper.createDatabase(dbName, 28).use { old ->
            old.execSQL(
                "INSERT INTO sessions (id, presetId, label, startedAtMs, completedAtMs, " +
                    "xpAwarded, strengthScore, title, note, privateNote) " +
                    "VALUES (91, NULL, 'Heavy Pull', 1700000000000, 1700003600000, 240, 512, '', '', 'sore left elbow')",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (5001, 91, 7, 0, 1, 6, 42.5, '', 1, NULL, NULL, NULL)",
            )
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            IronvellumDatabase.VERSION,
            true,
            *IronvellumDatabase.MIGRATIONS,
        )

        db.query(
            "SELECT label, xpAwarded, strengthScore, privateNote, imported FROM sessions WHERE id = 91",
        ).use { c ->
            assertTrue("the session must survive the upgrade", c.moveToFirst())
            assertEquals("Heavy Pull", c.getString(0))
            assertEquals(240L, c.getLong(1))
            assertEquals(512L, c.getLong(2))
            assertEquals("sore left elbow", c.getString(3))
            // The new column defaults to 0: existing rows are all app-logged
            // and stay eligible for the feed push.
            assertEquals(0, c.getInt(4))
        }
        db.query("SELECT reps, weightKg FROM set_logs WHERE sessionId = 91").use { c ->
            assertTrue("the logged set must survive the upgrade", c.moveToFirst())
            assertEquals(6, c.getInt(0))
            assertEquals(42.5, c.getDouble(1), 0.001)
        }
        db.close()
    }

    /**
     * Schema 29 -> 30 adds the per-workout cloud audience to `sessions`.
     *
     * Two real workouts (one app-logged, one CSV-imported) with their sets are
     * written at 29. Both must come through with every field they had and gain
     * only `audience = 'profile'` — what every workout meant before the column
     * existed, so nobody's sharing changes on upgrade. A migration that
     * recreated `sessions` would still validate against the exported schema;
     * the counts and sums are what catch it.
     */
    @Test
    fun upgradeTo30AddsTheAudienceWithoutLosingTheTraining() = runTest {
        helper.createDatabase(dbName, 29).use { old ->
            old.execSQL(
                "INSERT INTO sessions (id, presetId, label, startedAtMs, completedAtMs, " +
                    "xpAwarded, strengthScore, title, note, privateNote, imported) " +
                    "VALUES (91, NULL, 'Heavy Pull', 1700000000000, 1700003600000, 240, 512, 'Top set', '', 'sore left elbow', 0)",
            )
            old.execSQL(
                "INSERT INTO sessions (id, presetId, label, startedAtMs, completedAtMs, " +
                    "xpAwarded, strengthScore, title, note, privateNote, imported) " +
                    "VALUES (92, NULL, 'Legs', 1690000000000, 1690003600000, 150, 300, '', '', '', 1)",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (5001, 91, 7, 0, 1, 6, 42.5, '', 1, NULL, NULL, NULL)",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (5002, 91, 7, 0, 2, 5, 45.0, '', 1, NULL, NULL, NULL)",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (5003, 92, 9, 0, 1, 8, 60.0, '', 1, NULL, NULL, NULL)",
            )
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            IronvellumDatabase.VERSION,
            true,
            *IronvellumDatabase.MIGRATIONS,
        )

        db.query(
            "SELECT label, xpAwarded, strengthScore, title, privateNote, imported, audience " +
                "FROM sessions WHERE id = 91",
        ).use { c ->
            assertTrue("the logged workout must survive the upgrade", c.moveToFirst())
            assertEquals("Heavy Pull", c.getString(0))
            assertEquals(240L, c.getLong(1))
            assertEquals(512L, c.getLong(2))
            assertEquals("Top set", c.getString(3))
            assertEquals("sore left elbow", c.getString(4))
            assertEquals(0, c.getInt(5))
            assertEquals("profile", c.getString(6))
        }
        db.query("SELECT COUNT(*), SUM(xpAwarded), SUM(imported) FROM sessions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("both workouts must survive", 2, c.getInt(0))
            assertEquals(390, c.getInt(1))
            assertEquals("the imported flag must survive", 1, c.getInt(2))
        }
        db.query("SELECT COUNT(*) FROM sessions WHERE audience = 'profile'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("every existing workout keeps following the profile", 2, c.getInt(0))
        }
        db.query("SELECT COUNT(*), SUM(reps), MAX(weightKg) FROM set_logs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("all three logged sets must survive", 3, c.getInt(0))
            assertEquals(19, c.getInt(1))
            assertEquals(60.0, c.getDouble(2), 0.001)
        }
        db.close()
    }

    /**
     * Schema 30 -> 31 adds `favourite_exercises`. Purely additive: the
     * workouts, sets and exercise rows written at 30 must all come through
     * with their values, the new table must exist and be empty, and it must
     * accept a favourite that references a surviving exercise.
     */
    @Test
    fun upgradeTo31AddsFavouritesWithoutLosingTheTraining() = runTest {
        helper.createDatabase(dbName, 30).use { old ->
            old.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, isWeighted, metric, category) " +
                    "VALUES (7001, 'Fixture Pull-up', 'PULL', 0, 'REPS', '')",
            )
            old.execSQL(
                "INSERT INTO sessions (id, presetId, label, startedAtMs, completedAtMs, " +
                    "xpAwarded, strengthScore, title, note, privateNote, imported, audience) " +
                    "VALUES (91, NULL, 'Heavy Pull', 1700000000000, 1700003600000, 240, 512, 'Top set', '', 'sore left elbow', 0, 'profile')",
            )
            old.execSQL(
                "INSERT INTO sessions (id, presetId, label, startedAtMs, completedAtMs, " +
                    "xpAwarded, strengthScore, title, note, privateNote, imported, audience) " +
                    "VALUES (92, NULL, 'Legs', 1690000000000, 1690003600000, 150, 300, '', '', '', 1, 'profile')",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (5001, 91, 7001, 0, 1, 6, 42.5, '', 1, NULL, NULL, NULL)",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (5002, 91, 7001, 0, 2, 5, 45.0, '', 1, NULL, NULL, NULL)",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (5003, 92, 7001, 0, 1, 8, 60.0, '', 1, NULL, NULL, NULL)",
            )
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            IronvellumDatabase.VERSION,
            true,
            *IronvellumDatabase.MIGRATIONS,
        )

        db.query("SELECT COUNT(*), SUM(xpAwarded), SUM(imported) FROM sessions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("both workouts must survive", 2, c.getInt(0))
            assertEquals(390, c.getInt(1))
            assertEquals(1, c.getInt(2))
        }
        db.query("SELECT privateNote FROM sessions WHERE id = 91").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("sore left elbow", c.getString(0))
        }
        db.query("SELECT COUNT(*), SUM(reps), MAX(weightKg) FROM set_logs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("all three logged sets must survive", 3, c.getInt(0))
            assertEquals(19, c.getInt(1))
            assertEquals(60.0, c.getDouble(2), 0.001)
        }
        db.query("SELECT name, muscleGroup FROM exercises WHERE id = 7001").use { c ->
            assertTrue("the exercise row must survive", c.moveToFirst())
            assertEquals("Fixture Pull-up", c.getString(0))
        }
        db.query("SELECT COUNT(*) FROM favourite_exercises").use { c ->
            assertTrue("the favourites table must exist", c.moveToFirst())
            assertEquals("nothing is starred on upgrade", 0, c.getInt(0))
        }
        db.execSQL("INSERT INTO favourite_exercises (exerciseId, addedAtMs) VALUES (7001, 1700000000003)")
        db.query("SELECT COUNT(*) FROM favourite_exercises WHERE exerciseId = 7001").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    /**
     * Schema 31 -> 32 renames the seeded default name "Lifter" to
     * "Ironbound". A name the lifter typed must come through untouched, and
     * the rename must not disturb anything else on the row.
     */
    @Test
    fun upgradeTo32RenamesOnlyTheSeededDefaultName() = runTest {
        helper.createDatabase(dbName, 31).use { old ->
            old.execSQL("INSERT INTO profile (id, name, totalXp, currentTitleId, lifetimeStrength, trainingMode, sex, inkStyle, scoringVersion) VALUES (1, 'Lifter', 1234, NULL, 0, 'STRENGTH', 'MALE', 1, 0)")
        }
        helper.runMigrationsAndValidate(dbName, IronvellumDatabase.VERSION, true, *IronvellumDatabase.MIGRATIONS).use { db ->
            db.query("SELECT name, totalXp FROM profile WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Ironbound", c.getString(0))
                assertEquals("the rename must not touch XP", 1234, c.getInt(1))
            }
        }
        helper.createDatabase("$dbName-named", 31).use { old ->
            old.execSQL("INSERT INTO profile (id, name, totalXp, currentTitleId, lifetimeStrength, trainingMode, sex, inkStyle, scoringVersion) VALUES (1, 'Alex', 10, NULL, 0, 'STRENGTH', 'MALE', 1, 0)")
        }
        helper.runMigrationsAndValidate("$dbName-named", IronvellumDatabase.VERSION, true, *IronvellumDatabase.MIGRATIONS).use { db ->
            db.query("SELECT name FROM profile WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("a chosen name survives", "Alex", c.getString(0))
            }
        }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase("$dbName-named")
    }

    /**
     * Schema 32 -> 33 adds the sealed trial's amended stamp. Every existing
     * trial must come through unamended (null) with its figures untouched,
     * and the new column must take a value afterwards.
     */
    @Test
    fun upgradeTo33AddsTheAmendedStampAsNull() = runTest {
        helper.createDatabase(dbName, 32).use { old ->
            old.execSQL(
                "INSERT INTO sessions (id, presetId, label, startedAtMs, completedAtMs, " +
                    "xpAwarded, strengthScore, title, note, privateNote, imported, audience) " +
                    "VALUES (5, NULL, 'Pull', 1789782608320, 1789785525853, 210, 480, '', '', '', 0, 'friends')",
            )
        }
        helper.runMigrationsAndValidate(dbName, IronvellumDatabase.VERSION, true, *IronvellumDatabase.MIGRATIONS).use { db ->
            db.query("SELECT xpAwarded, strengthScore, audience, editedAtMs, sealedXp FROM sessions WHERE id = 5").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(210, c.getInt(0))
                assertEquals(480, c.getInt(1))
                assertEquals("friends", c.getString(2))
                assertTrue("an existing trial reads as never amended", c.isNull(3))
                assertTrue("no amendment cap is recorded before the first amendment", c.isNull(4))
            }
            db.execSQL("UPDATE sessions SET editedAtMs = 1789790000000 WHERE id = 5")
            db.query("SELECT editedAtMs FROM sessions WHERE id = 5").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1789790000000L, c.getLong(0))
            }
        }
    }

    /**
     * Schema 33 -> 34 adds the level the lifter has already been paid for to
     * the gacha row, seeded to their CURRENT level so no past level-up pays
     * again. The banked rolls must come through beside it, and a lifter with
     * no gacha row at all must get one rather than an UPDATE that matches
     * nothing and leaves the mark at 0 (which would re-pay every level).
     */
    @Test
    fun upgradeTo34SeedsTheRollLevelMarkToTheCurrentLevel() = runTest {
        val totalXp = 25_000L
        val level = Xp.levelFor(totalXp)
        assertTrue("the fixture must sit above level 1 to prove the seed", level > 1)
        val profile = "INSERT INTO profile (id, name, totalXp, currentTitleId, lifetimeStrength, trainingMode, sex, inkStyle, scoringVersion) " +
            "VALUES (1, 'Ironbound', $totalXp, NULL, 0, 'STRENGTH', 'MALE', 1, 0)"

        helper.createDatabase(dbName, 33).use { old ->
            old.execSQL(profile)
            old.execSQL("INSERT OR REPLACE INTO gacha_state (id, rolls, equippedFrame, figureStreak) VALUES (1, 4, 'masterwork', 2)")
        }
        helper.runMigrationsAndValidate(dbName, IronvellumDatabase.VERSION, true, *IronvellumDatabase.MIGRATIONS).use { db ->
            db.query("SELECT rolls, equippedFrame, figureStreak, rollLevelMark FROM gacha_state WHERE id = 1").use { c ->
                assertTrue("the gacha row must survive the upgrade", c.moveToFirst())
                assertEquals(4, c.getInt(0))
                assertEquals("masterwork", c.getString(1))
                assertEquals(2, c.getInt(2))
                assertEquals("existing levels count as already paid", level, c.getInt(3))
            }
        }

        helper.createDatabase("$dbName-norow", 33).use { old ->
            old.execSQL(profile)
            old.execSQL("DELETE FROM gacha_state")
        }
        helper.runMigrationsAndValidate("$dbName-norow", IronvellumDatabase.VERSION, true, *IronvellumDatabase.MIGRATIONS).use { db ->
            db.query("SELECT rolls, rollLevelMark FROM gacha_state WHERE id = 1").use { c ->
                assertTrue("a missing gacha row is created by the upgrade", c.moveToFirst())
                assertEquals(0, c.getInt(0))
                assertEquals(level, c.getInt(1))
            }
        }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase("$dbName-norow")
    }

    /** Schema 34 -> 35: every existing set reads back as a working set outside any superset. */
    @Test
    fun upgradeTo35KeepsEverySetAPlainWorkingSet() = runTest {
        helper.createDatabase(dbName, 34).use { old ->
            old.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, isWeighted, metric, category) " +
                    "VALUES (1, 'Bench Press', 'PUSH', 1, 'REPS', '')",
            )
            old.execSQL(
                "INSERT INTO sessions (id, presetId, label, startedAtMs, completedAtMs, xpAwarded, " +
                    "strengthScore, title, note, privateNote, imported, audience, editedAtMs, sealedXp) " +
                    "VALUES (2, NULL, 'Push', 1789782608320, 1789785525853, 330, 610, '', '', '', 0, 'profile', NULL, NULL)",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade) " +
                    "VALUES (7, 2, 1, 0, 0, 5, 80.0, '', 1, NULL, NULL, NULL)",
            )
        }
        helper.runMigrationsAndValidate(dbName, IronvellumDatabase.VERSION, true, *IronvellumDatabase.MIGRATIONS).use { db ->
            db.query("SELECT reps, done, warmup, supersetGroup FROM set_logs WHERE id = 7").use { c ->
                assertTrue("the set must survive", c.moveToFirst())
                assertEquals(5, c.getInt(0))
                assertEquals("its tick must not move", 1, c.getInt(1))
                assertEquals("it is not a warm-up", 0, c.getInt(2))
                assertTrue("it is in no superset", c.isNull(3))
            }
        }
    }

    /** Schema 35 -> 36: the notes table starts empty, and the trial and its sets survive untouched. */
    @Test
    fun upgradeTo36AddsAnEmptyExerciseNotesTable() = runTest {
        helper.createDatabase(dbName, 35).use { old ->
            old.execSQL(
                "INSERT INTO exercises (id, name, muscleGroup, isWeighted, metric, category) " +
                    "VALUES (1, 'Bench Press', 'PUSH', 1, 'REPS', '')",
            )
            old.execSQL(
                "INSERT INTO sessions (id, presetId, label, startedAtMs, completedAtMs, xpAwarded, " +
                    "strengthScore, title, note, privateNote, imported, audience, editedAtMs, sealedXp) " +
                    "VALUES (2, NULL, 'Push', 1789782608320, 1789785525853, 330, 610, '', '', '', 0, 'profile', NULL, NULL)",
            )
            old.execSQL(
                "INSERT INTO set_logs (id, sessionId, exerciseId, exercisePosition, setIndex, " +
                    "reps, weightKg, modifiers, done, durationSec, distanceM, grade, warmup, supersetGroup) " +
                    "VALUES (7, 2, 1, 0, 0, 5, 80.0, '', 1, NULL, NULL, NULL, 0, NULL)",
            )
        }
        helper.runMigrationsAndValidate(dbName, 36, true, *IronvellumDatabase.MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM session_exercise_notes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("no trial had a note before the table existed", 0, c.getInt(0))
            }
            db.query("SELECT reps FROM set_logs WHERE id = 7").use { c ->
                assertTrue("the set must survive", c.moveToFirst())
                assertEquals(5, c.getInt(0))
            }
            // The table works as the entity describes it: a note per (trial, exercise), gone with its trial.
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO session_exercise_notes (sessionId, exerciseId, note) VALUES (2, 1, 'Bar felt fast')")
            db.execSQL("DELETE FROM sessions WHERE id = 2")
            db.query("SELECT COUNT(*) FROM session_exercise_notes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("a note goes with its trial", 0, c.getInt(0))
            }
        }
    }

    /**
     * Schema 36 -> 37, relic houses: every stored relic gets a stable house + form id, keeps its
     * multiplier and its drawn time, none is lost (an overflowing tier folds into counted
     * refinements), and the strongest stays the active one.
     */
    @Test
    fun upgradeTo37PlacesEveryLegacyRelicInAHouse() = runTest {
        helper.createDatabase(dbName, 36).use { old ->
            old.execSQL("INSERT OR REPLACE INTO idle_state (id, essence, shadows, relicMultiplier, lastCollectedAtMs) VALUES (1, 0, 0, 1.9, 1)")
            old.execSQL("INSERT INTO owned_relics (id, name, multiplier, drawnAtMs) VALUES (1, 'Fang of the Mark', 1.20, 100)")
            old.execSQL("INSERT INTO owned_relics (id, name, multiplier, drawnAtMs) VALUES (2, 'Sigil of the Margin', 1.30, 200)")
            old.execSQL("INSERT INTO owned_relics (id, name, multiplier, drawnAtMs) VALUES (3, 'Greater Crown of the Abyss', 1.50, 300)")
            old.execSQL("INSERT INTO owned_relics (id, name, multiplier, drawnAtMs) VALUES (4, 'Masterwork Ember of the Ashen King', 2.10, 400)")
            // Eight Commons for six Common cells: two move to the nearest free cell of the next tier, keeping their multiplier.
            for (i in 0 until 8) {
                old.execSQL("INSERT INTO owned_relics (id, name, multiplier, drawnAtMs) VALUES (${10 + i}, 'Nameless Relic', ${1.06 + i * 0.01}, ${500 + i})")
            }
        }
        helper.runMigrationsAndValidate(dbName, 37, true, *IronvellumDatabase.MIGRATIONS).use { db ->
            var rows = 0
            var weight = 0
            val ids = mutableSetOf<String>()
            var best = 0.0
            val held = mutableListOf<Double>()
            db.query("SELECT id, name, multiplier, drawnAtMs, relicId, refinements FROM owned_relics").use { c ->
                while (c.moveToNext()) {
                    rows++
                    held += c.getDouble(2)
                    weight += 1 + c.getInt(5)
                    assertTrue("a house id on every row", c.getString(4).contains('.'))
                    assertTrue("unique ids", ids.add(c.getString(4)))
                    best = maxOf(best, c.getDouble(2))
                }
            }
            assertEquals("a relic is never lost", 12, weight)
            assertEquals(12, rows)
            assertEquals("the strongest relic is kept as it was", 2.1, best, 0.0)
            db.query("SELECT relicId FROM owned_relics WHERE id = 4").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("craft.chisel", c.getString(0))
            }
            db.query("SELECT relicMultiplier FROM idle_state WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(
                    "the stored relic number is the whole vault stacked, a real factor of the rate",
                    com.ironvellum.app.domain.Relics.effectiveMultiplier(held), c.getDouble(0), 1e-9,
                )
            }
        }
    }

    /**
     * Schema 36 -> 37: the Veil's new counters start at zero beside the banked rolls, the grant
     * version starts at 0 so the retro pass runs once, and lifetime essence starts at the
     * essence already held (nothing has been spent yet).
     */
    @Test
    fun upgradeTo37SeedsLifetimeEssenceAndLeavesTheGrantUnpaid() = runTest {
        helper.createDatabase(dbName, 36).use { old ->
            old.execSQL("INSERT OR REPLACE INTO idle_state (id, essence, shadows, relicMultiplier, lastCollectedAtMs) VALUES (1, 4321, 9, 1.4, 777)")
            old.execSQL("INSERT OR REPLACE INTO gacha_state (id, rolls, equippedFrame, figureStreak, rollLevelMark) VALUES (1, 3, 'iron', 2, 15)")
        }
        helper.runMigrationsAndValidate(dbName, 37, true, *IronvellumDatabase.MIGRATIONS).use { db ->
            db.query("SELECT essence, shadows, relicMultiplier, lastCollectedAtMs, lifetimeEssence FROM idle_state WHERE id = 1").use { c ->
                assertTrue("the idle row must survive", c.moveToFirst())
                assertEquals(4321L, c.getLong(0))
                assertEquals(9, c.getInt(1))
                assertEquals(1.4, c.getDouble(2), 0.0)
                assertEquals(777L, c.getLong(3))
                assertEquals("nothing has been spent, so lifetime equals the balance", 4321L, c.getLong(4))
            }
            db.query("SELECT rolls, equippedFrame, figureStreak, rollLevelMark, relicPity, drawsSpent, offeringsMade, veilGrantVersion FROM gacha_state WHERE id = 1").use { c ->
                assertTrue("the gacha row must survive", c.moveToFirst())
                assertEquals(3, c.getInt(0))
                assertEquals("iron", c.getString(1))
                assertEquals(2, c.getInt(2))
                assertEquals(15, c.getInt(3))
                assertEquals(0, c.getInt(4))
                assertEquals(0, c.getInt(5))
                assertEquals(0, c.getInt(6))
                assertEquals("the retro grant has not run yet", 0, c.getInt(7))
            }
        }
    }

    /**
     * Schema 37 -> 38, crests: every held crest frame gets a source ('legacy', a draw before sources were
     * kept, except the three that only the Veil ever paid, which are 'draw'), and the worn crest gets no
     * change stamp so the first change under the once-a-day rule is free. Nothing held is taken away.
     */
    @Test
    fun upgradeTo38TagsCrestSourcesAndKeepsWhatIsWorn() = runTest {
        helper.createDatabase(dbName, 37).use { old ->
            old.execSQL("INSERT OR REPLACE INTO gacha_state (id, rolls, equippedFrame, figureStreak, rollLevelMark, relicPity, drawsSpent, offeringsMade, veilGrantVersion) VALUES (1, 3, 'iron', 2, 15, 0, 0, 0, 1)")
            old.execSQL("INSERT INTO owned_crest_frames (frameId, ownedAtMs) VALUES ('iron', 100)")
            old.execSQL("INSERT INTO owned_crest_frames (frameId, ownedAtMs) VALUES ('aurora', 200)")
        }
        helper.runMigrationsAndValidate(dbName, 38, true, *IronvellumDatabase.MIGRATIONS).use { db ->
            db.query("SELECT frameId, ownedAtMs, source FROM owned_crest_frames ORDER BY frameId").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("aurora", c.getString(0))
                assertEquals(200L, c.getLong(1))
                assertEquals("draw", c.getString(2))
                assertTrue(c.moveToNext())
                assertEquals("iron", c.getString(0))
                assertEquals("legacy", c.getString(2))
            }
            db.query("SELECT equippedFrame, equippedChangedAtMs, veilGrantVersion FROM gacha_state WHERE id = 1").use { c ->
                assertTrue("the gacha row must survive", c.moveToFirst())
                assertEquals("iron", c.getString(0))
                assertTrue("no change stamp yet", c.isNull(1))
                assertEquals("the retro grant is not re-run", 1, c.getInt(2))
            }
        }
    }
}
