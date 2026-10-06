package com.ironvellum.app.domain

/**
 * Taking a sealed trial back ("Sealed too soon? Keep going"). The seal paid
 * XP and strength, so reopening is a ledger operation that must undo exactly
 * what the seal did, no more and no less, or a re-seal would pay twice.
 *
 * It is offered for [WINDOW_MS] after sealing, on the trial report. After
 * that the way to fix a trial is to amend it ([SealedEdit]).
 *
 * Deeds, inscriptions and a rank already announced are never taken back (the
 * house rule [SealedEdit] states): a re-seal finds them held and pays nothing
 * for them, so the end state equals a single seal.
 */
object SealedReopen {

    /** Ten minutes: long enough to notice the slip while the report is still open. */
    const val WINDOW_MS: Long = 10L * 60 * 1000

    enum class Refusal {
        /** The trial is still live: there is nothing to take back. */
        NOT_SEALED,

        /** More than [WINDOW_MS] since the seal. */
        WINDOW_CLOSED,

        /** Another trial is open; only one can be live at a time. */
        ANOTHER_LIVE,

        /** Amended since sealing: its XP is no longer what the seal paid. */
        AMENDED,

        /** The ledger holds less than the trial paid: a partial take-back would pay twice on re-seal. */
        LEDGER_SHORT,
    }

    sealed interface Decision {
        /** Take back exactly [xp] and [strength]: the figures the trial itself holds. */
        data class Reopen(val xp: Int, val strength: Int) : Decision

        data class Refuse(val reason: Refusal) : Decision
    }

    /** A clock set back behind the seal still counts as inside the window, as in [SealedEdit]. */
    fun withinWindow(completedAtMs: Long, nowMs: Long): Boolean = nowMs - completedAtMs <= WINDOW_MS

    fun decide(
        completedAtMs: Long?,
        /** The trial's stored xpAwarded, quest bonus included. */
        xpAwarded: Int,
        strengthScore: Int,
        amended: Boolean,
        anotherLive: Boolean,
        /** The ledger's figures now. */
        totalXp: Long,
        lifetimeStrength: Long,
        nowMs: Long,
    ): Decision = when {
        completedAtMs == null -> Decision.Refuse(Refusal.NOT_SEALED)
        !withinWindow(completedAtMs, nowMs) -> Decision.Refuse(Refusal.WINDOW_CLOSED)
        anotherLive -> Decision.Refuse(Refusal.ANOTHER_LIVE)
        amended -> Decision.Refuse(Refusal.AMENDED)
        totalXp < xpAwarded || lifetimeStrength < strengthScore -> Decision.Refuse(Refusal.LEDGER_SHORT)
        else -> Decision.Reopen(xpAwarded.coerceAtLeast(0), strengthScore.coerceAtLeast(0))
    }
}
