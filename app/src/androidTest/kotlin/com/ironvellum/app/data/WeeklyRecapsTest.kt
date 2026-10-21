package com.ironvellum.app.data

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.domain.RecapSchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The Sunday recap's chain against the real channel and the real permission: a week with workouts posts once, on its own
 * channel, with a lock-screen version that carries no figures; the same week is never posted twice; a week with nothing in it
 * posts nothing. The history is a test database, so the week is exactly the one written here (Mon 12 - Sun 18 Oct 2026).
 */
@RunWith(AndroidJUnit4::class)
class WeeklyRecapsTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as IronvellumApp
    private val zone = ZoneId.systemDefault()
    private val week = LocalDate.of(2026, 10, 12)
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private fun at(date: LocalDate, hour: Int = 18, minute: Int = 0) = ZonedDateTime.of(date, java.time.LocalTime.of(hour, minute), zone)

    @Before
    fun setUp() = runBlocking<Unit> {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        Notifications.ensureChannels(context)
        Words.setThemed(app, false)
        context.getSharedPreferences("weekly_recap", Context.MODE_PRIVATE).edit().clear().commit()
        manager.cancel(Notifications.ID_RECAP)
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        val bench = db.exerciseDao().observeAll().first().first { it.name == "Bench Press" }.id
        db.withTransaction {
            // A bench of 70 the week before, 80 on Tuesday: one workout, one record.
            insert(bench, week.minusDays(5), 70.0)
            insert(bench, week.plusDays(1), 80.0)
        }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
        manager.cancel(Notifications.ID_RECAP)
        context.getSharedPreferences("weekly_recap", Context.MODE_PRIVATE).edit().clear().commit()
        Words.setThemed(app, true)
    }

    private suspend fun insert(exercise: Long, day: LocalDate, kg: Double) {
        val start = at(day).toInstant().toEpochMilli()
        val id = db.sessionDao().insertSession(
            SessionEntity(
                presetId = null, label = "Push", startedAtMs = start, completedAtMs = start + 3_600_000L,
                xpAwarded = 0, strengthScore = 0, title = "", note = "", privateNote = "",
            ),
        )
        db.sessionDao().insertSets(
            List(3) { SetLogEntity(sessionId = id, exerciseId = exercise, exercisePosition = 0, setIndex = it, reps = 5, weightKg = kg, modifiers = "", done = true) },
        )
    }

    private fun posted(): android.service.notification.StatusBarNotification? =
        manager.activeNotifications.firstOrNull { it.id == Notifications.ID_RECAP }

    @Test
    fun sundayAtNineteenDeliversTheWeekOnItsOwnChannel() = runBlocking {
        val verdict = WeeklyRecaps.deliverIfDue(context, repo, at(week.plusDays(6), 19, 2))
        assertEquals(RecapSchedule.Verdict.Deliver(week), verdict)
        val shown = checkNotNull(posted()) { "the recap is in the tray" }
        assertEquals(Notifications.CHANNEL_RECAP, shown.notification.channelId)
        assertEquals(week, WeeklyRecaps.lastDelivered(context))
        val extras = shown.notification.extras
        assertEquals("Your week: 1 workout, 1 record", extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("1,200 kg lifted, up 14%"))
        val expanded = extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertTrue(expanded.contains("Records: Bench Press"))
        assertEquals(listOf("Open recap", "Share"), shown.notification.actions.map { it.title.toString() })
    }

    @Test
    fun theLockScreenVersionSaysOnlyThatTheRecapIsReady() = runBlocking {
        WeeklyRecaps.deliverIfDue(context, repo, at(week.plusDays(6), 19, 2))
        val notification = checkNotNull(posted()).notification
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val public = checkNotNull(notification.publicVersion) { "a private notification needs a public version" }
        val title = public.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val text = public.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertEquals("Your weekly recap is ready", title)
        assertEquals("Tap to see how the week went.", text)
        assertFalse("a figure on the lock screen", (title + text).any { it.isDigit() })
        assertNull("the public version has no actions to leak anything", public.actions)
    }

    @Test
    fun theSameWeekIsNeverPostedTwice() = runBlocking {
        val now = at(week.plusDays(6), 19, 2)
        assertEquals(RecapSchedule.Verdict.Deliver(week), WeeklyRecaps.deliverIfDue(context, repo, now))
        manager.cancel(Notifications.ID_RECAP)
        // A retry, and the catch-up run on Monday morning: the marker holds.
        assertEquals(RecapSchedule.Verdict.AlreadyDelivered(week), WeeklyRecaps.deliverIfDue(context, repo, now.plusMinutes(5)))
        assertEquals(RecapSchedule.Verdict.AlreadyDelivered(week), WeeklyRecaps.deliverIfDue(context, repo, at(week.plusDays(7), 7)))
        assertNull(posted())
    }

    @Test
    fun aWeekWithNoWorkoutsPostsNothingAndMarksNothing() = runBlocking {
        val next = week.plusDays(7)
        val verdict = WeeklyRecaps.deliverIfDue(context, repo, at(next.plusDays(6), 19, 2))
        assertEquals(RecapSchedule.Verdict.EmptyWeek(next), verdict)
        assertNull(posted())
        assertNull(WeeklyRecaps.lastDelivered(context))
    }

    @Test
    fun beforeSundayEveningNothingIsDue() = runBlocking {
        assertEquals(RecapSchedule.Verdict.NotDue, WeeklyRecaps.deliverIfDue(context, repo, at(week.plusDays(2), 12)))
        assertEquals(RecapSchedule.Verdict.NotDue, WeeklyRecaps.deliverIfDue(context, repo, at(week.plusDays(6), 18, 59)))
        assertNull(posted())
    }

    @Test
    fun theChannelIsCalledWeeklyRecapAndHasDefaultImportance() {
        val channel = required(manager.getNotificationChannel(Notifications.CHANNEL_RECAP))
        assertEquals("Weekly recap", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    private fun <T> required(value: T?): T = checkNotNull(value) { "expected a value" }

    private companion object {
        const val TEST_DB = "ironvellum-weekly-recap-test.db"
    }
}
