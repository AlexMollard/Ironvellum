package com.ironvellum.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID

internal class SharedWorkoutFixture(private val receiver: Context) : AutoCloseable {
    private val id = UUID.randomUUID().toString()
    private val authority = InstrumentationRegistry.getInstrumentation().context.packageName + ".ai-sender"
    val uri: Uri = Uri.parse("content://$authority/workout/$id")

    fun write(text: String) {
        check(request("write", Bundle().apply { putString("text", text) }) != Process.myUid())
    }

    fun grant() { request("grant") }
    fun revoke() { request("revoke") }
    fun delete() { request("delete") }

    override fun close() {
        revoke()
        delete()
    }

    private fun request(method: String, extras: Bundle? = null): Int =
        checkNotNull(receiver.contentResolver.call(uri, method, id, extras)).getInt("uid")
}
