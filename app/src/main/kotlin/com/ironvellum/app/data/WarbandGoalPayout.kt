package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.domain.Xp
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields

/**
 * The weekly band-goal bonus: the first time in an ISO week that the band's
 * total crosses the owner's goal, every member who contributed at least one
 * workout is owed a small XP award on their own phone. Zero server writes —
 * the XP lands in the local profile exactly like the daily quest bonus, and
 * the once-per-band+week guard is a local flag so a re-render, re-read or
 * reinstall-adjacent re-fetch can never pay twice.
 */
object WarbandGoalPayout {

    /** Stated in the payout overlay; small on purpose, beside [Xp.QUEST_BONUS]. */
    const val BONUS_XP = Xp.BAND_GOAL_BONUS

    /**
     * The week bucket, keyed on the same ISO Monday the server counts the
     * band's week from (UTC anchor), so the client's "paid this week" flag
     * flips at the same rollover the roster's counts reset on.
     */
    fun isoWeekKey(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): String =
        "%04d-W%02d".format(
            today.get(WeekFields.ISO.weekBasedYear()),
            today.get(WeekFields.ISO.weekOfWeekBasedYear()),
        )

    /** One band's flag for one week lives under this key. */
    fun payoutKey(bandId: String, weekKey: String): String = "$bandId:$weekKey"

    /**
     * True when this lifter is owed the payout right now: the band total has
     * crossed the goal, this lifter contributed at least one workout toward
     * it, and this band+week has not been paid locally yet.
     */
    fun owes(
        total: Int,
        goal: Int,
        contributed: Int,
        alreadyPaid: Boolean,
    ): Boolean = !alreadyPaid && contributed >= 1 && total >= goal

    /**
     * Pure decision over a set of already-paid keys — the shape the repository
     * calls and the tests mutate. [bandId] and [weekKey] must compose through
     * [payoutKey]: a week key that failed to vary per week would collapse both
     * weeks onto one flag and the band would be paid once a year, which is
     * exactly the regression [WarbandGoalPayoutTest] exists to catch.
     */
    fun owes(
        bandId: String,
        weekKey: String,
        total: Int,
        goal: Int,
        contributed: Int,
        paidKeys: Set<String>,
    ): Boolean = owes(total, goal, contributed, payoutKey(bandId, weekKey) in paidKeys)
}

/** The persisted once-per-band+week flags, over SharedPreferences. */
class WarbandGoalPayoutStore(private val prefs: SharedPreferences) {

    fun hasPaid(bandId: String, weekKey: String): Boolean =
        prefs.getBoolean(WarbandGoalPayout.payoutKey(bandId, weekKey), false)

    fun markPaid(bandId: String, weekKey: String) {
        prefs.edit { putBoolean(WarbandGoalPayout.payoutKey(bandId, weekKey), true) }
    }

    companion object {
        private const val PREFS_FILE = "warband_payout"

        fun from(context: Context): WarbandGoalPayoutStore =
            WarbandGoalPayoutStore(context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE))
    }
}
