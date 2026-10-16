package com.ironvellum.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.domain.Summons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The two home-screen widgets, "Today" (the day card) and "Streak & week", both drawn from one
 * [WidgetState] and wired to the same Begin / Open intents. Classic RemoteViews, so no new dependency.
 *
 * They are pushed, never polled: [refresh] runs when a workout starts, is ticked or sealed, or the
 * plan changes (the Room observer in IronvellumApp), and a WorkManager run at each local midnight
 * ([scheduleMidnight]) turns the day over. The midnight run is re-booked by TimeChangeReceiver when
 * the clock or the timezone moves, like the Summons.
 */
object TodayWidget {
    private const val MIDNIGHT_WORK = "widget-midnight"

    /** A minute past, so a run that lands early still reads the new day. */
    private val MIDNIGHT: LocalTime = LocalTime.of(0, 1)

    /** The smallest cell (dp) that gets the 4x2 layout; anything under it gets the one-row 2x1 layout. */
    internal const val REGULAR_WIDTH_DP = 250f
    internal const val REGULAR_HEIGHT_DP = 120f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val providers = listOf(TodayWidgetProvider::class.java, WeekWidgetProvider::class.java)

    private fun idsOf(context: Context, provider: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))

    private fun anyPlaced(context: Context): Boolean = providers.any { idsOf(context, it).isNotEmpty() }

    /** Redraws every placed widget from the current data; nothing runs when none is placed. */
    fun refresh(context: Context) {
        if (!anyPlaced(context)) return
        scope.launch { runCatching { update(context.applicationContext) } }
    }

    /** One draw at a time, each reading the data afresh, so the last draw is never an older read. */
    private val drawing = Mutex()

    internal suspend fun update(context: Context) = drawing.withLock {
        if (!anyPlaced(context)) return@withLock
        val state = loadWidgetState(context, (context.applicationContext as IronvellumApp).repository)
        val manager = AppWidgetManager.getInstance(context)
        for (provider in providers) {
            for (id in idsOf(context, provider)) {
                manager.updateAppWidget(id, views(context, provider, state, manager.getAppWidgetOptions(id)))
            }
        }
    }

    /**
     * The widget a launcher draws: from 31 on both layouts travel together and the launcher picks by the
     * cell's size, so a resize needs no callback; before that the size the host last reported decides.
     */
    internal fun views(context: Context, provider: Class<*>, state: WidgetState, options: Bundle?): RemoteViews {
        val week = provider == WeekWidgetProvider::class.java
        fun face(compact: Boolean) = when {
            week && compact -> WidgetFaces.weekCompact(context, state)
            week -> WidgetFaces.week(context, state)
            compact -> WidgetFaces.dayCompact(context, state)
            else -> WidgetFaces.day(context, state)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                mapOf(
                    SizeF(0f, 0f) to face(true),
                    SizeF(REGULAR_WIDTH_DP, REGULAR_HEIGHT_DP) to face(false),
                ),
            )
        }
        val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        return face(compact = width < REGULAR_WIDTH_DP || height < REGULAR_HEIGHT_DP)
    }

    /** Books the run that turns the day over; KEEP leaves a queued one alone, REPLACE re-reads the clock. */
    fun scheduleMidnight(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val delay = Summons.delayUntilNext(ZonedDateTime.now(), at = MIDNIGHT)
        val request = OneTimeWorkRequestBuilder<TodayWidgetWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MIDNIGHT_WORK, policy, request)
    }

    internal fun cancelMidnightIfNone(context: Context) {
        if (!anyPlaced(context)) WorkManager.getInstance(context).cancelUniqueWork(MIDNIGHT_WORK)
    }

    /** App start: a widget placed on an earlier run keeps its midnight run. */
    fun ensureScheduled(context: Context) {
        if (anyPlaced(context)) scheduleMidnight(context)
    }

    /** The clock or timezone moved: re-aim the midnight run and redraw for whatever day it now is. */
    fun onClockChanged(context: Context) {
        if (!anyPlaced(context)) return
        scheduleMidnight(context, ExistingWorkPolicy.REPLACE)
        refresh(context)
    }
}

/** Redraws the widgets for the new day, then books the next midnight. */
class TodayWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { TodayWidget.update(applicationContext) }
        // Last: REPLACE under this work's own name cancels this run, so nothing may follow it.
        TodayWidget.scheduleMidnight(applicationContext, ExistingWorkPolicy.REPLACE)
        return Result.success()
    }
}

/** The two widgets differ only in their layouts; the draw, the midnight run and the options are shared. */
abstract class BaseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = draw(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) = draw(context)

    private fun draw(context: Context) {
        // Room is read off the main thread; goAsync keeps the process alive until the draw lands.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { TodayWidget.update(context.applicationContext) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) = TodayWidget.scheduleMidnight(context)

    override fun onDisabled(context: Context) = TodayWidget.cancelMidnightIfNone(context)
}

/** "Today": the day card. */
class TodayWidgetProvider : BaseWidgetProvider()

/** "Streak & week": the streak and the week strip. */
class WeekWidgetProvider : BaseWidgetProvider()
