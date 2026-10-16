package com.ironvellum.app.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * A fresh, seeded test database without paying for the seed every time.
 *
 * `ensureSeeded` inserts the whole exercise catalogue (about 580 rows), which takes a quarter of a second on the
 * emulator, and a hundred and sixty tests start by doing exactly that. The catalogue is seeded once per process into
 * a template file and each test gets a copy of it. A test still calls `ensureSeeded` itself afterwards, so the
 * idempotent path is exercised as before, and the tests that exist to check seeding (an empty install, a library
 * upgrade, a migration) keep building their database from nothing.
 */
object SeededDb {
    private const val TEMPLATE = "seeded-template.db"
    private var built = false

    /** Replaces the database called [name] with a copy of the seeded template. */
    @Synchronized
    fun fresh(context: Context, name: String) {
        context.deleteDatabase(name)
        if (!built) {
            build(context)
            built = true
        }
        val target = context.getDatabasePath(name)
        target.parentFile?.mkdirs()
        context.getDatabasePath(TEMPLATE).copyTo(target, overwrite = true)
    }

    private fun build(context: Context) {
        context.deleteDatabase(TEMPLATE)
        val db = IronvellumDatabase.create(context, TEMPLATE)
        runBlocking { Repository(db).ensureSeeded() }
        db.close()
        // Only the main file is copied, so nothing may be left in the write-ahead log.
        val wal = File(context.getDatabasePath(TEMPLATE).path + "-wal")
        check(!wal.exists() || wal.length() == 0L) { "the seeded template still has a write-ahead log" }
    }
}
