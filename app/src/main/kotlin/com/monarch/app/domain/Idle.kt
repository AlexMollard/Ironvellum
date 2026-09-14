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

    /** The army works at FULL output for a whole day before it tires at all. */
    const val FULL_RATE_HOURS = 24.0

    /**
     * After the full day, output falls off linearly across this window down to
     * [MIN_EFFICIENCY] — and then holds there forever. The shadows never stop
     * working; they just work badly while you are gone.
     */
    const val TAPER_WINDOW_HOURS = 48.0

    /** Output never drops below a tenth of the army's rate, however long you are away. */
    const val MIN_EFFICIENCY = 0.10

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
     * Essence banked for time away. Three phases, and the army NEVER stops:
     *
     *  - 0 .. 24h        full output, an untouched day costs you nothing
     *  - 24 .. 72h       output falls linearly from 100% to [MIN_EFFICIENCY]
     *  - beyond 72h      output holds at [MIN_EFFICIENCY] forever
     *
     * Effective hours are the integral of that efficiency, computed piecewise
     * so every value can be checked by hand:
     *   24h  -> 24.0
     *   48h  -> 24 + 24 * 0.775  = 42.6
     *   72h  -> 24 + 48 * 0.55   = 50.4
     *   7d   -> 50.4 + 96 * 0.10 = 60.0
     */
    fun accrued(state: IdleState, rate: IdleRate, nowMs: Long): Long {
        val elapsedMs = nowMs - state.lastCollectedAtMs
        // Clock moved backwards (manual change, timezone/DST shift): collect nothing.
        if (elapsedMs <= 0L) return 0L
        val hours = elapsedMs.toDouble() / 3_600_000.0
        val taperEnd = FULL_RATE_HOURS + TAPER_WINDOW_HOURS
        val effectiveHours = when {
            hours <= FULL_RATE_HOURS -> hours
            hours <= taperEnd -> {
                // Average of the start and end efficiency over the elapsed slice
                // of the ramp — the area of a trapezium.
                val into = hours - FULL_RATE_HOURS
                val endEfficiency = 1.0 - (1.0 - MIN_EFFICIENCY) * (into / TAPER_WINDOW_HOURS)
                FULL_RATE_HOURS + into * (1.0 + endEfficiency) / 2.0
            }
            else -> {
                val rampArea = TAPER_WINDOW_HOURS * (1.0 + MIN_EFFICIENCY) / 2.0
                FULL_RATE_HOURS + rampArea + (hours - taperEnd) * MIN_EFFICIENCY
            }
        }
        val perHour = if (rate.perHour.isFinite()) rate.perHour.coerceAtLeast(0.0) else 0.0
        val amount = perHour * effectiveHours
        if (!amount.isFinite()) return 0L
        // Saturate rather than wrap: an absence measured in years still fits.
        return if (amount >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else amount.toLong()
    }

    fun collect(state: IdleState, rate: IdleRate, nowMs: Long): IdleState {
        val gained = accrued(state, rate, nowMs)
        // Saturate instead of overflowing a near-MAX Long balance.
        val essence = state.essence.coerceAtLeast(0L)
        val newEssence = if (gained > Long.MAX_VALUE - essence) Long.MAX_VALUE else essence + gained
        return state.copy(essence = newEssence, lastCollectedAtMs = nowMs)
    }
}
