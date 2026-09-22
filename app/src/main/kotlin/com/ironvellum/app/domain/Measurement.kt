package com.ironvellum.app.domain

/**
 * Each site carries its own technique note. A circumference is only comparable
 * to your own past numbers if the tape goes in the same place at the same
 * tension every time — an inconsistent method produces a trend line that
 * measures your measuring, not your training.
 */
enum class MeasurementSite(val label: String, val howTo: String) {
    NECK(
        "Neck",
        "Just below the Adam's apple, tape level and snug without pressing in. " +
            "Look straight ahead, shoulders relaxed.",
    ),
    SHOULDERS(
        "Shoulders",
        "Widest point around the deltoids, arms hanging loose. Keep the tape " +
            "level front and back — easiest with a mirror.",
    ),
    CHEST(
        "Chest",
        "Widest part, tape level under the armpits across the nipple line. " +
            "Measure at the end of a normal exhale, never on a held breath.",
    ),
    UPPER_ARM(
        "Upper arm",
        "Midway between shoulder and elbow. Pick flexed OR relaxed and keep it " +
            "that way forever — mixing the two invents gains. Same arm each time.",
    ),
    FOREARM(
        "Forearm",
        "Widest point below the elbow, arm hanging straight, hand open. " +
            "A clenched fist adds size that is not there.",
    ),
    WAIST(
        "Waist",
        "Narrowest point, usually just above the navel. Relaxed belly, normal " +
            "exhale, no sucking in. Morning before food is the most repeatable.",
    ),
    HIPS(
        "Hips",
        "Widest point around the glutes, feet together, weight even. " +
            "Tape level the whole way round.",
    ),
    THIGH(
        "Thigh",
        "Widest point high on the leg, just below the glute fold. Stand tall, " +
            "weight on both feet, muscle relaxed. Same leg every time.",
    ),
    CALF(
        "Calf",
        "Widest point mid-calf, standing with weight even on both feet. " +
            "Seated or on tiptoe gives a different number.",
    ),
}

data class MeasurementEntry(
    val id: Long = 0,
    val site: MeasurementSite,
    val takenAtMs: Long,
    val valueCm: Double,
)

object Measurements {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Newest reading per site; associateBy keeps the LAST occurrence, hence the sort first. */
    fun latest(entries: List<MeasurementEntry>): Map<MeasurementSite, MeasurementEntry> =
        entries.sortedBy { it.takenAtMs }.associateBy { it.site }

    /** Newest first. */
    fun history(entries: List<MeasurementEntry>, site: MeasurementSite): List<MeasurementEntry> =
        entries.filter { it.site == site }.sortedByDescending { it.takenAtMs }

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
