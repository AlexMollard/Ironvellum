package com.monarch.app.domain

/**
 * Shadow army idle maths. Training sets the RATE; idle time only collects at
 * that rate. Nothing here touches XP, strength score, or the training board —
 * this is a parallel economy with its own leaderboard later.
 *
 * Rate formula (per hour):
 *   perHour = FLOOR * trainingFactor * skillFactor * relicMultiplier
 *   trainingFactor = 1 + min(TRAINING_CAP, sessionsLast7d * SESSION_WEIGHT
 *                              + volumeLast7d * VOLUME_WEIGHT
 *                              + streakDays * STREAK_WEIGHT) / FLOOR
 *   skillFactor    = 1 + SKILL_STEP * skillsUnlocked (capped)
 *
 * Balance intent: skills are PERMANENT and pull the whole curve up forever,
 * while recent training saturates at TRAINING_CAP (a fixed ceiling on the
 * weekly component). Each unlocked skill adds half the floor again, so ten
 * skills triple the rate and beat the entire weekly training ceiling — after
 * ~a dozen unlocks the skill tree, not this week's sessions, dominates.
 * Recent training still matters early and for players who ignore the tree,
 * and decay to the floor punishes stopping.
 */
data class IdleState(
    val essence: Long,
    val shadows: Int,
    val relicMultiplier: Double,
    val lastCollectedAtMs: Long,
)

data class IdleRate(val perHour: Double, val trainingFactor: Double, val skillFactor: Double)

object Idle {

    /**
     * Hard stop: nothing accrues past a full day away. A real hunter opens this
     * app once a day, maybe twice — a 12h ceiling punished the normal case.
     */
    const val OFFLINE_CAP_HOURS = 24

    /** The first stretch away pays the full rate. */
    const val FULL_RATE_HOURS = 6.0

    /**
     * Past FULL_RATE_HOURS the MARGINAL rate decays with this time constant, so
     * a day away still pays well (~13h of essence) while two days pays the same:
     * checking in daily is rewarded, leaving the phone in a drawer is not.
     */
    private const val TAPER_HOURS = 8.0

    // Rate floor: with zero recent training the army still scavenges a trickle.
    private const val FLOOR = 10.0

    // Weekly training component saturates here so grinding one week can never
    // out-run the permanent skill multipliers.
    private const val TRAINING_CAP = 30.0
    private const val SESSION_WEIGHT = 3.0     // per session in the last 7 days
    private const val VOLUME_WEIGHT = 0.05     // per rep logged in the last 7 days
    private const val STREAK_WEIGHT = 1.0      // per consecutive day, capped with TRAINING_CAP

    // Permanent per-skill multiplier step (+50% of floor each), capped so
    // extreme inputs stay finite.
    private const val SKILL_STEP = 0.5
    private const val MAX_SKILL_FACTOR = 50.0

    // Guards so a corrupt relic multiplier can't push the rate to Infinity.
    private const val MAX_RELIC = 1e6
    private const val MAX_PER_HOUR = 1e15

    fun rate(
        state: IdleState,
        sessionsLast7d: Int,
        volumeLast7d: Double,
        skillsUnlocked: Int,
        streakDays: Int,
    ): IdleRate {
        val sessions = sessionsLast7d.coerceAtLeast(0)
        val volume = if (volumeLast7d.isFinite()) volumeLast7d.coerceAtLeast(0.0) else 0.0
        val streak = streakDays.coerceAtLeast(0)
        val skills = skillsUnlocked.coerceAtLeast(0)

        val trainingRaw = sessions * SESSION_WEIGHT + volume * VOLUME_WEIGHT + streak * STREAK_WEIGHT
        val trainingFactor = 1.0 + trainingRaw.coerceIn(0.0, TRAINING_CAP) / FLOOR

        val skillFactor = (1.0 + skills * SKILL_STEP).coerceAtMost(MAX_SKILL_FACTOR)

        val relic = when {
            !state.relicMultiplier.isFinite() -> 1.0
            else -> state.relicMultiplier.coerceIn(1.0, MAX_RELIC)
        }

        val perHour = (FLOOR * trainingFactor * skillFactor * relic).coerceIn(0.0, MAX_PER_HOUR)
        return IdleRate(perHour, trainingFactor, skillFactor)
    }

    /**
     * Essence banked for time away. The curve is full rate for the first
     * [FULL_RATE_HOURS], then the marginal rate decays exponentially, and the
     * clock stops entirely at [OFFLINE_CAP_HOURS].
     *
     * Integrating the decay gives effective hours:
     *   full + TAPER * (1 - e^-(t - full)/TAPER)
     * so 6h away pays 6h, 24h away pays ~13.2h, and 3 days away pays the same
     * ~13.2h as one — daily check-ins win, a drawer does not.
     */
    fun accrued(state: IdleState, rate: IdleRate, nowMs: Long): Long {
        val elapsedMs = nowMs - state.lastCollectedAtMs
        // Clock moved backwards (manual change, timezone/DST shift): collect nothing.
        if (elapsedMs <= 0L) return 0L
        val hours = minOf(elapsedMs, OFFLINE_CAP_HOURS * 3_600_000L).toDouble() / 3_600_000.0
        val effectiveHours = if (hours <= FULL_RATE_HOURS) {
            hours
        } else {
            FULL_RATE_HOURS + TAPER_HOURS * (1.0 - kotlin.math.exp(-(hours - FULL_RATE_HOURS) / TAPER_HOURS))
        }
        val perHour = if (rate.perHour.isFinite()) rate.perHour.coerceAtLeast(0.0) else 0.0
        val amount = perHour * effectiveHours
        if (!amount.isFinite()) return 0L
        return amount.toLong()
    }

    fun collect(state: IdleState, rate: IdleRate, nowMs: Long): IdleState {
        val gained = accrued(state, rate, nowMs)
        // Saturate instead of overflowing a near-MAX Long balance.
        val essence = state.essence.coerceAtLeast(0L)
        val newEssence = if (gained > Long.MAX_VALUE - essence) Long.MAX_VALUE else essence + gained
        return state.copy(essence = newEssence, lastCollectedAtMs = nowMs)
    }
}
