package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.domain.Xp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoField
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * The weekly circle-goal bonus: a small XP award on the lifter's own phone,
 * landing in the local profile exactly like the daily quest bonus.
 *
 * The once-a-week guard is keyed on the LIFTER and the week, never on the
 * circle: a lifter who leaves and re-forms a circle, or joins one that has
 * already met its goal, is still the same lifter in the same week and is
 * owed at most one bonus.
 */
object CirclePayout {

    /** Stated in the payout overlay; small on purpose, beside [Xp.QUEST_BONUS]. */
    const val BONUS_XP = Xp.BAND_GOAL_BONUS

    /**
     * The week bucket: the UTC Monday that opens [today]'s week, as an ISO
     * date. It is the same Monday the server counts the circle's week from
     * (UTC anchor), so the client's "paid this week" flag flips at the same
     * rollover the roster's counts reset on, and it names the week
     * unambiguously (an ISO week number repeats every year).
     */
    fun weekKey(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): String =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    /** One lifter's flag for one week lives under this key. */
    fun payoutKey(userId: String, weekKey: String): String = "$userId:$weekKey"

    /**
     * True when this lifter is owed the payout right now: the circle total
     * has crossed the goal, this lifter contributed at least one workout
     * toward it, and this lifter+week has not been paid yet.
     */
    fun owes(
        total: Int,
        goal: Int,
        contributed: Int,
        alreadyPaid: Boolean,
    ): Boolean = !alreadyPaid && contributed >= 1 && total >= goal

    /**
     * Pure decision over a set of already-paid keys. [userId] and [weekKey]
     * must compose through [payoutKey]: a week key that failed to vary per
     * week would collapse every week onto one flag and the lifter would be
     * paid once a year, which is exactly the regression [CirclePayoutTest]
     * exists to catch.
     */
    fun owes(
        userId: String,
        weekKey: String,
        total: Int,
        goal: Int,
        contributed: Int,
        paidKeys: Set<String>,
    ): Boolean = owes(total, goal, contributed, payoutKey(userId, weekKey) in paidKeys)

    private val LEGACY_KEY = Regex("""^.+:(\d{4})-W(\d{2})$""")

    /**
     * The week key of a flag written by the band-keyed store this one replaced
     * (`<bandId>:<iso year>-W<iso week>`), or null for anything else.
     */
    fun legacyWeekKey(oldKey: String): String? {
        val match = LEGACY_KEY.matchEntire(oldKey) ?: return null
        val (year, week) = match.destructured
        return runCatching {
            LocalDate.of(year.toInt(), 1, 4)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
                .with(ChronoField.DAY_OF_WEEK, 1)
                .toString()
        }.getOrNull()
    }
}

/**
 * The persisted once-per-lifter+week flags, over SharedPreferences. Writes are
 * synchronous: the flag has to be on disk before the XP it guards is added,
 * or a process death between the two could pay twice.
 */
class CirclePayoutStore(
    private val prefs: SharedPreferences,
    private val legacy: SharedPreferences? = null,
) {

    fun hasPaid(userId: String, weekKey: String): Boolean =
        prefs.getBoolean(CirclePayout.payoutKey(userId, weekKey), false)

    fun markPaid(userId: String, weekKey: String) {
        prefs.edit(commit = true) { putBoolean(CirclePayout.payoutKey(userId, weekKey), true) }
    }

    /** Takes a flag back when the XP it guarded could not be added. */
    fun unmark(userId: String, weekKey: String) {
        prefs.edit(commit = true) { remove(CirclePayout.payoutKey(userId, weekKey)) }
    }

    /**
     * Carries the flags of the band-keyed store over to [userId], once. An
     * update mid-week must not pay a lifter again for a week the old build
     * already paid. The old file is emptied so this is a no-op afterwards.
     */
    fun adoptLegacy(userId: String) {
        val old = legacy ?: return
        val keys = old.all.keys.toList()
        if (keys.isEmpty()) return
        prefs.edit(commit = true) {
            for (key in keys) {
                CirclePayout.legacyWeekKey(key)?.let { putBoolean(CirclePayout.payoutKey(userId, it), true) }
            }
        }
        old.edit(commit = true) { clear() }
    }

    companion object {
        private const val PREFS_FILE = "circle_payout"

        fun from(context: Context): CirclePayoutStore =
            CirclePayoutStore(
                context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
                context.getSharedPreferences("warband_payout", Context.MODE_PRIVATE),
            )
    }
}
