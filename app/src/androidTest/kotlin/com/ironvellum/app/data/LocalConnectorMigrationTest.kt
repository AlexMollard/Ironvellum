package com.ironvellum.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class LocalConnectorMigrationTest {
    @Test fun version50KeepsPrivateHistoryTargetsAndAccountScopedReceipt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val name = "local-connector-migration.db"
        val helper = MigrationTestHelper(instrumentation, IronvellumDatabase::class.java)
        try {
            helper.createDatabase(name, 50).use { db ->
                db.execSQL("""INSERT INTO sessions(id,label,startedAtMs,completedAtMs,xpAwarded,strengthScore,title,note,
                    privateNote,imported,audience) VALUES(7,'label',10,20,1,3,'title','public','private',0,'private')""")
                db.execSQL("""INSERT INTO set_logs(sessionId,exerciseId,exercisePosition,setIndex,reps,weightKg,modifiers,done,kind)
                    VALUES(7,3,0,0,8,15,'',1,'WORKING'), (7,3,0,1,7,17,'',1,'WORKING')""")
                db.execSQL("INSERT INTO presets(id,name,note,scheduledDay) VALUES(9,'original','preserve',2)")
                db.execSQL("INSERT INTO mcp_draft_receipts VALUES('backend','user','draft','hash',9,11,1)")
            }
            helper.runMigrationsAndValidate(name,51,true,*IronvellumDatabase.MIGRATIONS).use { db ->
                fun scalar(q: String) = db.query(q).use { assertTrue(it.moveToFirst()); it.getString(0) }
                assertEquals("private",scalar("SELECT privateNote FROM sessions WHERE id=7"))
                assertEquals("2/15/32.0",scalar("SELECT count(*)||'/'||sum(reps)||'/'||sum(weightKg) FROM set_logs WHERE sessionId=7"))
                assertEquals("original/preserve/2",scalar("SELECT name||'/'||note||'/'||scheduledDay FROM presets WHERE id=9"))
                assertEquals("9/hash/1",scalar("SELECT localPresetId||'/'||contentHash||'/'||ackPending FROM mcp_draft_receipts WHERE backend='backend' AND userId='user' AND draftId='draft'"))
                for (table in listOf("identity","objects","exports","receipts"))
                    assertEquals("0",scalar("SELECT count(*) FROM local_connector_" + table))
            }
        } finally { instrumentation.targetContext.deleteDatabase(name) }
    }
}
