package com.ironvellum.app.data.localconnector

import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWindow
import android.database.CursorWrapper
import android.database.sqlite.SQLiteBlobTooBigException
import com.ironvellum.app.domain.MAX_CONTEXT_BYTES

/** Framework SQLite's default window is smaller than a legal file. Try the ordinary window first;
 * retry filling it at the file size only for the exceptional oversized row. The row is projected once. */
internal inline fun <T> Cursor.useTrainingCursor(block: (Cursor) -> T): T = use { cursor ->
    try {
        // Touch the first window before invoking code that records object IDs or charges output bytes.
        cursor.count
    } catch (_: SQLiteBlobTooBigException) {
        cursor.expandWindow()
    }
    block(object : CursorWrapper(cursor) {
        override fun moveToNext(): Boolean {
            val next = cursor.position + 1
            return try {
                cursor.moveToPosition(next)
            } catch (_: SQLiteBlobTooBigException) {
                cursor.expandWindow()
                cursor.moveToPosition(next)
            }
        }
    })
}

internal fun Cursor.expandWindow() {
    var raw = this
    while (raw is CursorWrapper) raw = raw.wrappedCursor
    val cursor = raw as? AbstractWindowedCursor ?: throw LocalExportException(LocalFailure.FILE_LIMIT)
    cursor.window = CursorWindow("training export", MAX_CONTEXT_BYTES.toLong() + 8192)
}

internal inline fun <T> Cursor.projectRows(crossinline project: (Int, Cursor) -> T): List<T> =
    useTrainingCursor { cursor ->
        buildList { while (cursor.moveToNext()) add(project(size, cursor)) }
    }

/** The referenced non-null name becomes SQL NULL only when its UTF-8 bytes exceed the file cap. */
internal fun Cursor.publicName(i: Int): String = getString(i) ?: throw LocalExportException(LocalFailure.FILE_LIMIT)

internal fun Cursor.integer(i: Int): Long {
    require(getType(i) == Cursor.FIELD_TYPE_INTEGER)
    return getLong(i)
}

internal fun Cursor.naturalInt(i: Int) = integer(i).also { require(it in 0..Int.MAX_VALUE.toLong()) }.toInt()
internal fun Cursor.optionalInt(i: Int) = if (isNull(i)) null else naturalInt(i)
internal fun Cursor.optionalGroup(i: Int) = if (isNull(i)) null else integer(i).also {
    require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
}.toInt()
internal fun Cursor.flag(i: Int) = integer(i).also { require(it in 0..1) } == 1L
internal fun Cursor.measurement(i: Int) = if (isNull(i)) null else getDouble(i).also {
    require(getType(i) in Cursor.FIELD_TYPE_INTEGER..Cursor.FIELD_TYPE_FLOAT && it.isFinite() && it >= 0)
}
