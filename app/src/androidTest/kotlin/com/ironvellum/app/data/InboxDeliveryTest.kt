package com.ironvellum.app.data

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.cloud.InboxPollThrottle
import com.ironvellum.app.data.cloud.Inbox
import com.ironvellum.app.data.cloud.InboxItem
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real tray delivery with isolated bookkeeping; permission and manager faults never consume an event. */
@RunWith(AndroidJUnit4::class)
class InboxDeliveryTest {
    private val realContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() = realContext.getSystemService(NotificationManager::class.java)
    private val preferences get() = realContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var permissionGranted = true
    private var managerFails = false
    private lateinit var context: Context

    @Before fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(realContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        Notifications.ensureChannels(realContext)
        preferences.edit().clear().commit()
        NotificationTray.clear(realContext, Notifications.ID_ALLIES)
        context = object : ContextWrapper(realContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "inbox_notifier") preferences else super.getSharedPreferences(name, mode)

            override fun checkSelfPermission(permission: String): Int =
                if (permission == Manifest.permission.POST_NOTIFICATIONS && !permissionGranted) PackageManager.PERMISSION_DENIED
                else super.checkSelfPermission(permission)

            override fun getSystemService(name: String): Any? {
                if (name == Context.NOTIFICATION_SERVICE && managerFails) throw IllegalStateException("Notification service temporarily unavailable")
                return super.getSystemService(name)
            }
        }
    }

    @After fun tearDown() {
        NotificationTray.clear(realContext, Notifications.ID_ALLIES)
        preferences.edit().clear().commit()
    }

    private fun inbox(at: Long, body: String = "Ready for the next workout") = Inbox(
        listOf(InboxItem.NewComment(at, "ally", "Test Ally", "workout", "Training", "comment-$at", body)),
        seenAtMs = null,
    )

    private fun deliver(inbox: Inbox, user: String = "account-a", current: () -> Boolean = { true }) =
        InboxNotifier.onInbox(context, user, inbox, isCurrent = current)

    private fun posted() = manager.activeNotifications.firstOrNull { it.id == Notifications.ID_ALLIES }

    private fun awaitPosted() = NotificationTray.awaitPosted(realContext, Notifications.ID_ALLIES)

    @Test fun existingBacklogIsSilentButLaterActivityPostsOnlyOnce() {
        deliver(inbox(100))
        assertNull(posted())
        deliver(inbox(200))
        val notification = checkNotNull(awaitPosted()).notification
        assertEquals(Notifications.CHANNEL_ALLIES, notification.channelId)
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Ready for the next workout"))
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val public = checkNotNull(notification.publicVersion)
        assertFalse(public.extras.toString().contains("Test Ally"))
        assertFalse(public.extras.toString().contains("Ready for the next workout"))
        captureTray()
        NotificationTray.clear(realContext, Notifications.ID_ALLIES)
        deliver(inbox(200))
        assertNull("a duplicate read must not repost a dismissed alert", posted())
    }

    @Test fun sameAccountResignInImmediatelyBaselinesThenDeliversNewActivity() {
        val throttle = InboxPollThrottle()
        val client = Any()
        var reads = 0
        fun poll(at: Long, now: Long) {
            val generation = InboxNotifier.generation()
            if (throttle.shouldSkip("account-a", client, generation, now, true)) return
            reads++
            deliver(inbox(at))
            throttle.record("account-a", client, generation, now, true)
        }

        poll(100, 1_000)
        assertNull(posted())
        InboxNotifier.reset(context)
        poll(200, 2_000)
        assertEquals("new generation must read immediately", 2, reads)
        assertNull("history at the new baseline stays silent", posted())
        poll(300, 32_001)
        assertEquals(3, reads)
        assertNotNull("activity after the new baseline remains eligible", awaitPosted())
    }

    @Test fun permissionRecoveryDeliversThePendingComment() {
        permissionGranted = false
        deliver(inbox(100))
        deliver(inbox(200))
        assertNull(posted())
        permissionGranted = true
        deliver(inbox(200))
        assertNotNull("permission refusal must not consume the pending comment", awaitPosted())
    }

    @Test fun blockedReactionIsRetriedButRemovingAndReaddingItDoesNotNotifyAgain() {
        deliver(Inbox(emptyList(), null))
        val reaction = InboxItem.NewReaction(200, "ally", "Test Ally", "workout", "Training", com.ironvellum.app.data.cloud.Reaction.FLAME)
        permissionGranted = false
        deliver(Inbox(listOf(reaction), null))
        assertNull(posted())
        permissionGranted = true
        deliver(Inbox(listOf(reaction), null))
        assertNotNull("blocked reactions must not be recorded as already announced", awaitPosted())
        NotificationTray.clear(realContext, Notifications.ID_ALLIES)
        deliver(Inbox(listOf(reaction.copy(occurredAtMs = 300)), null))
        assertNull("toggling the same reaction must not ring the phone again", posted())
    }

    @Test fun preferenceRecoveryDeliversThePendingComment() {
        deliver(inbox(100))
        preferences.edit().putBoolean("enabled", false).commit()
        deliver(inbox(200))
        assertNull(posted())
        preferences.edit().putBoolean("enabled", true).commit()
        deliver(inbox(200))
        assertNotNull(awaitPosted())
    }

    @Test fun failedNotificationServiceDoesNotConsumeTheComment() {
        deliver(inbox(100))
        managerFails = true
        try {
            deliver(inbox(200))
            fail("the service fault must reach the caller so work can retry")
        } catch (expected: IllegalStateException) {
            assertEquals("Notification service temporarily unavailable", expected.message)
        }
        managerFails = false
        deliver(inbox(200))
        assertNotNull("a retry must still have an undelivered comment", awaitPosted())
    }

    @Test fun staleAccountReplyCannotPostOrReplaceTheNewAccountsBaseline() {
        deliver(inbox(100))
        deliver(inbox(300), user = "account-b")
        deliver(inbox(400), user = "account-a", current = { false })
        assertNull(posted())
        deliver(inbox(350), user = "account-b")
        assertNotNull("a stale reply must not advance another account's baseline", awaitPosted())
    }

    @Test fun readingElsewhereClearsTheTrayAndDoesNotReplaySeenActivity() {
        deliver(inbox(100))
        deliver(inbox(200))
        assertNotNull(awaitPosted())
        deliver(inbox(300).copy(seenAtMs = 300))
        NotificationTray.awaitAbsent(realContext, Notifications.ID_ALLIES)
        assertNull(posted())
        deliver(inbox(300).copy(seenAtMs = 300))
        assertNull(posted())
    }

    private fun captureTray() {
        if (InstrumentationRegistry.getArguments().getString("shots") != "true") return
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = automation.executeShellCommand(command).use {
            java.io.FileInputStream(it.fileDescriptor).use { input -> input.readBytes() }
        }
        shell("cmd statusbar expand-notifications")
        try {
            Thread.sleep(1000)
            val bitmap = checkNotNull(automation.takeScreenshot())
            val file = java.io.File(checkNotNull(realContext.getExternalFilesDir(null)), "notification-social-delivered.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            shell("mkdir -p /data/local/tmp/optimise-shots")
            shell("cp ${file.absolutePath} /data/local/tmp/optimise-shots/${file.name}")
        } finally {
            shell("cmd statusbar collapse")
        }
    }

    companion object { private const val PREFS = "inbox_delivery_test" }
}
