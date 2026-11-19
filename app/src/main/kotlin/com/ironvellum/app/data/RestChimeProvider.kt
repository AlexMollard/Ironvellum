package com.ironvellum.app.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import com.ironvellum.app.R
import java.io.FileNotFoundException

/** Exposes only the bundled cue to Android's notification player, at a URI that survives upgrades. */
class RestChimeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = if (isChime(uri)) "audio/wav" else null

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        if (mode != "r" || !isChime(uri)) throw FileNotFoundException(uri.toString())
        return requireNotNull(context).resources.openRawResourceFd(R.raw.rest_chime)
    }

    override fun canonicalize(uri: Uri): Uri? = uri.takeIf(::isChime)
    override fun uncanonicalize(uri: Uri): Uri? = uri.takeIf(::isChime)

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    private fun isChime(uri: Uri): Boolean = uri == soundUri(requireNotNull(context))

    companion object {
        internal fun soundUri(context: Context): Uri = Uri.parse("content://${context.packageName}.sounds/rest-chime.wav")
    }
}
