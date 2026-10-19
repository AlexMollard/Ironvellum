package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Weekday names in the app's language, from the platform's own calendar data. ISO days: 1 is Monday, 7 is Sunday. */
object DayNames {

    /** "Monday"; null for a number that is not a weekday. */
    fun full(day: Int, locale: Locale = Locale.getDefault()): String? = name(day, TextStyle.FULL, locale)

    /** "Mon"; null for a number that is not a weekday. */
    fun short(day: Int, locale: Locale = Locale.getDefault()): String? = name(day, TextStyle.SHORT, locale)

    private fun name(day: Int, style: TextStyle, locale: Locale): String? =
        if (day in 1..7) DayOfWeek.of(day).getDisplayName(style, locale) else null
}
