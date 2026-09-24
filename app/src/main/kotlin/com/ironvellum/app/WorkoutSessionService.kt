package com.ironvellum.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.SessionSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The mid-workout companion: an ongoing, silent notification that mirrors the
 * live session — which exercise is next, which set, how many sets are done —
 * so progress is glanceable from the lock screen the way a timer is.
 *
 * Started when a session screen opens; it watches the database and stops
 * itself the moment the session is completed or abandoned, so it can never
 * outlive the trial it describes.
 */
class WorkoutSessionService : Service() {

    companion object {
        const val CHANNEL_ID = "workout"
        private const val NOTIFICATION_ID = 2
        private const val EXTRA_SESSION_ID = "sessionId"

        fun start(context: Context, sessionId: Long) {
            ensureChannel(context)
            val intent = Intent(context, WorkoutSessionService::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Workout in progress",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Live progress for the session you have open." },
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: Repository

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        repo = (applicationContext as IronvellumApp).repository
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getLongExtra(EXTRA_SESSION_ID, -1L) ?: -1L
        if (sessionId <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        // API 34+ requires the manifest type to be restated here, or the
        // notification silently never reaches the shade.
        androidx.core.app.ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(emptyList()),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        scope.launch {
            combine(
                repo.observeSessionSets(sessionId),
                repo.observeRecentSessions(5),
            ) { sets, recent -> sets to recent.firstOrNull { it.id == sessionId } }
                .collect { (sets, session) ->
                    // Completed or abandoned: the notification has no reason to live.
                    if (session == null || session.completedAtMs != null) {
                        stopSelf()
                        return@collect
                    }
                    post(buildNotification(sets))
                }
        }
        return START_NOT_STICKY
    }

    private fun post(notification: Notification) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        @Suppress("MissingPermission") // guarded directly above
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(sets: List<SessionSet>): Notification {
        val done = sets.count { it.done }
        val total = sets.size
        val next = sets.firstOrNull { !it.done }
        val label = sets.firstOrNull()?.exerciseName?.uppercase() ?: "WARMING UP"
        val body = when {
            total == 0 -> "Loading the session..."
            next == null -> "Every set conquered. Claim the victory."
            else -> buildString {
                append("Next: ${next.exerciseName} · set ${next.setIndex + 1}")
                // A HOLD set carries its figure in seconds; REPS sets count.
                val figure = if (next.durationSec != null && next.reps == 0)
                    "${next.durationSec}s" else "${next.reps} reps"
                append(" · $figure")
                next.weightKg?.takeIf { it > 0.0 }?.let { append(" · ${formatLoad(it)}") }
            }
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pending = intent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("MID WORKOUT · $label")
            .setContentText(body)
            .setSubText("$done / $total sets")
            .setProgress(total.coerceAtLeast(1), done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pending)
            .build()
    }

    private fun formatLoad(kg: Double): String =
        if (kg == kg.toLong().toDouble()) "${kg.toLong()}kg" else "${kg}kg"

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
