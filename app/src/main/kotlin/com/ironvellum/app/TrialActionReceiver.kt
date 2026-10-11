package com.ironvellum.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ironvellum.app.data.LiveTrialActions
import com.ironvellum.app.data.RestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The trial notification's own buttons: Set done, Skip rest and +15 s. A broadcast
 * action runs from the lock screen without unlocking, which an activity cannot.
 * Each does what the matching button on the trial screen does, through the same
 * code ([LiveTrialActions] for the tick, [RestClock] for the rest), and the service
 * watching the trial redraws the notification from the result. Not exported: only
 * this app's own PendingIntents reach it.
 */
class TrialActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (sessionId <= 0L) return
        when (intent.action) {
            ACTION_SKIP_REST -> RestClock.cancel(sessionId)
            ACTION_EXTEND_REST -> RestClock.extend(sessionId)
            ACTION_SET_DONE -> {
                val app = context.applicationContext as IronvellumApp
                val expected = intent.getLongExtra(EXTRA_SET_ID, -1L).takeIf { it > 0L }
                // The tick writes the database: keep the process alive until it has.
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        LiveTrialActions(app.repository, app, sessionId).tickNext(expected)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SET_DONE = "com.ironvellum.app.TRIAL_SET_DONE"
        const val ACTION_SKIP_REST = "com.ironvellum.app.TRIAL_SKIP_REST"
        const val ACTION_EXTEND_REST = "com.ironvellum.app.TRIAL_EXTEND_REST"
        private const val EXTRA_SESSION_ID = "sessionId"
        private const val EXTRA_SET_ID = "setId"

        /** An explicit intent for this receiver; [setId] names the set a "Set done" is for. */
        fun intent(context: Context, action: String, sessionId: Long, setId: Long = -1L): Intent =
            Intent(context, TrialActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_SET_ID, setId)
    }
}
