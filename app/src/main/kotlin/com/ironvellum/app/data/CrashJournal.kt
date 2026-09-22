package com.ironvellum.app.data

import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant

/**
 * Local crash journal: one plain-text file per uncaught exception under
 * `filesDir/crash/`. No hosted service — the user reads and shares the records
 * themselves.
 *
 * Recording runs inside a process that is already crashing, so it obeys hard
 * rules: no coroutines, no Room, no main-thread assumptions, and every failure
 * swallowed — a journal that throws while recording a crash is worse than none.
 * The previous default handler ALWAYS runs afterwards, so the system crash
 * dialog still appears and the process still dies.
 */
object CrashJournal {

    private const val DIR_NAME = "crash"
    private const val FILE_PREFIX = "crash-"
    private const val MAX_FILES = 20

    @Volatile
    private var dir: File? = null

    /** Installed as early as possible in [com.ironvellum.app.IronvellumApp.onCreate]. */
    fun install(context: Context) {
        val journalDir = File(context.filesDir, DIR_NAME)
        dir = journalDir
        // Install UNCONDITIONALLY. Android always sets a default handler
        // (RuntimeInit's KillApplicationHandler), but a missing one must never
        // be a reason to stop recording — the record is the whole point.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(journalDir, throwable, thread.name)
            // Delegate so the system still shows its dialog and kills us.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Newest-first records, at most [limit] of them. Never throws. */
    fun recent(limit: Int = 20): List<String> = recentIn(dir, limit)

    /** Deletes every record. Never throws. */
    fun clear() {
        val journalDir = dir ?: return
        clearIn(journalDir)
    }

    fun crashCount(): Int = countIn(dir)

    fun latestTimestamp(): String? = latestIn(dir)

    // ---- directory-injected core, exercised directly by the unit test ----

    fun recentIn(journalDir: File?, limit: Int): List<String> =
        try {
            journalDir ?: return emptyList()
            files(journalDir)
                .take(limit.coerceAtLeast(0))
                .map { it.readText() }
        } catch (_: Throwable) {
            emptyList()
        }

    /** Every delete is best-effort: a locked file must never break a crash path. */
    fun clearIn(journalDir: File) {
        try {
            files(journalDir).forEach { it.delete() }
        } catch (_: Throwable) {
            // deletion is best-effort by contract
        }
    }

    fun countIn(journalDir: File?): Int =
        try {
            journalDir ?: return 0
            files(journalDir).size
        } catch (_: Throwable) {
            0
        }

    fun latestIn(journalDir: File?): String? =
        try {
            journalDir ?: return null
            files(journalDir).firstOrNull()?.readText()?.lineSequence()?.firstOrNull()
        } catch (_: Throwable) {
            null
        }

    private fun files(journalDir: File): List<File> =
        journalDir
            .listFiles { f -> f.name.startsWith(FILE_PREFIX) }
            ?.sortedByDescending { it.name }
            .orEmpty()

    /**
     * Writes one record and enforces retention. Directory-injected so the unit
     * test can drive it against a plain folder — no Robolectric, no Context.
     */
    fun record(dir: File, throwable: Throwable, threadName: String, now: Instant = Instant.now()) {
        try {
            val meta = AppMeta.current
            val text = format(
                timestamp = now.toString(),
                versionLine = "${meta.versionName} (${meta.versionCode})",
                androidLine = "${Build.VERSION.RELEASE.orEmpty()} (API ${Build.VERSION.SDK_INT})",
                deviceLine = "${Build.MANUFACTURER} ${Build.MODEL}",
                threadName = threadName,
                stackTrace = stackTraceOf(throwable),
            )
            writeRecord(dir, text, now.toEpochMilli())
            enforceRetention(dir, MAX_FILES)
        } catch (_: Throwable) {
            // best-effort by contract
        }
    }

    /** Full chain: the thrown exception plus every cause, indented by "Caused by:". */
    fun format(
        timestamp: String,
        versionLine: String,
        androidLine: String,
        deviceLine: String,
        threadName: String,
        stackTrace: String,
    ): String = buildString {
        append("Ironvellum crash — ").append(timestamp).append('\n')
        append("app: ").append(versionLine).append('\n')
        append("android: ").append(androidLine).append('\n')
        append("device: ").append(deviceLine).append('\n')
        append("thread: ").append(threadName).append('\n')
        append(stackTrace)
        if (!stackTrace.endsWith("\n")) append('\n')
    }

    private fun stackTraceOf(throwable: Throwable): String = buildString {
        var current: Throwable? = throwable
        while (current != null) {
            append(current.javaClass.name)
            current.message?.let { append(": ").append(it) }
            append('\n')
            current.stackTrace.forEach { frame ->
                append("    at ").append(frame).append('\n')
            }
            current = current.cause?.takeIf { it !== current }
            if (current != null) append("Caused by: ")
        }
    }

    private fun writeRecord(journalDir: File, text: String, epochMillis: Long) {
        if (!journalDir.exists() && !journalDir.mkdirs()) return
        // epoch-milli names can collide on two crashes in one millisecond;
        // bump until free rather than overwrite an existing record
        var target = File(journalDir, "$FILE_PREFIX$epochMillis.txt")
        var suffix = 0
        while (target.exists() && suffix < 100) {
            target = File(journalDir, "$FILE_PREFIX$epochMillis-${++suffix}.txt")
        }
        target.writeText(text)
    }

    /** Keeps only the newest [maxFiles] records; never throws. */
    fun enforceRetention(dir: File, maxFiles: Int) {
        try {
            files(dir).drop(maxFiles.coerceAtLeast(0)).forEach { f ->
                try {
                    f.delete()
                } catch (_: Throwable) {
                    // a locked file must never break retention or the crash path
                }
            }
        } catch (_: Throwable) {
            // listing failed — retention is best-effort
        }
    }

    /** Version metadata captured once at install; mutable so unit tests can set it. */
    data class AppMeta(val versionName: String, val versionCode: Long) {
        companion object {
            @Volatile
            var current: AppMeta = AppMeta("unknown", 0)
        }
    }

    fun installMeta(versionName: String, versionCode: Long) {
        AppMeta.current = AppMeta(versionName, versionCode)
    }
}
