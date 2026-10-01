package com.ironvellum.app.domain

/**
 * What amending a sealed trial does to its XP (owner decision, Option A:
 * a delta correction). The trial's sets are re-priced before and after the
 * edit at the same bodyweight, so an untouched set list moves nothing, and
 * only the difference is applied:
 *
 * - Within [WINDOW_MS] of sealing an upward delta is capped against what
 *   the trial paid when SEALED: the trial can never hold more than double
 *   that, however many amendments raise it, and a cut can be restored.
 * - After the window an edit can only lower XP: a raise clamps to 0.
 * - A cut never takes more than the trial holds, nor more than the ledger
 *   holds, and the trial row and the ledger move by the same amount so the
 *   two can never drift apart.
 *
 * Deeds are never revoked and no amendment pays inscriptions; those rules
 * live with the caller, which only asks [withinWindow].
 */
object SealedEdit {

    /** 48 hours: long enough to notice a slip the next morning. */
    const val WINDOW_MS: Long = 48L * 60 * 60 * 1000

    data class Settlement(
        /** XP the edit moves, applied to the trial row and the ledger alike. */
        val applied: Int,
        /** The trial's xpAwarded after the edit. */
        val xpAwarded: Int,
        /** Whether the edit landed inside the raise window. */
        val withinWindow: Boolean,
        /** True when the sets are now worth more but the window had closed. */
        val raiseRefused: Boolean,
        /** True when the raise was cut down to the in-window cap. */
        val raiseCapped: Boolean,
    )

    /** A clock set back behind the seal still counts as inside the window. */
    fun withinWindow(completedAtMs: Long, nowMs: Long): Boolean = nowMs - completedAtMs <= WINDOW_MS

    fun settle(
        /** The trial's stored xpAwarded, quest bonus included. */
        xpAwarded: Int,
        /** What the trial paid when it was sealed, before any amendment. */
        sealedXp: Int,
        /** The stored sets re-priced before the edit. */
        oldSetsXp: Int,
        /** The draft re-priced, at the same bodyweight as [oldSetsXp]. */
        newSetsXp: Int,
        /** The ledger's total before the edit. */
        totalXp: Long,
        completedAtMs: Long,
        nowMs: Long,
    ): Settlement {
        val held = xpAwarded.coerceAtLeast(0)
        val sealed = sealedXp.coerceAtLeast(0)
        val within = withinWindow(completedAtMs, nowMs)
        val raw = newSetsXp - oldSetsXp
        // Room left under double the sealed figure; never negative.
        val headroom = (2 * sealed - held).coerceAtLeast(0)
        val applied = when {
            raw > 0 && within -> minOf(raw, sealed, headroom)
            raw > 0 -> 0
            // A cut: bounded by the trial's own XP, then by the ledger.
            else -> -minOf(-raw.toLong(), held.toLong(), totalXp.coerceAtLeast(0)).toInt()
        }
        return Settlement(
            applied = applied,
            xpAwarded = held + applied,
            withinWindow = within,
            raiseRefused = raw > 0 && !within,
            raiseCapped = raw > 0 && within && raw > applied,
        )
    }
}
