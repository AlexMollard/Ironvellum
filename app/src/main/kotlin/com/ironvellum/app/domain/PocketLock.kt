package com.ironvellum.app.domain

/**
 * The pocket lock's one rule: a dim cover over the live trial that ignores touches
 * until the lifter holds for [HOLD_MS]. A brush of fabric, a tap or a swipe never
 * gets there, so a phone in a pocket or a gym bag cannot log, skip or abandon
 * anything. Pure, so the rule is tested without a screen.
 */
object PocketLock {

    /** How long a finger must stay down to unlock. */
    const val HOLD_MS = 1_200

    /** How far through the hold [heldMs] is, 0 to 1. */
    fun progress(heldMs: Long): Float = (heldMs.toFloat() / HOLD_MS).coerceIn(0f, 1f)

    fun unlocks(heldMs: Long): Boolean = heldMs >= HOLD_MS

    /**
     * What the cover shows: the rest countdown while one runs, else the set in
     * hand, else nothing. [restLabel] is the timer's own "1:05".
     */
    fun summary(restLabel: String?, nextLine: String?): String? = when {
        restLabel != null -> "Rest $restLabel"
        nextLine != null -> nextLine
        else -> null
    }
}
