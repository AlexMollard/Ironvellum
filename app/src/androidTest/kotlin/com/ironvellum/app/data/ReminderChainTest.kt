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
 * The Summons' whole chain, exercised for real: enable schedules the next
 * run and marks the preference, and notifyIfWanted posts through the real
 * channel with the real permission check. No Robolectric shadows.
 *
 * The post itself is conditional on the day: a rite scheduled today, not yet
 * sealed and not under way, posts; a respite, a sealed day or a trial in
 * progress stays silent. Either way the preference and the tray must agree.
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

        Notifications.ensureChannels(context)
        Reminders.enable(context)
        assertTrue("enable must persist the opt-in", Reminders.enabled(context))

        val posted = Reminders.notifyIfWanted(context, repo)
        assertEquals("the tray must match the day", summonsWanted(context), posted)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (posted) {
            assertTrue(
                "the posted Summons must be live in the tray",
                manager.activeNotifications.any { it.notification.channelId == Notifications.CHANNEL_SUMMONS },
            )
        }

        Reminders.disable(context)
        assertTrue("disable must clear the opt-in", !Reminders.enabled(context))
        assertEquals("a disabled Summons must post nothing", false, Reminders.notifyIfWanted(context, repo))
    }

    private suspend fun summonsWanted(context: Context): Boolean {
        val repo = (context.applicationContext as IronvellumApp).repository
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val riteToday = repo.observePresets().first().any { it.scheduledDay == today.dayOfWeek.value }
        val sealedToday = repo.observeHistory().first().any { it.first.completedAtMs?.let(::dayOf) == today }
        val liveToday = repo.observeLiveSession().first()?.let { dayOf(it.startedAtMs) == today } == true
        return riteToday && !sealedToday && !liveToday
    }
}
