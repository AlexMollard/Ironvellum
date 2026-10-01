package com.ironvellum.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ironvellum.app.domain.Xp
import java.time.LocalDate
import java.time.temporal.ChronoField
import java.time.temporal.IsoFields

/**
 * The weekly circle-goal bonus: a small XP award on the lifter's own phone,
 * landing in the local profile exactly like the daily quest bonus.
 *
 * What is owed is the SERVER's decision, read from its settled record of the
 * week (`circle_bonuses()`): the server counts the days, freezes the goal and
 * the roster when the week opens, and settles the week once, the same for every
 * member. This side only turns that record into XP, once.
 *
 * The once-a-week guard is keyed on the LIFTER and the week, never on the
 * circle: a lifter who leaves and re-forms a circle, or joins one that has
 * already met its goal, is still the same lifter in the same week and is
 * owed at most one bonus.
 */
object CirclePayout {

    /** Stated in the payout overlay; small on purpose, beside [Xp.QUEST_BONUS]. */
    const val BONUS_XP = Xp.CIRCLE_GOAL_BONUS

    /** One lifter's flag for one week lives under this key; [week] is the server's week (a UTC Monday, ISO date). */
    fun payoutKey(userId: String, week: String): String = "$userId:$week"

    /**
     * The weeks in [bonuses] this lifter has not been paid for, each once and
     * oldest first. A week with no days of the lifter's own is never owed: the
     * server only lists weeks the lifter trained in, and this is the same rule
     * stated where the money moves. [isPaid] answers for one week.
     */
    fun owedWeeks(bonuses: List<CircleBonusWeek>, isPaid: (week: String) -> Boolean): List<String> =
        bonuses.filter { it.days >= 1 }
            .map { it.week }
            .distinct()
            .sorted()
            .filterNot(isPaid)

    private val LEGACY_KEY = Regex("""^.+:(\d{4})-W(\d{2})$""")

    /**
     * The week of a flag written by the band-keyed store this one replaced
     * (`<bandId>:<iso year>-W<iso week>`) as the server writes weeks (the UTC
     * Monday), or null for anything else.
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

/** One settled week the server says the lifter is owed the bonus for. */
data class CircleBonusWeek(val week: String, val days: Int, val circleName: String?)

/** What a payout paid: the XP and the circle to name in the overlay. */
data class CircleBonusPaid(val xp: Int, val circleName: String?)

/**
 * The persisted once-per-lifter+week flags, over SharedPreferences. Writes are
 * synchronous: the flag has to be on disk before the XP it guards is added,
 * or a process death between the two could pay twice.
 */
class CirclePayoutStore(
    private val prefs: SharedPreferences,
    private val legacy: SharedPreferences? = null,
) {

    fun hasPaid(userId: String, week: String): Boolean =
        prefs.getBoolean(CirclePayout.payoutKey(userId, week), false)

    fun markPaid(userId: String, week: String) {
        prefs.edit(commit = true) { putBoolean(CirclePayout.payoutKey(userId, week), true) }
    }

    /** Takes a flag back when the XP it guarded could not be added. */
    fun unmark(userId: String, week: String) {
        prefs.edit(commit = true) { remove(CirclePayout.payoutKey(userId, week)) }
    }

    /**
     * Whether this lifter has ever been seen in a circle on this phone. Only a
     * hint: a lifter who has never joined one needs no bonus lookup, while one
     * who has joined and since left may still be owed the week they left in.
     */
    fun wasInCircle(userId: String): Boolean = prefs.getBoolean("seen:$userId", false)

    fun markInCircle(userId: String) {
        if (!wasInCircle(userId)) prefs.edit(commit = true) { putBoolean("seen:$userId", true) }
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
