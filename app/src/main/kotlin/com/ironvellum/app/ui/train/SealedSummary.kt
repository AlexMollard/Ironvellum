package com.ironvellum.app.ui.train

import com.ironvellum.app.domain.*

/** Read-only recovery: never recreate transient awards or re-price a sealed workout. */
data class SealedSummary(val session: WorkoutSession, val totals: WorkoutShare.Totals, val durationMinutes: Long) {
    companion object {
        fun from(session: WorkoutSession, sets: List<SessionSet>, exercises: List<Exercise>): SealedSummary? {
            val completed = session.completedAtMs ?: return null
            return SealedSummary(session, WorkoutShare.totals(sets, exercises.associateBy { it.id }),
                ((completed - session.startedAtMs) / 60000L).coerceAtLeast(1))
        }
    }
}
