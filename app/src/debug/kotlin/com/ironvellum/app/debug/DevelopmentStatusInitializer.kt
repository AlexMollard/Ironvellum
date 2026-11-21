package com.ironvellum.app.debug

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import androidx.core.content.ContextCompat

/** Installs the shell endpoint for this process without exposing a provider API. */
class DevelopmentStatusInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = requireNotNull(context).applicationContext
        ContextCompat.registerReceiver(
            app,
            DevelopmentStatusReceiver(),
            IntentFilter(DevelopmentStatusReceiver.ACTION_READ_STATUS),
            Manifest.permission.DUMP,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}
