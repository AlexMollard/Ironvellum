package com.ironvellum.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class DraftReceiptMigrationTest {
    @Test fun upgradeFrom49PreservesExistingWorkoutAndReceiptAfterDeletion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val name = "draft-receipt-migration-test.db"
        val helper = MigrationTestHelper(instrumentation, IronvellumDatabase::class.java)
        try {
            helper.createDatabase(name, 49).use { db ->
                db.execSQL("INSERT INTO presets(id,name,note,scheduledDay) VALUES (7,'Original','keep',3)")
            }
            helper.runMigrationsAndValidate(name, 50, true, *IronvellumDatabase.MIGRATIONS).use { db ->
                db.query("SELECT name,note,scheduledDay FROM presets WHERE id=7").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Original", it.getString(0))
                    assertEquals("keep", it.getString(1))
                    assertEquals(3, it.getInt(2))
                }
                db.execSQL("INSERT INTO mcp_draft_receipts(backend,userId,draftId,contentHash,localPresetId,savedAtMs,ackPending) VALUES ('X','A','D','H',7,1,1)")
                db.execSQL("DELETE FROM presets WHERE id=7")
                db.query("SELECT localPresetId,ackPending FROM mcp_draft_receipts").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(7, it.getLong(0))
                    assertEquals(1, it.getInt(1))
                }
            }
        } finally {
            instrumentation.targetContext.deleteDatabase(name)
        }
    }
}
