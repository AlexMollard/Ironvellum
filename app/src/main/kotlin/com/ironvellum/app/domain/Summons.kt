package com.ironvellum.app.domain

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

    data class Copy(val title: String, val text: String, val bigText: String)

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
        val exerciseText = if (exercises == 1) "1 exercise" else "$exercises exercises"
        val setText = if (sets == 1) "1 set" else "$sets sets"
        val days = pluralDays(oathDays)
        val oathLine = when {
            oathAtRisk -> "Your oath of $days ends tonight unless you seal a trial."
            oathDays == 0 -> "Today's Trial starts the oath."
            nextDeedDays != null -> "Oath · $days kept. Next deed at ${pluralDays(nextDeedDays)}."
            else -> "Oath · $days kept."
        }
        return Copy(
            // The rite keeps its own name and case (GLOSSARY: "THE SUMMONS · Heavy Pull").
            title = "THE SUMMONS · $riteName",
            text = if (oathAtRisk) "$exerciseText · $setText · your oath ends tonight." else "$exerciseText · $setText waiting.",
            bigText = "$riteName: $exerciseText, $setText today. $oathLine",
        )
    }

    private fun pluralDays(n: Int) = if (n == 1) "1 day" else "$n days"
}
