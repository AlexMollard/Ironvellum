package com.ironvellum.app.data

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The reminder's whole chain, exercised for real: enable schedules the daily
 * work and marks the preference, and notifyIfWanted posts through the real
 * channel with the real permission check. No Robolectric shadows.
 *
 * The post itself is conditional on the day: a quest open posts, a quest
 * already claimed today stays silent. Either way the preference and the
 * tray must agree with each other.
 */
@RunWith(AndroidJUnit4::class)
class ReminderChainTest {

    @Test
    fun enablingPostsAndDisablingStops() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = (context.applicationContext as IronvellumApp).repository
        // Grant what the Settings toggle would ask for, so the post is real.
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )

        Reminders.ensureChannel(context)
        Reminders.enable(context)
        assertTrue("enable must persist the opt-in", Reminders.enabled(context))

        val posted = Reminders.notifyIfWanted(context, repo)
        val questDoneToday = isQuestDoneToday(context)
        assertEquals("the tray must match the day", !questDoneToday, posted)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (posted) {
            assertTrue(
                "the posted reminder must be live in the tray",
                manager.activeNotifications.any { it.notification.channelId == Reminders.CHANNEL_ID },
            )
        }

        Reminders.disable(context)
        assertTrue("disable must clear the opt-in", !Reminders.enabled(context))
        assertEquals("a disabled reminder must post nothing", false, Reminders.notifyIfWanted(context, repo))
    }

    private suspend fun isQuestDoneToday(context: Context): Boolean {
        val app = context.applicationContext as IronvellumApp
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        return app.repository.observeHistory().first().any {
            it.first.completedAtMs?.let { ms ->
                Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() == today
            } == true
        }
    }
}
