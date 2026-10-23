package com.ironvellum.app.domain

/**
 * What the lifter said about how long they have been lifting. Nothing said ([UNANSWERED]: every install from before the
 * question existed, and anyone who skipped setup) counts as experienced, so only [UNDER_TWO_MONTHS] slows the fatigue
 * estimate's recovery times.
 */
enum class LiftingExperience {
    UNANSWERED,
    UNDER_TWO_MONTHS,
    LONGER,
    ;

    companion object {
        fun fromWire(name: String?): LiftingExperience = entries.firstOrNull { it.name == name } ?: UNANSWERED
    }
}
