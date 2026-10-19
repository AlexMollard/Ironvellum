package com.ironvellum.app.domain

import com.ironvellum.app.R
import com.ironvellum.app.data.Words

/** The one place a count of seconds becomes "1:05" or "1:04:07", so the rest bar, the hold clocks and the session timer agree. */
object ClockText {

    /** "1:05": minutes without limit (a 75 minute figure reads 75:00), seconds to two digits. */
    fun minutesSeconds(seconds: Long): String =
        Words.text(UiText.res(R.string.clock_minutes_seconds, seconds / 60, seconds % 60))

    /** "4:07" under an hour, "1:04:07" past it. */
    fun elapsed(seconds: Long): String {
        val h = seconds / 3600
        return if (h > 0) Words.text(UiText.res(R.string.clock_hours_minutes_seconds, h, (seconds % 3600) / 60, seconds % 60))
        else Words.text(UiText.res(R.string.clock_minutes_seconds, seconds / 60, seconds % 60))
    }
}
