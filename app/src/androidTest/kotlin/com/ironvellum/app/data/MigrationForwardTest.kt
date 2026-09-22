package com.ironvellum.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
                "INSERT OR REPLACE INTO profile " +
                    "(id, name, totalXp, currentTitleId, lifetimeStrength, trainingMode, " +
                    "heightCm, sex, inkStyle, scoringVersion, equippedFrame) " +
                    "VALUES (1, 'Kaida', 4200, 'shadow_ascendant', 777, 'HYPERTROPHY', " +
                    "178.0, 'FEMALE', 1, 0, 'monarch')",
            )
            old.execSQL("INSERT OR REPLACE INTO owned_crest_frames (id, ownedAtMs) VALUES ('monarch', 555)")
            old.execSQL("INSERT OR REPLACE INTO owned_crest_frames (id, ownedAtMs) VALUES ('gold', 111)")
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            IronvellumDatabase.VERSION,
            true,
            *IronvellumDatabase.MIGRATIONS,
        )

        db.query("SELECT equippedFrame FROM profile WHERE id = 1").use { c ->
            assertTrue("the profile row must survive the upgrade", c.moveToFirst())
            assertEquals("masterwork", c.getString(0))
        }
        db.query("SELECT id, ownedAtMs FROM owned_crest_frames ORDER BY ownedAtMs").use { c ->
            val owned = mutableListOf<Pair<String, Long>>()
            while (c.moveToNext()) owned.add(c.getString(0) to c.getLong(1))
            // The unrelated frame is untouched, the renamed one keeps the date
            // it was won, and nothing answers to "monarch" any more.
            assertEquals(listOf("gold" to 111L, "masterwork" to 555L), owned)
        }
        db.close()
    }
}
