package com.ironvellum.app.ui.recap

import com.ironvellum.app.R
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WeeklyRecap
import java.util.Locale
import kotlin.math.abs

/**
 * What the Sunday notification says. Described as [UiText] so it resolves in the language and mode in force when it is
 * posted, and is checked in a plain unit test.
 *
 * [publicTitle] and [publicText] are the lock screen's version: the recap is ready and nothing else. A figure, a lift or a
 * streak is never on it.
 */
class RecapCopy(
    val title: UiText,
    val text: UiText,
    val expanded: UiText,
    val publicTitle: UiText,
    val publicText: UiText,
) {
    companion object {
        /** The copy for [recap], a week with workouts. */
        fun of(recap: WeeklyRecap, locale: Locale = Locale.getDefault()): RecapCopy {
            val stats = recap.stats
            val counts = buildList {
                add(RecapText.workouts(stats.workouts))
                if (stats.records.isNotEmpty()) add(RecapText.records(stats.records.size))
            }
            val volume = RecapText.volume(stats.volumeKg, locale) + " " + RecapText.unit()
            val lifted = when (val change = recap.volumeChangePercent) {
                null, 0 -> UiText.res(R.string.recap_notify_lifted_flat, volume)
                else -> UiText.res(if (change > 0) R.string.recap_notify_lifted_up else R.string.recap_notify_lifted_down, volume, abs(change))
            }
            val text = buildList {
                add(lifted)
                if (recap.streak.days > 0) add(UiText.themedPlural(R.plurals.recap_notify_streak, R.plurals.recap_notify_streak__t, recap.streak.days))
            }
            val expanded = buildList {
                add(UiText.Raw(RecapText.range(recap.weekStart, recap.weekEnd, locale)))
                add(UiText.res(R.string.recap_notify_big_totals, UiText.plural(R.plurals.common_sets_count, stats.sets), RecapText.durationLong(stats.minutes)))
                if (stats.records.isNotEmpty()) {
                    add(UiText.res(R.string.recap_notify_big_records, UiText.Raw(stats.records.joinToString(", ") { it.label })))
                }
                recap.next?.let { add(UiText.res(R.string.recap_notify_big_next, UiText.Raw(it.firstName), UiText.Raw(RecapText.weekday(it.firstDay, locale)))) }
            }
            return RecapCopy(
                title = UiText.res(R.string.recap_notify_title, UiText.join(counts, ", ")),
                text = UiText.join(text, " "),
                expanded = UiText.join(expanded, "\n"),
                publicTitle = UiText.res(R.string.recap_notify_public_title),
                publicText = UiText.res(R.string.recap_notify_public_text),
            )
        }
    }
}
