package com.ironvellum.app.domain

/**
 * Muster roll idle maths. Training sets the RATE; idle time only collects at
 * that rate. Nothing here touches XP, strength score, or the training board —
 * this is a parallel economy with its own leaderboard later.
 * Rate formula (per hour):
 *   perHour = FLOOR * trainingFactor * skillFactor * relicMultiplier * echoFactor
 *   trainingFactor = 1 + min(TRAINING_CAP, sessionsLast7d * SESSION_WEIGHT
 *                              + volumeLast7d * VOLUME_WEIGHT
 *                              + streakDays * STREAK_WEIGHT) / FLOOR
 *   skillFactor    = 1 + SKILL_CEILING * (1 - e^(-SKILL_RATE * skillsUnlocked))
 *   echoFactor     = 1 + min(ECHO_BONUS_CAP, echoes / ECHO_BONUS_PER * ECHO_BONUS_STEP)
 *
 * relicMultiplier is the whole vault stacked by [Relics.effectiveMultiplier]: every relic lifts the
 * WHOLE rate, whatever its house. A house adds only its SET bonuses ([HouseEffects]): with 2 relics
 * +5% on the house's term (Iron the lifting part of trainingFactor, that is volume and trials sealed;
 * Vigil its consecutive days; Craft the technique part of skillFactor; Return the time paid for an
 * absence beyond the full-strength day), and with 4 relics one window moves. The +5% is applied AFTER
 * TRAINING_CAP, so a lifter who is already at the cap still gains from it.
 *
 * The worn crest ([CrestEffects], carried inside [HouseEffects]) changes a weight, a ceiling, a floor or a
 * factor in this same formula and in [accrued]; with none worn every term is the one written above.
 *
 * Balance intent: RECENT TRAINING is the engine — a committed week reaches x4
 * and dominates everything else. Skills are a permanent bonus that approaches
 * x2 asymptotically, so every unlock helps forever but the whole tree can
 * never out-earn a trained week. Time away is paid generously for a normal
 * rest week and then stops: absence must never climb the muster board.
 */
data class IdleState(
    val essence: Long,
    /** Echoes held. They lift the rate a little ([Idle.echoFactor]) and nothing else. */
    val figures: Int,
    val relicMultiplier: Double,
    val lastCollectedAtMs: Long,
    /**
     * Essence ever earned, never spent. [essence] drops when it buys an
     * inscription; this is what the board ranks and the server keeps greatest of.
     */
    val lifetimeEssence: Long = 0L,
)

/** [IdleState.relicMultiplier] is a factor of [perHour]: the "relic ×" figure shown beside it is what it was multiplied by. */
data class IdleRate(
    val perHour: Double,
    val trainingFactor: Double,
    val skillFactor: Double,
    /** What the relic houses changed in this rate. [HouseEffects.NONE] before any relic. */
    val effects: HouseEffects = HouseEffects.NONE,
    /** What the echoes held lift the rate by: 1.0 with none, at most 1 + [Idle.ECHO_BONUS_CAP]. */
    val echoFactor: Double = 1.0,
)

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
    const val FLOOR = 10.0

    // Recent training is the PRIMARY driver: a committed week reaches x4.
    private const val TRAINING_CAP = 30.0
    private const val SESSION_WEIGHT = 3.0     // per session in the last 7 days
    private const val VOLUME_WEIGHT = 0.05     // per rep logged in the last 7 days
    private const val STREAK_WEIGHT = 1.0      // per consecutive day, capped with TRAINING_CAP

    // Skills are a permanent BONUS, not the engine, and they approach x2
    // asymptotically rather than hitting a wall: at +4% flat with a x2 cap the
    // ceiling arrived at 25 unlocks and every skill after that was worthless.
    // This way the 90th unlock still adds something, just far less than the 2nd.
    const val SKILL_CEILING = 1.0   // maximum ADDED on top of 1.0

    /**
     * Ceilings the UI needs to draw progress against. Exposed here so a screen
     * can never drift from the curve: the rate dial previously hardcoded its
     * own 4.0 and the factor bars showed each factor's SHARE of the combined
     * product, which drew a half-full bar for two untrained x1.00 factors.
     */
    val MAX_TRAINING_FACTOR = 1.0 + TRAINING_CAP / FLOOR
    val MAX_SKILL_FACTOR = 1.0 + SKILL_CEILING
    const val SKILL_RATE = 0.045    // approach speed per unlock

    /** Every [ECHO_BONUS_PER] echoes held lift the rate by [ECHO_BONUS_STEP], counted continuously: 681 echoes is +6.81%. */
    const val ECHO_BONUS_PER = 100.0
    const val ECHO_BONUS_STEP = 0.01

    /** The most the echoes can ever add (+25%), reached at 2,500 echoes: a tally is a trim on the rate, not an engine. */
    const val ECHO_BONUS_CAP = 0.25
    val MAX_ECHO_FACTOR = 1.0 + ECHO_BONUS_CAP

    /** The factor [echoes] held put on the rate. Negative counts read as none. */
    fun echoFactor(echoes: Int): Double = echoFactor(echoes, CrestEffects.NONE)

    /** [echoFactor] under a worn crest: Silver lifts the step and the ceiling. */
    fun echoFactor(echoes: Int, crest: CrestEffects): Double =
        1.0 + (echoes.coerceAtLeast(0) / ECHO_BONUS_PER * crest.echoStep).coerceAtMost(crest.echoCap)

    // Guards so a corrupt relic multiplier can't push the rate to Infinity.
    private const val MAX_RELIC = 1e6
    private const val MAX_PER_HOUR = 1e15

    fun rate(
        state: IdleState,
        sessionsLast7d: Int,
        volumeLast7d: Double,
        skillsUnlocked: Int,
        streakDays: Int,
        houses: HouseEffects = HouseEffects.NONE,
    ): IdleRate {
        val sessions = sessionsLast7d.coerceAtLeast(0)
        val volume = if (volumeLast7d.isFinite()) volumeLast7d.coerceAtLeast(0.0) else 0.0
        val streak = streakDays.coerceAtLeast(0)
        val skills = skillsUnlocked.coerceAtLeast(0)

        // Two houses share the training term. Iron owns the lifting part: the volume AND the trials
        // sealed, so a lifter who logs only bodyweight sets (no load, no volume) still earns it.
        // Vigil owns the consecutive days. The cap is applied to the whole first, each part keeps
        // its share of what the cap lets through, and only then does a house's 2-relic bonus lift
        // its part: a set bonus can lift a lifter who is already at the cap.
        // The worn crest moves a weight or the ceiling as a TERM here, never the constants: MAX_TRAINING_FACTOR stays.
        val crest = houses.crest
        val ironRaw = volume * (VOLUME_WEIGHT + crest.volumeWeightBonus) + sessions * (SESSION_WEIGHT + crest.sessionWeightBonus)
        val steadyRaw = streak * (STREAK_WEIGHT + crest.streakWeightBonus)
        val trainingRaw = ironRaw + steadyRaw
        val cap = TRAINING_CAP + crest.trainingCapBonus
        val keep = if (trainingRaw > cap) cap / trainingRaw else 1.0
        val liftedRaw = (ironRaw * houses.term(RelicHouse.Iron) + steadyRaw * houses.term(RelicHouse.Vigil)) * keep
        val trainingFactor = 1.0 + liftedRaw / FLOOR

        // Asymptotic: 2 skills ~x1.09, 25 ~x1.67, 95 ~x1.99 — always rising,
        // never reaching x2, so no unlock is ever dead weight.
        // Craft lifts this term; a full Craft house also raises its ceiling from x2.0 to x2.1.
        val skillFactor = 1.0 + houses.skillCeiling * (1.0 - kotlin.math.exp(-SKILL_RATE * crest.skillRateScale * skills)) *
            houses.term(RelicHouse.Craft)

        val relic = when {
            !state.relicMultiplier.isFinite() -> 1.0
            else -> state.relicMultiplier.coerceIn(1.0, MAX_RELIC)
        }

        val echo = echoFactor(state.figures, crest)

        // The crest's flat trickle joins the base before every multiplier; its factors multiply the whole.
        val oath = if (streak >= CrestEffects.OATH_DAYS) crest.oathFactor else 1.0
        val perHour = ((FLOOR * trainingFactor + crest.flatPerHour) * skillFactor * relic * echo *
            crest.rateFactor * crest.relicFactor * oath).coerceIn(0.0, MAX_PER_HOUR)
        return IdleRate(perHour, trainingFactor, skillFactor, houses, echo)
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
        val houses = rate.effects
        val fullHours = houses.fullStrengthHours
        val floor = houses.minEfficiency
        val window = houses.taperWindowHours
        val taperEnd = fullHours + window
        val workedHours = when {
            hours <= fullHours -> hours
            hours <= taperEnd -> {
                // Average of the start and end efficiency over the elapsed slice
                // of the ramp — the area of a trapezium.
                val into = hours - fullHours
                val endEfficiency = efficiencyAtHours(hours, houses)
                fullHours + into * (1.0 + endEfficiency) / 2.0
            }
            else -> {
                val rampArea = window * (1.0 + floor) / 2.0
                fullHours + rampArea + (hours - taperEnd) * floor
            }
        }
        // Return lifts only the time paid beyond the full-strength day: it pays for coming back.
        val effectiveHours = (
            if (workedHours <= fullHours) workedHours else fullHours + (workedHours - fullHours) * houses.term(RelicHouse.Return)
            ).coerceAtMost(houses.maxEffectiveHours) * houses.crest.awayPay
        val perHour = if (rate.perHour.isFinite()) rate.perHour.coerceAtLeast(0.0) else 0.0
        val amount = perHour * effectiveHours
        return if (amount.isFinite()) amount else 0.0
    }

    /**
     * Share of full output after [hours] away: 1.0 through the full-strength day, then a straight
     * fall to [MIN_EFFICIENCY] across the taper window, then held there.
     */
    fun efficiencyAtHours(hours: Double, houses: HouseEffects = HouseEffects.NONE): Double {
        val fullHours = houses.fullStrengthHours
        val floor = houses.minEfficiency
        val window = houses.taperWindowHours
        return when {
            hours <= fullHours -> 1.0
            hours >= fullHours + window -> floor
            else -> 1.0 - (1.0 - floor) * ((hours - fullHours) / window)
        }
    }

    /** How much of the full-strength day [elapsedMs] away has used, 0..1; 1 once the taper begins. */
    fun fullStrengthFraction(elapsedMs: Long, houses: HouseEffects = HouseEffects.NONE): Double =
        (elapsedMs / 3_600_000.0 / houses.fullStrengthHours).coerceIn(0.0, 1.0)

    fun collect(state: IdleState, rate: IdleRate, nowMs: Long): IdleState {
        val gained = accrued(state, rate, nowMs)
        // Saturate instead of overflowing a near-MAX Long balance.
        val essence = state.essence.coerceAtLeast(0L)
        val newEssence = if (gained > Long.MAX_VALUE - essence) Long.MAX_VALUE else essence + gained
        return state.copy(essence = newEssence, lastCollectedAtMs = nowMs)
    }
}
