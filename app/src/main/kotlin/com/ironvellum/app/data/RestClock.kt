package com.ironvellum.app.data

import android.os.SystemClock
import com.ironvellum.app.domain.RestTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The one live rest between sets, shared by the trial screen (which starts,
 * extends and skips it) and the trial service (which counts it down in the
 * shade and buzzes when it ends). Process-wide, so it outlives the screen
 * going to the background; the trial's foreground service keeps the process.
 * Every call names its trial, so a stale screen can never touch another's rest.
 */
object RestClock {
    private val _timer = MutableStateFlow<RestTimer?>(null)
    val timer: StateFlow<RestTimer?> = _timer.asStateFlow()

    private fun now() = SystemClock.elapsedRealtime()

    fun start(sessionId: Long, seconds: Int) {
        _timer.value = RestTimer.start(sessionId, seconds, now())
    }

    fun extend(sessionId: Long) {
        _timer.update { t -> if (t?.sessionId == sessionId) t.extended(RestTimer.EXTEND_SECONDS, now()) else t }
    }

    /** Skip, or the trial ended: the rest goes, whatever is left of it. */
    fun cancel(sessionId: Long) {
        _timer.update { t -> if (t?.sessionId == sessionId) null else t }
    }

    /** Clears [ended] once it has been announced; a rest started since is left alone. */
    fun finish(ended: RestTimer) {
        _timer.update { t -> if (t == ended) null else t }
    }
}
