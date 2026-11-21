package com.ironvellum.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.Words
import com.ironvellum.app.debug.NotificationPreviewReceiver
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPreviewTest {
    private fun clearPreviews(context: Context, receiver: NotificationPreviewReceiver) {
        receiver.onReceive(context, Intent(NotificationPreviewReceiver.ACTION_CLEAR))
        val manager = context.getSystemService(NotificationManager::class.java)
        val until = android.os.SystemClock.elapsedRealtime() + 5_000
        while (manager.activeNotifications.any { it.tag == NotificationPreviewReceiver.TAG } &&
            android.os.SystemClock.elapsedRealtime() < until
        ) Thread.sleep(20)
        assertTrue(manager.activeNotifications.none { it.tag == NotificationPreviewReceiver.TAG })
    }

    @Test fun previewsDoNotRewriteAnExistingRestAlertsWorkoutDestination() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        Notifications.ensureChannels(context)
        val sessionId = 987_654_321L
        val pending = WorkoutSessionService.restOverNotification(context, sessionId, emptyList()).contentIntent
        val receiver = NotificationPreviewReceiver()
        try {
            receiver.onReceive(context, Intent(NotificationPreviewReceiver.ACTION_SHOW))
            val sent = AtomicReference<Intent>()
            val finished = CountDownLatch(1)
            pending.send(context, 0, null, { _, intent, _, _, _ ->
                sent.set(intent)
                finished.countDown()
            }, android.os.Handler(android.os.Looper.getMainLooper()))
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(sessionId, sent.get().getLongExtra(Notifications.EXTRA_SESSION_ID, -2L))
        } finally {
            clearPreviews(context, receiver)
        }
    }

    @Test fun previewsUseRealAppCardsWithoutConsumingInboxActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        Notifications.ensureChannels(context)
        val receiverInfo = context.packageManager.getReceiverInfo(ComponentName(context, NotificationPreviewReceiver::class.java), 0)
        assertEquals("android.permission.DUMP", receiverInfo.permission)
        assertTrue(receiverInfo.exported)
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefs = context.getSharedPreferences("inbox_notifier", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        val oldThemed = Words.themed
        val receiver = NotificationPreviewReceiver()
        try {
            Words.setThemed(context, false)
            receiver.onReceive(context, Intent(NotificationPreviewReceiver.ACTION_SHOW))
            val until = android.os.SystemClock.elapsedRealtime() + 5_000
            fun cards() = manager.activeNotifications.filter { it.tag == NotificationPreviewReceiver.TAG }
            while (cards().size < 6 && android.os.SystemClock.elapsedRealtime() < until) Thread.sleep(20)
            val cards = cards()
            assertEquals(6, cards.size)
            assertEquals(
                setOf("Friend request accepted", "New comment", "New kudos", "Group goal reached", "2 new social updates", "Rest is over"),
                cards.map { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString() }.toSet(),
            )
            cards.forEach {
                val notification = it.notification
                assertTrue(notification.actions.isNullOrEmpty())
                assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
                assertEquals(900_000L, notification.timeoutAfter)
                assertEquals("Preview · sample data", notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
                assertNotNull(notification.contentIntent)
            }
            assertEquals(before, prefs.all)
        } finally {
            Words.setThemed(context, oldThemed)
            clearPreviews(context, receiver)
        }
    }
}
