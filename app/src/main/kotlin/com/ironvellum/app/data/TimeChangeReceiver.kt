package com.ironvellum.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-books the Summons when the wall clock moves under it. WorkManager keeps
 * a delay in elapsed time and listens for neither broadcast, so after a
 * flight or a manual clock change the queued run would land at the old
 * 19:30. Both broadcasts are exempt from the implicit-broadcast limits, so a
 * manifest receiver costs nothing until the clock actually changes.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> Reminders.reschedule(context)
        }
    }
}
