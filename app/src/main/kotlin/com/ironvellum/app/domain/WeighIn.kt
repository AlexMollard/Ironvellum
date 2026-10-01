package com.ironvellum.app.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Whether a weigh-in belongs to the same calendar day as another, so a step
 * that can be walked twice (onboarding's Back then Continue) refreshes the
 * day's reading instead of stacking a second one.
 */
object WeighIn {

    /** True when both instants fall on the same local date; a missing [previousMs] is never the same day. */
    fun sameDay(previousMs: Long?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        previousMs != null &&
            Instant.ofEpochMilli(previousMs).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
}