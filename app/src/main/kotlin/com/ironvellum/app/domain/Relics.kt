package com.ironvellum.app.domain

import kotlin.math.exp

/**
 * How a collection of relics turns into one rate multiplier.
 *
 * The first implementation used only the STRONGEST relic, which meant a lifter
 * holding ten relics saw nine of them do nothing — owning a thing that has no
 * effect is not a reward. Every relic now counts.
 *
 * They cannot stack at full strength either: an inscription can yield a relic
 * and the reachable catalogue runs to dozens, so multiplying them together would take
 * the idle rate to absurd numbers within a few weeks and training — which is
 * meant to be the engine — would stop mattering.
 *
 * So relics stack on their EXCESS over 1.0, weighted by rank:
 *
 *     effective = 1 + (m1 - 1)/1 + (m2 - 1)/2 + (m3 - 1)/3 + ...
 *
 * Harmonic weights, not halving. A geometric decay was tried first and was
 * worse than the bug it fixed: by the eighth relic the weight rounded to zero
 * on screen, so a full vault openly displayed "0% WEIGHT" — every relic
 * counted in theory and visibly counted for nothing.
 *
 * Properties that matter:
 * - Strongest relic always applies in full, so a better relic is always an
 *   upgrade and never a downgrade.
 * - Every relic contributes a visible amount: the 48th still carries 2%.
 * - Harmonic sums grow without bound, so the total is capped at [EXCESS_CAP]
 *   times the best relic's excess. A vault of the entire catalogue lands near
 *   x5.5 rather than running away.
 */
object Relics {

    /**
     * Ceiling on stacked excess, as a multiple of the best relic's excess. The
     * strongest relic can never be worth less than a third of the total, so a
     * deep vault helps without eclipsing the search for a better relic.
     */
    private const val EXCESS_CAP = 3.0

    /**
     * Rate multiplier for a set of owned relics, strongest first. Values at or
     * below 1.0 contribute nothing; an empty vault yields exactly 1.0.
     */
    fun effectiveMultiplier(multipliers: List<Double>): Double {
        val held = multipliers.filter { it.isFinite() && it > 1.0 }.sortedDescending()
        if (held.isEmpty()) return 1.0
        val best = held.first() - 1.0
        // The rest SATURATE toward the ceiling rather than being clipped at it.
        // A hard min() made relics past the cap contribute exactly nothing,
        // which is the same dead-reward bug in a new place.
        val rest = held.drop(1).withIndex().sumOf { (i, m) -> (m - 1.0) * weightAt(i + 1) }
        val headroom = best * (EXCESS_CAP - 1.0)
        val contributed = if (headroom <= 0.0) 0.0 else headroom * (1.0 - exp(-rest / headroom))
        return 1.0 + best + contributed
    }

    /**
     * Weight a relic carries at [rank] in the vault, strongest first. Public so
     * the vault screen shows the SAME weight the rate uses, rather than its own
     * guess at the rule.
     */
    fun weightAt(rank: Int): Double = 1.0 / (rank + 1)
}
