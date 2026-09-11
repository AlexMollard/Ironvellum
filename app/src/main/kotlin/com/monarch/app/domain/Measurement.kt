package com.monarch.app.domain

/** Sites worth tracking for a calisthenics/weighted athlete. */
enum class MeasurementSite(val label: String) {
    NECK("Neck"), SHOULDERS("Shoulders"), CHEST("Chest"), UPPER_ARM("Upper arm"),
    FOREARM("Forearm"), WAIST("Waist"), HIPS("Hips"), THIGH("Thigh"), CALF("Calf"),
}

data class MeasurementEntry(
    val id: Long = 0,
    val site: MeasurementSite,
    val takenAtMs: Long,
    val valueCm: Double,
)

/** A target for one site. Direction is derived from startCm vs targetCm, never asked for. */
data class MeasurementGoal(
    val site: MeasurementSite,
    val targetCm: Double,
    val setAtMs: Long,
    /** The reading when the goal was set — the baseline progress measures from. */
    val startCm: Double,
    val achievedAtMs: Long? = null,
)

data class GoalProgress(
    val goal: MeasurementGoal,
    val currentCm: Double?,
    /** 0f..1f from startCm toward targetCm; 1f when reached or passed. */
    val fraction: Float,
    val remainingCm: Double,
    /** Target below start (waist) vs above (arms). */
    val shrinking: Boolean,
    val achieved: Boolean,
)

object Measurements {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Newest reading per site; associateBy keeps the LAST occurrence, hence the sort first. */
    fun latest(entries: List<MeasurementEntry>): Map<MeasurementSite, MeasurementEntry> =
        entries.sortedBy { it.takenAtMs }.associateBy { it.site }

    /** Newest first. */
    fun history(entries: List<MeasurementEntry>, site: MeasurementSite): List<MeasurementEntry> =
        entries.filter { it.site == site }.sortedByDescending { it.takenAtMs }

    fun progress(goal: MeasurementGoal, entries: List<MeasurementEntry>): GoalProgress {
        val current = history(entries, goal.site).firstOrNull()?.valueCm
        val shrinking = goal.targetCm < goal.startCm
        if (current == null) {
            return GoalProgress(goal, currentCm = null, fraction = 0f, remainingCm = 0.0, shrinking = shrinking, achieved = false)
        }
        val range = goal.targetCm - goal.startCm
        val raw = if (range == 0.0) {
            // Zero-width goal: only the exact target counts, never a division.
            if (current == goal.targetCm) 1.0 else 0.0
        } else {
            (current - goal.startCm) / range
        }
        val achieved = if (shrinking) current <= goal.targetCm else current >= goal.targetCm
        return GoalProgress(
            goal = goal,
            currentCm = current,
            fraction = raw.coerceIn(0.0, 1.0).toFloat(),
            remainingCm = kotlin.math.abs(goal.targetCm - current),
            shrinking = shrinking,
            achieved = achieved,
        )
    }

    /**
     * Change over the trailing [days], anchored at the newest reading for the
     * site; null when there is nothing to compare.
     */
    fun deltaCm(entries: List<MeasurementEntry>, site: MeasurementSite, days: Int = 30): Double? {
        val readings = history(entries, site)
        if (readings.size < 2) return null
        val windowStart = readings.first().takenAtMs - days * DAY_MS
        val inWindow = readings.filter { it.takenAtMs >= windowStart }.sortedBy { it.takenAtMs }
        if (inWindow.size < 2) return null
        return inWindow.last().valueCm - inWindow.first().valueCm
    }
}
