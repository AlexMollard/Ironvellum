package com.ironvellum.app

import com.ironvellum.app.data.Units
import android.app.Notification
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
import com.ironvellum.app.data.TrialPrefs
import com.ironvellum.app.domain.RestTimer
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.Supersets
import com.ironvellum.app.domain.workingNumber
import com.ironvellum.app.ui.asString
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
 * rest channel when it ends. What it shows is [LiveWorkoutNotification]'s.
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

    /** The trial [watcher] describes; a restart for the same one must not tear it down. */
    private var watchedSessionId = -1L

    /** The newest trial notification, re-shown as is when the same trial is announced again. */
    private var lastTrial: Notification? = null

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
        val sessionId = intent?.getLongExtra(EXTRA_SESSION_ID, -1L) ?: -1L
        val sameTrial = sessionId > 0L && sessionId == watchedSessionId && watcher?.isActive == true
        if (!sameTrial) {
            watcher?.cancel()
            watcher = null
            restWait?.cancel()
            restWait = null
            lastTrial = null
            watchedSessionId = -1L
        }
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
            // The same trial keeps its last notification and its armed rest: no
            // "Opening the trial" flicker, no gap before the watcher's next emission.
            lastTrial ?: LiveWorkoutNotification.build(this, sessionId, emptyList(), routine = "", startedAtMs = null, rest = null),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )

        if (sameTrial) return START_NOT_STICKY
        watchedSessionId = sessionId

        watcher = scope.launch {
            var lastDone = -1
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
                    val done = sets.count { it.done }
                    val allDone = sets.any { !it.warmup } && sets.none { it.isPending }
                    // "Rest is over" is stale news once the sets move on: a set
                    // ticked or unticked, or every set done (no rest follows).
                    if (allDone || (lastDone >= 0 && done != lastDone)) {
                        NotificationManagerCompat.from(this@WorkoutSessionService).cancel(Notifications.ID_REST_OVER)
                    }
                    lastDone = done
                    if (allDone && rest != null) {
                        // Nothing is left to rest for; the emission this causes re-enters with no rest.
                        RestClock.cancel(sessionId)
                        return@collect
                    }
                    val trial = LiveWorkoutNotification.build(this@WorkoutSessionService, sessionId, sets, session.label, session.startedAtMs, rest)
                    lastTrial = trial
                    post(Notifications.ID_TRIAL, trial)
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

    /** "Set 3 · 8 reps · 60kg" for the next set to do, or null when every set is done. */
    private fun nextLine(next: SessionSet?, sets: List<SessionSet>): UiText? = next?.let {
        val number = it.workingNumber(sets)
        val load = it.weightKg?.takeIf { kg -> kg > 0.0 }?.let(::formatLoad)
        // A HOLD set carries its figure in seconds; REPS sets count.
        val seconds = it.durationSec
        if (seconds != null && it.reps == 0) {
            if (load == null) UiText.res(R.string.notification_trial_set_hold, number, seconds)
            else UiText.res(R.string.notification_trial_set_hold_load, number, seconds, load)
        } else {
            if (load == null) UiText.plural(R.plurals.notification_trial_set_reps, it.reps, number, it.reps)
            else UiText.plural(R.plurals.notification_trial_set_reps_load, it.reps, number, it.reps, load)
        }
    }

    private fun restOverNotification(sessionId: Long, sets: List<SessionSet>): Notification {
        val next = Supersets.nextSet(sets)
        // The alert mode picks the channel: vibrate, sound and vibrate, or a silent notice.
        return NotificationCompat.Builder(this, Notifications.restChannel(TrialPrefs(this).restAlert))
            .setSmallIcon(R.drawable.ic_reminder)
            .setColor(Notifications.accent(this))
            .setContentTitle(getString(R.string.notification_rest_over_title))
            .setContentText(
                next?.let { UiText.res(R.string.notification_rest_over_text, it.exerciseName, nextLine(it, sets)!!).asString(this) }
                    ?: getString(R.string.notification_rest_over_next),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(LiveWorkoutNotification.trialIntent(this, sessionId, seal = false, requestCode = Notifications.REQUEST_REST_OVER))
            .setAutoCancel(true)
            // A buzz, not a fixture: gone by the time the next rest could end.
            .setTimeoutAfter(60_000)
            .build()
    }

    private fun formatLoad(kg: Double): String =
        Units.format(kg) + Units.name

    override fun onDestroy() {
        releaseWake()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
