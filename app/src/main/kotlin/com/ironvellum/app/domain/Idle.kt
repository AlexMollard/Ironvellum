package com.ironvellum.app.domain

/**
 * Muster roll idle maths. Training sets the RATE; idle time only collects at
 * that rate. Nothing here touches XP, strength score, or the training board —
 * this is a parallel economy with its own leaderboard later.
 * Rate formula (per hour):
 *   perHour = FLOOR * trainingFactor * skillFactor * relicMultiplier
 *   trainingFactor = 1 + min(TRAINING_CAP, sessionsLast7d * SESSION_WEIGHT
 *                              + volumeLast7d * VOLUME_WEIGHT
 *                              + streakDays * STREAK_WEIGHT) / FLOOR
 *   skillFactor    = 1 + SKILL_CEILING * (1 - e^(-SKILL_RATE * skillsUnlocked))
 *
 * Balance intent: RECENT TRAINING is the engine — a committed week reaches x4
 * and dominates everything else. Skills are a permanent bonus that approaches
 * x2 asymptotically, so every unlock helps forever but the whole tree can
 * never out-earn a trained week. Time away is paid generously for a normal
 * rest week and then stops: absence must never climb the muster board.
 */
data class IdleState(
    val essence: Long,
    val figures: Int,
    val relicMultiplier: Double,
    val lastCollectedAtMs: Long,
)

data class IdleRate(val perHour: Double, val trainingFactor: Double, val skillFactor: Double)

object Idle {

    /** The roll works at FULL output for a whole day before it tires at all. */
    const val FULL_RATE_HOURS = 24.0

    /**
     * After the full day, output falls off linearly across this window down to
     * [MIN_EFFICIENCY], and then holds there until [MAX_EFFECTIVE_HOURS] of
     * work are banked.
     */
    const val TAPER_WINDOW_HOURS = 48.0

    /** Past the taper the roll labours on at a tenth of its rate, up to the cap. */
    const val MIN_EFFICIENCY = 0.10

    /**
     * The most one absence can ever pay: three full days of work. Idle
     * essence is what the muster board ranks on, and without a ceiling the
     * floor paid forever — a year away banked 919 effective hours, 38 times a
     * day, so NOT training climbed the board. A normal rest week (60 hours) is
     * untouched; the cap is reached only after 12 days away.
     */
    const val MAX_EFFECTIVE_HOURS = 72.0

    // Rate floor: with zero recent training the roll still scavenges a trickle.
    private const val FLOOR = 10.0

    // Recent training is the PRIMARY driver: a committed week reaches x4.
    private const val TRAINING_CAP = 30.0
    private const val SESSION_WEIGHT = 3.0     // per session in the last 7 days
    private const val VOLUME_WEIGHT = 0.05     // per rep logged in the last 7 days
    private const val STREAK_WEIGHT = 1.0      // per consecutive day, capped with TRAINING_CAP

    // Skills are a permanent BONUS, not the engine, and they approach x2
    // asymptotically rather than hitting a wall: at +4% flat with a x2 cap the
    // ceiling arrived at 25 unlocks and every skill after that was worthless.
    // This way the 90th unlock still adds something, just far less than the 2nd.
    private const val SKILL_CEILING = 1.0   // maximum ADDED on top of 1.0

    /**
     * Ceilings the UI needs to draw progress against. Exposed here so a screen
     * can never drift from the curve: the rate dial previously hardcoded its
     * own 4.0 and the factor bars showed each factor's SHARE of the combined
     * product, which drew a half-full bar for two untrained x1.00 factors.
     */
    val MAX_TRAINING_FACTOR = 1.0 + TRAINING_CAP / FLOOR
    val MAX_SKILL_FACTOR = 1.0 + SKILL_CEILING
    const val SKILL_RATE = 0.045    // approach speed per unlock

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

        // Asymptotic: 2 skills ~x1.09, 25 ~x1.67, 95 ~x1.99 — always rising,
        // never reaching x2, so no unlock is ever dead weight.
        val skillFactor = 1.0 + SKILL_CEILING * (1.0 - kotlin.math.exp(-SKILL_RATE * skills))

        val relic = when {
            !state.relicMultiplier.isFinite() -> 1.0
            else -> state.relicMultiplier.coerceIn(1.0, MAX_RELIC)
        }

        val perHour = (FLOOR * trainingFactor * skillFactor * relic).coerceIn(0.0, MAX_PER_HOUR)
        return IdleRate(perHour, trainingFactor, skillFactor)
    }

    /**
     * Essence banked for time away, in three phases and one ceiling:
     *
     *  - 0 .. 24h        full output, an untouched day costs you nothing
     *  - 24 .. 72h       output falls linearly from 100% to [MIN_EFFICIENCY]
     *  - beyond 72h      output holds at [MIN_EFFICIENCY]
     *  - at any length   never more than [MAX_EFFECTIVE_HOURS] of work
     *
     * Effective hours are the integral of that efficiency, computed piecewise
     * so every value can be checked by hand:
     *   24h  -> 24.0
     *   48h  -> 24 + 24 * 0.775  = 42.6
     *   72h  -> 24 + 48 * 0.55   = 50.4
     *   7d   -> 50.4 + 96 * 0.10 = 60.0
     *   12d+ -> 72.0 (capped)
     */
    fun accrued(state: IdleState, rate: IdleRate, nowMs: Long): Long {
        val amount = accruedExact(state, rate, nowMs)
        // Saturate rather than wrap: an absence measured in years still fits.
        return if (amount >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else amount.toLong()
    }

    /**
     * The same curve, undivided by truncation, so a live counter can climb
     * smoothly instead of stepping once a second. Banking always goes through
     * [accrued] — this is for display only.
     */
    fun accruedExact(state: IdleState, rate: IdleRate, nowMs: Long): Double {
        // No baseline yet: a freshly created idle_state row carries
        // lastCollectedAtMs = 0, which would read as a 56-year absence and
        // bank a full capped absence on a brand new account. No baseline
        // means nothing has been earned.
        if (state.lastCollectedAtMs <= 0L) return 0.0
        val elapsedMs = nowMs - state.lastCollectedAtMs
        // Clock moved backwards (manual change, timezone/DST shift): collect nothing.
        if (elapsedMs <= 0L) return 0.0
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
        }.coerceAtMost(MAX_EFFECTIVE_HOURS)
        val perHour = if (rate.perHour.isFinite()) rate.perHour.coerceAtLeast(0.0) else 0.0
        val amount = perHour * effectiveHours
        return if (amount.isFinite()) amount else 0.0
    }

    fun collect(state: IdleState, rate: IdleRate, nowMs: Long): IdleState {
        val gained = accrued(state, rate, nowMs)
        // Saturate instead of overflowing a near-MAX Long balance.
        val essence = state.essence.coerceAtLeast(0L)
        val newEssence = if (gained > Long.MAX_VALUE - essence) Long.MAX_VALUE else essence + gained
        return state.copy(essence = newEssence, lastCollectedAtMs = nowMs)
    }
}
