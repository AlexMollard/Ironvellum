package com.monarch.app.data

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
        MonarchDatabase::class.java,
    )

    private val dbName = "monarch-migration-forward-test.db"

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .deleteDatabase(dbName)
    }

    @Test
    fun currentSchemaIsValidAndChainReopensIt() = runTest {
        // Create exactly the schema exported for the current version...
        helper.createDatabase(dbName, MonarchDatabase.VERSION).close()

        // ...then re-open through the registered chain and validate. At the
        // current version this is a no-op walk; once the version bumps it
        // exercises every newly added migration and validates its output.
        helper.runMigrationsAndValidate(
            dbName,
            MonarchDatabase.VERSION,
            true,
            *MonarchDatabase.MIGRATIONS,
        ).close()
    }

    /**
     * A REAL upgrade walk, not a no-op one.
     *
     * The test above creates the database at the current version, so as its own
     * comment admits it validates nothing about the newest migration. Schema 21
     * is now exported, so this starts there, writes a profile row, migrates to
     * 22 and checks the row survived with the new column readable.
     *
     * This is the guard for a whole bug class: MIGRATION_21_22 was first written
     * against `profiles` when the table is `profile`. That compiles, passes every
     * JVM test, and only fails when a real install upgrades.
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
            22,
            true,
            *MonarchDatabase.MIGRATIONS,
        )

        db.query("SELECT name, totalXp, trainingMode, inkStyle FROM profile WHERE id = 1").use { c ->
            assertTrue("the profile row must survive the upgrade", c.moveToFirst())
            assertEquals("Kaida", c.getString(0))
            assertEquals(4200L, c.getLong(1))
            assertEquals("HYPERTROPHY", c.getString(2))
            // Default is on: the ink treatment is the app's look, and the
            // toggle exists to leave it rather than to opt in.
            assertEquals(1, c.getInt(3))
        }
        db.close()
    }
}
