package com.ironvellum.app.domain

/**
 * The decisions of a lifter's first run, as plain functions so they can be tested without a screen:
 * when the in-app notification ask is shown, when the Veil introduces itself, and when a restore
 * counts as having finished setup.
 */
object FirstRun {

    /**
     * How many times the notification explainer is shown at most. "Not now" does not use up the
     * system prompt's one chance, so it is offered again on the next trial, up to this many times in all.
     */
    const val MAX_NOTIFICATION_ASKS = 3

    /** Show the explainer only while the system prompt is unspent and "Not now" has not been said [MAX_NOTIFICATION_ASKS] times. */
    fun askForNotifications(asked: Boolean, notNowCount: Int): Boolean =
        !asked && notNowCount < MAX_NOTIFICATION_ASKS

    /**
     * The Veil introduces itself once, to a lifter who has seen none of it. Anyone with essence earned
     * (spent or not), a relic or a crest already knows their way round, so an existing install never sees it.
     */
    fun veilIntroDue(seen: Boolean, lifetimeEssence: Long, relics: Int, crests: Int): Boolean =
        !seen && lifetimeEssence <= 0L && relics <= 0 && crests <= 0

    /** A restored archive finishes setup only when it brought a body profile and at least one rite. */
    fun restoreCompletesSetup(heightCm: Double?, riteCount: Int): Boolean =
        BodyLimits.validHeight(heightCm) && riteCount > 0
}
