package com.monarch.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.data.db.ExerciseEntity
import com.monarch.app.data.db.GachaStateEntity
import com.monarch.app.data.db.IdleStateEntity
import com.monarch.app.data.db.OwnedCrestFrameEntity
import com.monarch.app.data.db.OwnedRelicEntity
import com.monarch.app.data.db.ProfileEntity
import com.monarch.app.data.db.SessionEntity
import com.monarch.app.data.db.SetLogEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the real database on a device and proves the exported schema and the
 * hand-written DAO SQL agree at runtime for the riskiest recent tables. This
 * is what catches the opaque Room/KSP failures and the "column type must match
 * the entity exactly" traps called out in the migration comments.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSchemaSmokeTest {

    private lateinit var context: Context
    private lateinit var db: MonarchDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = MonarchDatabase.create(context, TEST_DB)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun daoRoundTripsAgainstRealSchema() = runTest {
        // profile (single row, height/sex columns from MIGRATION_18_19)
        db.profileDao().upsert(
            ProfileEntity(name = "Hunter", totalXp = 10, currentTitleId = null, heightCm = 180.0, sex = "MALE"),
        )
        val profile = db.profileDao().get()
        assertEquals("Hunter", profile?.name)
        assertEquals(180.0, profile?.heightCm!!, 0.0)

        // idle_state (single row from MIGRATION_16_17)
        db.idleDao().upsert(
            IdleStateEntity(essence = 42, shadows = 3, relicMultiplier = 1.5, lastCollectedAtMs = 123L),
        )
        assertEquals(42L, db.idleDao().get()?.essence)

        // gacha_state + owned_crest_frames (MIGRATION_17_18 / 19_20)
        db.gachaDao().upsert(GachaStateEntity(rolls = 2, equippedFrame = "iron"))
        val gacha = db.gachaDao().get()
        assertEquals(2, gacha?.rolls)
        assertEquals("iron", gacha?.equippedFrame)
        db.gachaDao().insertFrame(OwnedCrestFrameEntity(frameId = "laurel", ownedAtMs = 7L))
        assertTrue(db.gachaDao().ownedFrameIds().contains("laurel"))

        // owned_relics (MIGRATION_20_21)
        db.gachaDao().insertRelic(OwnedRelicEntity(name = "R1", multiplier = 1.25, drawnAtMs = 9L))
        assertEquals(listOf(1.25), db.gachaDao().relicMultipliers())

        // sessions + set_logs, including the feed columns from MIGRATION_11_12
        val exerciseId = db.exerciseDao()
            .insertAll(listOf(ExerciseEntity(name = "Smoke Squat", muscleGroup = "LEGS", isWeighted = true)))
            .single()
        val sessionId = db.sessionDao().insertSession(
            SessionEntity(presetId = null, label = "smoke", startedAtMs = 1L, completedAtMs = 2L, xpAwarded = 5),
        )
        db.sessionDao().insertSets(
            listOf(
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    setIndex = 0,
                    reps = 5,
                    weightKg = 100.0,
                    modifiers = "",
                    done = true,
                ),
            ),
        )
        db.sessionDao().setPrivateNote(sessionId, "device-only")
        val session = db.sessionDao().byId(sessionId)
        assertEquals("device-only", session?.privateNote)
        val sets = db.sessionDao().setsFor(sessionId)
        assertEquals(1, sets.size)
        assertEquals(100.0, sets.single().weightKg!!, 0.0)
        assertEquals(1, db.sessionDao().completedCount())
    }

    /**
     * The @Database version actually opened on device equals the companion
     * constant the registry tests use, so a bumped annotation version without
     * a matching migration fails here as an open-time crash instead of silently.
     */
    @Test
    fun databaseOpensAtDeclaredVersion() {
        val opened = Room.databaseBuilder(context, MonarchDatabase::class.java, TEST_DB)
            .addMigrations(*MonarchDatabase.MIGRATIONS)
            .build()
        opened.openHelper.writableDatabase.version.let { version ->
            assertEquals(MonarchDatabase.VERSION, version)
        }
        opened.close()
    }

    private companion object {
        /** Never the app's live database: deleting that under the running
         *  Application left its open Room instance serving an empty file. */
        const val TEST_DB = "monarch-schema-smoke-test.db"
    }
}
