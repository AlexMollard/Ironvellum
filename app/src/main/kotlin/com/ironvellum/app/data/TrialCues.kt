package com.ironvellum.app.data

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.ironvellum.app.domain.RestAlert
import com.ironvellum.app.domain.SetClock

/**
 * The in-app cues for the last seconds of a timed set and its end: a short beep and
 * a short buzz, as the alert mode ([RestAlert]) allows. Sound is the phone's own
 * (a system tone for the ticks, the default notification sound at zero), never a
 * bundled file, and every call is allowed to fail quietly: a cue is a courtesy.
 * What each mode plays is [RestAlert.cuePlan], tested without a phone.
 */
object TrialCues {

    fun play(context: Context, alert: RestAlert, cue: SetClock.Cue) {
        val plan = alert.cuePlan(cue)
        if (plan.buzzMs > 0) buzz(context, plan.buzzMs)
        if (plan.toneMs > 0) {
            if (cue == SetClock.Cue.DONE) notificationSound(context) else beep(plan.toneMs)
        }
    }

    private fun buzz(context: Context, ms: Long) = runCatching {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return@runCatching
        if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun beep(ms: Int) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, ms)
            Handler(Looper.getMainLooper()).postDelayed({ generator.release() }, ms + RELEASE_SLACK_MS)
        }
    }

    private fun notificationSound(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        }
    }

    private const val TONE_VOLUME = 80
    private const val RELEASE_SLACK_MS = 150L
}
