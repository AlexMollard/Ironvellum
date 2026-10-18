package com.ironvellum.app.domain

import com.ironvellum.app.R
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The pure half of the Summons: when it fires next, whether a run is still in
 * its evening, and what it says. The Android half (WorkManager, the post)
 * lives in data/Reminders.kt.
 */
object Summons {

    /** The hour the Summons fires. One number, one place to change it. */
    val REMIND_AT: LocalTime = LocalTime.of(19, 30)

    /**
     * How far ahead the next fire must be. A run WorkManager starts a few
     * seconds early would otherwise find tonight's 19:30 still ahead and
     * schedule itself again moments later: two Summons in one evening.
     */
    val MIN_LEAD: Duration = Duration.ofMinutes(5)

    /**
     * The next local [at] strictly later than [now] plus [minLead]. Built
     * from the wall-clock date in [now]'s zone, so a DST change or a new
     * timezone moves the instant and never the hour on the clock.
     */
    fun nextFireAt(now: ZonedDateTime, at: LocalTime = REMIND_AT, minLead: Duration = Duration.ZERO): ZonedDateTime {
        val earliest = now.plus(minLead)
        val tonight = ZonedDateTime.of(earliest.toLocalDate(), at, now.zone)
        return if (tonight.isAfter(earliest)) tonight else ZonedDateTime.of(earliest.toLocalDate().plusDays(1), at, now.zone)
    }

    /** The delay WorkManager is given: real elapsed time, so 23 or 25 hours across a DST change. */
    fun delayUntilNext(now: ZonedDateTime, at: LocalTime = REMIND_AT, minLead: Duration = Duration.ZERO): Duration =
        Duration.between(now.toInstant(), nextFireAt(now, at, minLead).toInstant())

    /**
     * True when a run at [now] still belongs to this evening's Summons. A run
     * held back by Doze or a powered-off phone may land late at night and
     * still say something true about today; one that only lands the next
     * morning would summon to a day that has barely begun.
     */
    fun isSummonsHour(now: LocalTime, at: LocalTime = REMIND_AT): Boolean = !now.isBefore(at.minus(MIN_LEAD))

    /**
     * Whether tonight's Summons should be posted at all: a rite is scheduled
     * today, no trial is sealed today, none is already under way, and the
     * notification can actually arrive.
     */
    fun wanted(riteScheduledToday: Boolean, sealedToday: Boolean, liveToday: Boolean, canPost: Boolean): Boolean =
        canPost && riteScheduledToday && !sealedToday && !liveToday

    /** The three texts of the notification, described so they follow the language and the Themed names switch when posted. */
    data class Copy(val title: UiText, val text: UiText, val bigText: UiText)

    /**
     * The words for one scheduled rite. [oathDays] is the oath as it stands,
     * [nextDeedDays] the next oath deed above it, and [oathAtRisk] true only
     * on the last day the oath survives without a trial (see [Streak.atRisk]):
     * the one evening the Summons says so, and never in a notification of its own.
     */
    fun copy(
        riteName: String,
        exercises: Int,
        sets: Int,
        oathDays: Int,
        nextDeedDays: Int?,
        oathAtRisk: Boolean,
    ): Copy {
        val exerciseText = UiText.plural(R.plurals.common_exercises_count, exercises)
        val setText = UiText.plural(R.plurals.common_sets_count, sets)
        val days = UiText.plural(R.plurals.common_days_count, oathDays)
        val oathLine = when {
            oathAtRisk -> UiText.themed(
                R.string.summons_notification_streak_at_risk, R.string.summons_notification_streak_at_risk__t, days,
            )
            oathDays == 0 -> UiText.themed(R.string.summons_notification_streak_zero, R.string.summons_notification_streak_zero__t)
            nextDeedDays != null -> UiText.themed(
                R.string.summons_notification_streak_next, R.string.summons_notification_streak_next__t,
                days, UiText.plural(R.plurals.common_days_count, nextDeedDays),
            )
            else -> UiText.themed(R.string.summons_notification_streak_kept, R.string.summons_notification_streak_kept__t, days)
        }
        return Copy(
            // The rite keeps its own name and case (GLOSSARY: "THE SUMMONS · Heavy Pull").
            title = UiText.themed(R.string.summons_notification_title, R.string.summons_notification_title__t, riteName),
            text = if (oathAtRisk) {
                UiText.themed(
                    R.string.summons_notification_text_at_risk, R.string.summons_notification_text_at_risk__t,
                    exerciseText, setText,
                )
            } else {
                UiText.res(R.string.summons_notification_text, exerciseText, setText)
            },
            bigText = UiText.res(R.string.summons_notification_big, riteName, exerciseText, setText, oathLine),
        )
    }
}
