package com.ironvellum.app.data

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import org.junit.Assert.assertNull

/** Android processes tray changes asynchronously, including cancellation and replacement. */
internal object NotificationTray {
    fun posted(context: Context, id: Int): StatusBarNotification? =
        context.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == id }

    fun awaitPosted(context: Context, id: Int): StatusBarNotification? {
        val until = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < until) {
            posted(context, id)?.let { return it }
            Thread.sleep(20)
        }
        return posted(context, id)
    }

    fun awaitAbsent(context: Context, id: Int) {
        val until = SystemClock.elapsedRealtime() + 5_000
        while (posted(context, id) != null && SystemClock.elapsedRealtime() < until) Thread.sleep(20)
        assertNull("notification $id must leave the tray", posted(context, id))
    }

    fun clear(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
        awaitAbsent(context, id)
    }
}
