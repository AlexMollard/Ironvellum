package com.ironvellum.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.RestTimer
import com.ironvellum.app.domain.SessionSet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The live workout notification as the system holds it: posted on the workout channel, with the Live Update request
 * and chip text, the lock-screen versions, the actions, plus the static shortcuts and the Quick Settings tile the
 * manifest declares. The shapes are built with [live] forced, so they are checked on any Android version; whether
 * Android 16 actually promotes one is a device matter (the user's own switch decides) and is not asserted here.
 */
@RunWith(AndroidJUnit4::class)
class LiveWorkoutNotificationTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as IronvellumApp
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private fun set(id: Long, exercise: Long, name: String, index: Int, done: Boolean = false, kg: Double = 47.5, reps: Int = 6) =
        SessionSet(id = id, exerciseId = exercise, exerciseName = name, exercisePosition = exercise.toInt(), setIndex = index, reps = reps, weightKg = kg, done = done)

    private val sets = listOf(
        set(1, 1, "Overhead Press", 0, done = true), set(2, 1, "Overhead Press", 1, done = true), set(3, 1, "Overhead Press", 2),
        set(4, 2, "Incline Press", 0, kg = 30.0, reps = 10), set(5, 2, "Incline Press", 1, kg = 30.0, reps = 10),
    )

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        Notifications.ensureChannels(context)
        Words.setThemed(app, false)
        manager.cancel(Notifications.ID_TRIAL)
    }

    @After
    fun tearDown() {
        manager.cancel(Notifications.ID_TRIAL)
        Words.setThemed(app, true)
    }

    private fun build(live: Boolean, showLifts: Boolean = true, rest: RestTimer? = null) =
        LiveWorkoutNotification.build(context, 7L, sets, "Push A", System.currentTimeMillis() - 60_000, rest, live = live, showLifts = showLifts)

    private fun Notification.text(key: String) = extras.getCharSequence(key)?.toString()

    @Test
    fun theLiveUpdateAsksToBePromotedAndShowsSetProgressOnTheChip() {
        val n = build(live = true)
        assertTrue("requests promotion", NotificationCompat.isRequestPromotedOngoing(n))
        assertEquals("Set 3 of 3", NotificationCompat.getShortCriticalText(n))
        assertTrue("ongoing", n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNull("no RemoteViews", n.contentView)
        assertEquals(Notifications.CHANNEL_TRIAL, n.channelId)
        assertEquals("Overhead Press", n.text(Notification.EXTRA_TITLE))
        assertEquals("Set 3 of 3 · 6 reps · 47.5 kg", n.text(Notification.EXTRA_TEXT))
        assertEquals("Push A · 2 / 5 sets", n.text(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun whileRestingTheChipCountsDownInsteadOfShowingText() {
        val rest = RestTimer.start(7L, 84, SystemClock.elapsedRealtime())
        val n = build(live = true, rest = rest)
        assertNull("the chronometer is the chip text", NotificationCompat.getShortCriticalText(n))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals("Rest · then set 3 of 3 · 6 × 47.5 kg", n.text(Notification.EXTRA_TEXT))
        assertEquals(listOf("Set done", "Skip rest", "+15 s"), n.actions.map { it.title.toString() })
    }

    @Test
    fun betweenSetsTheActionsAreSetDoneAndFinish() {
        assertEquals(listOf("Set done", "Finish"), build(live = true).actions.map { it.title.toString() })
    }

    @Test
    fun theFallbackIsTheSameCardWithTheSameWordsAndTheExpandedExtras() {
        val n = build(live = false, rest = RestTimer.start(7L, 84, SystemClock.elapsedRealtime()))
        assertFalse(NotificationCompat.isRequestPromotedOngoing(n))
        assertNull(NotificationCompat.getShortCriticalText(n))
        assertEquals("Push A · 2 / 5 sets", n.text(Notification.EXTRA_SUB_TEXT))
        val big = n.text(Notification.EXTRA_BIG_TEXT)!!
        assertTrue(big, big.startsWith("Rest · then set 3 of 3 · 6 × 47.5 kg"))
        assertTrue(big, big.contains("Last set: 6 × 47.5 kg · set 2 of 3"))
        assertTrue(big, big.contains("Next up: Incline Press, 2 × 10"))
    }

    @Test
    fun liftsShowOnTheLockScreenUnlessTheLifterHidesThem() {
        val shown = build(live = true, showLifts = true)
        assertEquals(Notification.VISIBILITY_PUBLIC, shown.visibility)
        assertNull(shown.publicVersion)

        val hidden = build(live = true, showLifts = false)
        assertEquals(Notification.VISIBILITY_PRIVATE, hidden.visibility)
        val public = checkNotNull(hidden.publicVersion)
        assertEquals("Workout in progress", public.text(Notification.EXTRA_TITLE))
        assertEquals("Unlock to see your set.", public.text(Notification.EXTRA_TEXT))
        assertEquals(listOf("Open"), public.actions.map { it.title.toString() })
        val leaked = (public.text(Notification.EXTRA_TITLE) + public.text(Notification.EXTRA_TEXT)).contains("Press")
        assertFalse("no lift on the lock screen", leaked)
    }

    @Test
    fun theThemedWordingFollowsTheSwitch() {
        Words.setThemed(app, true)
        val n = build(live = false, showLifts = false)
        assertEquals("Trial in progress", n.publicVersion!!.text(Notification.EXTRA_TITLE))
        assertEquals("Seal", build(live = false).actions.last().title.toString())
    }

    @Test
    fun itPostsOnTheWorkoutChannelAsOneOngoingNotification() {
        manager.notify(Notifications.ID_TRIAL, build(live = true))
        val posted = manager.activeNotifications.filter { it.id == Notifications.ID_TRIAL }
        assertEquals(1, posted.size)
        assertEquals(Notifications.CHANNEL_TRIAL, posted.single().notification.channelId)
    }

    @Test
    fun theManifestDeclaresTheTwoStaticShortcuts() {
        val shortcuts = context.getSystemService(ShortcutManager::class.java).manifestShortcuts.associateBy { it.id }
        assertEquals(setOf("start_today", "resume"), shortcuts.keys)
        assertEquals("Start today", shortcuts.getValue("start_today").shortLabel.toString())
        assertEquals("Resume", shortcuts.getValue("resume").shortLabel.toString())
        assertEquals("begin", shortcuts.getValue("start_today").intent!!.getStringExtra(Notifications.EXTRA_OPEN_TAB))
        assertEquals("resume", shortcuts.getValue("resume").intent!!.getStringExtra(Notifications.EXTRA_OPEN_TAB))
    }

    @Test
    fun theTileIsDeclaredAndOnlyTheSystemMayBindIt() {
        val info = context.packageManager.getServiceInfo(
            android.content.ComponentName(context, LiveWorkoutTileService::class.java), PackageManager.GET_META_DATA,
        )
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", info.permission)
        assertTrue(info.exported)
    }
}
