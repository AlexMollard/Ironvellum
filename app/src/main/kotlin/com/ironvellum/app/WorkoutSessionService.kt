package com.ironvellum.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.RestClock
import com.ironvellum.app.domain.RestTimer
import com.ironvellum.app.domain.SessionSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The mid-workout companion: an ongoing, silent notification that mirrors the
 * live session — which exercise is next, which set, how many sets are done —
 * so progress is glanceable from the lock screen the way a timer is. While a
 * rest between sets runs it counts that down instead, and buzzes once on the
 * rest channel when it ends.
 *
 * Started when a session screen opens; it watches the database and stops
 * itself the moment the session is completed or abandoned, so it can never
 * outlive the trial it describes, nor can its rest.
 */
class WorkoutSessionService : Service() {

    companion object {
        private const val EXTRA_SESSION_ID = "sessionId"

        /** Slack on the rest wake lock past the rest's own end. */
        private const val WAKE_SLACK_MS = 10_000L

        fun start(context: Context, sessionId: Long) {
            Notifications.ensureChannels(context)
            val intent = Intent(context, WorkoutSessionService::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: Repository

    /**
     * The one live collector. A second start (another trial opened while this
     * one still ran) must replace it: a stale collector for the old trial would
     * see that trial end and stopSelf() the new trial's notification.
     */
    private var watcher: Job? = null

    /** Waits out the running rest; replaced whenever the rest changes. */
    private var restWait: Job? = null

    /**
     * Held only while a rest runs. Coroutine and handler delays count uptime,
     * which stops in deep sleep: a phone locked on the bench would buzz
     * minutes late. Bounded by its own timeout, so it can never leak past the rest.
     */
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        repo = (applicationContext as IronvellumApp).repository
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        watcher?.cancel()
        watcher = null
        restWait?.cancel()
        restWait = null
        val sessionId = intent?.getLongExtra(EXTRA_SESSION_ID, -1L) ?: -1L
        if (sessionId <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        // API 34+ requires the manifest type to be restated here, or the
        // notification silently never reaches the shade. The constant only
        // exists from 34 (UPSIDE_DOWN_CAKE); below it the manifest type is enough.
        androidx.core.app.ServiceCompat.startForeground(
            this,
            Notifications.ID_TRIAL,
            buildNotification(sessionId, emptyList(), startedAtMs = null, rest = null),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )

        watcher = scope.launch {
            combine(
                repo.observeSessionSets(sessionId),
                repo.observeSession(sessionId),
                RestClock.timer,
            ) { sets, session, timer -> Triple(sets, session, timer?.takeIf { it.sessionId == sessionId }) }
                .collect { (sets, session, rest) ->
                    // Completed or abandoned: the notification has no reason to
                    // live, and neither has a rest between sets of it.
                    if (session == null || session.completedAtMs != null) {
                        RestClock.cancel(sessionId)
                        NotificationManagerCompat.from(this@WorkoutSessionService).cancel(Notifications.ID_REST_OVER)
                        stopSelf()
                        return@collect
                    }
                    post(Notifications.ID_TRIAL, buildNotification(sessionId, sets, session.startedAtMs, rest))
                    awaitRest(sessionId, rest, sets)
                }
        }
        return START_NOT_STICKY
    }

    /** (Re)arms the wait for [rest]'s end; a set ticked or a rest skipped re-arms or disarms it. */
    private fun awaitRest(sessionId: Long, rest: RestTimer?, sets: List<SessionSet>) {
        restWait?.cancel()
        restWait = null
        releaseWake()
        if (rest == null) return
        // A new rest makes the last one's buzz stale news.
        NotificationManagerCompat.from(this).cancel(Notifications.ID_REST_OVER)
        val left = rest.remainingMs(SystemClock.elapsedRealtime())
        holdWake(left + WAKE_SLACK_MS)
        restWait = scope.launch {
            // Elapsed realtime decides, not the delay: loop until it says done.
            while (!rest.isOver(SystemClock.elapsedRealtime())) {
                delay(rest.remainingMs(SystemClock.elapsedRealtime()))
            }
            post(Notifications.ID_REST_OVER, restOverNotification(sessionId, sets))
            // Back to the trial's own notification; a countdown left in place
            // would run on into negative time.
            RestClock.finish(rest)
            releaseWake()
        }
    }

    private fun holdWake(timeoutMs: Long) {
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ironvellum:rest").apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    private fun releaseWake() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun post(id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        @Suppress("MissingPermission") // guarded directly above
        manager.notify(id, notification)
    }

    /** Opens the live trial; with [seal], with its seal prompt showing. */
    private fun trialIntent(sessionId: Long, seal: Boolean, requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(Notifications.EXTRA_OPEN_TAB, Notifications.TAB_TRIAL)
            .putExtra(Notifications.EXTRA_SESSION_ID, sessionId)
            .putExtra(Notifications.EXTRA_SEAL, seal)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** "Set 3 · 8 reps · 60kg" for the next set to do, or null when every set is done. */
    private fun nextLine(next: SessionSet?): String? = next?.let {
        buildString {
            append("Set ${it.setIndex + 1}")
            // A HOLD set carries its figure in seconds; REPS sets count.
            val figure = if (it.durationSec != null && it.reps == 0)
                "${it.durationSec}s" else "${it.reps} ${if (it.reps == 1) "rep" else "reps"}"
            append(" · $figure")
            it.weightKg?.takeIf { kg -> kg > 0.0 }?.let { kg -> append(" · ${formatLoad(kg)}") }
        }
    }

    /**
     * Clock-app style: the chronometer counts up from the trial's start, the
     * title is the exercise the lifter is on, the text the set in hand, and
     * the sub-text the progress, so the collapsed row carries all three.
     * During a rest the chronometer counts that down instead.
     */
    private fun buildNotification(sessionId: Long, sets: List<SessionSet>, startedAtMs: Long?, rest: RestTimer?): Notification {
        val done = sets.count { it.done }
        val total = sets.size
        val next = sets.firstOrNull { !it.done }
        // The exercise the lifter is on now, not the first one of the day.
        val title = (next ?: sets.firstOrNull())?.exerciseName ?: "Trial"
        val resting = rest != null && !rest.isOver(SystemClock.elapsedRealtime())
        val body = when {
            total == 0 -> "Opening the trial…"
            next == null -> "Every set is done. Seal the Trial."
            resting -> "Rest · then ${nextLine(next)}"
            else -> nextLine(next).orEmpty()
        }
        val setWord = if (total == 1) "set" else "sets"
        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_TRIAL)
            .setSmallIcon(R.drawable.ic_reminder)
            .setColor(Notifications.ACCENT)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText("TRIAL · $done / $total $setWord")
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(trialIntent(sessionId, seal = false, requestCode = Notifications.REQUEST_TRIAL))
        if (resting) {
            // The chronometer counts toward a wall-clock instant; the rest is
            // kept in elapsed time, so translate the time left, not the end.
            val left = rest.remainingMs(SystemClock.elapsedRealtime())
            builder.setWhen(System.currentTimeMillis() + left)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else if (startedAtMs != null) {
            builder.setWhen(startedAtMs).setShowWhen(true).setUsesChronometer(true)
        }
        // Sealing pays XP, shows the victory and offers to update the rite, and
        // a hasty seal asks first: none of that can happen in the shade. The
        // action opens the trial with its seal prompt instead.
        if (done > 0) {
            builder.addAction(0, "Seal", trialIntent(sessionId, seal = true, requestCode = Notifications.REQUEST_SEAL))
        }
        return builder.build()
    }

    private fun restOverNotification(sessionId: Long, sets: List<SessionSet>): Notification {
        val next = sets.firstOrNull { !it.done }
        return NotificationCompat.Builder(this, Notifications.CHANNEL_REST)
            .setSmallIcon(R.drawable.ic_reminder)
            .setColor(Notifications.ACCENT)
            .setContentTitle("Rest is over")
            .setContentText(next?.let { "${it.exerciseName} · ${nextLine(it)}" } ?: "On to the next set.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(trialIntent(sessionId, seal = false, requestCode = Notifications.REQUEST_REST_OVER))
            .setAutoCancel(true)
            // A buzz, not a fixture: gone by the time the next rest could end.
            .setTimeoutAfter(60_000)
            .build()
    }

    private fun formatLoad(kg: Double): String =
        if (kg == kg.toLong().toDouble()) "${kg.toLong()}kg" else "${kg}kg"

    override fun onDestroy() {
        releaseWake()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
