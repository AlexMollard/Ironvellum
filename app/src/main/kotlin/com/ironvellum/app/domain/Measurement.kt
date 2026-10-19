package com.ironvellum.app.domain

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words

/**
 * Each site carries its own technique note. A circumference is only comparable
 * to your own past numbers if the tape goes in the same place at the same
 * tension every time — an inconsistent method produces a trend line that
 * measures your measuring, not your training.
 */
enum class MeasurementSite(@StringRes val labelRes: Int, @StringRes val howToRes: Int) {
    NECK(R.string.measurement_site_neck, R.string.measurement_site_neck_how_to),
    SHOULDERS(R.string.measurement_site_shoulders, R.string.measurement_site_shoulders_how_to),
    CHEST(R.string.measurement_site_chest, R.string.measurement_site_chest_how_to),
    UPPER_ARM(R.string.measurement_site_upper_arm, R.string.measurement_site_upper_arm_how_to),
    FOREARM(R.string.measurement_site_forearm, R.string.measurement_site_forearm_how_to),
    WAIST(R.string.measurement_site_waist, R.string.measurement_site_waist_how_to),
    HIPS(R.string.measurement_site_hips, R.string.measurement_site_hips_how_to),
    THIGH(R.string.measurement_site_thigh, R.string.measurement_site_thigh_how_to),
    CALF(R.string.measurement_site_calf, R.string.measurement_site_calf_how_to),
    ;

    /** The site name on screen, in the current language. */
    val label: String get() = Words.text(UiText.res(labelRes))

    /** The technique note on screen, in the current language. */
    val howTo: String get() = Words.text(UiText.res(howToRes))
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
